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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc

@TestPropertySource(properties = {
                "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AuthControllerH2Test {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private ObjectMapper objectMapper;

        @Test
        void shouldSignupAndSignin() throws Exception {
                SignupRequest signupRequest = new SignupRequest();
                signupRequest.setFullName("H2 User");
                signupRequest.setEmail("h2@example.com");
                signupRequest.setPassword("password123");
                signupRequest.setMobile("1234567890");

                // Signup
                String signupResponse = mockMvc.perform(post("/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(signupRequest)))
                                .andDo(result -> System.out.println(
                                                "Signup Response: " + result.getResponse().getContentAsString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.jwt").exists())
                                .andReturn().getResponse().getContentAsString();

                // Signin
                LoginRequest loginRequest = new LoginRequest();
                loginRequest.setEmail("h2@example.com");
                loginRequest.setPassword("password123");

                String response = mockMvc.perform(post("/auth/signin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andDo(result -> System.out.println(
                                                "Signin Response: " + result.getResponse().getContentAsString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.jwt").exists())
                                .andReturn().getResponse().getContentAsString();

                String token = objectMapper.readTree(response).get("jwt").asText();

                // Access protected endpoint
                mockMvc.perform(get("/api/users/profile")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isAccepted())
                                .andExpect(jsonPath("$.email").value("h2@example.com"));
        }
}
