package com.stagepass.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Mints Supabase-shaped access tokens signed with a test RSA key. */
public final class TestJwt {

    public static final String ISSUER = "https://test.supabase.co/auth/v1";

    private static final KeyPair KEYS = generate();
    private static final KeyPair OTHER_KEYS = generate();

    private TestJwt() {
    }

    public static RSAPublicKey publicKey() {
        return (RSAPublicKey) KEYS.getPublic();
    }

    public static String token(UUID sub, String email) {
        return builder(sub, email).build();
    }

    public static Builder builder(UUID sub, String email) {
        return new Builder(sub, email);
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static final class Builder {
        private final UUID sub;
        private final String email;
        private String issuer = ISSUER;
        private String audience = "authenticated";
        private Instant expiresAt = Instant.now().plusSeconds(3600);
        private boolean otherKey;
        private final Map<String, Object> extraClaims = new HashMap<>();
        private final Map<String, Object> userMetadata = new HashMap<>();

        private Builder(UUID sub, String email) {
            this.sub = sub;
            this.email = email;
        }

        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        public Builder audience(String audience) {
            this.audience = audience;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder signedWithOtherKey() {
            this.otherKey = true;
            return this;
        }

        public Builder claim(String name, Object value) {
            extraClaims.put(name, value);
            return this;
        }

        public Builder fullName(String fullName) {
            userMetadata.put("full_name", fullName);
            return this;
        }

        public String build() {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .subject(sub.toString())
                    .issuer(issuer)
                    .audience(audience)
                    .issueTime(Date.from(Instant.now().minusSeconds(5)))
                    .expirationTime(Date.from(expiresAt))
                    .claim("role", "authenticated")
                    .claim("user_metadata", userMetadata);
            if (email != null) {
                claims.claim("email", email);
            }
            extraClaims.forEach(claims::claim);
            try {
                SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test").build(),
                        claims.build());
                jwt.sign(new RSASSASigner((otherKey ? OTHER_KEYS : KEYS).getPrivate()));
                return jwt.serialize();
            } catch (JOSEException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
