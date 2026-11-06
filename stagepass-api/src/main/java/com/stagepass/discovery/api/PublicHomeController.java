package com.stagepass.discovery.api;

import com.stagepass.common.error.FieldErrors;
import com.stagepass.discovery.service.HomeFeedService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
public class PublicHomeController {

    private static final CacheControl ONE_MINUTE = CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic();

    private final HomeFeedService home;

    public PublicHomeController(HomeFeedService home) {
        this.home = home;
    }

    @GetMapping("/api/v1/public/home")
    public ResponseEntity<HomeFeedResponse> home(@RequestParam(required = false) String city,
            @RequestParam(required = false) Double lat, @RequestParam(required = false) Double lng) {
        FieldErrors.start()
                .addIf(lat != null && (!Double.isFinite(lat) || lat < -90 || lat > 90), "lat", "must be between -90 and 90")
                .addIf(lng != null && (!Double.isFinite(lng) || lng < -180 || lng > 180), "lng", "must be between -180 and 180")
                .addIf(city != null && city.length() > 80, "city", "is too long")
                .throwIfAny();
        return ResponseEntity.ok().cacheControl(ONE_MINUTE).body(home.home(city, lat, lng));
    }

    @GetMapping("/api/v1/public/cities")
    public ResponseEntity<List<CityResponse>> cities() {
        return ResponseEntity.ok().cacheControl(ONE_MINUTE).body(home.cities());
    }
}
