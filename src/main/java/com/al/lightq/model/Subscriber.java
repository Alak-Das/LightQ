package com.al.lightq.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.al.lightq.LightQConstants;

@Document(collection = "subscribers")
public class Subscriber {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    private String password;

    private boolean enabled = true;

    /**
     * Rate limit (requests per second). Null or <= 0 means use global default.
     */
    private Integer rateLimit;

    private Set<String> roles = Collections.singleton(LightQConstants.USER_ROLE);

    @CreatedDate
    private LocalDateTime createdAt;

    public Subscriber() {
    }

    public Subscriber(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(Integer rateLimit) {
        this.rateLimit = rateLimit;
    }
}
