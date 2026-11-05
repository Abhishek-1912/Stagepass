package com.stagepass.common.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.stagepass.common.config.SupabaseProperties;
import com.stagepass.common.error.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the production JWKS decoder against a local HTTP server, as Supabase would serve it. */
class SupabaseJwtDecoderTest {

    private static final String ISSUER = "https://proj.supabase.co/auth/v1";

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private SupabaseProperties serve(String body, long delayMillis) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        String uri = "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks";
        return new SupabaseProperties("https://proj.supabase.co", uri, ISSUER, "authenticated");
    }

    private static String es256Token(ECKey key) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .audience("authenticated")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new ECDSASigner(key));
        return jwt.serialize();
    }

    @Test
    void acceptsSupabaseDefaultEs256Keys() throws Exception {
        ECKey key = new ECKeyGenerator(Curve.P_256).keyID("supabase-key").generate();
        SupabaseProperties props = serve(new JWKSet(key.toPublicJWK()).toString(), 0);
        JwtDecoder decoder = SecurityConfig.supabaseJwtDecoder(props);

        Jwt jwt = decoder.decode(es256Token(key));

        assertThat(jwt.getIssuer().toString()).isEqualTo(ISSUER);
    }

    @Test
    void hangingJwksEndpointFailsFastAsServiceUnavailable() throws Exception {
        ECKey key = new ECKeyGenerator(Curve.P_256).keyID("slow").generate();
        SupabaseProperties props = serve(new JWKSet(key.toPublicJWK()).toString(), 15_000);
        JwtDecoder decoder = SecurityConfig.supabaseJwtDecoder(props);
        String token = es256Token(key);

        long start = System.nanoTime();
        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(StagepassAuthenticationException.class)
                .satisfies(e -> assertThat(((StagepassAuthenticationException) e).code())
                        .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE));
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
    }
}
