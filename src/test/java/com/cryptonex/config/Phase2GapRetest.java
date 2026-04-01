package com.cryptonex.config;

import com.cryptonex.domain.USER_ROLE;
import com.cryptonex.domain.VerificationType;
import com.cryptonex.model.TwoFactorAuth;
import com.cryptonex.model.TwoFactorOTP;
import com.cryptonex.model.User;
import com.cryptonex.repository.TwoFactorOtpRepository;
import com.cryptonex.repository.UserRepository;
import com.cryptonex.request.LoginRequest;
import com.cryptonex.request.SignupRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("prod")
@Transactional
public class Phase2GapRetest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private TwoFactorOtpRepository twoFactorOtpRepository;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        private ObjectMapper objectMapper;

        @Value("${jwt.secret}")
        private String jwtSecret;

        @org.springframework.boot.test.mock.mockito.MockBean
        private com.cryptonex.service.AlertService alertService;

        @org.springframework.boot.test.mock.mockito.MockBean
        private com.cryptonex.service.EmailService emailService;

        @BeforeEach
        void setUp() {
                if (userRepository.findByEmail("gaptest@example.com") != null) {
                        userRepository.delete(userRepository.findByEmail("gaptest@example.com"));
                }
        }

        // 1. Password Complexity Test
        @Test
        void weakPasswordShouldBeRejected() throws Exception {
                SignupRequest req = new SignupRequest();
                req.setFullName("Weak Pass User");
                req.setEmail("gaptest@example.com");
                req.setPassword("simplepass"); // No number, no special char, no uppercase
                req.setMobile("1234567890");

                // Expect 400 Bad Request due to validation
                mockMvc.perform(post("https://localhost/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                                .andExpect(status().isBadRequest());
        }

        // 2. 2FA Flow Test
        @Test
        void twoFactorAuthFlowTest() throws Exception {
                // Create user with 2FA enabled
                User user = new User();
                user.setFullName("2FA User");
                user.setEmail("gaptest@example.com");
                user.setPassword(passwordEncoder.encode("StrongPass1!"));
                user.getRoles().add(USER_ROLE.ROLE_USER);

                TwoFactorAuth tfa = new TwoFactorAuth();
                tfa.setEnabled(true);
                tfa.setSendTo(VerificationType.EMAIL);
                user.setTwoFactorAuth(tfa);

                userRepository.save(user);

                // 1. Login -> Expect 2FA required
                LoginRequest login = new LoginRequest();
                login.setEmail("gaptest@example.com");
                login.setPassword("StrongPass1!");

                MvcResult result = mockMvc.perform(post("https://localhost/auth/signin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(login)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.twoFactorAuthEnabled").value(true))
                                .andExpect(jsonPath("$.session").exists()) // OTP ID
                                .andReturn();

                String responseStr = result.getResponse().getContentAsString();
                JsonNode root = objectMapper.readTree(responseStr);
                String sessionId = root.path("session").asText();

                // 2. Get the OTP from DB (since we mocked EmailService)
                TwoFactorOTP otpEntity = twoFactorOtpRepository.findById(sessionId).orElseThrow();
                String otpCode = otpEntity.getOtp();

                // 3. Verify OTP -> Expect JWT
                mockMvc.perform(post("https://localhost/auth/two-factor/otp/" + otpCode)
                                .param("id", sessionId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.jwt").exists());
        }

        // 3. RBAC Test (Admin Route Access)
        @Test
        void normalUserCannotAccessAdminRoutes() throws Exception {
                // Create normal user
                User user = new User();
                user.setFullName("Normal User");
                user.setEmail("gaptest@example.com");
                user.setPassword(passwordEncoder.encode("StrongPass1!"));
                user.getRoles().add(USER_ROLE.ROLE_USER);
                userRepository.save(user);

                // Login to get token
                LoginRequest login = new LoginRequest();
                login.setEmail("gaptest@example.com");
                login.setPassword("StrongPass1!");

                MvcResult result = mockMvc.perform(post("https://localhost/auth/signin")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(login)))
                                .andReturn();

                String token = objectMapper.readTree(result.getResponse().getContentAsString()).path("jwt").asText();

                // Try to access Admin route
                mockMvc.perform(get("https://localhost/api/admin/users")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isForbidden()); // 403
        }

        // 4. Expired Token Test
        @Test
        void expiredTokenShouldBeRejected() throws Exception {
                SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
                String expiredToken = Jwts.builder()
                                .setIssuedAt(new Date(System.currentTimeMillis() - 100000)) // Past
                                .setExpiration(new Date(System.currentTimeMillis() - 1000)) // Past
                                .claim("email", "gaptest@example.com")
                                .claim("authorities", "ROLE_USER")
                                .signWith(key)
                                .compact();

                mockMvc.perform(get("https://localhost/api/users/profile")
                                .header("Authorization", "Bearer " + expiredToken))
                                .andExpect(status().isUnauthorized()); // 401
        }
}
