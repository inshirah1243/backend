package com.sheroute.service;

import com.sheroute.dto.LocationDto;

public interface GeocodingService {

    LocationDto geocode(String placeName);
}
