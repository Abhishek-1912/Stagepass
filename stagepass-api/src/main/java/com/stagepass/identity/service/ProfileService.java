package com.stagepass.identity.service;

import com.stagepass.catalog.service.ImageUrlPolicy;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.FieldErrors;
import com.stagepass.identity.api.UpdateProfileRequest;
import com.stagepass.identity.domain.User;
import com.stagepass.identity.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProfileService {

    private final UserRepository users;
    private final ImageUrlPolicy images;

    public ProfileService(UserRepository users, ImageUrlPolicy images) {
        this.users = users;
        this.images = images;
    }

    @Transactional(readOnly = true)
    public User get(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
    }

    @Transactional
    public User update(UUID userId, UpdateProfileRequest request) {
        User user = get(userId);
        if (request.fullName() != null) {
            user.setFullName(request.fullName().trim());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }
        if (request.city() != null) {
            user.setCity(request.city().trim());
        }
        if (request.avatarUrl() != null) {
            String url = request.avatarUrl().strip();
            if (url.isEmpty()) {
                user.setAvatarUrl(null);
            } else if (images.isOwned(url, ImageUrlPolicy.avatarFolder(userId))) {
                user.setAvatarUrl(url);
            } else {
                throw FieldErrors.single("avatarUrl", "must be a photo uploaded to Stagepass");
            }
        }
        if (request.emailReminders() != null) {
            user.setEmailReminders(request.emailReminders());
        }
        return user;
    }
}
