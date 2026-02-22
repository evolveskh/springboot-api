package com.example.springbootapi.service;

import com.example.springbootapi.dto.CreateTransactionRequest;
import com.example.springbootapi.dto.TransactionDTO;
import com.example.springbootapi.entity.Account;
import com.example.springbootapi.entity.Transaction;
import com.example.springbootapi.exception.InsufficientFundsException;
import com.example.springbootapi.exception.ResourceNotFoundException;
import com.example.springbootapi.mapper.TransactionMapper;
import com.example.springbootapi.repository.AccountRepository;
import com.example.springbootapi.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final TransactionMapper transactionMapper;

    @Transactional
    @CacheEvict(value = "balances", allEntries = true)
    public TransactionDTO createTransaction(CreateTransactionRequest request){

        // Validate based on transaction type
        Account fromAccount = null;
        Account toAccount = null;

        switch (request.getType()) {

            case TRANSFER:
                // validate make sure we have both accounts
                if (request.getFromAccountId() == null || request.getToAccountId() == null) {
                    throw new IllegalArgumentException("Transfer transaction requires both from and to accounts");
                }
                if (request.getFromAccountId().equals(request.getToAccountId())) {
                    throw new IllegalArgumentException("Cannot transfer to the same account");
                }
                fromAccount = findAccount(request.getFromAccountId());
                toAccount = findAccount(request.getToAccountId());
                if (fromAccount.getBalance().compareTo(request.getAmount()) < 0) {
                    throw new InsufficientFundsException("Insufficient funds. Available: " + fromAccount.getBalance() + ", Requested: " + request.getAmount());
                } else {
                    fromAccount.setBalance(fromAccount.getBalance().subtract(request.getAmount()));
                    toAccount.setBalance(toAccount.getBalance().add(request.getAmount()));
                }
                accountRepository.save(fromAccount);
                accountRepository.save(toAccount);
                break;
            case DEPOSIT:
                if (request.getToAccountId() == null) {
                    throw new IllegalArgumentException("Deposit transaction requires to account");
                }
                toAccount = findAccount(request.getToAccountId());
                toAccount.setBalance(toAccount.getBalance().add(request.getAmount()));
                accountRepository.save(toAccount);
                break;
            case WITHDRAWAL:
                if (request.getFromAccountId() == null) {
                    throw new IllegalArgumentException("Withdrawal transaction requires from account");
                }
                fromAccount = findAccount(request.getFromAccountId());
                if (fromAccount.getBalance().compareTo(request.getAmount()) < 0) {
                    throw new InsufficientFundsException("Insufficient funds. Available: " + fromAccount.
                            getBalance() + ", Requested: " + request.getAmount());
                }
                fromAccount.setBalance(fromAccount.getBalance().subtract(request.getAmount()));
                accountRepository.save(fromAccount);
                break;
        }

        // Build and save transaction
        Transaction transaction = Transaction.builder()
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(request.getAmount())
                .type(request.getType())
                .build();
        Transaction savedTransaction = transactionRepository.save(transaction);
        return transactionMapper.toDTO(savedTransaction);
    }

    public TransactionDTO getTransactionById(Long id){
        Transaction transaction = transactionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
        return transactionMapper.toDTO(transaction);
    }

    public Page<TransactionDTO> getAllTransactions(Pageable pageable) {
        return transactionRepository.findAll(pageable)
                .map(transactionMapper::toDTO);
    }

    public Page<TransactionDTO> getTransactionsByFromAccountId(Long fromAccountId, Pageable pageable) {
        if (!accountRepository.existsById(fromAccountId)) {
            throw new ResourceNotFoundException("Account not found with id: " + fromAccountId);
        }
        return transactionRepository.findByFromAccountId(fromAccountId, pageable)
                .map(transactionMapper::toDTO);
    }

    public Page<TransactionDTO> getTransactionsByToAccountId(Long toAccountId, Pageable pageable) {
        if (!accountRepository.existsById(toAccountId)) {
            throw new ResourceNotFoundException("Account not found with id: " + toAccountId);
        }
        return transactionRepository.findByToAccountId(toAccountId, pageable)
                .map(transactionMapper::toDTO);
    }

    //Helper: find an account or throw
    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account not found with id:" + accountId));
    }
}

