package com.al.simplequeueservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler globalExceptionHandler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(request.getRequestURI()).thenReturn("/test-uri");
    }

    @Test
    void handleException() {
        Exception exception = new Exception("Test exception message");
        ResponseEntity<ErrorResponse> responseEntity = globalExceptionHandler.handleException(exception, request);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), responseEntity.getBody().getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), responseEntity.getBody().getError());
        assertEquals("Test exception message", responseEntity.getBody().getMessage());
        assertEquals("/test-uri", responseEntity.getBody().getPath());
    }

    @Test
    void handleIllegalArgumentException() {
        IllegalArgumentException exception = new IllegalArgumentException("Test illegal argument message");
        ResponseEntity<ErrorResponse> responseEntity = globalExceptionHandler.handleIllegalArgumentException(exception, request);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.BAD_REQUEST, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(HttpStatus.BAD_REQUEST.value(), responseEntity.getBody().getStatus());
        assertEquals(HttpStatus.BAD_REQUEST.getReasonPhrase(), responseEntity.getBody().getError());
        assertEquals("Test illegal argument message", responseEntity.getBody().getMessage());
        assertEquals("/test-uri", responseEntity.getBody().getPath());
    }
}
