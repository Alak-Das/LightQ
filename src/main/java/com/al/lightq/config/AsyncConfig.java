package com.al.lightq.config;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Configuration for asynchronous processing using Java 21 Virtual Threads.
 * <p>
 * Virtual threads provide lightweight, scalable concurrency for I/O-bound
 * operations. They eliminate thread pool sizing concerns and enable massive
 * concurrency with minimal overhead.
 * </p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

	/**
	 * Creates a virtual thread executor with MDC context propagation.
	 * <p>
	 * Virtual threads are ideal for I/O-bound operations like database and Redis
	 * calls. Each task runs on its own virtual thread, providing unlimited
	 * scalability without traditional thread pool constraints.
	 * </p>
	 *
	 * @return the task executor backed by virtual threads
	 */
	@Bean(name = "taskExecutor")
	public TaskExecutor taskExecutor() {
		// Create a virtual thread executor with custom thread factory for naming
		Executor virtualThreadExecutor = Executors.newThreadPerTaskExecutor(
				Thread.ofVirtual().name("lightq-vt-", 0).factory());

		// Wrap with TaskExecutorAdapter and add MDC context propagation
		return new TaskExecutorAdapter(virtualThreadExecutor) {
			@Override
			public void execute(Runnable task) {
				final Map<String, String> contextMap = MDC.getCopyOfContextMap();
				super.execute(() -> {
					if (contextMap != null) {
						MDC.setContextMap(contextMap);
					}
					try {
						task.run();
					} finally {
						MDC.clear();
					}
				});
			}
		};
	}
}
