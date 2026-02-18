package com.example.springbootapi.service;

import com.example.springbootapi.dto.CreateTransactionRequest;
import com.example.springbootapi.dto.TransactionDTO;
import com.example.springbootapi.entity.Account;
import com.example.springbootapi.entity.Transaction;
import com.example.springbootapi.exception.ResourceNotFoundException;
import com.example.springbootapi.mapper.TransactionMapper;
import com.example.springbootapi.repository.AccountRepository;
import com.example.springbootapi.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.repository.Repository;
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
                fromAccount = findAccount(request.getFromAccountId());
                toAccount = findAccount(request.getToAccountId());
                break;
            case DEPOSIT:
                if (request.getToAccountId() == null) {
                    throw new IllegalArgumentException("Deposit transaction requires to account");
                }
                toAccount = findAccount(request.getToAccountId());
                break;
            case WITHDRAWAL:
                if (request.getFromAccountId() == null) {
                    throw new IllegalArgumentException("Withdrawal transaction requires from account");
                }
                fromAccount = findAccount(request.getFromAccountId());
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

    public List<TransactionDTO> getAllTransactions() {
        return transactionRepository.findAll()
                .stream()
                .map(transactionMapper::toDTO)
                .collect(Collectors.toList());
    }

    public List<TransactionDTO> getTransactionsByFromAccountId(Long fromAccountId) {
        if (!accountRepository.existsById(fromAccountId)) {
            throw new ResourceNotFoundException("Account not found with id: " + fromAccountId);
        }
        return transactionRepository.findByFromAccountId(fromAccountId)
                .stream()
                .map(transactionMapper::toDTO)
                .collect(Collectors.toList());
    }

    public List<TransactionDTO> getTransactionsByToAccountId(Long toAccountId) {
        if (!accountRepository.existsById(toAccountId)) {
            throw new ResourceNotFoundException("Account not found with id: " + toAccountId);
        }
        return transactionRepository.findByToAccountId(toAccountId)
                .stream()
                .map(transactionMapper::toDTO)
                .collect(Collectors.toList());
    }

    //Helper: find an account or throw
    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account not found with id:" + accountId));
    }
}

