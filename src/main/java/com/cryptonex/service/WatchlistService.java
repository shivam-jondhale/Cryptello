package com.cryptonex.service;

import com.cryptonex.model.Coin;
import com.cryptonex.model.User;
import com.cryptonex.model.Watchlist;


public interface WatchlistService {

    Watchlist findUserWatchlist(Long userId) throws Exception;

    Watchlist createWatchList(User user);

    Watchlist findById(Long id) throws Exception;

    Coin addItemToWatchlist(Coin coin,User user) throws Exception;
}
