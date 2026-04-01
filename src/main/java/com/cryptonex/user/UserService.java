package com.cryptonex.user;

import com.cryptonex.domain.VerificationType;
import com.cryptonex.common.exception.UserException;
import com.cryptonex.model.User;

public interface UserService {

	public User findUserProfileByJwt(String jwt) throws UserException;

	public User findUserByEmail(String email) throws UserException;

	public User findUserById(Long userId) throws UserException;

	public User verifyUser(User user) throws UserException;

	public User enabledTwoFactorAuthentication(VerificationType verificationType,
			String sendTo, User user) throws UserException;

	User updatePassword(User user, String newPassword);

	void sendUpdatePasswordOtp(String email, String otp);

	User saveUser(User user) throws UserException;

	User updateUserInformation(String jwt, User updatedInfo) throws UserException;

}
