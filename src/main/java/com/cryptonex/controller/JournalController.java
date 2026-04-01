package com.cryptonex.controller;

import com.cryptonex.model.User;
import com.cryptonex.model.UserTrade;
import com.cryptonex.service.JournalService;
import com.cryptonex.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/journal")
public class JournalController {

    @Autowired
    private JournalService journalService;

    @Autowired
    private UserService userService;

    @PostMapping("/add/{signalId}")
    public ResponseEntity<UserTrade> addTrade(
            @RequestHeader("Authorization") String jwt,
            @PathVariable Long signalId,
            @RequestBody Map<String, Double> request) throws Exception {
        User user = userService.findUserProfileByJwt(jwt);
        Double entryPriceUser = request.get("entryPrice");
        java.math.BigDecimal entryPrice = entryPriceUser != null ? java.math.BigDecimal.valueOf(entryPriceUser) : null;
        UserTrade trade = journalService.addToMyTrades(user, signalId, entryPrice);
        return new ResponseEntity<>(trade, HttpStatus.CREATED);
    }

    @PostMapping("/close/{tradeId}")
    public ResponseEntity<UserTrade> closeTrade(
            @RequestHeader("Authorization") String jwt,
            @PathVariable Long tradeId,
            @RequestBody Map<String, Double> request) throws Exception {
        User user = userService.findUserProfileByJwt(jwt);
        Double exitPriceUser = request.get("exitPrice");
        java.math.BigDecimal exitPrice = exitPriceUser != null ? java.math.BigDecimal.valueOf(exitPriceUser) : null;
        UserTrade trade = journalService.closeTrade(user, tradeId, exitPrice);
        return new ResponseEntity<>(trade, HttpStatus.OK);
    }

    @GetMapping("/my-trades")
    public ResponseEntity<List<UserTrade>> getMyTrades(
            @RequestHeader("Authorization") String jwt) throws Exception {
        User user = userService.findUserProfileByJwt(jwt);
        List<UserTrade> trades = journalService.getUserTrades(user);
        return new ResponseEntity<>(trades, HttpStatus.OK);
    }
}
