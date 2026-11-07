package com.stagepass.engagement.api;

import com.stagepass.common.api.PageResponse;
import com.stagepass.common.security.CurrentUser;
import com.stagepass.engagement.service.SavedEventService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/saved")
public class SavedEventController {

    private final SavedEventService saved;
    private final CurrentUser currentUser;

    public SavedEventController(SavedEventService saved, CurrentUser currentUser) {
        this.saved = saved;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<SavedEventView> list(@RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return saved.list(currentUser.id(), page, size);
    }

    @GetMapping("/ids")
    public List<UUID> ids() {
        return saved.ids(currentUser.id());
    }

    @PutMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@PathVariable UUID eventId) {
        saved.save(currentUser.id(), eventId);
    }

    @DeleteMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsave(@PathVariable UUID eventId) {
        saved.unsave(currentUser.id(), eventId);
    }
}
