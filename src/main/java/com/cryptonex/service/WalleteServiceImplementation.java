package com.cryptonex.service;

import com.cryptonex.domain.OrderType;
import com.cryptonex.domain.WalletTransactionType;
import com.cryptonex.common.exception.WalletException;
import com.cryptonex.model.*;

import com.cryptonex.repository.WalletRepository;
import com.cryptonex.repository.WalletTransactionRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

@Service

public class WalleteServiceImplementation implements WalletService {

    private static final Logger logger = LoggerFactory.getLogger(WalleteServiceImplementation.class);

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    public Wallet genrateWallete(User user) {
        Wallet wallet = new Wallet();
        wallet.setUser(user);
        return walletRepository.save(wallet);
    }

    @Override
    public Wallet getUserWallet(User user) throws WalletException {

        Wallet wallet = walletRepository.findByUserId(user.getId());
        if (wallet != null) {
            return wallet;
        }

        wallet = genrateWallete(user);
        return wallet;
    }

    @Override
    public Wallet findWalletById(Long id) throws WalletException {
        Optional<Wallet> wallet = walletRepository.findById(id);
        if (wallet.isPresent()) {
            return wallet.get();
        }
        throw new WalletException("Wallet not found with id " + id);
    }

    @Override
    public Wallet walletToWalletTransfer(User sender, Wallet receiverWallet, BigDecimal amount) throws WalletException {
        Wallet senderWallet = getUserWallet(sender);

        if (senderWallet.getBalance().compareTo(amount) < 0) {
            throw new WalletException("Insufficient balance...");
        }

        BigDecimal senderBalance = senderWallet.getBalance().subtract(amount);
        senderWallet.setBalance(senderBalance);
        walletRepository.save(senderWallet);

        BigDecimal receiverBalance = receiverWallet.getBalance();
        receiverBalance = receiverBalance.add(amount);
        receiverWallet.setBalance(receiverBalance);
        walletRepository.save(receiverWallet);

        return senderWallet;
    }

    @Override
    public Wallet payOrderPayment(Order order, User user) throws WalletException {
        Wallet wallet = getUserWallet(user);

        WalletTransaction walletTransaction = new WalletTransaction();
        walletTransaction.setWallet(wallet);
        walletTransaction.setPurpose(order.getOrderType() + " " + order.getOrderItem().getCoin().getId());

        walletTransaction.setDate(LocalDate.now());
        walletTransaction.setTransferId(order.getOrderItem().getCoin().getSymbol());

        if (order.getOrderType().equals(OrderType.BUY)) {
            // walletTransaction.setType(WalletTransactionType.BUY_ASSET);
            walletTransaction.setAmount(order.getPrice().negate());
            BigDecimal newBalance = wallet.getBalance().subtract(order.getPrice());

            if (newBalance.compareTo(order.getPrice()) < 0) {
                logger.warn("Insufficient funds for transaction. Balance: {}, Required: {}", wallet.getBalance(),
                        order.getPrice());
                throw new WalletException("Insufficient funds for this transaction.");
            }
            logger.info("Funds deducted for BUY order. New Balance: {}", newBalance);
            wallet.setBalance(newBalance);
        } else if (order.getOrderType().equals(OrderType.SELL)) {
            // walletTransaction.setType(WalletTransactionType.SELL_ASSET);
            walletTransaction.setAmount(order.getPrice());
            BigDecimal newBalance = wallet.getBalance().add(order.getPrice());
            wallet.setBalance(newBalance);
        }

        // logger.debug("wallet balance {}-------{}", wallet, order.getPrice());
        walletTransactionRepository.save(walletTransaction);
        walletRepository.save(wallet);
        return wallet;
    }

    @Override
    public Wallet addBalanceToWallet(Wallet wallet, BigDecimal money) throws WalletException {

        BigDecimal newBalance = wallet.getBalance().add(money);

        // if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
        // throw new Exception("Insufficient funds for this transaction.");
        // }

        wallet.setBalance(wallet.getBalance().add(money));

        walletRepository.save(wallet);
        // logger.debug("updated wallet - {}", wallet);
        return wallet;
    }

}
