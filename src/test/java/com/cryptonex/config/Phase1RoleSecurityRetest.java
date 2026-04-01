package com.cryptonex.config;

import com.cryptonex.domain.USER_ROLE;
import com.cryptonex.model.User;
import com.cryptonex.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class Phase1RoleSecurityRetest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private UserDetailsService userDetailsService;

        @Autowired
        private PasswordEncoder passwordEncoder;

        @Autowired
        private com.cryptonex.security.JwtProvider jwtProvider;

        @BeforeEach
        public void setUp() {
                // Transactional handles cleanup
        }

        // ==========================================
        // A. Database & Role Model Tests
        // ==========================================

        @Test
        public void testP1T1_RolesSeededCorrectly() {
                // Verify the USER_ROLE enum contains exactly the expected values
                Set<String> expectedRoles = Set.of("ROLE_USER", "ROLE_TRADER", "ROLE_VERIFIED_TRADER", "ROLE_ADMIN");
                Set<String> actualRoles = Arrays.stream(USER_ROLE.values())
                                .map(Enum::name)
                                .collect(Collectors.toSet());

                assertEquals(expectedRoles, actualRoles, "Roles should match exactly the defined set");
                assertEquals(4, actualRoles.size(), "There should be exactly 4 roles");
        }

        @Test
        public void testP1T2_UserCanHaveMultipleRoles() {
                // Create a user with multiple roles
                User user = new User();
                String email = "multirole_" + System.currentTimeMillis() + "@example.com";
                user.setEmail(email);
                user.setPassword(passwordEncoder.encode("Password123!"));
                user.setFullName("Multi Role User");
                user.getRoles().add(USER_ROLE.ROLE_USER);
                user.getRoles().add(USER_ROLE.ROLE_TRADER);

                userRepository.save(user);

                // Load via UserDetailsService
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                // Verify authorities
                Set<String> authorities = userDetails.getAuthorities().stream()
                                .map(a -> a.getAuthority())
                                .collect(Collectors.toSet());

                assertTrue(authorities.contains("ROLE_USER"), "Should have ROLE_USER");
                assertTrue(authorities.contains("ROLE_TRADER"), "Should have ROLE_TRADER");
                assertEquals(2, authorities.size(), "Should have exactly 2 authorities");
        }

        // ==========================================
        // B. Role Hierarchy & URL Access Tests
        // ==========================================

        private String generateTokenForRoles(USER_ROLE... roles) {
                User user = new User();
                String email = "role_test_" + System.currentTimeMillis() + "_" + Arrays.toString(roles)
                                + "@example.com";
                user.setEmail(email);
                user.setPassword(passwordEncoder.encode("Password123!"));
                user.setFullName("Role Test User");
                for (USER_ROLE role : roles) {
                        user.getRoles().add(role);
                }
                userRepository.save(user);

                org.springframework.security.core.Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                user.getEmail(), null,
                                user.getRoles().stream()
                                                .map(r -> new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                                                r.toString()))
                                                .collect(Collectors.toList()));

                return jwtProvider.generateToken(auth);
        }

        @Test
        public void testP1T3_UserAccessMatrix() throws Exception {
                String tokenUser = generateTokenForRoles(USER_ROLE.ROLE_USER);

                // Anon -> 401
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/user/profile"))
                                .andExpect(status().isUnauthorized());

                // User -> 200
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/user/profile")
                                .header("Authorization", "Bearer " + tokenUser))
                                .andExpect(status().isOk());
        }

        @Test
        public void testP1T4_TraderAccessMatrix() throws Exception {
                String tokenUser = generateTokenForRoles(USER_ROLE.ROLE_USER);
                String tokenTrader = generateTokenForRoles(USER_ROLE.ROLE_USER, USER_ROLE.ROLE_TRADER);

                // Anon -> 401
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                                .get("/api/trader/dashboard"))
                                .andExpect(status().isUnauthorized());

                // User -> 403 (Forbidden by security rule)
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/trader/dashboard")
                                .header("Authorization", "Bearer " + tokenUser))
                                .andExpect(status().isForbidden());

                // Trader -> 404 or 500 (Passed security, but endpoint doesn't exist)
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/trader/dashboard")
                                .header("Authorization", "Bearer " + tokenTrader))
                                .andExpect(result -> {
                                        int status = result.getResponse().getStatus();
                                        assertTrue(status == 404 || status == 500,
                                                        "Expected 404 or 500, but got " + status);
                                });
        }

        @Test
        public void testP1T5_VerifiedTraderAccessMatrix() throws Exception {
                String tokenTrader = generateTokenForRoles(USER_ROLE.ROLE_USER, USER_ROLE.ROLE_TRADER);
                String tokenVerified = generateTokenForRoles(USER_ROLE.ROLE_USER, USER_ROLE.ROLE_TRADER,
                                USER_ROLE.ROLE_VERIFIED_TRADER);

                // Anon -> 401
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/verified-trader/premium"))
                                .andExpect(status().isUnauthorized());

                // Trader -> 403 (Forbidden)
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/verified-trader/premium")
                                .header("Authorization", "Bearer " + tokenTrader))
                                .andExpect(status().isForbidden());

                // Verified -> 404 or 500 (Passed security, endpoint missing)
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/verified-trader/premium")
                                .header("Authorization", "Bearer " + tokenVerified))
                                .andExpect(result -> {
                                        int status = result.getResponse().getStatus();
                                        assertTrue(status == 404 || status == 500,
                                                        "Expected 404 or 500, but got " + status);
                                });
        }

        @Test
        public void testP1T6_AdminAccessMatrix() throws Exception {
                String tokenUser = generateTokenForRoles(USER_ROLE.ROLE_USER);
                String tokenAdmin = generateTokenForRoles(USER_ROLE.ROLE_USER, USER_ROLE.ROLE_ADMIN);

                // Anon -> 401
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/admin/reconcile"))
                                .andExpect(status().isUnauthorized());

                // User -> 403
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/admin/reconcile")
                                .header("Authorization", "Bearer " + tokenUser))
                                .andExpect(status().isForbidden());

                // Admin -> 200
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/admin/reconcile")
                                .header("Authorization", "Bearer " + tokenAdmin))
                                .andExpect(status().isOk());
        }

        // ==========================================
        // C. Actuator & Webhook Security
        // ==========================================

        @Test
        public void testP1T7_ActuatorSecurity() throws Exception {
                // Health -> Public (200)
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/actuator/health"))
                                .andExpect(status().isOk());

                // Env -> Admin Only (403 for Anon/User, 200/404 for Admin)
                // Anon -> 401 (Unauthorized)
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/actuator/env"))
                                .andExpect(status().isUnauthorized());

                String tokenUser = generateTokenForRoles(USER_ROLE.ROLE_USER);
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/actuator/env")
                                .header("Authorization", "Bearer " + tokenUser))
                                .andExpect(status().isForbidden());
        }

        @Test
        public void testP1T8_WebhookSecurity() throws Exception {
                // Webhooks should be public (no 401)
                // We expect 400 Bad Request because we are sending dummy data/headers, but NOT
                // 401/403.

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/webhooks/stripe")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{}")
                                .header("Stripe-Signature", "dummy"))
                                .andExpect(result -> {
                                        int status = result.getResponse().getStatus();
                                        assertNotEquals(401, status, "Webhooks should not return 401");
                                        assertNotEquals(403, status, "Webhooks should not return 403");
                                });
        }

        // ==========================================
        // D. CORS & Security Headers
        // ==========================================

        @Test
        public void testP1T9_CorsAllowedOrigin() throws Exception {
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .options("/api/user/profile")
                                .header("Origin", "http://localhost:3000")
                                .header("Access-Control-Request-Method", "GET"))
                                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                                                .exists("Access-Control-Allow-Origin"))
                                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                                                .string("Access-Control-Allow-Origin", "http://localhost:3000"));
        }

        @Test
        public void testP1T10_CorsDisallowedOrigin() throws Exception {
                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .options("/api/user/profile")
                                .header("Origin", "http://evil.com")
                                .header("Access-Control-Request-Method", "GET"))
                                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                                                .doesNotExist("Access-Control-Allow-Origin"));
        }

        @Test
        public void testP1T11_SecurityHeaders() throws Exception {
                String tokenUser = generateTokenForRoles(USER_ROLE.ROLE_USER);

                mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/user/profile")
                                .header("Authorization", "Bearer " + tokenUser))
                                .andExpect(status().isOk())
                                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                                                .string("X-Frame-Options", "DENY"))
                                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                                                .exists("X-Content-Type-Options"));
        }
}
