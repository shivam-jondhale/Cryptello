package com.cryptonex.repository;

import com.cryptonex.model.TradeSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeSignalRepository extends JpaRepository<TradeSignal, Long> {
    List<TradeSignal> findByStatus(TradeSignal.SignalStatus status);

    org.springframework.data.domain.Page<TradeSignal> findPageByStatus(TradeSignal.SignalStatus status,
            org.springframework.data.domain.Pageable pageable);

    List<TradeSignal> findByPostAuthorId(Long authorId);
}
