package com.stagepass.discovery.api;

/** Average star rating (one decimal; null when there are no reviews) and how many reviews it is based on. */
public record RatingSummary(Double average, long count) {
}
