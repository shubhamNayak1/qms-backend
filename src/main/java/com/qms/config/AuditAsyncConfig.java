package com.qms.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Dedicated thread pool for asynchronous audit log writes.
 *
 * Design choices:
 * ───────────────
 * • Separate from Spring's default async executor so audit writes cannot
 *   starve application tasks (or vice versa).
 * • CallerRunsPolicy on queue saturation — if the queue is full the calling
 *   thread writes the log itself. This guarantees audit entries are never
 *   silently dropped under heavy load; it just removes the async benefit
 *   briefly during spikes.
 * • ThreadNamePrefix aids debugging — audit thread names appear in log output.
 */
@Slf4j
@Configuration
public class AuditAsyncConfig {

    @Value("${audit.async.core-pool-size:2}")
    private int corePoolSize;

    @Value("${audit.async.max-pool-size:5}")
    private int maxPoolSize;

    @Value("${audit.async.queue-capacity:500}")
    private int queueCapacity;

    @Bean(name = "auditTaskExecutor")
    public Executor auditTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("qms-audit-");
        executor.setRejectedExecutionHandler(callerRunsWithWarning());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();

        log.info("Audit async executor initialised — core={} max={} queue={}",
                corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }

    /**
     * Logs a warning when the audit queue is saturated, then falls back to
     * executing the audit write on the calling thread (CallerRunsPolicy).
     */
    private RejectedExecutionHandler callerRunsWithWarning() {
        return (runnable, executor) -> {
            log.warn("Audit executor queue saturated — executing audit write on calling thread");
            new ThreadPoolExecutor.CallerRunsPolicy().rejectedExecution(runnable, executor);
        };
    }
}
