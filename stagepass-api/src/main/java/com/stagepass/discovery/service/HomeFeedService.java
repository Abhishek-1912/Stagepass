package com.stagepass.discovery.service;

import com.stagepass.catalog.repository.CategoryRepository;
import com.stagepass.discovery.api.CityResponse;
import com.stagepass.discovery.api.EventSearchParams;
import com.stagepass.discovery.api.EventSearchParams.Sort;
import com.stagepass.discovery.api.HomeFeedResponse;
import com.stagepass.discovery.api.HomeFeedResponse.CategoryRail;
import com.stagepass.discovery.api.PublicEventCard;
import com.stagepass.discovery.repository.PublicEventQueries;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/** Assembles the homepage feeds (spec §8.7). A chosen city limits every feed except "near you" to it plus online events. */
@Service
public class HomeFeedService {

    static final int FEATURED = 6;
    static final int FEED = 12;
    static final int RAILS = 6;
    static final int RAIL_SIZE = 8;
    static final double NEARBY_KM = 50;

    private final PublicEventQueries queries;
    private final CategoryRepository categories;
    private final Clock clock;

    public HomeFeedService(PublicEventQueries queries, CategoryRepository categories, Clock clock) {
        this.queries = queries;
        this.categories = categories;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public HomeFeedResponse home(String city, Double lat, Double lng) {
        String c = city == null || city.isBlank() ? null : city.trim();
        EventSearchParams base = EventSearchParams.of(Sort.POPULAR).withCity(c, true);

        List<PublicEventCard> featured = list(base.featured(), FEATURED, null);
        List<PublicEventCard> trending = list(base.startingWithin(30), FEED, null);
        List<PublicEventCard> weekend = list(withSort(base, Sort.SOONEST), FEED,
                DateRanges.resolve("weekend", null, null, clock).orElse(null));
        List<PublicEventCard> upcoming = list(withSort(base, Sort.SOONEST), FEED, null);

        List<PublicEventCard> nearby;
        if (lat != null && lng != null) {
            nearby = list(EventSearchParams.of(Sort.NEARBY).withLocation(lat, lng, NEARBY_KM), FEED, null);
        } else if (c != null) {
            nearby = list(EventSearchParams.of(Sort.SOONEST).withCity(c, false).inPersonOnly(), FEED, null);
        } else {
            nearby = List.of();
        }

        List<CategoryRail> rails = new ArrayList<>();
        for (var category : categories.findByActiveTrueOrderBySortOrderAsc()) {
            if (rails.size() == RAILS) {
                break;
            }
            List<PublicEventCard> events = list(base.withCategory(category.getSlug()), RAIL_SIZE, null);
            if (!events.isEmpty()) {
                rails.add(new CategoryRail(category.getSlug(), category.getName(), category.getIcon(), events));
            }
        }
        return new HomeFeedResponse(featured, trending, weekend, upcoming, nearby, rails);
    }

    @Transactional(readOnly = true)
    public List<CityResponse> cities() {
        return queries.cities().stream().map(c -> new CityResponse(c.city(), c.events())).toList();
    }

    private List<PublicEventCard> list(EventSearchParams p, int size, DateRanges.Range range) {
        return queries.search(p, range, PageRequest.of(0, size)).getContent();
    }

    private static EventSearchParams withSort(EventSearchParams p, Sort sort) {
        return new EventSearchParams(p.q(), p.city(), p.category(), p.minPricePaise(), p.maxPricePaise(), p.freeOnly(),
                p.lat(), p.lng(), p.radiusKm(), sort, p.organizerId(), p.startsWithinDays(), p.includeOnlineWithCity(),
                p.featuredOnly(), p.excludeOnline());
    }
}
