package com.al.lightq.config;

import com.al.lightq.util.LightQConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Configuration for asynchronous processing.
 * <p>
 * This class sets up a thread pool for executing tasks asynchronously.
 * It also configures the thread pool to propagate the MDC context to child threads.
 * </p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Creates a thread pool task executor.
     *
     * @return the thread pool task executor
     */
    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(LightQConstants.CORE_POOL_SIZE);
        executor.setMaxPoolSize(LightQConstants.MAX_POOL_SIZE);
        executor.setQueueCapacity(LightQConstants.QUEUE_CAPACITY);
        executor.setThreadNamePrefix(LightQConstants.THREAD_NAME_PREFIX);
        executor.setTaskDecorator(runnable -> {
            final java.util.Map<String, String> contextMap = MDC.getCopyOfContextMap();
            return () -> {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                try {
                    runnable.run();
                } finally {
                    MDC.clear();
                }
            };
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
