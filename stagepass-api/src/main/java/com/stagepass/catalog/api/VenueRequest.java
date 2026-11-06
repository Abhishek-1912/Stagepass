package com.stagepass.catalog.api;

import com.stagepass.common.validation.Formats;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create (name, addressLine, city required — checked in the service) or partial update of a venue. */
public record VenueRequest(
        @Size(min = 1, max = 120) String name,
        @Size(min = 1, max = 300) String addressLine,
        @Size(min = 1, max = 80) String city,
        @Size(max = 60) String state,
        @Pattern(regexp = Formats.PINCODE, message = "must be a 6-digit Indian PIN code") String pincode,
        @DecimalMin("-90") @DecimalMax("90") Double lat,
        @DecimalMin("-180") @DecimalMax("180") Double lng,
        @Size(max = 300) String googlePlaceId) {

    public VenueRequest {
        name = Formats.trim(name);
        addressLine = Formats.trim(addressLine);
        city = Formats.trim(city);
        state = Formats.trim(state);
        pincode = Formats.trim(pincode);
        googlePlaceId = Formats.trim(googlePlaceId);
    }
}
