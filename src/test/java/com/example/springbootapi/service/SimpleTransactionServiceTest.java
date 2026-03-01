package com.example.springbootapi.service;

import com.example.springbootapi.dto.CreateTransactionRequest;
import com.example.springbootapi.dto.TransactionDTO;
import com.example.springbootapi.entity.Account;
import com.example.springbootapi.entity.Transaction;
import com.example.springbootapi.enums.TransactionStatus;
import com.example.springbootapi.enums.TransactionType;
import com.example.springbootapi.exception.ResourceNotFoundException;
import com.example.springbootapi.mapper.TransactionMapper;
import com.example.springbootapi.repository.AccountRepository;
import com.example.springbootapi.repository.TransactionRepository;
import com.example.springbootapi.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

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

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TransactionService transactionService;

    @BeforeEach
    void setUpSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("testuser", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ============================================
    // TEST 1: Happy Path - Everything Works
    // ============================================
    @Test
    void createTransaction_WithValidTransfer_Success() {
        // ARRANGE
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

        Transaction pendingTransaction = new Transaction();
        pendingTransaction.setId(1L);
        pendingTransaction.setStatus(TransactionStatus.COMPLETED);

        TransactionDTO expectedDTO = new TransactionDTO();
        expectedDTO.setId(1L);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(toAccount));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(pendingTransaction);
        when(transactionMapper.toDTO(pendingTransaction)).thenReturn(expectedDTO);

        // ACT
        TransactionDTO result = transactionService.createTransaction(request);

        // ASSERT
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(new BigDecimal("900.00"), fromAccount.getBalance());
        assertEquals(new BigDecimal("600.00"), toAccount.getBalance());

        verify(accountRepository).findById(1L);
        verify(accountRepository).findById(2L);
        // Save called twice: once for PENDING, once for COMPLETED
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    // ============================================
    // TEST 2: Sad Path - Account Not Found
    // ============================================
    @Test
    void createTransaction_AccountNotFound_ThrowsException() {
        // ARRANGE
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setFromAccountId(999L);
        request.setToAccountId(2L);
        request.setAmount(new BigDecimal("100.00"));
        request.setType(TransactionType.TRANSFER);

        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        // ACT + ASSERT — upfront validation throws before any save
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> transactionService.createTransaction(request)
        );

        assertTrue(exception.getMessage().contains("Account not found with id:999"));
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
        request.setToAccountId(null);
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
        request.setToAccountId(1L);
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
        request.setFromAccountId(null);
        request.setToAccountId(1L);
        request.setAmount(new BigDecimal("500.00"));
        request.setType(TransactionType.DEPOSIT);

        Account toAccount = Account.builder()
                .id(1L)
                .balance(new BigDecimal("1000.00"))
                .build();

        Transaction savedTransaction = new Transaction();
        savedTransaction.setStatus(TransactionStatus.COMPLETED);
        TransactionDTO expectedDTO = new TransactionDTO();

        when(accountRepository.findById(1L)).thenReturn(Optional.of(toAccount));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        when(transactionMapper.toDTO(savedTransaction)).thenReturn(expectedDTO);

        // ACT
        TransactionDTO result = transactionService.createTransaction(request);

        // ASSERT
        assertNotNull(result);
        assertEquals(new BigDecimal("1500.00"), toAccount.getBalance());

        verify(accountRepository, times(1)).findById(1L);
        verify(accountRepository).save(toAccount);
        verify(transactionRepository, times(2)).save(any(Transaction.class));
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
        savedTransaction.setStatus(TransactionStatus.COMPLETED);
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
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    // ============================================
    // TEST 7: Insufficient Funds - Returns FAILED status (no exception thrown)
    // ============================================
    @Test
    void createTransaction_InsufficientFunds_ReturnsFailed() {
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

        Transaction savedTransaction = new Transaction();
        savedTransaction.setStatus(TransactionStatus.FAILED);
        TransactionDTO expectedDTO = new TransactionDTO();
        expectedDTO.setStatus(TransactionStatus.FAILED);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(toAccount));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        when(transactionMapper.toDTO(savedTransaction)).thenReturn(expectedDTO);

        // ACT — no exception; returns DTO with FAILED status
        TransactionDTO result = transactionService.createTransaction(request);

        // ASSERT
        assertNotNull(result);
        assertEquals(TransactionStatus.FAILED, result.getStatus());

        // Save called twice: once for PENDING, once for FAILED
        verify(transactionRepository, times(2)).save(any(Transaction.class));

        // Balance unchanged
        assertEquals(new BigDecimal("1000.00"), fromAccount.getBalance());
        assertEquals(new BigDecimal("500.00"), toAccount.getBalance());
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
