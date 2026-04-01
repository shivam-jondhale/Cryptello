package com.cryptonex.request;

import lombok.Data;

@Data
public class ResetPasswordRequest {

    private String password;
    private String otp;
    
    public ResetPasswordRequest() {}

	public ResetPasswordRequest(String password, String otp) {
		super();
		this.password = password;
		this.otp = otp;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getOtp() {
		return otp;
	}

	public void setOtp(String otp) {
		this.otp = otp;
	}
    
    
}
