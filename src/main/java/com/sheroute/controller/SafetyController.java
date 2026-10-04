package com.sheroute.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sheroute.dto.SafetyResponse;
import com.sheroute.service.PoliceSafetyService;
import com.sheroute.service.TimeSafetyService;

@RestController
@RequestMapping("/api/safety")
public class SafetyController {

    private final TimeSafetyService timeSafetyService;
    private final PoliceSafetyService policeSafetyService;

    public SafetyController(
            TimeSafetyService timeSafetyService,
            PoliceSafetyService policeSafetyService) {

        this.timeSafetyService = timeSafetyService;
        this.policeSafetyService = policeSafetyService;
    }

    @GetMapping("/check")
    public SafetyResponse checkSafety(
            @RequestParam double lat,
            @RequestParam double lon) {

        double timeIndex =
                timeSafetyService.calculateTimeIndex();

        double policeDistance =
                policeSafetyService.findNearestPoliceDistance(lat, lon);

        double policeIndex =
                policeSafetyService.calculatePoliceIndex(policeDistance);

        return new SafetyResponse(
                timeIndex,
                policeDistance,
                policeIndex
        );
    }
}