package com.stagepass.catalog.service;

import com.stagepass.catalog.api.UploadSignatureResponse;
import com.stagepass.catalog.config.CloudinaryProperties;
import com.stagepass.common.error.ApiException;
import com.stagepass.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Issues short-lived Cloudinary upload signatures locked to one folder and to image formats. */
@Service
public class UploadService {

    public static final String ALLOWED_FORMATS = "jpg,png,webp";
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private final CloudinaryProperties props;
    private final CloudinarySigner signer;
    private final EventService events;

    public UploadService(CloudinaryProperties props, CloudinarySigner signer, EventService events) {
        this.props = props;
        this.signer = signer;
        this.events = events;
    }

    public UploadSignatureResponse signEventImage(UUID organizerId, UUID eventId) {
        requireConfigured();
        if (eventId == null) {
            throw com.stagepass.common.error.FieldErrors.single("eventId", "is required for event images");
        }
        events.owned(organizerId, eventId);
        return sign(ImageUrlPolicy.eventFolder(eventId));
    }

    public UploadSignatureResponse signLogo(UUID userId) {
        requireConfigured();
        return sign(ImageUrlPolicy.organizerFolder(userId));
    }

    private UploadSignatureResponse sign(String folder) {
        long timestamp = Instant.now().getEpochSecond();
        String signature = signer.sign(Map.of("folder", folder, "allowed_formats", ALLOWED_FORMATS), timestamp);
        return new UploadSignatureResponse(props.cloudName(), props.apiKey(), timestamp, signature, folder,
                ALLOWED_FORMATS, "https://api.cloudinary.com/v1_1/" + props.cloudName() + "/image/upload", MAX_BYTES);
    }

    private void requireConfigured() {
        if (!props.configured()) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Image uploads aren't configured yet.");
        }
    }
}
