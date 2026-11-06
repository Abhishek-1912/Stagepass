package com.stagepass.discovery.api;

import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.ErrorCode;
import com.stagepass.discovery.service.PublicEventService;
import com.stagepass.discovery.service.SharePageRenderer;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Duration;
import java.util.regex.Pattern;

/** {@code /e/{slug}}: the link people share. Previews well in WhatsApp/X/Slack, then sends the visitor to the web app. */
@RestController
public class SharePageController {

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9-]{1,80}$");
    private static final MediaType HTML = new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8);

    private final PublicEventService events;
    private final SharePageRenderer renderer;

    public SharePageController(PublicEventService events, SharePageRenderer renderer) {
        this.events = events;
        this.renderer = renderer;
    }

    @GetMapping("/e/{slug}")
    public ResponseEntity<String> share(@PathVariable String slug) {
        if (!SLUG.matcher(slug).matches()) {
            return notFound();
        }
        try {
            var event = events.findVisible(slug);
            String shareUrl = ServletUriComponentsBuilder.fromCurrentContextPath().path("/e/{slug}")
                    .buildAndExpand(slug).toUriString();
            return ResponseEntity.ok().contentType(HTML)
                    .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                    .body(renderer.render(event, shareUrl));
        } catch (ApiException e) {
            if (e.code() == ErrorCode.NOT_FOUND) {
                return notFound();
            }
            throw e;
        }
    }

    private ResponseEntity<String> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(HTML).body(renderer.notFound());
    }
}
