package com.al.lightq.config;

import com.al.lightq.util.LightQConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Configuration for security.
 * <p>
 * This class sets up the security filter chain, user details service, and password encoder.
 * </p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${security.user.username}")
    private String userUsername;

    @Value("${security.user.password}")
    private String userPassword;

    @Value("${security.admin.username}")
    private String adminUsername;

    @Value("${security.admin.password}")
    private String adminPassword;

    /**
     * Creates a security filter chain.
     *
     * @param http the HttpSecurity
     * @return the security filter chain
     * @throws Exception if an error occurs
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests((requests) -> requests
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(LightQConstants.QUEUE_BASE_URL + LightQConstants.PUSH_URL, LightQConstants.QUEUE_BASE_URL + LightQConstants.POP_URL).hasAnyRole(LightQConstants.USER_ROLE, LightQConstants.ADMIN_ROLE)
                        .requestMatchers(LightQConstants.QUEUE_BASE_URL + LightQConstants.VIEW_URL).hasRole(LightQConstants.ADMIN_ROLE)
                        .anyRequest().authenticated()
                )
                .httpBasic(withDefaults())
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /**
     * Creates a user details service.
     *
     * @param passwordEncoder the password encoder
     * @return the user details service
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails user =
                User.builder()
                        .username(userUsername)
                        .password(passwordEncoder.encode(userPassword))
                        .roles(LightQConstants.USER_ROLE)
                        .build();

        UserDetails admin =
                User.builder()
                        .username(adminUsername)
                        .password(passwordEncoder.encode(adminPassword))
                        .roles(LightQConstants.ADMIN_ROLE, LightQConstants.USER_ROLE)
                        .build();

        return new InMemoryUserDetailsManager(user, admin);
    }

    /**
     * Creates a password encoder.
     *
     * @return the password encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
