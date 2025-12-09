package com.al.simplequeueservice.config;

import com.al.simplequeueservice.util.SQSConstants;
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
        executor.setCorePoolSize(SQSConstants.CORE_POOL_SIZE);
        executor.setMaxPoolSize(SQSConstants.MAX_POOL_SIZE);
        executor.setQueueCapacity(SQSConstants.QUEUE_CAPACITY);
        executor.setThreadNamePrefix(SQSConstants.THREAD_NAME_PREFIX);
        executor.initialize();
        return executor;
    }
}
