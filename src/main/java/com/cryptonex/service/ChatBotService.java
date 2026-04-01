package com.cryptonex.service;

import com.cryptonex.model.CoinDTO;
import com.cryptonex.response.ApiResponse;


public interface ChatBotService {
    ApiResponse getCoinDetails(String coinName);

    CoinDTO getCoinByName(String coinName);

    String simpleChat(String prompt);
}
