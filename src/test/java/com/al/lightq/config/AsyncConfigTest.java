package com.al.lightq.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

class AsyncConfigTest {

	private AsyncConfig asyncConfig;

	@BeforeEach
	void setUp() {
		asyncConfig = new AsyncConfig();
	}

	@Test
	void taskExecutorBean() {
		TaskExecutor executor = asyncConfig.taskExecutor();
		assertNotNull(executor, "TaskExecutor bean should not be null");
		// Virtual thread executor doesn't expose pool metrics like
		// ThreadPoolTaskExecutor
		// We just verify it's created successfully
	}
}
