package com.cryptonex.controller;

import com.cryptonex.auth.AuthController;
import com.cryptonex.repository.UserRepository;
import com.cryptonex.request.LoginRequest;
import com.cryptonex.request.SignupRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc

class AuthControllerIntegrationTest {

        @Container
        @ServiceConnection
        static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private ObjectMapper objectMapper;

        @Test
        void shouldSignupAndSignin() throws Exception {
                SignupRequest signupRequest = new SignupRequest();
                signupRequest.setFullName("Integration User");
                signupRequest.setEmail("integration@example.com");
                signupRequest.setPassword("password123");
                signupRequest.setMobile("1234567890");

                // Signup
                mockMvc.perform(post("/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(signupRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.jwt").exists());

                // Signin
                LoginRequest loginRequest = new LoginRequest();
                loginRequest.setEmail("integration@example.com");
                loginRequest.setPassword("password123");

                String response = mockMvc.perform(post("/auth/signin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.jwt").exists())
                                .andReturn().getResponse().getContentAsString();

                String token = objectMapper.readTree(response).get("jwt").asText();

                // Access protected endpoint
                mockMvc.perform(get("/api/users/profile")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.email").value("integration@example.com"));
        }
}
