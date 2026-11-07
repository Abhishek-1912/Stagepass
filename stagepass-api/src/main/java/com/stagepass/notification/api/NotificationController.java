package com.stagepass.notification.api;

import com.stagepass.common.api.PageResponse;
import com.stagepass.common.security.CurrentUser;
import com.stagepass.notification.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/notifications")
public class NotificationController {

    private final NotificationService notifications;
    private final CurrentUser currentUser;

    public NotificationController(NotificationService notifications, CurrentUser currentUser) {
        this.notifications = notifications;
        this.currentUser = currentUser;
    }

    @GetMapping
    public PageResponse<NotificationView> list(@RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return notifications.list(currentUser.id(), page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notifications.unreadCount(currentUser.id()));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable UUID id) {
        notifications.markRead(currentUser.id(), id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll() {
        notifications.markAllRead(currentUser.id());
    }
}
