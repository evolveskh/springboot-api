package com.example.springbootapi.service;

import com.example.springbootapi.dto.CreateTransactionRequest;
import com.example.springbootapi.dto.TransactionDTO;
import com.example.springbootapi.entity.Account;
import com.example.springbootapi.entity.Transaction;
import com.example.springbootapi.enums.TransactionType;
import com.example.springbootapi.mapper.TransactionMapper;
import com.example.springbootapi.repository.AccountRepository;
import com.example.springbootapi.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void createTransaction_SetsTypeOnEntity() {
        // Arrange
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setFromAccountId(1L);
        request.setToAccountId(2L);
        request.setAmount(new BigDecimal("100.00"));
        request.setType(TransactionType.TRANSFER);

        Account fromAccount = new Account();
        fromAccount.setId(1L);
        Account toAccount = new Account();
        toAccount.setId(2L);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(toAccount));

        // When saved, we want to capture the entity to verify it has the type set
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            return t; // return the same object
        });

        when(transactionMapper.toDTO(any(Transaction.class))).thenReturn(new TransactionDTO());

        // Act
        transactionService.createTransaction(request);

        // Assert
        verify(transactionRepository).save(argThat(transaction -> {
            assertEquals(TransactionType.TRANSFER, transaction.getType(), "Transaction type should be set on the entity");
            return true;
        }));
    }
}
