package com.sheroute.repository;

import com.sheroute.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    boolean existsByPhone(String phone);

    Optional<User> findByPhone(String phone);
}
