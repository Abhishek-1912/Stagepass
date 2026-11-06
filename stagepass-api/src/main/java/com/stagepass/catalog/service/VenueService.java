package com.stagepass.catalog.service;

import com.stagepass.catalog.api.VenueRequest;
import com.stagepass.catalog.api.VenueResponse;
import com.stagepass.catalog.domain.Venue;
import com.stagepass.catalog.repository.VenueRepository;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.FieldErrors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** An organizer's own venues. Another organizer's venue id always answers 404. */
@Service
public class VenueService {

    private final VenueRepository venues;

    public VenueService(VenueRepository venues) {
        this.venues = venues;
    }

    @Transactional(readOnly = true)
    public List<VenueResponse> list(UUID organizerId) {
        return venues.findByOrganizerIdOrderByNameAsc(organizerId).stream().map(VenueResponse::from).toList();
    }

    @Transactional
    public VenueResponse create(UUID organizerId, VenueRequest request) {
        FieldErrors.start().require(request.name(), "name").require(request.addressLine(), "addressLine")
                .require(request.city(), "city").throwIfAny();
        Venue venue = new Venue();
        venue.setOrganizerId(organizerId);
        apply(venue, request);
        return VenueResponse.from(venues.saveAndFlush(venue));
    }

    @Transactional
    public VenueResponse update(UUID organizerId, UUID venueId, VenueRequest request) {
        Venue venue = owned(organizerId, venueId);
        apply(venue, request);
        venues.flush();
        return VenueResponse.from(venue);
    }

    /** For other catalog services: the organizer's venue or 404. */
    @Transactional(readOnly = true)
    public Venue owned(UUID organizerId, UUID venueId) {
        return venues.findByIdAndOrganizerId(venueId, organizerId).orElseThrow(() -> ApiException.notFound("Venue"));
    }

    private static void apply(Venue v, VenueRequest r) {
        if (r.name() != null) {
            v.setName(r.name());
        }
        if (r.addressLine() != null) {
            v.setAddressLine(r.addressLine());
        }
        if (r.city() != null) {
            v.setCity(r.city());
        }
        if (r.state() != null) {
            v.setState(r.state());
        }
        if (r.pincode() != null) {
            v.setPincode(r.pincode());
        }
        if (r.lat() != null) {
            v.setLat(r.lat());
        }
        if (r.lng() != null) {
            v.setLng(r.lng());
        }
        if (r.googlePlaceId() != null) {
            v.setGooglePlaceId(r.googlePlaceId());
        }
    }
}
