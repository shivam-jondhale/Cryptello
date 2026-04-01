package com.cryptonex.auth;

import com.cryptonex.request.LoginRequest;
import com.cryptonex.common.utils.OtpUtils;
import com.cryptonex.security.JwtProvider;
import com.cryptonex.common.exception.UserException;
import com.cryptonex.model.User;
import com.cryptonex.model.TwoFactorOTP;
import com.cryptonex.repository.UserRepository;
import com.cryptonex.service.WatchlistService;
import com.cryptonex.service.WalletService;
import com.cryptonex.trader.VerificationService;
import com.cryptonex.service.TwoFactorOtpService;
import com.cryptonex.service.EmailService;
import com.cryptonex.service.AlertService;
import com.cryptonex.user.UserService;
import com.cryptonex.response.AuthResponse;
import com.cryptonex.request.SignupRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserService userService;

    @Autowired
    private WatchlistService watchlistService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private VerificationService verificationService;

    @Autowired
    private TwoFactorOtpService twoFactorOtpService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private AlertService alertService;

    @PostMapping("/signup")
    @Transactional
    public ResponseEntity<AuthResponse> createUserHandler(
            @jakarta.validation.Valid @RequestBody SignupRequest signupRequest) throws UserException {

        String email = signupRequest.getEmail();
        String password = signupRequest.getPassword();
        String fullName = signupRequest.getFullName();
        String mobile = signupRequest.getMobile();

        User isEmailExist = userRepository.findByEmail(email);

        if (isEmailExist != null) {
            throw new UserException("Email Is Already Used With Another Account");
        }

        // Create new user
        User createdUser = new User();
        createdUser.setEmail(email);
        createdUser.setFullName(fullName);
        createdUser.setMobile(mobile);
        createdUser.setPassword(passwordEncoder.encode(password));
        createdUser.getRoles().add(com.cryptonex.domain.USER_ROLE.ROLE_USER);

        User savedUser = userRepository.save(createdUser);

        watchlistService.createWatchList(savedUser);
        // walletService.createWallet(user);

        Authentication authentication = new UsernamePasswordAuthenticationToken(email, password);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String token = jwtProvider.generateToken(authentication);

        AuthResponse authResponse = new AuthResponse();
        authResponse.setJwt(token);
        authResponse.setStatus(true);
        authResponse.setMessage("Register Success");

        return new ResponseEntity<AuthResponse>(authResponse, HttpStatus.OK);

    }

    @Autowired
    private com.cryptonex.security.LoginAttemptService loginAttemptService;

    @PostMapping("/signin")
    public ResponseEntity<AuthResponse> signing(@RequestBody LoginRequest loginRequest, HttpServletRequest request)
            throws UserException, MessagingException {

        String username = loginRequest.getEmail().trim(); // Trim spaces
        String password = loginRequest.getPassword();
        String ip = getClientIp(request);

        // Pre-check: If blocked, avoid processing and return generic error
        if (loginAttemptService.isBlocked(username, ip)) {
            logger.warn("Blocked login attempt for user {} from IP {}", username, ip);
            throw new com.cryptonex.common.exception.LockedException(
                    "Invalid credentials or account temporarily unavailable");
        }

        logger.debug("Signing in user: {}", username);

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));

            // Login successful
            loginAttemptService.loginSucceeded(username, ip);

        } catch (Exception e) {
            // Login failed
            logger.error("Authentication failed for user {}: {}", username, e.getMessage());
            loginAttemptService.loginFailed(username, ip);
            alertService.logAlert("Authentication failed for user: " + username);

            // Mask the specific error (BadCredentials vs Locked etc) for security
            throw new com.cryptonex.common.exception.LockedException(
                    "Invalid credentials or account temporarily unavailable");
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = userService.findUserByEmail(username);

        String token = jwtProvider.generateToken(authentication);

        if (user.getTwoFactorAuth().isEnabled()) {
            AuthResponse authResponse = new AuthResponse();
            authResponse.setMessage("Two factor authentication enabled");
            authResponse.setTwoFactorAuthEnabled(true);

            String otp = OtpUtils.generateOTP();

            TwoFactorOTP oldTwoFactorOTP = twoFactorOtpService.findByUser(user.getId());
            if (oldTwoFactorOTP != null) {
                twoFactorOtpService.deleteTwoFactorOtp(oldTwoFactorOTP);
            }

            TwoFactorOTP twoFactorOTP = twoFactorOtpService.createTwoFactorOtp(user, otp, token);

            emailService.sendVerificationOtpEmail(user.getEmail(), otp);

            authResponse.setSession(twoFactorOTP.getId());
            return new ResponseEntity<>(authResponse, HttpStatus.OK);
        }

        AuthResponse authResponse = new AuthResponse();

        authResponse.setMessage("Login Success");
        authResponse.setJwt(token);
        authResponse.setStatus(true);

        return new ResponseEntity<>(authResponse, HttpStatus.OK);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }

}
