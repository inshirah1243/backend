package com.sheroute.service;

import java.time.LocalTime;

import org.springframework.stereotype.Service;

@Service
public class TimeSafetyService {

    public double calculateTimeIndex() {

        LocalTime time = LocalTime.now();

        // 6 AM to 6 PM
        if (!time.isBefore(LocalTime.of(6, 0))
                && time.isBefore(LocalTime.of(18, 0))) {
            return 1.0;
        }

        // 6 PM to 10 PM
        if (!time.isBefore(LocalTime.of(18, 0))
                && time.isBefore(LocalTime.of(22, 0))) {
            return 0.6;
        }

        // 10 PM to 6 AM
        return 0.3;
    }
}