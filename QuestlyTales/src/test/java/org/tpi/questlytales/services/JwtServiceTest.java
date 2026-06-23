package org.tpi.questlytales.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // Secret Base64 décodant sur 32 octets (256 bits requis pour HS256)
        ReflectionTestUtils.setField(jwtService, "secret", "HsMWMWKExJcjwKbJuTD0DpWN95KBEGsAZA6Zp24axzM=");
        ReflectionTestUtils.setField(jwtService, "expiration", 86_400_000L);
    }

    @Test
    void generatedToken_carriesEmailAndUserId() {
        String token = jwtService.generateToken("leo@example.com", "user-123");

        assertEquals("leo@example.com", jwtService.extractEmail(token));
        assertEquals("user-123", jwtService.extractUserId(token));
    }

    @Test
    void freshToken_isValid() {
        String token = jwtService.generateToken("leo@example.com", "user-123");
        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void malformedToken_isInvalid() {
        assertFalse(jwtService.isTokenValid("not-a-real-jwt"));
    }

    @Test
    void expiredToken_isInvalid() {
        ReflectionTestUtils.setField(jwtService, "expiration", -1_000L);
        String expired = jwtService.generateToken("leo@example.com", "user-123");
        assertFalse(jwtService.isTokenValid(expired));
    }

    @Test
    void tokenSignedWithAnotherSecret_isInvalid() {
        String token = jwtService.generateToken("leo@example.com", "user-123");

        JwtService other = new JwtService();
        ReflectionTestUtils.setField(other, "secret", "T3RoZXJTZWNyZXRLZXlGb3JUZXN0aW5nMTIzNDU2Nzg5MA==");
        ReflectionTestUtils.setField(other, "expiration", 86_400_000L);

        assertFalse(other.isTokenValid(token));
    }
}
