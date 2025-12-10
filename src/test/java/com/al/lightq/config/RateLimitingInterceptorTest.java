package com.al.lightq.config;

import com.al.lightq.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitingInterceptorTest {

    @Test
    void pushLimitedAfterThreshold() throws Exception {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setPushPerSecond(1);
        properties.setPopPerSecond(1000);

        RateLimitingInterceptor interceptor = new RateLimitingInterceptor(properties);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/queue/push");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertDoesNotThrow(() -> interceptor.preHandle(request, response, new Object()));
        assertThrows(RateLimitExceededException.class, () -> interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void popLimitedAfterThreshold() throws Exception {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setPushPerSecond(1000);
        properties.setPopPerSecond(1);

        RateLimitingInterceptor interceptor = new RateLimitingInterceptor(properties);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/queue/pop");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertDoesNotThrow(() -> interceptor.preHandle(request, response, new Object()));
        assertThrows(RateLimitExceededException.class, () -> interceptor.preHandle(request, response, new Object()));
    }
}
