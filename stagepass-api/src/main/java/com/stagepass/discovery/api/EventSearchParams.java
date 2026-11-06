package com.stagepass.discovery.api;

import java.util.UUID;

/**
 * Validated discovery filters. Prices are in paise here (the API accepts rupees). {@code radiusKm} defaults to 50.
 * {@code organizerId} and {@code startsWithinDays} are internal (organizer profile, trending feed).
 */
public record EventSearchParams(String q, String city, String category, Long minPricePaise, Long maxPricePaise,
        boolean freeOnly, Double lat, Double lng, double radiusKm, Sort sort, UUID organizerId,
        Integer startsWithinDays, boolean includeOnlineWithCity) {

    public enum Sort { POPULAR, SOONEST, NEARBY }

    public boolean hasLocation() {
        return lat != null && lng != null;
    }

    public static EventSearchParams of(Sort sort) {
        return new EventSearchParams(null, null, null, null, null, false, null, null, 50, sort, null, null, false);
    }

    public EventSearchParams withCity(String newCity, boolean includeOnline) {
        return new EventSearchParams(q, newCity, category, minPricePaise, maxPricePaise, freeOnly, lat, lng, radiusKm,
                sort, organizerId, startsWithinDays, includeOnline);
    }

    public EventSearchParams withCategory(String slug) {
        return new EventSearchParams(q, city, slug, minPricePaise, maxPricePaise, freeOnly, lat, lng, radiusKm, sort,
                organizerId, startsWithinDays, includeOnlineWithCity);
    }

    public EventSearchParams withLocation(double newLat, double newLng, double radius) {
        return new EventSearchParams(q, city, category, minPricePaise, maxPricePaise, freeOnly, newLat, newLng, radius,
                Sort.NEARBY, organizerId, startsWithinDays, includeOnlineWithCity);
    }

    public EventSearchParams withOrganizer(UUID id) {
        return new EventSearchParams(q, city, category, minPricePaise, maxPricePaise, freeOnly, lat, lng, radiusKm,
                sort, id, startsWithinDays, includeOnlineWithCity);
    }

    public EventSearchParams startingWithin(int days) {
        return new EventSearchParams(q, city, category, minPricePaise, maxPricePaise, freeOnly, lat, lng, radiusKm,
                sort, organizerId, days, includeOnlineWithCity);
    }
}
