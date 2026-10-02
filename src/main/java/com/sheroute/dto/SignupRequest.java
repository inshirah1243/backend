package com.sheroute.dto;

import jakarta.validation.constraints.NotBlank;

public record SignupRequest(
        @NotBlank(message = "name is required")
        String name,

        @NotBlank(message = "phone is required")
        String phone,

        @NotBlank(message = "password is required")
        String password
) {
}
