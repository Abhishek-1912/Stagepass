package com.stagepass.support;

import com.stagepass.common.config.SupabaseProperties;
import com.stagepass.common.security.SecurityConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Replaces the JWKS-backed decoder with one that trusts {@link TestJwt}'s key, keeping production validators. */
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    JwtDecoder testJwtDecoder(SupabaseProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TestJwt.publicKey()).build();
        decoder.setJwtValidator(SecurityConfig.supabaseValidator(props));
        return decoder;
    }
}
