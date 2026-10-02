package com.sheroute.dto;

public record AuthResponse(
        boolean success,
        String message
) {
}
