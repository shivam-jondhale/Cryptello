package com.cryptonex.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

@Configuration
public class AppConfig {

	@Autowired
	private Environment env;

	@Value("${app.cors.allowed-origins:http://localhost:3000}")
	private String allowedOrigins;

	@Autowired
	private JwtTokenValidator jwtTokenValidator;

	// Removed manual bean definition for JwtTokenValidator as it is now a
	// @Component

	@Autowired
	private OAuth2SuccessHandler oAuth2SuccessHandler;

	@Autowired
	private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

	@Autowired
	private JwtAccessDeniedHandler jwtAccessDeniedHandler;

	@Autowired
	private RateLimitingFilter rateLimitingFilter;

	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

		// Conditional HTTPS enforcement for Production
		if (env.matchesProfiles("prod")) {
			http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
			http.headers(headers -> headers
					.httpStrictTransportSecurity(hsts -> hsts
							.includeSubDomains(true)
							.maxAgeInSeconds(31536000)));
		}

		// Security Headers (Global)
		http.headers(headers -> headers
				// Content Security Policy (Strict but allows Stripe/Razorpay/CoinGecko)
				.contentSecurityPolicy(csp -> csp.policyDirectives(
						"default-src 'self'; " +
								"script-src 'self' 'unsafe-inline' https://js.stripe.com https://checkout.razorpay.com; "
								+
								"style-src 'self' 'unsafe-inline'; " +
								"img-src 'self' data: https://*.coinmarketcap.com https://*.coingecko.com; " +
								"font-src 'self' data:; " +
								"connect-src 'self' https://api.stripe.com https://api.razorpay.com "
								+ allowedOrigins.replace(",", " ") + ";"))
				// Frame Options - Deny embedding to prevent Clickjacking
				.frameOptions(frame -> frame.deny())
				// XSS Protection
				.xssProtection(xss -> xss.headerValue(
						org.springframework.security.web.header.writers.XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
				// Content Type Options - Prevent MIME Sniffing
				.contentTypeOptions(contentType -> {
				})
				// Permissions Policy (Disable unused features)
				// HSTS (Strict Transport Security)
				.httpStrictTransportSecurity(hsts -> hsts
						.includeSubDomains(true)
						.maxAgeInSeconds(31536000)
						.preload(true))
				// Permissions Policy (Disable unused features)
				.permissionsPolicy(permissions -> permissions.policy(
						"camera=(), microphone=(), geolocation=(), payment=(self 'https://js.stripe.com')")));

		http.sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/api/auth/**").permitAll()
						.requestMatchers("/api/public/**").permitAll()
						.requestMatchers("/api/webhooks/**").permitAll()
						.requestMatchers("/actuator/health").permitAll()
						.requestMatchers("/api/admin/**").hasRole("ADMIN")
						.requestMatchers("/actuator/**").hasRole("ADMIN")
						.requestMatchers("/api/verified-trader/**").hasRole("VERIFIED_TRADER")
						.requestMatchers("/api/trader/**").hasRole("TRADER")
						.requestMatchers("/api/user/**").hasRole("USER")
						.requestMatchers("/api/payments/**").hasRole("USER")
						.anyRequest().authenticated())
				.oauth2Login(oauth -> {
					oauth.loginPage("/login/google");
					oauth.authorizationEndpoint(authorization -> authorization.baseUri("/login/oauth2/authorization"));
					oauth.successHandler(oAuth2SuccessHandler);
				})
				.addFilterBefore(jwtTokenValidator, BasicAuthenticationFilter.class)
				.addFilterAfter(rateLimitingFilter, JwtTokenValidator.class)
				.csrf(csrf -> csrf.disable())
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint(jwtAuthenticationEntryPoint)
						.accessDeniedHandler(jwtAccessDeniedHandler));
		return http.build();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();

		// Dynamic CORS configuration
		java.util.List<String> origins = java.util.Arrays.asList(allowedOrigins.split(","));
		configuration.setAllowedOrigins(origins);

		configuration.setAllowedMethods(java.util.Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
		configuration.setAllowedHeaders(java.util.Arrays.asList("Authorization", "Content-Type", "X-Requested-With",
				"Accept", "Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers"));
		configuration.setExposedHeaders(java.util.Arrays.asList("Authorization"));
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(3600L);

		org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	@Bean
	public RoleHierarchy roleHierarchy() {
		RoleHierarchyImpl roleHierarchy = new RoleHierarchyImpl();
		String hierarchy = "ROLE_ADMIN > ROLE_VERIFIED_TRADER\n" +
				"ROLE_VERIFIED_TRADER > ROLE_TRADER\n" +
				"ROLE_TRADER > ROLE_USER";
		roleHierarchy.setHierarchy(hierarchy);
		return roleHierarchy;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public okhttp3.OkHttpClient okHttpClient() {
		return new okhttp3.OkHttpClient();
	}

}
