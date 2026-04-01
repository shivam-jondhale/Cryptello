package com.cryptonex.response;

import lombok.Data;

@Data
public class FunctionResponse {
    private String functionName;
    private String currencyName;
    private String currencyData;
    
    public FunctionResponse() {}

	public FunctionResponse(String functionName, String currencyName, String currencyData) {
		super();
		this.functionName = functionName;
		this.currencyName = currencyName;
		this.currencyData = currencyData;
	}

	public String getFunctionName() {
		return functionName;
	}

	public void setFunctionName(String functionName) {
		this.functionName = functionName;
	}

	public String getCurrencyName() {
		return currencyName;
	}

	public void setCurrencyName(String currencyName) {
		this.currencyName = currencyName;
	}

	public String getCurrencyData() {
		return currencyData;
	}

	public void setCurrencyData(String currencyData) {
		this.currencyData = currencyData;
	}
    
}
