package com.stagepass.catalog.service;

import com.stagepass.catalog.config.CloudinaryProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Cloudinary upload signature: parameters (including timestamp) sorted by key, joined as {@code k=v&k=v},
 * the API secret appended, SHA-1 hex. Empty values are not signed.
 */
@Component
public class CloudinarySigner {

    private final CloudinaryProperties props;

    public CloudinarySigner(CloudinaryProperties props) {
        this.props = props;
    }

    public String sign(Map<String, String> params, long timestamp) {
        TreeMap<String, String> sorted = new TreeMap<>(params);
        sorted.put("timestamp", Long.toString(timestamp));
        String toSign = sorted.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&")) + props.apiSecret();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(toSign.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
