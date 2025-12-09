package com.al.simplequeueservice.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class SecurityConfigTest {

    private SecurityConfig securityConfig;
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        securityConfig = new SecurityConfig();
        ReflectionTestUtils.setField(securityConfig, "userUsername", "testuser");
        ReflectionTestUtils.setField(securityConfig, "userPassword", "userpass");
        ReflectionTestUtils.setField(securityConfig, "adminUsername", "testadmin");
        ReflectionTestUtils.setField(securityConfig, "adminPassword", "adminpass");
        passwordEncoder = securityConfig.passwordEncoder();
    }

    @Test
    void passwordEncoderBean() {
        assertNotNull(passwordEncoder);
        String encodedPassword = passwordEncoder.encode("rawPassword");
        assertTrue(passwordEncoder.matches("rawPassword", encodedPassword));
        assertFalse(passwordEncoder.matches("wrongPassword", encodedPassword));
    }

    @Test
    void userDetailsServiceBean() {
        UserDetailsService userDetailsService = securityConfig.userDetailsService(passwordEncoder);
        assertNotNull(userDetailsService);

        UserDetails user = userDetailsService.loadUserByUsername("testuser");
        assertNotNull(user);
        assertEquals("testuser", user.getUsername());
        assertTrue(passwordEncoder.matches("userpass", user.getPassword()));
        assertTrue(user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        assertFalse(user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));

        UserDetails admin = userDetailsService.loadUserByUsername("testadmin");
        assertNotNull(admin);
        assertEquals("testadmin", admin.getUsername());
        assertTrue(passwordEncoder.matches("adminpass", admin.getPassword()));
        assertTrue(admin.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        assertTrue(admin.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }
}
