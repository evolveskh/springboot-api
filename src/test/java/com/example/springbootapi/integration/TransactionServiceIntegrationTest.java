package com.example.springbootapi.integration;

import com.example.springbootapi.dto.CreateAccountRequest;
import com.example.springbootapi.dto.CreateTransactionRequest;
import com.example.springbootapi.dto.TransactionDTO;
import com.example.springbootapi.dto.UserRequestDTO;
import com.example.springbootapi.dto.UserResponseDTO;
import com.example.springbootapi.enums.TransactionType;
import com.example.springbootapi.exception.InsufficientFundsException;
import com.example.springbootapi.repository.AccountRepository;
import com.example.springbootapi.repository.TransactionRepository;
import com.example.springbootapi.repository.UserRepository;
import com.example.springbootapi.service.AccountService;
import com.example.springbootapi.service.TransactionService;
import com.example.springbootapi.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class TransactionServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private UserService userService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private Long userId;
    private Long account1Id;
    private Long account2Id;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        // Setup common test data
        UserRequestDTO userRequest = new UserRequestDTO();
        userRequest.setUsername("testuser");
        userRequest.setPassword("password");
        userRequest.setEmail("test@example.com");
        UserResponseDTO user = userService.createUser(userRequest);
        userId = user.getId();

        account1Id = accountService.createAccount(new CreateAccountRequest(userId)).getId();
        account2Id = accountService.createAccount(new CreateAccountRequest(userId)).getId();
    }

    @Test
    void deposit_ShouldIncreaseBalanceInDatabase() {
        // ARRANGE
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setToAccountId(account1Id);
        request.setAmount(new BigDecimal("1000.00"));
        request.setType(TransactionType.DEPOSIT);

        // ACT
        transactionService.createTransaction(request);

        // ASSERT
        BigDecimal balance = accountService.getAccountBalance(account1Id);
        assertEquals(0, new BigDecimal("1000.00").compareTo(balance));
    }

    @Test
    void transfer_ShouldBeAtomicInDatabase() {
        // 1. Initial Deposit to Account 1
        CreateTransactionRequest deposit = new CreateTransactionRequest();
        deposit.setToAccountId(account1Id);
        deposit.setAmount(new BigDecimal("1000.00"));
        deposit.setType(TransactionType.DEPOSIT);
        transactionService.createTransaction(deposit);

        // 2. Transfer from Account 1 to Account 2
        CreateTransactionRequest transfer = new CreateTransactionRequest();
        transfer.setFromAccountId(account1Id);
        transfer.setToAccountId(account2Id);
        transfer.setAmount(new BigDecimal("400.00"));
        transfer.setType(TransactionType.TRANSFER);

        // ACT
        TransactionDTO result = transactionService.createTransaction(transfer);

        // ASSERT
        assertNotNull(result.getId());
        assertEquals(0, new BigDecimal("600.00").compareTo(accountService.getAccountBalance(account1Id)));
        assertEquals(0, new BigDecimal("400.00").compareTo(accountService.getAccountBalance(account2Id)));
    }

    @Test
    void transfer_InsufficientFunds_ShouldRollback() {
        // 1. Initial Deposit to Account 1
        CreateTransactionRequest deposit = new CreateTransactionRequest();
        deposit.setToAccountId(account1Id);
        deposit.setAmount(new BigDecimal("100.00"));
        deposit.setType(TransactionType.DEPOSIT);
        transactionService.createTransaction(deposit);

        // 2. Attempt Transfer more than balance
        CreateTransactionRequest transfer = new CreateTransactionRequest();
        transfer.setFromAccountId(account1Id);
        transfer.setToAccountId(account2Id);
        transfer.setAmount(new BigDecimal("500.00"));
        transfer.setType(TransactionType.TRANSFER);

        // ACT & ASSERT
        assertThrows(InsufficientFundsException.class, () -> transactionService.createTransaction(transfer));

        // Verify balances remain unchanged (Rollback check)
        assertEquals(0, new BigDecimal("100.00").compareTo(accountService.getAccountBalance(account1Id)));
        assertEquals(0, new BigDecimal("0.00").compareTo(accountService.getAccountBalance(account2Id)));
        
        // Verify no transaction record was saved
        assertEquals(1, transactionRepository.findAll().size()); // Only the deposit
    }
}
