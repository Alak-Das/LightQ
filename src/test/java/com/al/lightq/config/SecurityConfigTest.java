package com.al.lightq.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

class SecurityConfigTest {

	@BeforeEach
	void setUp() {
		// securityConfig = new SecurityConfig(); // No longer needed
	}

	@Test
	void passwordEncoderBean() {
		PasswordEncoder passwordEncoder = SecurityConfig.passwordEncoder();
		assertNotNull(passwordEncoder);
		String encodedPassword = passwordEncoder.encode("rawPassword");
		assertTrue(passwordEncoder.matches("rawPassword", encodedPassword));
		assertFalse(passwordEncoder.matches("wrongPassword", encodedPassword));
	}

	@Test
	void lightQUserDetailsService() {
		com.al.lightq.service.SubscriberService subscriberService = org.mockito.Mockito
				.mock(com.al.lightq.service.SubscriberService.class);
		PasswordEncoder encoder = SecurityConfig.passwordEncoder();

		// Instantiate the service directly (simulating Spring injection)
		com.al.lightq.service.LightQUserDetailsService userDetailsService = new com.al.lightq.service.LightQUserDetailsService(
				"testadmin", "adminpass", subscriberService, encoder);

		// Test Admin (from properties)
		UserDetails admin = userDetailsService.loadUserByUsername("testadmin");
		assertNotNull(admin);
		assertEquals("testadmin", admin.getUsername());
		assertTrue(encoder.matches("adminpass", admin.getPassword()));
		assertTrue(admin.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));

		// Test Subscriber (from service)
		com.al.lightq.model.Subscriber mockSub = new com.al.lightq.model.Subscriber("testuser", "encodedPass");
		mockSub.setRoles(java.util.Collections.singleton("USER"));
		mockSub.setRateLimit(10);
		org.mockito.Mockito.when(subscriberService.findByUsername("testuser"))
				.thenReturn(java.util.Optional.of(mockSub));

		UserDetails user = userDetailsService.loadUserByUsername("testuser");
		assertNotNull(user);
		assertEquals("testuser", user.getUsername());
		assertEquals("encodedPass", user.getPassword());
		assertTrue(user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));

		// Verify LightQUserDetails specific fields
		assertTrue(user instanceof com.al.lightq.model.LightQUserDetails);
		assertEquals(10, ((com.al.lightq.model.LightQUserDetails) user).getRateLimit());
	}
}
