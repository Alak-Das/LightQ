package com.al.lightq.config;

import com.al.lightq.util.LightQConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(LightQConstants.CORE_POOL_SIZE);
        executor.setMaxPoolSize(LightQConstants.MAX_POOL_SIZE);
        executor.setQueueCapacity(LightQConstants.QUEUE_CAPACITY);
        executor.setThreadNamePrefix(LightQConstants.THREAD_NAME_PREFIX);
        executor.initialize();
        return executor;
    }
}
