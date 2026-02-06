package com.al.lightq.config;

import static org.springframework.security.config.Customizer.withDefaults;

import com.al.lightq.LightQConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuration for security.
 * <p>
 * This class sets up the security filter chain, user details service, and
 * password encoder.
 * </p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	/**
	 * Creates a security filter chain.
	 *
	 * @param http
	 *             the HttpSecurity
	 * @return the security filter chain
	 * @throws Exception
	 *                   if an error occurs
	 */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests((requests) -> requests.requestMatchers("/actuator/health").permitAll()
				.requestMatchers(LightQConstants.QUEUE_BASE_URL + LightQConstants.PUSH_URL,
						LightQConstants.QUEUE_BASE_URL + LightQConstants.BATCH_PUSH_URL,
						LightQConstants.QUEUE_BASE_URL + LightQConstants.POP_URL,
						LightQConstants.QUEUE_BASE_URL + LightQConstants.ACK_URL,
						LightQConstants.QUEUE_BASE_URL + LightQConstants.NACK_URL,
						LightQConstants.QUEUE_BASE_URL + LightQConstants.EXTEND_VIS_URL)
				.hasAnyRole(LightQConstants.USER_ROLE, LightQConstants.ADMIN_ROLE)
				.requestMatchers(LightQConstants.QUEUE_BASE_URL + LightQConstants.VIEW_URL,
						LightQConstants.QUEUE_BASE_URL + LightQConstants.DLQ_VIEW_URL,
						LightQConstants.QUEUE_BASE_URL + LightQConstants.DLQ_REPLAY_URL,
						"/admin/**")
				.hasRole(LightQConstants.ADMIN_ROLE).anyRequest().authenticated()).httpBasic(withDefaults())
				.csrf(AbstractHttpConfigurer::disable);
		return http.build();
	}

	/**
	 * Creates a password encoder.
	 *
	 * @return the password encoder
	 */
	@Bean
	public static PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
