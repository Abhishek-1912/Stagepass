package com.stagepass.catalog.api;

/** Everything the browser needs to POST a file straight to Cloudinary. */
public record UploadSignatureResponse(String cloudName, String apiKey, long timestamp, String signature, String folder,
        String allowedFormats, String uploadUrl, long maxBytes) {
}
