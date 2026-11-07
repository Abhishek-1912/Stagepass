package com.stagepass.catalog.service;

import com.stagepass.catalog.config.CloudinaryProperties;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Accepts only images delivered from our Cloudinary account and the expected folder, so nobody can attach another
 * event's (or another site's) image. Allowed before the folder: optional transformation segments
 * ({@code f_auto,q_auto}) and an optional version ({@code v1712345}).
 */
@Component
public class ImageUrlPolicy {

    private static final String TRANSFORMS = "(?:[a-z]{1,3}_[A-Za-z0-9.:-]+(?:,[a-z]{1,3}_[A-Za-z0-9.:-]+)*/)*";
    private static final String VERSION = "(?:v\\d+/)?";
    private static final String FILE = "/[A-Za-z0-9_.-]+\\.(?:jpe?g|png|webp)";

    private final CloudinaryProperties props;

    public ImageUrlPolicy(CloudinaryProperties props) {
        this.props = props;
    }

    public boolean isOwned(String url, String folder) {
        if (url == null || props.cloudName() == null || props.cloudName().isBlank()) {
            return false;
        }
        Pattern pattern = Pattern.compile("^https://res\\.cloudinary\\.com/" + Pattern.quote(props.cloudName())
                + "/image/upload/" + TRANSFORMS + VERSION + Pattern.quote(folder) + FILE + "$");
        return pattern.matcher(url).matches();
    }

    public static String eventFolder(java.util.UUID eventId) {
        return "stagepass/events/" + eventId;
    }

    public static String avatarFolder(java.util.UUID userId) {
        return "stagepass/avatars/" + userId;
    }

    public static String organizerFolder(java.util.UUID userId) {
        return "stagepass/organizers/" + userId;
    }
}
