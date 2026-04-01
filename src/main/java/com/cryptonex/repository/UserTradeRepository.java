package com.cryptonex.repository;

import com.cryptonex.model.UserTrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserTradeRepository extends JpaRepository<UserTrade, Long> {
    List<UserTrade> findByUserId(Long userId);

    List<UserTrade> findByUserIdOrderByEntryTimeDesc(Long userId);

    List<UserTrade> findByUserIdAndStatus(Long userId, UserTrade.TradeStatus status);
}
