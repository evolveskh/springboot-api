package com.example.springbootapi.service;

import com.example.springbootapi.dto.CreateTransactionRequest;
import com.example.springbootapi.dto.TransactionDTO;
import com.example.springbootapi.entity.Account;
import com.example.springbootapi.entity.Transaction;
import com.example.springbootapi.enums.TransactionType;
import com.example.springbootapi.exception.ResourceNotFoundException;
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
public class SimpleTransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @InjectMocks
    private TransactionService transactionService;

    // ============================================
    // TEST 1: Happy Path - Everything Works
    // ============================================
    @Test
    void createTransaction_WithValidTransfer_Success() {
        // ARRANGE - Prepare test data
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setFromAccountId(1L);
        request.setToAccountId(2L);
        request.setAmount(new BigDecimal("100.00"));
        request.setType(TransactionType.TRANSFER);

        Account fromAccount = new Account();
        fromAccount.setId(1L);

        Account toAccount = new Account();
        toAccount.setId(2L);

        Transaction savedTransaction = new Transaction();
        savedTransaction.setId(1L);

        TransactionDTO expectedDTO = new TransactionDTO();
        expectedDTO.setId(1L);

        // Tell mocks what to return
        when(accountRepository.findById(1L)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(toAccount));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        when(transactionMapper.toDTO(savedTransaction)).thenReturn(expectedDTO);

        // ACT - Call the method
        TransactionDTO result = transactionService.createTransaction(request);

        // ASSERT - Check results
        assertNotNull(result);  // Result should not be null
        assertEquals(1L, result.getId());  // Should have ID = 1

        // Verify repository was called
        verify(accountRepository).findById(1L);  // Should look up from account
        verify(accountRepository).findById(2L);  // Should look up to account
        verify(transactionRepository).save(any(Transaction.class));  // Should save
    }

    // ============================================
    // TEST 2: Sad Path - Account Not Found
    // ============================================
    @Test
    void createTransaction_AccountNotFound_ThrowsException() {
        // ARRANGE
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setFromAccountId(999L);  // Account that doesn't exist!
        request.setToAccountId(2L);
        request.setAmount(new BigDecimal("100.00"));
        request.setType(TransactionType.TRANSFER);

        // Mock returns empty (account not found)
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        // ACT + ASSERT
        // Expect exception to be thrown
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> transactionService.createTransaction(request)
        );

        // Check exception message
        assertTrue(exception.getMessage().contains("Account not found with id:999"));

        // Verify save was NEVER called (because exception was thrown first)
        verify(transactionRepository, never()).save(any());
    }

    // ============================================
    // TEST 3: Validation - TRANSFER needs both accounts
    // ============================================
    @Test
    void createTransaction_TransferMissingToAccount_ThrowsException() {
        // ARRANGE
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setFromAccountId(1L);
        request.setToAccountId(null);  // Missing!
        request.setAmount(new BigDecimal("100.00"));
        request.setType(TransactionType.TRANSFER);

        // ACT + ASSERT
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.createTransaction(request)
        );

        assertTrue(exception.getMessage().contains("Transfer transaction requires both"));
    }

    // ============================================
    // TEST 4: Validation - Cannot transfer to same account
    // ============================================
    @Test
    void createTransaction_TransferToSameAccount_ThrowsException() {
        // ARRANGE
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setFromAccountId(1L);
        request.setToAccountId(1L);  // Same account!
        request.setAmount(new BigDecimal("100.00"));
        request.setType(TransactionType.TRANSFER);

        // ACT + ASSERT
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> transactionService.createTransaction(request)
        );

        assertTrue(exception.getMessage().contains("Cannot transfer to the same account"));
    }

    // ============================================
    // TEST 5: DEPOSIT only needs toAccount
    // ============================================
    @Test
    void createTransaction_Deposit_Success() {
        // ARRANGE
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setFromAccountId(null);  // No from account for deposit!
        request.setToAccountId(1L);
        request.setAmount(new BigDecimal("500.00"));
        request.setType(TransactionType.DEPOSIT);

        Account toAccount = new Account();
        toAccount.setId(1L);

        Transaction savedTransaction = new Transaction();
        TransactionDTO expectedDTO = new TransactionDTO();

        when(accountRepository.findById(1L)).thenReturn(Optional.of(toAccount));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        when(transactionMapper.toDTO(savedTransaction)).thenReturn(expectedDTO);

        // ACT
        TransactionDTO result = transactionService.createTransaction(request);

        // ASSERT
        assertNotNull(result);

        // Verify only TO account was looked up (no FROM account)
        verify(accountRepository, times(1)).findById(1L);
        verify(accountRepository, never()).findById(null);  // Never looked up null
    }
}