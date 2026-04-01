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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("prod")
@Transactional // Rollback after each test
public class Phase2Retest {

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
        // Clean up specific test user if exists (though @Transactional should handle
        // it)
        if (userRepository.findByEmail("phase2test@example.com") != null) {
            userRepository.delete(userRepository.findByEmail("phase2test@example.com"));
        }
    }

    // -------------------------------------------------------------------
    // 1. Signup Edge Cases
    // -------------------------------------------------------------------

    @Test
    void signupHappyPath() throws Exception {
        SignupRequest req = new SignupRequest();
        req.setFullName("Phase2 User");
        req.setEmail("phase2test@example.com");
        req.setPassword("StrongPass123!");
        req.setMobile("1234567890");

        mockMvc.perform(post("https://localhost/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk()) // Controller returns 200 OK
                .andExpect(jsonPath("$.jwt").exists())
                .andExpect(jsonPath("$.status").value(true));
    }

    @Test
    void signupDuplicateEmail() throws Exception {
        // 1. First Signup
        SignupRequest req = new SignupRequest();
        req.setFullName("Phase2 User");
        req.setEmail("phase2test@example.com");
        req.setPassword("StrongPass123!");
        req.setMobile("1234567890");

        mockMvc.perform(post("https://localhost/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        // 2. Duplicate Signup (Case Insensitive Check)
        req.setEmail("PHASE2TEST@example.com");

        mockMvc.perform(post("https://localhost/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest()); // Expecting 400 or 409 for duplicate
    }

    @Test
    void signupBadEmailFormat() throws Exception {
        SignupRequest req = new SignupRequest();
        req.setFullName("Bad Email");
        req.setEmail("bad-email-format"); // No @
        req.setPassword("StrongPass123!");

        mockMvc.perform(post("https://localhost/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest()); // Validation error
    }

    @Test
    void signupPasswordTooShort() throws Exception {
        SignupRequest req = new SignupRequest();
        req.setFullName("Short Pass");
        req.setEmail("shortpass@example.com");
        req.setPassword("123"); // Too short

        // Assuming there is validation on password length
        // If not, this test might fail (return 200), indicating a security gap
        mockMvc.perform(post("https://localhost/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signupUnicodeName() throws Exception {
        SignupRequest req = new SignupRequest();
        req.setFullName("User 🚀 Unicode \u00F1");
        req.setEmail("unicode@example.com");
        req.setPassword("StrongPass123!");

        mockMvc.perform(post("https://localhost/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true));
    }

    // -------------------------------------------------------------------
    // 2. Signin Edge Cases
    // -------------------------------------------------------------------

    @Test
    void signinHappyPath() throws Exception {
        // Create user first
        User user = new User();
        user.setFullName("Signin User");
        user.setEmail("signin@example.com");
        user.setPassword(passwordEncoder.encode("Pass123!"));
        userRepository.save(user);

        LoginRequest login = new LoginRequest();
        login.setEmail("signin@example.com");
        login.setPassword("Pass123!");

        mockMvc.perform(post("https://localhost/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jwt").exists());
    }

    @Test
    void signinWrongPassword() throws Exception {
        // Create user
        User user = new User();
        user.setFullName("Wrong Pass User");
        user.setEmail("wrongpass@example.com");
        user.setPassword(passwordEncoder.encode("CorrectPass!"));
        userRepository.save(user);

        LoginRequest login = new LoginRequest();
        login.setEmail("wrongpass@example.com");
        login.setPassword("WrongPass!");

        mockMvc.perform(post("https://localhost/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized()); // Expect 401
    }

    @Test
    void signinNonExistentUser() throws Exception {
        LoginRequest login = new LoginRequest();
        login.setEmail("nonexistent@example.com");
        login.setPassword("AnyPass!");

        mockMvc.perform(post("https://localhost/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized()); // Expect 401
    }

    @Test
    void signinExtraSpaces() throws Exception {
        // Create user
        User user = new User();
        user.setFullName("Space User");
        user.setEmail("spaces@example.com");
        user.setPassword(passwordEncoder.encode("Pass123!"));
        userRepository.save(user);

        // Login with spaces
        LoginRequest login = new LoginRequest();
        login.setEmail(" spaces@example.com ");
        login.setPassword("Pass123!");

        // This expects the backend to trim the email
        mockMvc.perform(post("https://localhost/auth/signin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------
    // 3. Auth Header & JWT Usage
    // -------------------------------------------------------------------

    @Test
    void missingAuthHeader() throws Exception {
        mockMvc.perform(get("https://localhost/api/users/profile"))
                .andExpect(status().isUnauthorized()); // 401
    }

    @Test
    void wrongScheme() throws Exception {
        mockMvc.perform(get("https://localhost/api/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Token some_token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedHeader() throws Exception {
        mockMvc.perform(get("https://localhost/api/users/profile")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ")) // Empty token
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------
    // 4. OAuth2 Flow (Limited)
    // -------------------------------------------------------------------

    @Test
    void oauth2Redirect() throws Exception {
        mockMvc.perform(get("https://localhost/auth/login/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/login/oauth2/authorization/google"));
    }
}
