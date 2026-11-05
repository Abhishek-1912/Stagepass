package com.stagepass.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Supabase Auth settings used to validate access tokens, bound from {@code supabase.*}. */
@ConfigurationProperties(prefix = "supabase")
public record SupabaseProperties(
        String url,
        String jwksUri,
        String issuer,
        @DefaultValue("authenticated") String audience) {
}
