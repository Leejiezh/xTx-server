package com.leejie.xtx.core.service;

import com.leejie.xtx.core.dto.PresignReq;
import com.leejie.xtx.core.dto.PresignResp;
import com.leejie.xtx.core.dto.UploadResp;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件服务。
 *
 * <p>两条上传路径（见 ADR-0001）：图片走 {@link #presign} 由前端拿 S3 POST 表单直传 MinIO，
 * 不经后端字节；非图片走 {@link #upload} 后端代理，以便校验并留下原始文件名。
 *
 * <p>DB 永久存 objectKey，读时才现签发 access URL（见 ADR-0002）——
 * 因此不存在「URL 过期导致图片裂掉」。
 *
 * <p>与 {@code OwnedService} 一样，所有方法都不接 userId 参数：归属由
 * {@code CurrentUserProvider} 决定，调用者没有「访问别人文件」的表达能力。
 * 唯一例外是 {@link #sweepOrphans}，它跑在定时任务里、没有登录态。
 */
public interface FileService {

    /**
     * 图片预签名直传：校验后生成 objectKey、落一条 TEMP 元数据、返回 S3 POST 表单
     * （{@code postUrl + formData}，配合小程序 {@code wx.uploadFile} 直传）。
     *
     * @throws com.leejie.xtx.common.exception.BusinessException 422，类型不在白名单或声明大小超限
     */
    PresignResp presign(PresignReq req);

    /**
     * 代理上传：后端接收字节、校验、写入 MinIO，落一条 TEMP 元数据。
     * 图片也允许走这条路（小程序端不便直传时的退路）。
     *
     * @throws com.leejie.xtx.common.exception.BusinessException 422，类型不在白名单或超限
     */
    UploadResp upload(MultipartFile file);

    /**
     * 签发单个文件的 access URL。
     *
     * @param download true 走 {@code attachment} 附原始文件名；false 走 {@code inline} 预览
     * @throws com.leejie.xtx.common.exception.BusinessException 404，不存在或不属于当前用户
     */
    String accessUrl(String objectKey, boolean download);

    /**
     * 批量签发预览用 access URL，保持入参顺序。
     *
     * <p>与 {@link #accessUrl} 不同，这里对不存在/非本人的 key 只跳过并记日志：
     * 这是列表读路径，不该因为一条脏数据让整页 404。
     */
    List<String> accessUrls(List<String> objectKeys);

    /**
     * 删除文件（MinIO 对象 + 元数据行一并物理删除）。
     *
     * @throws com.leejie.xtx.common.exception.BusinessException 404，不存在或不属于当前用户
     */
    void delete(String objectKey);

    /**
     * 把文件附加到记录：标 ATTACHED，从此不再被孤儿清理任务回收。
     *
     * @throws com.leejie.xtx.common.exception.BusinessException 422，key 无效、尚未上传、
     *                                                          真实大小超限或已附加到别的记录
     */
    void attach(Long recordId, List<String> objectKeys);

    /**
     * 记录更新时增量同步：新增的标 ATTACHED，被移除的标 DETACHED（进入 24h 宽限期）。
     */
    void reconcile(Long recordId, List<String> newKeys, List<String> oldKeys);

    /**
     * 记录删除时全部标 DETACHED —— 记录是逻辑删除，文件也只进宽限期而非立即物理删除。
     */
    void detachAll(List<String> objectKeys);

    /**
     * 清理孤儿：删除超过宽限期的 TEMP/DETACHED 元数据行及其 MinIO 对象。
     *
     * <p>无登录态，跑在 {@code OrphanFileSweeper} 定时任务里，处理全体用户。
     *
     * @return 实际清理的文件数
     */
    int sweepOrphans();
}
