package com.cryptonex.config;

import com.cryptonex.security.JwtProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtProviderTest {

    private JwtProvider jwtProvider;
    private String secret = "your_test_jwt_secret_value_must_be_long_enough_for_hs512"; // Same as in properties for
                                                                                         // test

    @BeforeEach
    void setUp() {
        com.cryptonex.config.JwtConfig jwtConfig = new com.cryptonex.config.JwtConfig(secret);
        jwtProvider = new JwtProvider(jwtConfig);
        ReflectionTestUtils.setField(jwtProvider, "jwtExpirationMs", 86400000L);
    }

    @Test
    void generateToken_shouldGenerateValidToken() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        Authentication auth = new UsernamePasswordAuthenticationToken("test@example.com", "password", authorities);

        String token = jwtProvider.generateToken(auth);

        assertNotNull(token);
        assertFalse(token.isEmpty());

        String email = jwtProvider.getEmailFromJwtToken(token);
        assertEquals("test@example.com", email);
    }

    @Test
    void getEmailFromJwtToken_shouldReturnCorrectEmail() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        Authentication auth = new UsernamePasswordAuthenticationToken("user@example.com", "password", authorities);

        String token = jwtProvider.generateToken(auth);
        String email = jwtProvider.getEmailFromJwtToken(token);

        assertEquals("user@example.com", email);
    }
}
