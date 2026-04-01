package com.cryptonex.request;

import lombok.Data;

@Data
public class PromptBody {
    private String prompt;
    
    public PromptBody() {}

	public PromptBody(String prompt) {
		super();
		this.prompt = prompt;
	}

	public String getPrompt() {
		return prompt;
	}

	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}
    
    
}
