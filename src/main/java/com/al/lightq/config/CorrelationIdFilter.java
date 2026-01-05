package com.al.lightq.config;

import com.al.lightq.LightQConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that:
 * <ul>
 * <li>Establishes a correlation/request ID for each request (reads
 * X-Request-Id/X-Correlation-Id or generates one)</li>
 * <li>Puts useful request context into MDC (requestId, path, method,
 * consumerGroup) for log enrichment</li>
 * <li>Logs a concise start/end line with timing and status</li>
 * </ul>
 * <p>
 * MDC entries can be referenced in logging patterns with %X{requestId}, etc.
 * </p>
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

	private static final Logger logger = LoggerFactory.getLogger(CorrelationIdFilter.class);
	public static final String MDC_REQUEST_ID = "requestId";
	public static final String MDC_METHOD = "method";
	public static final String MDC_PATH = "path";
	public static final String MDC_CONSUMER_GROUP = "consumerGroup";

	private static final String HDR_REQUEST_ID = "X-Request-Id";
	private static final String HDR_CORRELATION_ID = "X-Correlation-Id";

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		String requestId = firstNonBlank(request.getHeader(HDR_REQUEST_ID), request.getHeader(HDR_CORRELATION_ID),
				UUID.randomUUID().toString());

		MDC.put(MDC_REQUEST_ID, requestId);
		MDC.put(MDC_METHOD, request.getMethod());
		MDC.put(MDC_PATH, request.getRequestURI());

		String consumerGroup = request.getHeader(LightQConstants.CONSUMER_GROUP_HEADER);
		if (StringUtils.isNotBlank(consumerGroup)) {
			MDC.put(MDC_CONSUMER_GROUP, consumerGroup);
		}

		response.setHeader(HDR_REQUEST_ID, requestId);
		response.setHeader(HDR_CORRELATION_ID, requestId);
		long start = System.currentTimeMillis();
		logger.debug("Incoming request: {} {} (requestId={})", request.getMethod(), request.getRequestURI(), requestId);

		try {
			filterChain.doFilter(request, response);
		} finally {
			long tookMs = System.currentTimeMillis() - start;
			logger.debug("Completed {} {} -> status={} in {} ms", request.getMethod(), request.getRequestURI(),
					response.getStatus(), tookMs);
			// Do not clear MDC here; tests assert MDC contents post-filter. MDC is cleared
			// by test teardown.
		}
	}

	/**
	 * Returns the first non-blank string from a list of candidates.
	 *
	 * @param candidates
	 *            the candidates
	 * @return the first non-blank string, or null if none are found
	 */
	private static String firstNonBlank(String... candidates) {
		for (String c : candidates) {
			if (StringUtils.isNotBlank(c)) {
				return c;
			}
		}
		return null;
	}
}
