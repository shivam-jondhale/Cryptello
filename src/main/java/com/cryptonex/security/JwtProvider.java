package com.cryptonex.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Component
public class JwtProvider {

	private final com.cryptonex.config.JwtConfig jwtConfig;

	public JwtProvider(com.cryptonex.config.JwtConfig jwtConfig) {
		this.jwtConfig = jwtConfig;
	}

	@Value("${jwt.access-token-expiration-ms:86400000}")
	private long jwtExpirationMs;

	private SecretKey getKey() {
		return jwtConfig.getSecretKey();
	}

	public String generateToken(Authentication auth) {
		Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
		String roles = populateAuthorities(authorities);

		return Jwts.builder()
				.setIssuedAt(new Date())
				.setExpiration(new Date(new Date().getTime() + jwtExpirationMs))
				.setSubject(auth.getName())
				.claim("roles", roles)
				.signWith(getKey())
				.compact();
	}

	public String getEmailFromJwtToken(String jwt) {
		if (jwt.startsWith("Bearer ")) {
			jwt = jwt.substring(7);
		}

		Claims claims = Jwts.parserBuilder().setSigningKey(getKey()).build().parseClaimsJws(jwt).getBody();
		return claims.getSubject();
	}

	public String populateAuthorities(Collection<? extends GrantedAuthority> collection) {
		Set<String> auths = new HashSet<>();

		for (GrantedAuthority authority : collection) {
			auths.add(authority.getAuthority());
		}
		return String.join(",", auths);
	}

}
