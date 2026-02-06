package com.al.lightq.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.al.lightq.model.Subscriber;
import com.al.lightq.repository.SubscriberRepository;

@Service
public class SubscriberService {

    private final SubscriberRepository subscriberRepository;
    private final PasswordEncoder passwordEncoder;

    public SubscriberService(SubscriberRepository subscriberRepository, PasswordEncoder passwordEncoder) {
        this.subscriberRepository = subscriberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Subscriber createSubscriber(String username, String rawPassword, Integer rateLimit) {
        if (subscriberRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        Subscriber subscriber = new Subscriber(username, passwordEncoder.encode(rawPassword));
        subscriber.setRateLimit(rateLimit);
        subscriber.setCreatedAt(LocalDateTime.now());
        return subscriberRepository.save(subscriber);
    }

    public List<Subscriber> getAllSubscribers() {
        return subscriberRepository.findAll();
    }

    public void deleteSubscriber(String username) {
        Optional<Subscriber> subscriber = subscriberRepository.findByUsername(username);
        subscriber.ifPresent(subscriberRepository::delete);
    }

    public Optional<Subscriber> findByUsername(String username) {
        return subscriberRepository.findByUsername(username);
    }
}
