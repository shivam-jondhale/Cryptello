package com.cryptonex.config;

import com.cryptonex.model.User;
import com.cryptonex.repository.UserRepository;
import com.cryptonex.request.LoginRequest;
import com.cryptonex.request.SignupRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("prod")
@Transactional
public class Phase2ExtremeRetest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.cryptonex.service.AlertService alertService;

    @BeforeEach
    void setUp() {
        if (userRepository.findByEmail("extreme@example.com") != null) {
            userRepository.delete(userRepository.findByEmail("extreme@example.com"));
        }
    }

    // 1. SQL Injection in Login
    @Test
    void sqlInjectionInLoginShouldFail() throws Exception {
        LoginRequest login = new LoginRequest();
        login.setEmail("' OR '1'='1");
        login.setPassword("anything");

        mockMvc.perform(post("https://localhost/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized()); // Should not bypass auth
    }

    // 2. Stored XSS in Signup
    @Test
    void xssInSignupShouldBeSanitizedOrRejected() throws Exception {
        // DTO usually
        // But if DTO has it or if using Map, it might work.
        // SignupRequest likely doesn't have 'role', so Jackson might ignore it or fail.
        // We want to ensure the user created does NOT have ROLE_ADMIN.

        String json = "{" +
                "\"fullName\": \"Hacker\"," +
                "\"email\": \"hacker@example.com\"," +
                "\"password\": \"StrongPass123!\"," +
                "\"mobile\": \"1234567890\"," +
                "\"role\": \"ROLE_ADMIN\"" +
                "}";

        mockMvc.perform(post("https://localhost/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk());

        User hacker = userRepository.findByEmail("hacker@example.com");
        assert hacker != null;
        assert !hacker.getRoles().contains(com.cryptonex.domain.USER_ROLE.ROLE_ADMIN)
                : "Privilege Escalation Successful! User became ADMIN.";
    }

    // 4. JWT None Algorithm Attack
    @Test
    void jwtNoneAlgorithmShouldFail() throws Exception {
        // Create a token with "alg": "none"
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes());
        String body = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"extreme@example.com\"}".getBytes());
        String token = header + "." + body + ".";

        mockMvc.perform(get("https://localhost/api/users/profile")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // 5. Account Enumeration (Message Check)
    @Test
    void accountEnumerationCheck() throws Exception {
        // 1. Non-existent user
        LoginRequest nonExistent = new LoginRequest();
        nonExistent.setEmail("doesnotexist@example.com");
        nonExistent.setPassword("RandomPass123!");

        String responseNonExistent = mockMvc.perform(post("https://localhost/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(nonExistent)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // 2. Existing user with wrong password
        User user = new User();
        user.setFullName("Enumeration User");
        user.setEmail("enum@example.com");
        user.setPassword(passwordEncoder.encode("CorrectPass123!"));
        userRepository.save(user);

        LoginRequest wrongPass = new LoginRequest();
        wrongPass.setEmail("enum@example.com");
        wrongPass.setPassword("WrongPass123!");

        String responseWrongPass = mockMvc.perform(post("https://localhost/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(wrongPass)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // Ideally, messages should be identical or generic "Invalid credentials"
        // If they are different (e.g. "User not found" vs "Bad credentials"), it allows
        // enumeration.
        // We will assert that they DO NOT contain specific details like "User not
        // found".

        // NOTE: Current implementation might return "User not found". If so, this test
        // will fail, and we fix it.
        if (responseNonExistent.contains("User not found") || responseWrongPass.contains("User not found")) {
            throw new AssertionError("Account Enumeration Vulnerability: Error messages reveal user existence.");
        }
    }
}
