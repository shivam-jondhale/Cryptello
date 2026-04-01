package com.cryptonex.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;

@ControllerAdvice
public class GlobelExeptions {

	@ExceptionHandler(UserException.class)
	public ResponseEntity<ErrorDetails> userExceptionHandler(UserException ue,
			WebRequest req) {
		ErrorDetails error = new ErrorDetails(LocalDateTime.now(), HttpStatus.BAD_REQUEST.value(), "User Exception",
				ue.getMessage(), req.getDescription(false));
		return new ResponseEntity<ErrorDetails>(error, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(RuntimeException.class)
	public ResponseEntity<ErrorDetails> handleRuntimeException(RuntimeException ex, WebRequest request) {
		ErrorDetails error = new ErrorDetails(LocalDateTime.now(), HttpStatus.BAD_REQUEST.value(), "Runtime Exception",
				ex.getMessage(), request.getDescription(false));
		return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
	public ResponseEntity<ErrorDetails> handleMissingRequestHeaderException(
			org.springframework.web.bind.MissingRequestHeaderException ex, WebRequest request) {
		ErrorDetails error = new ErrorDetails(LocalDateTime.now(), HttpStatus.BAD_REQUEST.value(), "Missing Header",
				ex.getMessage(), request.getDescription(false));
		return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorDetails> handleOtherExceptions(Exception ex, WebRequest request) {
		ErrorDetails error = new ErrorDetails(LocalDateTime.now(), HttpStatus.INTERNAL_SERVER_ERROR.value(),
				"Internal Server Error", ex.getMessage(), request.getDescription(false));
		return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
	}

	@ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
	public ResponseEntity<ErrorDetails> handleBadCredentialsException(
			org.springframework.security.authentication.BadCredentialsException ex, WebRequest request) {
		ErrorDetails error = new ErrorDetails(LocalDateTime.now(), HttpStatus.UNAUTHORIZED.value(), "Unauthorized",
				ex.getMessage(), request.getDescription(false));
		return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
	}

	@ExceptionHandler(org.springframework.security.authentication.InternalAuthenticationServiceException.class)
	public ResponseEntity<ErrorDetails> handleInternalAuthenticationServiceException(
			org.springframework.security.authentication.InternalAuthenticationServiceException ex, WebRequest request) {
		ErrorDetails error = new ErrorDetails(LocalDateTime.now(), HttpStatus.UNAUTHORIZED.value(), "Unauthorized",
				ex.getMessage(), request.getDescription(false));
		return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
	}

	@ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorDetails> handleValidationException(
			org.springframework.web.bind.MethodArgumentNotValidException ex, WebRequest request) {
		StringBuilder message = new StringBuilder("Validation Failed: ");
		ex.getBindingResult().getAllErrors().forEach(error -> {
			message.append(error.getDefaultMessage()).append("; ");
		});
		ErrorDetails error = new ErrorDetails(LocalDateTime.now(), HttpStatus.BAD_REQUEST.value(), "Validation Error",
				message.toString(), request.getDescription(false));
		return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
	}

	@ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
	public ResponseEntity<ErrorDetails> handleAccessDeniedException(
			org.springframework.security.access.AccessDeniedException ex, WebRequest request) {
		ErrorDetails error = new ErrorDetails(LocalDateTime.now(), HttpStatus.FORBIDDEN.value(), "Forbidden",
				ex.getMessage(), request.getDescription(false));
		return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
	}

	@ExceptionHandler(LockedException.class)
	public ResponseEntity<ErrorDetails> handleLockedException(LockedException ex, WebRequest request) {
		// Generic message to avoid leaking lockout state if desired, but here we use
		// the exception message
		// The caller (AuthController) should provide the safe generic message if strict
		// security is needed.
		// However, per plan, we return 401.
		ErrorDetails error = new ErrorDetails(LocalDateTime.now(), HttpStatus.UNAUTHORIZED.value(), "Unauthorized",
				ex.getMessage(), request.getDescription(false));
		return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
	}

}
