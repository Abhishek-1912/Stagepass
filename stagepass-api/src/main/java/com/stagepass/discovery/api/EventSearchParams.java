package com.stagepass.discovery.api;

import java.util.UUID;

/**
 * Validated discovery filters. Prices are in paise here (the API accepts rupees). {@code radiusKm} defaults to 50.
 * {@code organizerId}, {@code startsWithinDays}, {@code featuredOnly}, {@code excludeOnline} and
 * {@code includeOnlineWithCity} are internal (home feeds, organizer profile).
 */
public record EventSearchParams(String q, String city, String category, Long minPricePaise, Long maxPricePaise,
        boolean freeOnly, Double lat, Double lng, double radiusKm, Sort sort, UUID organizerId,
        Integer startsWithinDays, boolean includeOnlineWithCity, boolean featuredOnly, boolean excludeOnline) {

    /** TRENDING is internal (home feed): popularity only, without the featured boost. */
    public enum Sort { POPULAR, SOONEST, NEARBY, TRENDING }

    public boolean hasLocation() {
        return lat != null && lng != null;
    }

    public static EventSearchParams of(Sort sort) {
        return new EventSearchParams(null, null, null, null, null, false, null, null, 50, sort, null, null, false,
                false, false);
    }

    public EventSearchParams withCity(String newCity, boolean includeOnline) {
        return new EventSearchParams(q, newCity, category, minPricePaise, maxPricePaise, freeOnly, lat, lng, radiusKm,
                sort, organizerId, startsWithinDays, includeOnline, featuredOnly, excludeOnline);
    }

    public EventSearchParams withCategory(String slug) {
        return new EventSearchParams(q, city, slug, minPricePaise, maxPricePaise, freeOnly, lat, lng, radiusKm, sort,
                organizerId, startsWithinDays, includeOnlineWithCity, featuredOnly, excludeOnline);
    }

    public EventSearchParams withLocation(double newLat, double newLng, double radius) {
        return new EventSearchParams(q, city, category, minPricePaise, maxPricePaise, freeOnly, newLat, newLng, radius,
                Sort.NEARBY, organizerId, startsWithinDays, includeOnlineWithCity, featuredOnly, true);
    }

    public EventSearchParams withOrganizer(UUID id) {
        return new EventSearchParams(q, city, category, minPricePaise, maxPricePaise, freeOnly, lat, lng, radiusKm,
                sort, id, startsWithinDays, includeOnlineWithCity, featuredOnly, excludeOnline);
    }

    public EventSearchParams startingWithin(int days) {
        return new EventSearchParams(q, city, category, minPricePaise, maxPricePaise, freeOnly, lat, lng, radiusKm,
                sort, organizerId, days, includeOnlineWithCity, featuredOnly, excludeOnline);
    }

    public EventSearchParams featured() {
        return new EventSearchParams(q, city, category, minPricePaise, maxPricePaise, freeOnly, lat, lng, radiusKm,
                sort, organizerId, startsWithinDays, includeOnlineWithCity, true, excludeOnline);
    }

    public EventSearchParams inPersonOnly() {
        return new EventSearchParams(q, city, category, minPricePaise, maxPricePaise, freeOnly, lat, lng, radiusKm,
                sort, organizerId, startsWithinDays, includeOnlineWithCity, featuredOnly, true);
    }
}
