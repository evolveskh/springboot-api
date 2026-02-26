package com.example.springbootapi.integration;

import com.example.springbootapi.dto.CreateAccountRequest;
import com.example.springbootapi.dto.CreateTransactionRequest;
import com.example.springbootapi.dto.UserRequestDTO;
import com.example.springbootapi.dto.UserResponseDTO;
import com.example.springbootapi.enums.TransactionType;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ConcurrentTransactionIntegrationTest extends BaseIntegrationTest {

    @Autowired private TransactionService transactionService;
    @Autowired private UserService userService;
    @Autowired private AccountService accountService;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;

    private Long accountId;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        UserRequestDTO userRequest = new UserRequestDTO();
        userRequest.setUsername("concurrentuser");
        userRequest.setPassword("password");
        userRequest.setEmail("concurrent@example.com");
        UserResponseDTO user = userService.createUser(userRequest);

        accountId = accountService.createAccount(new CreateAccountRequest(user.getId())).getId();

        // Fund the account with exactly enough for one withdrawal
        CreateTransactionRequest deposit = new CreateTransactionRequest();
        deposit.setToAccountId(accountId);
        deposit.setAmount(new BigDecimal("100.00"));
        deposit.setType(TransactionType.DEPOSIT);
        transactionService.createTransaction(deposit);
    }

    @Test
    void concurrentWithdrawals_OnlyOneSucceeds_BalanceNeverNegative() throws InterruptedException {
        // Two threads simultaneously withdraw 100 from an account that only has 100.
        // Exactly one must succeed; the other must be rejected (either by the
        // insufficient-funds check after re-reading, or by optimistic locking).
        int threads = 2;
        BigDecimal withdrawAmount = new BigDecimal("100.00");

        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                try {
                    startGate.await(); // All threads start at the same moment
                    CreateTransactionRequest req = new CreateTransactionRequest();
                    req.setFromAccountId(accountId);
                    req.setAmount(withdrawAmount);
                    req.setType(TransactionType.WITHDRAWAL);
                    transactionService.createTransaction(req);
                    successes.incrementAndGet();
                } catch (RuntimeException e) {
                    failures.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        startGate.countDown(); // Release all threads simultaneously
        for (Future<?> f : futures) {
            try { f.get(); } catch (Exception ignored) {}
        }
        executor.shutdown();

        BigDecimal finalBalance = accountService.getAccountBalance(accountId);

        // Balance must never go negative
        assertTrue(finalBalance.compareTo(BigDecimal.ZERO) >= 0,
                "Balance went negative: " + finalBalance);

        // Exactly one withdrawal should have succeeded
        assertEquals(1, successes.get(),
                "Expected exactly 1 successful withdrawal, got: " + successes.get());
        assertEquals(1, failures.get(),
                "Expected exactly 1 failed withdrawal, got: " + failures.get());

        // Final balance must be 0 (the one success drained it)
        assertEquals(0, BigDecimal.ZERO.compareTo(finalBalance),
                "Expected balance 0.00 after one successful withdrawal, got: " + finalBalance);
    }
}
