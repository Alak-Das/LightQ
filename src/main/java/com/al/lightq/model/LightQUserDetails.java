package com.al.lightq.model;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class LightQUserDetails extends User {

    private final Integer rateLimit;

    public LightQUserDetails(String username, String password, boolean enabled, boolean accountNonExpired,
            boolean credentialsNonExpired, boolean accountNonLocked,
            Collection<? extends GrantedAuthority> authorities, Integer rateLimit) {
        super(username, password, enabled, accountNonExpired, credentialsNonExpired, accountNonLocked, authorities);
        this.rateLimit = rateLimit;
    }

    public Integer getRateLimit() {
        return rateLimit;
    }
}
