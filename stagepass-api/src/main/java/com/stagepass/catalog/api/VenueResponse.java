package com.stagepass.catalog.api;

import com.stagepass.catalog.domain.Venue;

import java.util.UUID;

public record VenueResponse(UUID id, String name, String addressLine, String city, String state, String pincode,
        Double lat, Double lng, String googlePlaceId) {

    public static VenueResponse from(Venue v) {
        return v == null ? null : new VenueResponse(v.getId(), v.getName(), v.getAddressLine(), v.getCity(),
                v.getState(), v.getPincode(), v.getLat(), v.getLng(), v.getGooglePlaceId());
    }
}
