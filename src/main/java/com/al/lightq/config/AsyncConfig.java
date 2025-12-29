package com.al.lightq.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuration for asynchronous processing.
 * <p>
 * This class sets up a thread pool for executing tasks asynchronously. It also
 * configures the thread pool to propagate the MDC context to child threads.
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
	public ThreadPoolTaskExecutor taskExecutor(LightQProperties props) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(props.getCorePoolSize());
		executor.setMaxPoolSize(props.getMaxPoolSize());
		executor.setQueueCapacity(props.getQueueCapacity());
		executor.setThreadNamePrefix(props.getThreadNamePrefix());
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
		executor.setAllowCoreThreadTimeOut(props.isAllowCoreThreadTimeout());
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(props.getAwaitTerminationSeconds());
		executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
		executor.initialize();
		return executor;
	}
}
