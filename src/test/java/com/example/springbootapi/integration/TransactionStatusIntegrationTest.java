package com.example.springbootapi.integration;

import com.example.springbootapi.dto.CreateAccountRequest;
import com.example.springbootapi.dto.CreateTransactionRequest;
import com.example.springbootapi.dto.TransactionDTO;
import com.example.springbootapi.dto.UserRequestDTO;
import com.example.springbootapi.dto.UserResponseDTO;
import com.example.springbootapi.enums.Role;
import com.example.springbootapi.enums.TransactionStatus;
import com.example.springbootapi.enums.TransactionType;
import com.example.springbootapi.repository.AccountRepository;
import com.example.springbootapi.repository.TransactionRepository;
import com.example.springbootapi.repository.UserRepository;
import com.example.springbootapi.service.AccountService;
import com.example.springbootapi.service.TransactionService;
import com.example.springbootapi.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
public class TransactionStatusIntegrationTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private AccountService accountService;
    @Autowired private TransactionService transactionService;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;

    private Long accountAId;
    private Long accountBId;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("setup", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );
        try {
            transactionRepository.deleteAll();
            accountRepository.deleteAll();
            userRepository.deleteAll();

            UserRequestDTO reqA = new UserRequestDTO();
            reqA.setUsername("userA");
            reqA.setPassword("passwordA");
            reqA.setEmail("userA@example.com");
            UserResponseDTO userA = userService.createUser(reqA);

            UserRequestDTO reqB = new UserRequestDTO();
            reqB.setUsername("userB");
            reqB.setPassword("passwordB");
            reqB.setEmail("userB@example.com");
            UserResponseDTO userB = userService.createUser(reqB);

            UserRequestDTO adminReq = new UserRequestDTO();
            adminReq.setUsername("admin");
            adminReq.setPassword("adminPass");
            adminReq.setEmail("admin@example.com");
            userService.createUser(adminReq);
            userRepository.findByUsername("admin").ifPresent(u -> {
                u.setRole(Role.ADMIN);
                userRepository.save(u);
            });

            accountAId = accountService.createAccount(new CreateAccountRequest(userA.getId())).getId();
            accountBId = accountService.createAccount(new CreateAccountRequest(userB.getId())).getId();

            // Fund account A
            CreateTransactionRequest deposit = new CreateTransactionRequest();
            deposit.setToAccountId(accountAId);
            deposit.setAmount(new BigDecimal("500.00"));
            deposit.setType(TransactionType.DEPOSIT);
            transactionService.createTransaction(deposit);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ============================================
    // TEST 1: Successful deposit → COMPLETED
    // ============================================
    @Test
    void createDeposit_ShouldBeCompleted() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("userA", null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );
        try {
            CreateTransactionRequest request = new CreateTransactionRequest();
            request.setToAccountId(accountAId);
            request.setAmount(new BigDecimal("100.00"));
            request.setType(TransactionType.DEPOSIT);

            TransactionDTO result = transactionService.createTransaction(request);

            assertEquals(TransactionStatus.COMPLETED, result.getStatus());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ============================================
    // TEST 2: Transfer with insufficient funds → FAILED, record persisted, balances unchanged
    // ============================================
    @Test
    void createTransfer_InsufficientFunds_ShouldBeFailed() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("userA", null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );
        try {
            CreateTransactionRequest request = new CreateTransactionRequest();
            request.setFromAccountId(accountAId);
            request.setToAccountId(accountBId);
            request.setAmount(new BigDecimal("9999.00")); // Far exceeds balance
            request.setType(TransactionType.TRANSFER);

            TransactionDTO result = transactionService.createTransaction(request);

            assertEquals(TransactionStatus.FAILED, result.getStatus());
            assertNotNull(result.getId()); // Record was persisted

            // Verify balances unchanged
            BigDecimal balanceA = accountService.getAccountBalance(accountAId);
            BigDecimal balanceB = accountService.getAccountBalance(accountBId);
            assertEquals(new BigDecimal("500.00"), balanceA);
            assertEquals(new BigDecimal("0.00"), balanceB);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ============================================
    // TEST 3: Retry a FAILED transaction after funding → COMPLETED, balance updated
    // ============================================
    @Test
    void retryTransaction_AfterFunding_ShouldComplete() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("userA", null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );
        try {
            // Create a FAILED transfer (insufficient funds)
            CreateTransactionRequest request = new CreateTransactionRequest();
            request.setFromAccountId(accountAId);
            request.setToAccountId(accountBId);
            request.setAmount(new BigDecimal("9999.00"));
            request.setType(TransactionType.TRANSFER);
            TransactionDTO failed = transactionService.createTransaction(request);
            assertEquals(TransactionStatus.FAILED, failed.getStatus());

            // Fund account A enough
            SecurityContextHolder.clearContext();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("setup", null,
                            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
            );
            CreateTransactionRequest fundRequest = new CreateTransactionRequest();
            fundRequest.setToAccountId(accountAId);
            fundRequest.setAmount(new BigDecimal("10000.00"));
            fundRequest.setType(TransactionType.DEPOSIT);
            transactionService.createTransaction(fundRequest);

            // Retry as userA
            SecurityContextHolder.clearContext();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("userA", null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER")))
            );
            TransactionDTO retried = transactionService.retryTransaction(failed.getId());

            assertEquals(TransactionStatus.COMPLETED, retried.getStatus());

            // Verify balance transferred
            BigDecimal balanceB = accountService.getAccountBalance(accountBId);
            assertEquals(new BigDecimal("9999.00"), balanceB);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ============================================
    // TEST 4: Retry a COMPLETED transaction → 409
    // ============================================
    @Test
    void retryTransaction_Completed_Returns409() throws Exception {
        // Create a successful deposit first
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("userA", null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );
        TransactionDTO completed;
        try {
            CreateTransactionRequest request = new CreateTransactionRequest();
            request.setToAccountId(accountAId);
            request.setAmount(new BigDecimal("50.00"));
            request.setType(TransactionType.DEPOSIT);
            completed = transactionService.createTransaction(request);
            assertEquals(TransactionStatus.COMPLETED, completed.getStatus());
        } finally {
            SecurityContextHolder.clearContext();
        }

        // Retry via MockMvc → expect 409
        mockMvc.perform(post("/api/transactions/{id}/retry", completed.getId())
                        .with(user("userA").roles("USER")))
                .andExpect(status().isConflict());
    }

    // ============================================
    // TEST 5: Admin sees all FAILED transactions
    // ============================================
    @Test
    void getTransactionsByStatus_Admin_ReturnsAll() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("setup", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );
        try {
            // Create failed transaction for userA
            CreateTransactionRequest req = new CreateTransactionRequest();
            req.setFromAccountId(accountAId);
            req.setToAccountId(accountBId);
            req.setAmount(new BigDecimal("9999.00"));
            req.setType(TransactionType.TRANSFER);
            transactionService.createTransaction(req);

            // Admin query
            Page<TransactionDTO> page = transactionService.getTransactionsByStatus(
                    TransactionStatus.FAILED, PageRequest.of(0, 10));

            assertTrue(page.getTotalElements() >= 1);
            page.getContent().forEach(t -> assertEquals(TransactionStatus.FAILED, t.getStatus()));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // ============================================
    // TEST 6: User only sees their own FAILED transactions
    // ============================================
    @Test
    void getTransactionsByStatus_User_ReturnsOnlyOwned() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("setup", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );
        try {
            // Create a failed transfer from accountA
            CreateTransactionRequest req = new CreateTransactionRequest();
            req.setFromAccountId(accountAId);
            req.setToAccountId(accountBId);
            req.setAmount(new BigDecimal("9999.00"));
            req.setType(TransactionType.TRANSFER);
            transactionService.createTransaction(req);
        } finally {
            SecurityContextHolder.clearContext();
        }

        // userB queries FAILED — should see 0 (they don't own any)
        mockMvc.perform(get("/api/transactions/status/FAILED")
                        .with(user("userB").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        // userA queries FAILED — should see their own
        mockMvc.perform(get("/api/transactions/status/FAILED")
                        .with(user("userA").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ============================================
    // TEST 7: User cannot retry another user's transaction
    // ============================================
    @Test
    void retryTransaction_OtherUserTransaction_Returns403() throws Exception {
        // Create a failed transaction owned by userA
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("userA", null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")))
        );
        TransactionDTO failedTx;
        try {
            CreateTransactionRequest req = new CreateTransactionRequest();
            req.setFromAccountId(accountAId);
            req.setToAccountId(accountBId);
            req.setAmount(new BigDecimal("9999.00"));
            req.setType(TransactionType.TRANSFER);
            failedTx = transactionService.createTransaction(req);
            assertEquals(TransactionStatus.FAILED, failedTx.getStatus());
        } finally {
            SecurityContextHolder.clearContext();
        }

        // userB tries to retry it — expect 403
        mockMvc.perform(post("/api/transactions/{id}/retry", failedTx.getId())
                        .with(user("userB").roles("USER")))
                .andExpect(status().isForbidden());
    }
}
