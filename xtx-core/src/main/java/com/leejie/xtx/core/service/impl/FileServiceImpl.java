package com.leejie.xtx.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.leejie.xtx.common.base.security.CurrentUserProvider;
import com.leejie.xtx.common.exception.BusinessException;
import com.leejie.xtx.core.config.FileProperties;
import com.leejie.xtx.core.config.MinioConfig;
import com.leejie.xtx.core.dto.PresignReq;
import com.leejie.xtx.core.dto.PresignResp;
import com.leejie.xtx.core.dto.UploadResp;
import com.leejie.xtx.core.entity.FileMetadata;
import com.leejie.xtx.core.entity.User;
import com.leejie.xtx.core.mapper.FileMetadataMapper;
import com.leejie.xtx.core.mapper.UserMapper;
import com.leejie.xtx.core.service.FileService;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PostPolicy;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文件服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private static final String STATUS_TEMP = "TEMP";
    private static final String STATUS_ATTACHED = "ATTACHED";
    private static final String STATUS_DETACHED = "DETACHED";

    /** objectKey 的首段，同时用于反推大小上限（见 {@link #sizeLimitOf}） */
    private static final String PREFIX_IMAGE = "img";
    private static final String PREFIX_DOCUMENT = "file";

    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** 扩展名长度上限：客户端文件名不可信，不能让它无界地撑长 objectKey */
    private static final int MAX_EXT_LENGTH = 10;

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final FileMetadataMapper fileMetadataMapper;
    private final FileProperties fileProperties;
    private final CurrentUserProvider currentUser;
    private final UserMapper userMapper;

    @Override
    public PresignResp presign(PresignReq req) {
        Long userId = currentUser.currentUserId();
        if (!isAllowed(fileProperties.getImageTypes(), req.contentType())) {
            throw new BusinessException(422, "不支持的图片类型: " + req.contentType());
        }
        if (req.size() == null || req.size() <= 0 || req.size() > fileProperties.getImageMaxSize()) {
            throw new BusinessException(422, "图片大小超过限制");
        }

        String objectKey = buildObjectKey(PREFIX_IMAGE, userId, req.contentType(), req.originalFilename());
        // 先签表单再落库：反序则表单已签出而元数据缺失，前端能传上一个后端认不出的对象
        PresignResp resp = buildPostForm(objectKey, req.contentType());
        insertTemp(objectKey, userId, req.originalFilename(), req.contentType(), req.size());

        return resp;
    }

    @Override
    public UploadResp upload(MultipartFile file) {
        Long userId = currentUser.currentUserId();
        String contentType = file.getContentType();
        boolean isImage = contentType != null && contentType.startsWith("image/");
        if (isImage) {
            if (!isAllowed(fileProperties.getImageTypes(), contentType)) {
                throw new BusinessException(422, "不支持的图片类型: " + contentType);
            }
            if (file.getSize() > fileProperties.getImageMaxSize()) {
                throw new BusinessException(422, "图片大小超过限制");
            }
        } else {
            if (!isAllowed(fileProperties.getDocumentTypes(), contentType)) {
                throw new BusinessException(422, "不支持的文件类型: " + contentType);
            }
            if (file.getSize() > fileProperties.getDocumentMaxSize()) {
                throw new BusinessException(422, "文件大小超过限制");
            }
        }

        String objectKey = buildObjectKey(isImage ? PREFIX_IMAGE : PREFIX_DOCUMENT,
                userId, contentType, file.getOriginalFilename());
        try (InputStream in = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectKey)
                    .stream(in, file.getSize(), -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new BusinessException("上传文件失败: " + e.getMessage());
        }
        insertTemp(objectKey, userId, file.getOriginalFilename(), contentType, file.getSize());

        return new UploadResp(objectKey, file.getOriginalFilename(), contentType, file.getSize());
    }

    @Override
    public String accessUrl(String objectKey, boolean download) {
        FileMetadata meta = getOwnedFile(objectKey);
        return presignedGetUrl(objectKey, meta, download);
    }

    @Override
    public List<String> accessUrls(List<String> objectKeys) {
        if (CollectionUtils.isEmpty(objectKeys)) {
            return List.of();
        }
        Long userId = currentUser.currentUserId();
        Map<String, FileMetadata> byKey = fileMetadataMapper.selectBatchIds(objectKeys).stream()
                .collect(Collectors.toMap(FileMetadata::getObjectKey, Function.identity()));

        List<String> urls = new ArrayList<>(objectKeys.size());
        for (String key : objectKeys) {
            FileMetadata meta = byKey.get(key);
            if (meta == null || !meta.getUserId().equals(userId)) {
                // 列表读路径：一条脏数据不该让整页 404，跳过并留日志追查
                log.warn("跳过无法签发的 objectKey: {}（元数据缺失或不属于用户 {}）", key, userId);
                continue;
            }
            urls.add(presignedGetUrl(key, meta, false));
        }
        return urls;
    }

    @Override
    public void delete(String objectKey) {
        getOwnedFile(objectKey);
        // 先删对象、失败就抛：元数据行留着，用户可重试。反序则行没了而对象永久失联
        removeObject(objectKey);
        fileMetadataMapper.deleteById(objectKey);
    }

    @Override
    public void requireOwned(String objectKey) {
        getOwnedFile(objectKey);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void attach(Long recordId, List<String> objectKeys) {
        if (CollectionUtils.isEmpty(objectKeys)) {
            return;
        }
        Long userId = currentUser.currentUserId();
        Map<String, FileMetadata> byKey = fileMetadataMapper.selectBatchIds(objectKeys).stream()
                .collect(Collectors.toMap(FileMetadata::getObjectKey, Function.identity()));

        for (String key : new LinkedHashSet<>(objectKeys)) {
            FileMetadata meta = byKey.get(key);
            if (meta == null || !meta.getUserId().equals(userId)) {
                throw new BusinessException(422, "无效的文件: " + key);
            }
            // 一个 key 被两条记录共用时，从其中一条移除会把它标 DETACHED 并最终清理，
            // 另一条的图片随之裂掉 —— 因此禁止跨记录复用
            if (STATUS_ATTACHED.equals(meta.getStatus()) && !recordId.equals(meta.getRecordId())) {
                throw new BusinessException(422, "文件已附加到其他记录: " + key);
            }

            FileMetadata update = new FileMetadata();
            update.setObjectKey(key);
            update.setStatus(STATUS_ATTACHED);
            update.setRecordId(recordId);
            update.setAttachedAt(LocalDateTime.now());
            update.setSize(requireUploaded(key));
            fileMetadataMapper.updateById(update);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reconcile(Long recordId, List<String> newKeys, List<String> oldKeys) {
        Set<String> newSet = newKeys == null ? Set.of() : new LinkedHashSet<>(newKeys);
        Set<String> oldSet = oldKeys == null ? Set.of() : new LinkedHashSet<>(oldKeys);
        attach(recordId, newSet.stream().filter(k -> !oldSet.contains(k)).toList());
        detach(oldSet.stream().filter(k -> !newSet.contains(k)).toList());
    }

    @Override
    public void detachAll(List<String> objectKeys) {
        detach(objectKeys);
    }

    @Override
    public int sweepOrphans() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(fileProperties.getOrphanGraceHours());
        QueryWrapper<FileMetadata> wrapper = new QueryWrapper<FileMetadata>()
                .in("status", STATUS_TEMP, STATUS_DETACHED)
                .lt("created_at", cutoff);

        List<FileMetadata> orphans = fileMetadataMapper.selectList(wrapper);
        // 用户头像停在被 user.avatar_url 引用、不进任何记录 → 永远是 TEMP。
        // 先排除被引用的 key，否则头像会被当孤儿在宽限期后删掉（见头像持久化设计）。
        Set<String> avatarKeys = referencedAvatarKeys(orphans);
        int deleted = 0;
        for (FileMetadata meta : orphans) {
            if (avatarKeys.contains(meta.getObjectKey())) {
                continue;
            }
            // 对象删不掉就留着这一行，下一轮重扫时重试 —— 先删行会让残留对象永久失联
            if (!tryRemoveObject(meta.getObjectKey())) {
                continue;
            }
            fileMetadataMapper.deleteById(meta.getObjectKey());
            deleted++;
        }
        return deleted;
    }

    /** 收集被 user.avatar_url 引用的 objectKey：这些是有效头像，不是孤儿 */
    private Set<String> referencedAvatarKeys(List<FileMetadata> orphans) {
        if (CollectionUtils.isEmpty(orphans)) {
            return Set.of();
        }
        List<String> keys = orphans.stream().map(FileMetadata::getObjectKey).toList();
        return userMapper.selectList(new QueryWrapper<User>()
                        .select("avatar_url")
                        .isNotNull("avatar_url")
                        .in("avatar_url", keys))
                .stream()
                .map(User::getAvatarUrl)
                .collect(Collectors.toSet());
    }

    // ---------------- 内部辅助 ----------------

    /**
     * content-type 白名单判定。
     *
     * <p>必须先挡 null：白名单默认值是 {@code List.of(...)}，不可变集合的
     * {@code contains(null)} 抛 NPE 而不是返回 false —— 缺 content-type 的请求
     * 会变成 500 而非 422。
     */
    private boolean isAllowed(List<String> whitelist, String contentType) {
        return contentType != null && !contentType.isBlank() && whitelist.contains(contentType);
    }

    /**
     * objectKey 形如 {@code img/42/2026/08/24/3f2a...c1.jpg}。
     *
     * <p>userId 放进路径而非仅存表里：清理与排障时能从 key 直接看出归属，
     * 也让「按用户前缀批量操作」成为可能。
     */
    String buildObjectKey(String typePrefix, Long userId, String contentType, String originalFilename) {
        return "%s/%d/%s/%s%s".formatted(
                typePrefix,
                userId,
                LocalDate.now().format(DATE_PATH),
                UUID.randomUUID().toString().replace("-", ""),
                resolveExtension(contentType, originalFilename));
    }

    /**
     * 取扩展名，优先用原始文件名，取不到才按 content-type 推。
     *
     * <p>必须洗掉非字母数字字符：文件名来自客户端，{@code "a.jp/g"} 这类输入会把
     * 斜杠带进 objectKey，从而把对象写到 {@code img/{userId}/} 前缀之外。
     */
    private String resolveExtension(String contentType, String originalFilename) {
        String raw = null;
        if (originalFilename != null) {
            int dot = originalFilename.lastIndexOf('.');
            if (dot >= 0 && dot < originalFilename.length() - 1) {
                raw = originalFilename.substring(dot + 1);
            }
        }
        if (raw == null) {
            raw = switch (contentType == null ? "" : contentType) {
                case "image/jpeg" -> "jpg";
                case "image/png" -> "png";
                case "image/webp" -> "webp";
                case "image/gif" -> "gif";
                case "application/pdf" -> "pdf";
                case "application/zip" -> "zip";
                case "text/plain" -> "txt";
                default -> "";
            };
        }
        String cleaned = raw.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
        if (cleaned.isEmpty()) {
            return "";
        }
        return "." + (cleaned.length() > MAX_EXT_LENGTH ? cleaned.substring(0, MAX_EXT_LENGTH) : cleaned);
    }

    private void insertTemp(String objectKey, Long userId, String originalFilename, String contentType, Long size) {
        FileMetadata meta = new FileMetadata();
        meta.setObjectKey(objectKey);
        meta.setUserId(userId);
        meta.setOriginalFilename(originalFilename);
        meta.setContentType(contentType);
        meta.setSize(size);
        meta.setStatus(STATUS_TEMP);
        fileMetadataMapper.insert(meta);
    }

    /** 不属于当前用户的 key 一律按「不存在」返回 404，与 {@code OwnedServiceImpl} 同一取舍 */
    private FileMetadata getOwnedFile(String objectKey) {
        FileMetadata meta = fileMetadataMapper.selectById(objectKey);
        if (meta == null || !meta.getUserId().equals(currentUser.currentUserId())) {
            throw new BusinessException(404, "文件不存在");
        }
        return meta;
    }

    /**
     * 确认对象已真的传上来，并返回其真实大小。
     *
     * <p>预签名 PUT 无法在 MinIO 侧限制大小，presign 时校验的只是前端自报的 size；
     * 这里按 statObject 拿到的真实字节数再挡一次，否则「声明 1KB 实传 100MB」畅通无阻。
     *
     * @throws BusinessException 422，对象不存在或真实大小超限
     */
    private long requireUploaded(String objectKey) {
        StatObjectResponse stat;
        try {
            stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new BusinessException(422, "文件尚未上传: " + objectKey);
        }
        if (stat.size() > sizeLimitOf(objectKey)) {
            throw new BusinessException(422, "文件大小超过限制: " + objectKey);
        }
        return stat.size();
    }

    private long sizeLimitOf(String objectKey) {
        return objectKey.startsWith(PREFIX_IMAGE + "/")
                ? fileProperties.getImageMaxSize()
                : fileProperties.getDocumentMaxSize();
    }

    /**
     * 批量标 DETACHED 进入宽限期。带 user_id 条件：即便调用方传进别人的 key 也改不动。
     *
     * <p>不物理删除，宽限期内可恢复（见 ADR-0003）。
     */
    private void detach(List<String> objectKeys) {
        if (CollectionUtils.isEmpty(objectKeys)) {
            return;
        }
        UpdateWrapper<FileMetadata> wrapper = new UpdateWrapper<FileMetadata>()
                .in("object_key", objectKeys)
                .eq("user_id", currentUser.currentUserId())
                .set("status", STATUS_DETACHED)
                .set("record_id", null);
        fileMetadataMapper.update(null, wrapper);
    }

    /**
     * S3 POST 表单直传，配合小程序 {@code wx.uploadFile}（它只能发 multipart POST，发不了 PUT）。
     *
     * <p>SDK 返回的 formData 只含 policy/x-amz-* /signature，**不含 key 与 Content-Type**——
     * 这两个字段以 eq 条件进了 policy，form 里必须带同名字段才能通过 MinIO 校验，故这里补齐。
     */
    private PresignResp buildPostForm(String objectKey, String contentType) {
        try {
            PostPolicy policy = new PostPolicy(minioConfig.getBucket(),
                    ZonedDateTime.now().plusSeconds(fileProperties.getPresignedPutExpiry()));
            policy.addEqualsCondition("key", objectKey);
            policy.addEqualsCondition("Content-Type", contentType);

            Map<String, String> formData = minioClient.getPresignedPostFormData(policy);
            formData.put("key", objectKey);
            formData.put("Content-Type", contentType);

            long expiresAt = System.currentTimeMillis() + fileProperties.getPresignedPutExpiry() * 1000L;
            return new PresignResp(postUrl(), formData, objectKey, expiresAt);
        } catch (Exception e) {
            throw new BusinessException("生成上传表单失败: " + e.getMessage());
        }
    }

    private String postUrl() {
        // endpoint 可能带尾斜杠，规整后再拼 bucket
        return minioConfig.getEndpoint().replaceAll("/+$", "") + "/" + minioConfig.getBucket();
    }

    private String presignedGetUrl(String objectKey, FileMetadata meta, boolean download) {
        try {
            String disposition = "inline";
            if (download) {
                String name = meta.getOriginalFilename() != null ? meta.getOriginalFilename() : "file";
                // 去掉引号避免截断 header；objectKey 里只有 uuid，原始名只能从元数据取
                disposition = "attachment; filename=\"" + name.replace("\"", "") + "\"";
            }
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(minioConfig.getBucket())
                    .object(objectKey)
                    .expiry(fileProperties.getPresignedGetExpiry())
                    .extraQueryParams(Map.of("response-content-disposition", disposition))
                    .build());
        } catch (Exception e) {
            throw new BusinessException("生成访问URL失败: " + e.getMessage());
        }
    }

    /**
     * 删除 MinIO 对象。S3 的 DELETE 是幂等的（删不存在的 key 也成功），
     * 因此这里抛出的一定是真实故障，值得让调用方看见。
     */
    private void removeObject(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new BusinessException("删除文件失败: " + e.getMessage());
        }
    }

    /**
     * 同上但不抛：{@link #sweepOrphans} 是批量清理，一个对象删不掉不该让整批停下。
     *
     * @return 是否删除成功；失败时调用方应保留元数据行以便下一轮重试
     */
    private boolean tryRemoveObject(String objectKey) {
        try {
            removeObject(objectKey);
            return true;
        } catch (Exception e) {
            log.warn("删除 MinIO 对象失败，保留元数据行待下轮重试: {} - {}", objectKey, e.getMessage());
            return false;
        }
    }
}
