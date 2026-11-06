package com.stagepass.discovery.api;

import com.stagepass.common.api.PageResponse;
import com.stagepass.discovery.service.EventSearchService;
import com.stagepass.discovery.service.EventSearchService.RawQuery;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/public/events")
public class PublicEventController {

    private final EventSearchService search;

    public PublicEventController(EventSearchService search) {
        this.search = search;
    }

    @GetMapping
    public ResponseEntity<PageResponse<PublicEventCard>> search(
            @RequestParam(required = false) String q, @RequestParam(required = false) String city,
            @RequestParam(required = false) String category, @RequestParam(required = false) String date,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) String minPrice, @RequestParam(required = false) String maxPrice,
            @RequestParam(required = false) String free, @RequestParam(required = false) String lat,
            @RequestParam(required = false) String lng, @RequestParam(required = false) String radiusKm,
            @RequestParam(required = false) String sort, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        var result = search.search(new RawQuery(q, city, category, date, from, to, minPrice, maxPrice, free, lat, lng,
                radiusKm, sort, page, size));
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic()).body(result);
    }
}
