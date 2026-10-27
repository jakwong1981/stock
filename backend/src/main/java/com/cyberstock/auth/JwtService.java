package com.cyberstock.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues HS256-signed access tokens. The subject is the user id (UUID). */
@Service
public class JwtService {

    public static final String ISSUER = "cyberstock-pulse";

    private final JwtEncoder encoder;
    private final long ttlMinutes;

    public JwtService(JwtEncoder encoder, com.cyberstock.config.AppProperties props) {
        this.encoder = encoder;
        this.ttlMinutes = Math.max(5, props.jwt().ttlMinutes());
    }

    public record IssuedToken(String token, Instant expiresAt) {}

    public IssuedToken issue(UUID userId) {
        Instant now = Instant.now();
        Instant exp = now.plus(ttlMinutes, ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .issuedAt(now)
                .expiresAt(exp)
                .subject(userId.toString())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return new IssuedToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), exp);
    }
}
