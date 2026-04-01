package com.cryptonex.user;

import com.cryptonex.domain.VerificationType;
import com.cryptonex.common.exception.UserException;
import com.cryptonex.model.ForgotPasswordToken;
import com.cryptonex.model.Gender;
import com.cryptonex.model.Mode;
import com.cryptonex.model.User;
import com.cryptonex.model.VerificationCode;
import com.cryptonex.request.ResetPasswordRequest;
import com.cryptonex.request.UpdatePasswordRequest;
import com.cryptonex.response.ApiResponse;
import com.cryptonex.response.AuthResponse;
import com.cryptonex.service.EmailService;
import com.cryptonex.service.FileStorageService;
import com.cryptonex.service.ForgotPasswordService;
import com.cryptonex.common.exception.UserException;
import com.cryptonex.user.UserService;
import com.cryptonex.trader.VerificationService;
import com.cryptonex.common.utils.OtpUtils;

import jakarta.mail.MessagingException;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
public class UserController {

	@Autowired
	private UserService userService;

	@Autowired
	private VerificationService verificationService;

	@Autowired
	private ForgotPasswordService forgotPasswordService;

	@Autowired
	private EmailService emailService;

	@GetMapping("/api/user/profile")
	public ResponseEntity<User> getUserProfileHandler(
			@RequestHeader("Authorization") String jwt) throws UserException {

		User user = userService.findUserProfileByJwt(jwt);
		user.setPassword(null);

		return new ResponseEntity<>(user, HttpStatus.OK);
	}

	@GetMapping("/api/user/{userId}")
	public ResponseEntity<User> findUserById(
			@PathVariable Long userId,
			@RequestHeader("Authorization") String jwt) throws UserException {

		User user = userService.findUserById(userId);
		user.setPassword(null);

		return new ResponseEntity<>(user, HttpStatus.OK);
	}

	@GetMapping("/api/user/email/{email}")
	public ResponseEntity<User> findUserByEmail(
			@PathVariable String email,
			@RequestHeader("Authorization") String jwt) throws UserException {

		User user = userService.findUserByEmail(email);

		return new ResponseEntity<>(user, HttpStatus.OK);
	}

	@PatchMapping("/api/user/enable-two-factor/verify-otp/{otp}")
	public ResponseEntity<User> enabledTwoFactorAuthentication(
			@RequestHeader("Authorization") String jwt,
			@PathVariable String otp) throws Exception {

		User user = userService.findUserProfileByJwt(jwt);

		VerificationCode verificationCode = verificationService.findUsersVerification(user);

		String sendTo = verificationCode.getVerificationType().equals(VerificationType.EMAIL)
				? verificationCode.getEmail()
				: verificationCode.getMobile();

		boolean isVerified = verificationService.VerifyOtp(otp, verificationCode);

		if (isVerified) {
			User updatedUser = userService.enabledTwoFactorAuthentication(verificationCode.getVerificationType(),
					sendTo, user);
			verificationService.deleteVerification(verificationCode);
			return ResponseEntity.ok(updatedUser);
		}
		throw new Exception("wrong otp");

	}

	@PatchMapping("/auth/user/reset-password/verify-otp")
	public ResponseEntity<ApiResponse> resetPassword(
			@RequestParam String id,
			@RequestBody ResetPasswordRequest req) throws Exception {
		ForgotPasswordToken forgotPasswordToken = forgotPasswordService.findById(id);

		boolean isVerified = forgotPasswordService.verifyToken(forgotPasswordToken, req.getOtp());

		if (isVerified) {

			userService.updatePassword(forgotPasswordToken.getUser(), req.getPassword());
			ApiResponse apiResponse = new ApiResponse();
			apiResponse.setMessage("password updated successfully");
			return ResponseEntity.ok(apiResponse);
		}
		throw new Exception("wrong otp");

	}

	@PostMapping("/auth/user/reset-password/send-otp")
	public ResponseEntity<AuthResponse> sendUpdatePasswordOTP(
			@RequestBody UpdatePasswordRequest req)
			throws Exception {

		User user = userService.findUserByEmail(req.getSendTo());
		String otp = OtpUtils.generateOTP();
		UUID uuid = UUID.randomUUID();
		String id = uuid.toString();

		ForgotPasswordToken token = forgotPasswordService.findByUser(user.getId());

		if (token == null) {
			token = forgotPasswordService.createToken(
					user, id, otp, req.getVerificationType(), req.getSendTo());
		}

		if (req.getVerificationType().equals(VerificationType.EMAIL)) {
			emailService.sendVerificationOtpEmail(
					user.getEmail(),
					token.getOtp());
		}

		AuthResponse res = new AuthResponse();
		res.setSession(token.getId());
		res.setMessage("Password Reset OTP sent successfully.");

		return ResponseEntity.ok(res);

	}

	@PatchMapping("/api/user/verification/verify-otp/{otp}")
	public ResponseEntity<User> verifyOTP(
			@RequestHeader("Authorization") String jwt,
			@PathVariable String otp) throws Exception {

		User user = userService.findUserProfileByJwt(jwt);

		VerificationCode verificationCode = verificationService.findUsersVerification(user);

		boolean isVerified = verificationService.VerifyOtp(otp, verificationCode);

		if (isVerified) {
			verificationService.deleteVerification(verificationCode);
			User verifiedUser = userService.verifyUser(user);
			return ResponseEntity.ok(verifiedUser);
		}
		throw new Exception("wrong otp");

	}

	@PostMapping("/api/user/verification/{verificationType}/send-otp")
	public ResponseEntity<String> sendVerificationOTP(
			@PathVariable VerificationType verificationType,
			@RequestHeader("Authorization") String jwt)
			throws Exception {

		User user = userService.findUserProfileByJwt(jwt);

		VerificationCode verificationCode = verificationService.findUsersVerification(user);

		if (verificationCode == null) {
			verificationCode = verificationService.sendVerificationOTP(user, verificationType);
		}

		if (verificationType.equals(VerificationType.EMAIL)) {
			emailService.sendVerificationOtpEmail(user.getEmail(), verificationCode.getOtp());
		}

		return ResponseEntity.ok("Verification OTP sent successfully.");

	}

	@PatchMapping("/api/user/update-information")
	public ResponseEntity<User> updateUserProfile(
			@RequestHeader("Authorization") String jwtToken,
			@RequestBody User updatedInfo) throws UserException {
		// Fetch user based on the JWT token
		User user = userService.findUserProfileByJwt(jwtToken);

		// Update full name if provided and valid
		if (updatedInfo.getFullName() != null && !updatedInfo.getFullName().isBlank()) {
			user.setFullName(updatedInfo.getFullName());
		}

		// Update mobile number if provided and valid
		if (updatedInfo.getMobile() != null && !updatedInfo.getMobile().isBlank()) {
			user.setMobile(updatedInfo.getMobile());
		}

		// Update gender if provided
		if (updatedInfo.getGender() != null) {
			user.setGender(updatedInfo.getGender());
		}

		// Update date of birth if provided
		if (updatedInfo.getDob() != null) {
			user.setDob(updatedInfo.getDob());
		}

		// Update address if provided and valid
		if (updatedInfo.getAddress() != null && !updatedInfo.getAddress().isBlank()) {
			user.setAddress(updatedInfo.getAddress());
		}

		// Update city if provided and valid
		if (updatedInfo.getCity() != null && !updatedInfo.getCity().isBlank()) {
			user.setCity(updatedInfo.getCity());
		}

		// Update postcode if provided
		if (updatedInfo.getPostcode() != null) {
			user.setPostcode(updatedInfo.getPostcode());
		}

		// Update country if provided and valid
		if (updatedInfo.getCountry() != null && !updatedInfo.getCountry().isBlank()) {
			user.setCountry(updatedInfo.getCountry());
		}

		if (updatedInfo.getTheme() != null && !updatedInfo.getTheme().isBlank()) {
			user.setTheme(updatedInfo.getTheme());
		}

		// Update mode if provided and valid

		if (user.getMode() == null) {
			user.setMode(Mode.DARK);
		}

		if (updatedInfo.getMode() != null) {
			user.setMode(updatedInfo.getMode());
		}

		// Update profilePhoto if provided and valid
		if (updatedInfo.getProfilePhoto() != null && !updatedInfo.getProfilePhoto().isBlank()) {

			user.setProfilePhoto(updatedInfo.getProfilePhoto());

		} else if (user.getProfilePhoto() == null) {
			if (user.getGender() == Gender.MALE) {
				// Assign default male profile photo
				user.setProfilePhoto("Men.jpg");
			} else if (user.getGender() == Gender.FEMALE) {
				// Assign default female profile photo
				user.setProfilePhoto("Default-Women (1).jpg");
			} else {
				// Assign default profile photo for other genders
				user.setProfilePhoto("Default (1).jpg");
			}
		}

		// Save the updated user to the database
		User updatedUser = userService.saveUser(user);

		// Return the updated user object as the response
		return ResponseEntity.ok(updatedUser);
	}

	/**
	 * Helper method to get default profile photo based on gender.
	 *
	 * @param gender The gender of the user.
	 * @return The filename of the default profile photo.
	 */
	private String getDefaultProfilePhoto(Gender gender) {
		List<String> malePhotos = Arrays.asList(
				"Men.jpg",
				"Men(2).jpg",
				"Men(3).jpg",
				"Men(4).jpg",
				"Men(5).jpg",
				"Men(6).jpg",
				"Men(7).jpg",
				"Men(8).jpg",
				"Men(9).jpg");

		List<String> femalePhotos = Arrays.asList(
				"Default-Women (1).jpg",
				"Default-Women (2).jpg",
				"Default-Women (3).jpg",
				"Default-Women (4).jpg",
				"Default-Women (5).jpg",
				"Default-Women (6).jpg",
				"Default-Women (7).jpg",
				"Default-Women (8).jpg",
				"Default-Women (9).jpg");

		List<String> otherPhotos = Arrays.asList(
				"Default (1).jpg",
				"Default (2).jpg",
				"Default (3).jpg",
				"Default (4).jpg",
				"Default (5).jpg",
				"Default (6).jpg",
				"Default (7).jpg",
				"Default (8).jpg",
				"Default (9).jpg",
				"Default (10).jpg",
				"Default (11).jpg");

		switch (gender) {
			case MALE:
				return malePhotos.get(0); // Assign the first male photo as default
			case FEMALE:
				return femalePhotos.get(0); // Assign the first female photo as default
			default:
				return otherPhotos.get(0); // Assign the first other photo as default
		}
	}

	/**
	 * Helper method to validate if the selected profile photo is allowed based on
	 * gender.
	 *
	 * @param gender       The gender of the user.
	 * @param profilePhoto The selected profile photo filename.
	 * @return True if valid, else false.
	 */
	private boolean isValidProfilePhoto(Gender gender, String profilePhoto) {
		List<String> allowedPhotos = getProfilePhotosByGender(gender);
		return allowedPhotos.contains(profilePhoto);
	}

	/**
	 * Helper function to get profile photos based on gender.
	 *
	 * @param gender The gender of the user.
	 * @return A list of allowed profile photo filenames.
	 */
	private List<String> getProfilePhotosByGender(Gender gender) {
		List<String> malePhotos = Arrays.asList(
				"Men.jpg",
				"Men(2).jpg",
				"Men(3).jpg",
				"Men(4).jpg",
				"Men(5).jpg",
				"Men(6).jpg",
				"Men(7).jpg",
				"Men(8).jpg",
				"Men(9).jpg");

		List<String> femalePhotos = Arrays.asList(
				"Default-Women (1).jpg",
				"Default-Women (2).jpg",
				"Default-Women (3).jpg",
				"Default-Women (4).jpg",
				"Default-Women (5).jpg",
				"Default-Women (6).jpg",
				"Default-Women (7).jpg",
				"Default-Women (8).jpg",
				"Default-Women (9).jpg");

		List<String> otherPhotos = Arrays.asList(
				"Default (1).jpg",
				"Default (2).jpg",
				"Default (3).jpg",
				"Default (4).jpg",
				"Default (5).jpg",
				"Default (6).jpg",
				"Default (7).jpg",
				"Default (8).jpg",
				"Default (9).jpg",
				"Default (10).jpg",
				"Default (11).jpg");

		switch (gender) {
			case MALE:
				return malePhotos;
			case FEMALE:
				return femalePhotos;
			default:
				return otherPhotos;
		}
	}
}
