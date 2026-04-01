package com.cryptonex.user;

import com.cryptonex.security.JwtProvider;
import com.cryptonex.domain.VerificationType;
import com.cryptonex.common.exception.UserException;
import com.cryptonex.model.TwoFactorAuth;
import com.cryptonex.model.User;
import com.cryptonex.repository.UserRepository;

import jakarta.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserServiceImplementation implements UserService {

	@Override
	@Transactional
	public User updateUserInformation(String jwt, User updatedInfo) throws UserException {
		// Retrieve the existing user based on JWT
		User existingUser = findUserProfileByJwt(jwt);

		// Update only the allowed fields from the information section
		existingUser.setFullName(updatedInfo.getFullName());
		existingUser.setGender(updatedInfo.getGender());
		existingUser.setDob(updatedInfo.getDob());
		existingUser.setAddress(updatedInfo.getAddress());
		existingUser.setCity(updatedInfo.getCity());
		existingUser.setPostcode(updatedInfo.getPostcode());
		existingUser.setCountry(updatedInfo.getCountry());
		existingUser.setMobile(updatedInfo.getMobile());
		existingUser.setPicture(updatedInfo.getPicture());

		// Save and return the updated user
		return userRepository.save(existingUser);
	}

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtProvider jwtProvider;

	@Override
	public User findUserProfileByJwt(String jwt) throws UserException {
		String email = jwtProvider.getEmailFromJwtToken(jwt);

		User user = userRepository.findByEmail(email);

		if (user == null) {
			throw new UserException("user not exist with email " + email);
		}
		return user;
	}

	@Override
	public User saveUser(User user) {
		// Save user details to the database
		return userRepository.save(user);
	}

	@Override
	public User findUserByEmail(String username) throws UserException {

		User user = userRepository.findByEmail(username);

		if (user != null) {

			return user;
		}

		throw new UserException("user not exist with username " + username);
	}

	@Override
	public User findUserById(Long userId) throws UserException {
		Optional<User> opt = userRepository.findById(userId);

		if (opt.isEmpty()) {
			throw new UserException("user not found with id " + userId);
		}
		return opt.get();
	}

	@Override
	public User verifyUser(User user) throws UserException {
		user.setVerified(true);
		return userRepository.save(user);
	}

	@Override
	public User enabledTwoFactorAuthentication(
			VerificationType verificationType, String sendTo, User user) throws UserException {
		TwoFactorAuth twoFactorAuth = new TwoFactorAuth();
		twoFactorAuth.setEnabled(true);
		twoFactorAuth.setSendTo(verificationType);

		user.setTwoFactorAuth(twoFactorAuth);
		return userRepository.save(user);
	}

	@Override
	public User updatePassword(User user, String newPassword) {
		user.setPassword(passwordEncoder.encode(newPassword));
		return userRepository.save(user);
	}

	@Override
	public void sendUpdatePasswordOtp(String email, String otp) {

	}

}
