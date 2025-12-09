package com.al.simplequeueservice.config;

import com.al.simplequeueservice.util.SQSConstants;
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
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;

import static org.springframework.security.config.Customizer.withDefaults;

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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests((requests) -> requests
                        .requestMatchers(SQSConstants.QUEUE_BASE_URL + SQSConstants.PUSH_URL, SQSConstants.QUEUE_BASE_URL + SQSConstants.POP_URL).hasAnyRole(SQSConstants.USER_ROLE, SQSConstants.ADMIN_ROLE)
                        .requestMatchers(SQSConstants.QUEUE_BASE_URL + SQSConstants.VIEW_URL).access(new WebExpressionAuthorizationManager(SQSConstants.HAS_ADMIN_ROLE))
                        .anyRequest().authenticated()
                )
                .httpBasic(withDefaults())
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails user =
                User.builder()
                        .username(userUsername)
                        .password(passwordEncoder.encode(userPassword))
                        .roles(SQSConstants.USER_ROLE)
                        .build();

        UserDetails admin =
                User.builder()
                        .username(adminUsername)
                        .password(passwordEncoder.encode(adminPassword))
                        .roles(SQSConstants.ADMIN_ROLE, SQSConstants.USER_ROLE)
                        .build();

        return new InMemoryUserDetailsManager(user, admin);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
