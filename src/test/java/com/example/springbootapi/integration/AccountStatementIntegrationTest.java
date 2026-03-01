package com.example.springbootapi.integration;

import com.example.springbootapi.dto.CreateAccountRequest;
import com.example.springbootapi.dto.CreateTransactionRequest;
import com.example.springbootapi.dto.TransactionDTO;
import com.example.springbootapi.dto.UserRequestDTO;
import com.example.springbootapi.dto.UserResponseDTO;
import com.example.springbootapi.enums.Role;
import com.example.springbootapi.enums.TransactionType;
import com.example.springbootapi.repository.AccountRepository;
import com.example.springbootapi.repository.TransactionRepository;
import com.example.springbootapi.repository.UserRepository;
import com.example.springbootapi.service.AccountService;
import com.example.springbootapi.service.TransactionService;
import com.example.springbootapi.service.UserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
public class AccountStatementIntegrationTest extends BaseIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private AccountService accountService;
    @Autowired private TransactionService transactionService;
    @Autowired private UserRepository userRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private EntityManager em;

    private Long accountAId;

    // Test date range: 2026-01-15 to 2026-01-31
    // Pre-range:  deposit 500  @ 2026-01-01T12:00
    // In-range:   deposit 200  @ 2026-01-20T10:00
    // In-range:   withdrawal 50 @ 2026-01-25T10:00
    // Post-range: deposit 100  @ 2026-02-01T10:00
    // Current balance: 750
    // openingBalance = 750 - (200 - 50 + 100) = 500
    // closingBalance = 500 + (200 - 50) = 650

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
            userService.createUser(reqB);

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

            // Pre-range deposit: 500 @ 2026-01-01
            TransactionDTO preDeposit = createDeposit(accountAId, "500.00");
            setCreatedAt(preDeposit.getId(), LocalDateTime.of(2026, 1, 1, 12, 0));

            // In-range deposit: 200 @ 2026-01-20
            TransactionDTO inDeposit = createDeposit(accountAId, "200.00");
            setCreatedAt(inDeposit.getId(), LocalDateTime.of(2026, 1, 20, 10, 0));

            // In-range withdrawal: 50 @ 2026-01-25
            TransactionDTO inWithdrawal = createWithdrawal(accountAId, "50.00");
            setCreatedAt(inWithdrawal.getId(), LocalDateTime.of(2026, 1, 25, 10, 0));

            // Post-range deposit: 100 @ 2026-02-01
            TransactionDTO postDeposit = createDeposit(accountAId, "100.00");
            setCreatedAt(postDeposit.getId(), LocalDateTime.of(2026, 2, 1, 10, 0));

        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownerCanGetOwnStatement() throws Exception {
        mockMvc.perform(get("/api/accounts/{id}/statement", accountAId)
                        .param("from", "2026-01-15")
                        .param("to", "2026-01-31")
                        .with(user("userA").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingBalance").value(500.00))
                .andExpect(jsonPath("$.closingBalance").value(650.00))
                .andExpect(jsonPath("$.transactions.totalElements").value(2));
    }

    @Test
    void otherUserCannotGetStatement() throws Exception {
        mockMvc.perform(get("/api/accounts/{id}/statement", accountAId)
                        .param("from", "2026-01-15")
                        .param("to", "2026-01-31")
                        .with(user("userB").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanGetAnyStatement() throws Exception {
        mockMvc.perform(get("/api/accounts/{id}/statement", accountAId)
                        .param("from", "2026-01-15")
                        .param("to", "2026-01-31")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingBalance").value(500.00))
                .andExpect(jsonPath("$.closingBalance").value(650.00))
                .andExpect(jsonPath("$.transactions.totalElements").value(2));
    }

    @Test
    void accountNotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/accounts/{id}/statement", 99999L)
                        .param("from", "2026-01-15")
                        .param("to", "2026-01-31")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void fromAfterTo_Returns400() throws Exception {
        mockMvc.perform(get("/api/accounts/{id}/statement", accountAId)
                        .param("from", "2026-02-01")
                        .param("to", "2026-01-01")
                        .with(user("userA").roles("USER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyDateRange_ReturnsZeroTransactions() throws Exception {
        // No transactions exist before 2025 — both balances should equal 0 (same value)
        mockMvc.perform(get("/api/accounts/{id}/statement", accountAId)
                        .param("from", "2025-01-01")
                        .param("to", "2025-01-31")
                        .with(user("userA").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions.totalElements").value(0))
                .andExpect(jsonPath("$.openingBalance").value(0))
                .andExpect(jsonPath("$.closingBalance").value(0));
    }

    // --- helpers ---

    private TransactionDTO createDeposit(Long toAccountId, String amount) {
        CreateTransactionRequest req = new CreateTransactionRequest();
        req.setToAccountId(toAccountId);
        req.setAmount(new BigDecimal(amount));
        req.setType(TransactionType.DEPOSIT);
        return transactionService.createTransaction(req);
    }

    private TransactionDTO createWithdrawal(Long fromAccountId, String amount) {
        CreateTransactionRequest req = new CreateTransactionRequest();
        req.setFromAccountId(fromAccountId);
        req.setAmount(new BigDecimal(amount));
        req.setType(TransactionType.WITHDRAWAL);
        return transactionService.createTransaction(req);
    }

    @Transactional
    public void setCreatedAt(Long txId, LocalDateTime when) {
        em.createQuery("UPDATE Transaction t SET t.createdAt = :when WHERE t.id = :id")
                .setParameter("when", when)
                .setParameter("id", txId)
                .executeUpdate();
        em.flush();
        em.clear();
    }
}
