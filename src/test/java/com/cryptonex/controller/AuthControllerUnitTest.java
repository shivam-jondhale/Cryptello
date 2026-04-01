package com.cryptonex.controller;

import com.cryptonex.auth.AuthController;
import com.cryptonex.security.JwtProvider;
import com.cryptonex.user.UserService;
import com.cryptonex.model.TwoFactorAuth;
import com.cryptonex.model.TwoFactorOTP;
import com.cryptonex.model.User;
import com.cryptonex.repository.UserRepository;
import com.cryptonex.request.LoginRequest;
import com.cryptonex.response.AuthResponse;
import com.cryptonex.service.CustomeUserServiceImplementation;
import com.cryptonex.service.TwoFactorOtpService;
import com.cryptonex.service.WatchlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthControllerUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomeUserServiceImplementation customUserDetailsService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private TwoFactorOtpService twoFactorOtpService;

    @Mock
    private WatchlistService watchlistService;

    @Mock
    private UserService userService;

    @Mock
    private com.cryptonex.security.LoginAttemptService loginAttemptService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void login_shouldReturnJwt_when2FaDisabled() throws Exception {
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("password");

        User user = new User();
        user.setEmail("test@example.com");
        TwoFactorAuth twoFactorAuth = new TwoFactorAuth();
        twoFactorAuth.setEnabled(false);
        user.setTwoFactorAuth(twoFactorAuth);

        UserDetails userDetails = mock(UserDetails.class);
        Authentication auth = mock(Authentication.class);
        jakarta.servlet.http.HttpServletRequest request = mock(jakarta.servlet.http.HttpServletRequest.class);

        when(customUserDetailsService.loadUserByUsername("test@example.com")).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(jwtProvider.generateToken(auth)).thenReturn("jwt_token");
        when(userRepository.findByEmail("test@example.com")).thenReturn(user);
        when(userService.findUserByEmail("test@example.com")).thenReturn(user);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(loginAttemptService.isBlocked(anyString(), anyString())).thenReturn(false);

        ResponseEntity<AuthResponse> response = authController.signing(loginRequest, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("jwt_token", response.getBody().getJwt());
        assertEquals(true, response.getBody().isStatus());
    }
}
