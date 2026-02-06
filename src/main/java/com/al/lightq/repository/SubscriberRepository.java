package com.al.lightq.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.al.lightq.model.Subscriber;

@Repository
public interface SubscriberRepository extends MongoRepository<Subscriber, String> {
    Optional<Subscriber> findByUsername(String username);
}
