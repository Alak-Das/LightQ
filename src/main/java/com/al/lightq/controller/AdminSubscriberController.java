package com.al.lightq.controller;

import java.util.List;

import com.al.lightq.dto.CreateSubscriberRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.al.lightq.model.Subscriber;
import com.al.lightq.service.SubscriberService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/admin/subscribers")
@Tag(name = "Admin Subscriber Management", description = "APIs for managing subscribers (Admin only)")
public class AdminSubscriberController {

    private final SubscriberService subscriberService;

    public AdminSubscriberController(SubscriberService subscriberService) {
        this.subscriberService = subscriberService;
    }

    @PostMapping
    @Operation(summary = "Create a new subscriber")
    public ResponseEntity<Subscriber> createSubscriber(@RequestBody @Valid CreateSubscriberRequest request) {
        try {
            Subscriber created = subscriberService.createSubscriber(request.getUsername(), request.getPassword(),
                    request.getRateLimit());
            // Don't return the encoded password
            created.setPassword(null);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            // This might be redundant if GlobalExceptionHandler handles it, but good for
            // specific service errors
            throw e;
        }
    }

    @GetMapping
    @Operation(summary = "List all subscribers")
    public ResponseEntity<List<Subscriber>> getAllSubscribers() {
        List<Subscriber> subscribers = subscriberService.getAllSubscribers();
        // Hide passwords
        subscribers.forEach(s -> s.setPassword(null));
        return ResponseEntity.ok(subscribers);
    }

    @DeleteMapping("/{username}")
    @Operation(summary = "Delete a subscriber")
    public ResponseEntity<Void> deleteSubscriber(@PathVariable String username) {
        subscriberService.deleteSubscriber(username);
        return ResponseEntity.noContent().build();
    }
}
