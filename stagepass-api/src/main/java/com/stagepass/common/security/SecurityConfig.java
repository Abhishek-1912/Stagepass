package com.stagepass.common.security;

import com.stagepass.common.config.StagepassProperties;
import com.stagepass.common.config.SupabaseProperties;
import com.stagepass.common.error.ErrorCode;
import com.stagepass.common.error.ProblemAccessDeniedHandler;
import jakarta.servlet.DispatcherType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.client.RestTemplate;
import com.stagepass.common.error.ProblemAuthEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemAuthEntryPoint entryPoint,
            ProblemAccessDeniedHandler accessDeniedHandler, StagepassAuthenticationConverter converter)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> { })
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // Let container error dispatches through so failures surface as 5xx, not a fake 401.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/**", "/e/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/webhooks/**", "/api/v1/public/quote").permitAll()
                        .requestMatchers("/api/v1/organizer/**").hasRole("ORGANIZER")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(SupabaseProperties props) {
        return supabaseJwtDecoder(props);
    }

    /**
     * Decoder for Supabase access tokens: keys from the project JWKS (ES256 is Supabase's default signing algorithm,
     * RS256 is allowed too), 3-second HTTP timeouts, and JWKS outages reported as 503 rather than "bad token" —
     * otherwise a Supabase blip would look like an expired session and sign every user out.
     */
    public static JwtDecoder supabaseJwtDecoder(SupabaseProperties props) {
        SimpleClientHttpRequestFactory http = new SimpleClientHttpRequestFactory();
        http.setConnectTimeout(Duration.ofSeconds(3));
        http.setReadTimeout(Duration.ofSeconds(3));
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(props.jwksUri())
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(new RestTemplate(http))
                .build();
        decoder.setJwtValidator(supabaseValidator(props));
        return token -> {
            try {
                return decoder.decode(token);
            } catch (BadJwtException e) {
                throw e;
            } catch (JwtException e) {
                throw new StagepassAuthenticationException(ErrorCode.SERVICE_UNAVAILABLE,
                        "Sign-in is temporarily unavailable. Please try again in a moment.");
            }
        };
    }

    /** Issuer, expiry/not-before and audience checks shared by production and test decoders. */
    public static OAuth2TokenValidator<Jwt> supabaseValidator(SupabaseProperties props) {
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>("aud",
                aud -> aud != null && aud.contains(props.audience()));
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(props.issuer()), audience);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(StagepassProperties props) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(props.cors().allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        cors.setExposedHeaders(List.of("Location"));
        cors.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
