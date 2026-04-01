package com.cryptonex.request;

import com.cryptonex.domain.VerificationType;
import lombok.Data;

@Data
public class UpdatePasswordRequest {
    private String sendTo;
    private VerificationType verificationType;
    
    public UpdatePasswordRequest() {}
	public UpdatePasswordRequest(String sendTo, VerificationType verificationType) {
		super();
		this.sendTo = sendTo;
		this.verificationType = verificationType;
	}
	public String getSendTo() {
		return sendTo;
	}
	public void setSendTo(String sendTo) {
		this.sendTo = sendTo;
	}
	public VerificationType getVerificationType() {
		return verificationType;
	}
	public void setVerificationType(VerificationType verificationType) {
		this.verificationType = verificationType;
	}
	
	
    
    
}
