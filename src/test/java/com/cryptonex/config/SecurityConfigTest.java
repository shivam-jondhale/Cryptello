package com.cryptonex.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod") // Simulate prod profile for security headers
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void securityHeaders_shouldBePresentInProd() throws Exception {
        mockMvc.perform(get("/").header("X-Forwarded-Proto", "https"))
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age")))
                .andExpect(header().string("Content-Security-Policy", notNullValue()))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
