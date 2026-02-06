package com.al.lightq.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.al.lightq.LightQConstants;
import com.al.lightq.model.LightQUserDetails;
import org.springframework.security.core.authority.AuthorityUtils;

/**
 * Hybrid UserDetailsService that loads the admin from properties and
 * subscribers from the database.
 */
@Service
public class LightQUserDetailsService implements UserDetailsService {

    private final String adminUsername;
    private final String adminPassword;
    private final SubscriberService subscriberService;
    private final PasswordEncoder passwordEncoder;

    public LightQUserDetailsService(
            @Value("${security.admin.username}") String adminUsername,
            @Value("${security.admin.password}") String adminPassword,
            SubscriberService subscriberService,
            PasswordEncoder passwordEncoder) {
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.subscriberService = subscriberService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 1. Check if it's the admin from properties
        if (adminUsername.equals(username)) {
            return User.builder()
                    .username(adminUsername)
                    .password(passwordEncoder.encode(adminPassword))
                    .roles(LightQConstants.ADMIN_ROLE, LightQConstants.USER_ROLE)
                    .build();
        }

        // 2. Check if it's a persistent subscriber
        return subscriberService.findByUsername(username)
                .map(subscriber -> new LightQUserDetails(
                        subscriber.getUsername(),
                        subscriber.getPassword(),
                        subscriber.isEnabled(),
                        true, true, true,
                        AuthorityUtils.createAuthorityList(
                                subscriber.getRoles().stream().map(r -> "ROLE_" + r).toArray(String[]::new)),
                        subscriber.getRateLimit()))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}
