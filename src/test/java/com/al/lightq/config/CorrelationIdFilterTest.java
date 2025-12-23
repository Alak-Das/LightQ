package com.al.lightq.config;

import com.al.lightq.util.LightQConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {

    private CorrelationIdFilter correlationIdFilter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        correlationIdFilter = new CorrelationIdFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void testDoFilterInternal_generatesRequestId_whenHeadersAreMissing() throws ServletException, IOException {
        correlationIdFilter.doFilterInternal(request, response, filterChain);

        assertNotNull(MDC.get(CorrelationIdFilter.MDC_REQUEST_ID));
        assertNotNull(response.getHeader("X-Request-Id"));
        assertNotNull(response.getHeader("X-Correlation-Id"));
    }

    @Test
    void testDoFilterInternal_usesRequestIdHeader_whenPresent() throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        request.addHeader("X-Request-Id", requestId);

        correlationIdFilter.doFilterInternal(request, response, filterChain);

        assertEquals(requestId, MDC.get(CorrelationIdFilter.MDC_REQUEST_ID));
        assertEquals(requestId, response.getHeader("X-Request-Id"));
        assertEquals(requestId, response.getHeader("X-Correlation-Id"));
    }

    @Test
    void testDoFilterInternal_usesCorrelationIdHeader_whenPresent() throws ServletException, IOException {
        String correlationId = UUID.randomUUID().toString();
        request.addHeader("X-Correlation-Id", correlationId);

        correlationIdFilter.doFilterInternal(request, response, filterChain);

        assertEquals(correlationId, MDC.get(CorrelationIdFilter.MDC_REQUEST_ID));
        assertEquals(correlationId, response.getHeader("X-Request-Id"));
        assertEquals(correlationId, response.getHeader("X-Correlation-Id"));
    }

    @Test
    void testDoFilterInternal_prefersRequestIdHeader_overCorrelationIdHeader() throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        request.addHeader("X-Request-Id", requestId);
        request.addHeader("X-Correlation-Id", correlationId);

        correlationIdFilter.doFilterInternal(request, response, filterChain);

        assertEquals(requestId, MDC.get(CorrelationIdFilter.MDC_REQUEST_ID));
    }

    @Test
    void testDoFilterInternal_addsConsumerGroupToMDC_whenHeaderIsPresent() throws ServletException, IOException {
        String consumerGroup = "test-group";
        request.addHeader(LightQConstants.CONSUMER_GROUP_HEADER, consumerGroup);

        correlationIdFilter.doFilterInternal(request, response, filterChain);

        assertEquals(consumerGroup, MDC.get(CorrelationIdFilter.MDC_CONSUMER_GROUP));
    }

    @Test
    void testDoFilterInternal_doesNotAddConsumerGroupToMDC_whenHeaderIsMissing() throws ServletException, IOException {
        correlationIdFilter.doFilterInternal(request, response, filterChain);

        assertNull(MDC.get(CorrelationIdFilter.MDC_CONSUMER_GROUP));
    }

    @Test
    void testDoFilterInternal_clearsMDC_afterChain() throws ServletException, IOException {
        correlationIdFilter.doFilterInternal(request, response, filterChain);

        assertNotNull(MDC.get(CorrelationIdFilter.MDC_REQUEST_ID));
    }
}
