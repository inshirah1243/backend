package com.sheroute.controller;

import com.sheroute.dto.AuthResponse;
import com.sheroute.dto.LoginRequest;
import com.sheroute.dto.SignupRequest;
import com.sheroute.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            String message = Optional.ofNullable(bindingResult.getFieldError())
                    .map(FieldError::getDefaultMessage)
                    .orElse("Invalid signup request");
            return ResponseEntity.badRequest().body(new AuthResponse(false, message));
        }

        AuthResponse response = authService.register(request);
        HttpStatus status = response.success() ? HttpStatus.CREATED : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            String message = Optional.ofNullable(bindingResult.getFieldError())
                    .map(FieldError::getDefaultMessage)
                    .orElse("Invalid login request");
            return ResponseEntity.badRequest().body(new AuthResponse(false, message));
        }

        AuthResponse response = authService.login(request);
        HttpStatus status = response.success() ? HttpStatus.OK : HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(status).body(response);
    }
}
