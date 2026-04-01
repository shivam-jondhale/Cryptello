package com.cryptonex.model;

import com.cryptonex.domain.WalletTransactionType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

@Entity
@Data
public class WalletTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long id;

	@ManyToOne
	private Wallet wallet;

	private WalletTransactionType type;

	private LocalDate date;

	private String transferId;

	private String purpose;

	private java.math.BigDecimal amount;

	public WalletTransaction() {
	}

	public WalletTransaction(Long id, Wallet wallet, WalletTransactionType type, LocalDate date, String transferId,
			String purpose, java.math.BigDecimal amount) {
		super();
		this.id = id;
		this.wallet = wallet;
		this.type = type;
		this.date = date;
		this.transferId = transferId;
		this.purpose = purpose;
		this.amount = amount;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Wallet getWallet() {
		return wallet;
	}

	public void setWallet(Wallet wallet) {
		this.wallet = wallet;
	}

	public WalletTransactionType getType() {
		return type;
	}

	public void setType(WalletTransactionType type) {
		this.type = type;
	}

	public LocalDate getDate() {
		return date;
	}

	public void setDate(LocalDate date) {
		this.date = date;
	}

	public String getTransferId() {
		return transferId;
	}

	public void setTransferId(String transferId) {
		this.transferId = transferId;
	}

	public String getPurpose() {
		return purpose;
	}

	public void setPurpose(String purpose) {
		this.purpose = purpose;
	}

	public java.math.BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(java.math.BigDecimal amount) {
		this.amount = amount;
	}

}
