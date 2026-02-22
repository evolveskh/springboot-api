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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
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

        Account fromAccount = Account.builder()
                .id(1L)
                .balance(new BigDecimal("1000.00"))
                .build();

        Account toAccount = Account.builder()
                .id(2L)
                .balance(new BigDecimal("500.00"))
                .build();

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
        assertEquals(new BigDecimal("900.00"), fromAccount.getBalance());
        assertEquals(new BigDecimal("600.00"), toAccount.getBalance());

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

        Account toAccount = Account.builder()
                .id(1L)
                .balance(new BigDecimal("1000.00"))
                .build();

        Transaction savedTransaction = new Transaction();
        TransactionDTO expectedDTO = new TransactionDTO();

        when(accountRepository.findById(1L)).thenReturn(Optional.of(toAccount));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        when(transactionMapper.toDTO(savedTransaction)).thenReturn(expectedDTO);

        // ACT
        TransactionDTO result = transactionService.createTransaction(request);

        // ASSERT
        assertNotNull(result);
        assertEquals(new BigDecimal("1500.00"), toAccount.getBalance());

        // Verify only TO account was looked up (no FROM account)
        verify(accountRepository, times(1)).findById(1L);
        verify(accountRepository).save(toAccount);
    }

    // ============================================
    // TEST 6: WITHDRAWAL - Success
    // ============================================
    @Test
    void createTransaction_Withdrawal_Success() {
        // ARRANGE
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setFromAccountId(1L);
        request.setToAccountId(null);
        request.setAmount(new BigDecimal("200.00"));
        request.setType(TransactionType.WITHDRAWAL);

        Account fromAccount = Account.builder()
                .id(1L)
                .balance(new BigDecimal("1000.00"))
                .build();

        Transaction savedTransaction = new Transaction();
        TransactionDTO expectedDTO = new TransactionDTO();

        when(accountRepository.findById(1L)).thenReturn(Optional.of(fromAccount));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        when(transactionMapper.toDTO(savedTransaction)).thenReturn(expectedDTO);

        // ACT
        TransactionDTO result = transactionService.createTransaction(request);

        // ASSERT
        assertNotNull(result);
        assertEquals(new BigDecimal("800.00"), fromAccount.getBalance());

        verify(accountRepository).findById(1L);
        verify(accountRepository).save(fromAccount);
    }

    // ============================================
    // TEST 7: Sad Path - Insufficient Funds
    // ============================================
    @Test
    void createTransaction_InsufficientFunds_ThrowsException() {
        // ARRANGE
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setFromAccountId(1L);
        request.setToAccountId(2L);
        request.setAmount(new BigDecimal("5000.00")); // Too much!
        request.setType(TransactionType.TRANSFER);

        Account fromAccount = Account.builder()
                .id(1L)
                .balance(new BigDecimal("1000.00"))
                .build();

        Account toAccount = Account.builder()
                .id(2L)
                .balance(new BigDecimal("500.00"))
                .build();

        when(accountRepository.findById(1L)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(toAccount));

        // ACT + ASSERT
        assertThrows(com.example.springbootapi.exception.InsufficientFundsException.class,
                () -> transactionService.createTransaction(request)
        );

        // Verify save was NEVER called
        verify(transactionRepository, never()).save(any());
    }

    // ============================================
    // TEST 8: Pagination - Get All Transactions
    // ============================================
    @Test
    void getAllTransactions_Success() {
        // ARRANGE
        Pageable pageable = PageRequest.of(0, 10);
        Transaction transaction = new Transaction();
        Page<Transaction> page = new PageImpl<>(Collections.singletonList(transaction));
        TransactionDTO dto = new TransactionDTO();

        when(transactionRepository.findAll(pageable)).thenReturn(page);
        when(transactionMapper.toDTO(transaction)).thenReturn(dto);

        // ACT
        Page<TransactionDTO> result = transactionService.getAllTransactions(pageable);

        // ASSERT
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(transactionRepository).findAll(pageable);
    }
}