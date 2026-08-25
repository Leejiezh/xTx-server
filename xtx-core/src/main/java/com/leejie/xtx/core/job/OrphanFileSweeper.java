package com.leejie.xtx.core.job;

import com.leejie.xtx.core.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 孤儿文件清理任务：删掉超过宽限期仍未附加（TEMP）或已被移除（DETACHED）的文件。
 *
 * <p>凌晨 3 点跑：这个任务会真删 MinIO 对象，放在低峰期，避免与用户上传抢带宽。
 *
 * <p>单实例部署下无需分布式锁；一旦多副本，需要加锁或改由外部调度触发，
 * 否则多个实例同时扫同一批行会互相踩（虽然 S3 DELETE 幂等，日志与计数会失真）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanFileSweeper {

    private final FileService fileService;

    @Scheduled(cron = "0 0 3 * * *")
    public void sweep() {
        int n = fileService.sweepOrphans();
        log.info("OrphanFileSweeper 清理 {} 个孤儿文件", n);
    }
}
