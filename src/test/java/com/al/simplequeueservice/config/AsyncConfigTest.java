package com.al.simplequeueservice.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AsyncConfigTest {

    private AsyncConfig asyncConfig;

    @BeforeEach
    void setUp() {
        asyncConfig = new AsyncConfig();
    }

    @Test
    void taskExecutorBean() {
        ThreadPoolTaskExecutor executor = asyncConfig.taskExecutor();
        assertNotNull(executor);
        assertEquals(5, executor.getCorePoolSize());
        assertEquals(10, executor.getMaxPoolSize());
        assertEquals(25, executor.getQueueCapacity());
        assertEquals("DBDataUpdater-", executor.getThreadNamePrefix());
    }
}
