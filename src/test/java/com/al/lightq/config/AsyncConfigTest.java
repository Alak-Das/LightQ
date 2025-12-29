package com.al.lightq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class AsyncConfigTest {

	private AsyncConfig asyncConfig;

	@BeforeEach
	void setUp() {
		asyncConfig = new AsyncConfig();
	}

	@Test
	void taskExecutorBean() {
		LightQProperties props = new LightQProperties();
		ThreadPoolTaskExecutor executor = asyncConfig.taskExecutor(props);
		assertNotNull(executor);
		assertEquals(5, executor.getCorePoolSize());
		assertEquals(10, executor.getMaxPoolSize());
		assertEquals(25, executor.getQueueCapacity());
		assertEquals("DBDataUpdater-", executor.getThreadNamePrefix());
	}
}
