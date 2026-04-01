package com.cryptonex.service;

import com.cryptonex.model.User;
import com.cryptonex.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CustomeUserServiceImplementation implements UserDetailsService {

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory
			.getLogger(CustomeUserServiceImplementation.class);
	private UserRepository userRepository;

	public CustomeUserServiceImplementation(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		logger.debug("Attempting to load user: {}", username);
		User user = userRepository.findByEmail(username);

		if (user == null) {
			logger.warn("User not found with email: {}", username);
			throw new UsernameNotFoundException("user not found with email  - " + username);
		}

		logger.debug("User found: {}", user.getEmail());

		List<GrantedAuthority> authorities = new ArrayList<>();
		for (com.cryptonex.domain.USER_ROLE role : user.getRoles()) {
			authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority(role.toString()));
		}

		return new org.springframework.security.core.userdetails.User(
				user.getEmail(), user.getPassword(), authorities);
	}

}
