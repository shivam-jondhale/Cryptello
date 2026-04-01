package com.cryptonex.security;

import com.cryptonex.model.User;
import com.cryptonex.repository.UserRepository;
import com.cryptonex.service.WatchlistService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.UUID;

@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2SuccessHandler.class);

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WatchlistService watchlistService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        if (authentication.getPrincipal() instanceof DefaultOAuth2User) {
            DefaultOAuth2User userDetails = (DefaultOAuth2User) authentication.getPrincipal();
            String email = userDetails.getAttribute("email");
            String fullName = userDetails.getAttribute("name");
            String picture = userDetails.getAttribute("picture");
            boolean emailVerified = Boolean.TRUE.equals(userDetails.getAttribute("email_verified"));

            User user = userRepository.findByEmail(email);

            if (user == null) {
                user = new User();
                user.setFullName(fullName);
                user.setEmail(email);
                user.setPicture(picture);
                user.setVerified(emailVerified);
                user.setPassword(UUID.randomUUID().toString()); // Random password for OAuth users
                user = userRepository.save(user);
                watchlistService.createWatchList(user);
                logger.info("Created new user from OAuth: {}", email);
            } else {
                logger.info("User logged in via OAuth: {}", email);
            }

            String token = jwtProvider.generateToken(authentication);

            // Redirect to frontend with token
            // Assuming frontend is at localhost:3000 or configured URL
            // In production, this should be dynamic or configurable
            String targetUrl = UriComponentsBuilder.fromUriString("http://localhost:3000/auth/success")
                    .queryParam("token", token)
                    .build().toUriString();

            response.sendRedirect(targetUrl);
        }
    }
}
