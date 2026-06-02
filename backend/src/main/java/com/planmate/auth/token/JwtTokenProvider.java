package com.planmate.auth.token;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.auth.config.JwtProperties;
import com.planmate.auth.exception.InvalidTokenException;
import com.planmate.user.domain.UserAccount;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JwtTokenProvider {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String JWT_ALGORITHM = "HS256";
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;
    private final byte[] secret;

    public JwtTokenProvider(JwtProperties jwtProperties, ObjectMapper objectMapper) {
        validateProperties(jwtProperties);
        this.jwtProperties = jwtProperties;
        this.objectMapper = objectMapper;
        this.secret = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
    }

    public IssuedToken issueAccessToken(UserAccount userAccount) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(jwtProperties.accessTokenTtl());

        Map<String, Object> header = Map.of(
                "alg", JWT_ALGORITHM,
                "typ", "JWT"
        );

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", userAccount.id().toString());
        claims.put("email", userAccount.email());
        claims.put("role", userAccount.role().name());
        claims.put("iat", issuedAt.getEpochSecond());
        claims.put("exp", expiresAt.getEpochSecond());

        String unsignedToken = encodeJson(header) + "." + encodeJson(claims);
        String signature = encode(sign(unsignedToken));

        return new IssuedToken(unsignedToken + "." + signature, expiresAt);
    }

    public JwtClaims parse(String token) {
        if (!StringUtils.hasText(token)) {
            throw new InvalidTokenException("Token is empty.");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new InvalidTokenException("Token format is invalid.");
        }

        Map<String, Object> header = decodeJson(parts[0]);
        if (!JWT_ALGORITHM.equals(header.get("alg"))) {
            throw new InvalidTokenException("Token algorithm is invalid.");
        }

        verifySignature(parts[0] + "." + parts[1], parts[2]);

        Map<String, Object> claims = decodeJson(parts[1]);
        Long userId = readSubject(claims.get("sub"));
        Instant expiresAt = Instant.ofEpochSecond(readEpochSecond(claims.get("exp"), "exp"));

        if (!expiresAt.isAfter(Instant.now())) {
            throw new InvalidTokenException("Token is expired.");
        }

        return new JwtClaims(userId, expiresAt);
    }

    private void verifySignature(String unsignedToken, String actualSignature) {
        String expectedSignature = encode(sign(unsignedToken));
        byte[] expected = expectedSignature.getBytes(StandardCharsets.US_ASCII);
        byte[] actual = actualSignature.getBytes(StandardCharsets.US_ASCII);

        if (!MessageDigest.isEqual(expected, actual)) {
            throw new InvalidTokenException("Token signature is invalid.");
        }
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return encode(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot write JWT json.", exception);
        }
    }

    private Map<String, Object> decodeJson(String value) {
        try {
            byte[] decoded = BASE64_URL_DECODER.decode(value);
            return objectMapper.readValue(decoded, new TypeReference<Map<String, Object>>() {
            });
        } catch (IllegalArgumentException | IOException exception) {
            throw new InvalidTokenException("Token json is invalid.", exception);
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret, HMAC_SHA256));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Cannot sign JWT.", exception);
        }
    }

    private String encode(byte[] value) {
        return BASE64_URL_ENCODER.encodeToString(value);
    }

    private Long readSubject(Object value) {
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException exception) {
                throw new InvalidTokenException("Token subject is invalid.", exception);
            }
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        throw new InvalidTokenException("Token subject is missing.");
    }

    private long readEpochSecond(Object value, String claimName) {
        if (value instanceof Number number) {
            return number.longValue();
        }

        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException exception) {
                throw new InvalidTokenException("Token " + claimName + " is invalid.", exception);
            }
        }

        throw new InvalidTokenException("Token " + claimName + " is missing.");
    }

    private void validateProperties(JwtProperties properties) {
        if (!StringUtils.hasText(properties.secret())) {
            throw new IllegalStateException("JWT secret must not be empty.");
        }

        Duration accessTokenTtl = properties.accessTokenTtl();
        if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalStateException("JWT access token TTL must be positive.");
        }
    }

}

