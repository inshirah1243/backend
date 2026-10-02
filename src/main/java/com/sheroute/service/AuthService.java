package com.sheroute.service;

import com.sheroute.dto.AuthResponse;
import com.sheroute.dto.LoginRequest;
import com.sheroute.dto.SignupRequest;
import com.sheroute.model.User;
import com.sheroute.repository.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthResponse register(SignupRequest request) {
        String name = request.name().trim();
        String phone = request.phone().trim();

        if (userRepository.existsByPhone(phone)) {
            return new AuthResponse(false, "Phone number already exists");
        }

        String hashedPassword = passwordEncoder.encode(request.password());
        User user = new User(name, phone, hashedPassword);

        try {
            userRepository.save(user);
        } catch (DuplicateKeyException ex) {
            return new AuthResponse(false, "Phone number already exists");
        }

        return new AuthResponse(true, "User registered successfully");
    }

    public AuthResponse login(LoginRequest request) {
        String phone = request.phone().trim();

        return userRepository.findByPhone(phone)
                .filter(user -> passwordEncoder.matches(request.password(), user.getPassword()))
                .map(user -> new AuthResponse(true, "Login successful"))
                .orElseGet(() -> new AuthResponse(false, "Invalid phone number or password"));
    }
}
