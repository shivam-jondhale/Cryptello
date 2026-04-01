package com.cryptonex.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenValidator extends OncePerRequestFilter {

	private static final Logger logger = LoggerFactory.getLogger(JwtTokenValidator.class);

	private final com.cryptonex.config.JwtConfig jwtConfig;

	@Autowired
	private org.springframework.security.web.AuthenticationEntryPoint entryPoint;

	public JwtTokenValidator(com.cryptonex.config.JwtConfig jwtConfig) {
		this.jwtConfig = jwtConfig;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String jwt = request.getHeader(JwtConstant.JWT_HEADER);

		if (jwt != null && !jwt.isBlank()) {
			// Bearer token
			if (jwt.startsWith("Bearer ")) {
				jwt = jwt.substring(7);
			}

			try {
				SecretKey key = jwtConfig.getSecretKey();

				Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(jwt).getBody();

				String email = claims.getSubject(); // Use subject as email/username
				String authorities = String.valueOf(claims.get("roles")); // Use 'roles' claim

				logger.debug("Processing JWT for email: {}, authorities: {}", email, authorities);

				List<GrantedAuthority> auths = AuthorityUtils.commaSeparatedStringToAuthorityList(authorities);
				Authentication authentication = new UsernamePasswordAuthenticationToken(email, null, auths);

				SecurityContextHolder.getContext().setAuthentication(authentication);

			} catch (Exception e) {
				logger.error("Invalid token received", e);
				SecurityContextHolder.clearContext();
				entryPoint.commence(request, response,
						new org.springframework.security.authentication.BadCredentialsException(
								"Invalid token: " + e.getMessage(), e));
				return; // Stop filter chain
			}
		}
		filterChain.doFilter(request, response);
	}
}
