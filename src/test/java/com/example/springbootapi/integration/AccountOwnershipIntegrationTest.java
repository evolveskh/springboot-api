package com.example.springbootapi.integration;

import com.example.springbootapi.dto.CreateAccountRequest;
import com.example.springbootapi.dto.CreateTransactionRequest;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
public class AccountOwnershipIntegrationTest extends BaseIntegrationTest {

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
        // Use ADMIN context for all service-layer setup calls
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("setup", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        );

        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        // Create user A (regular user)
        UserRequestDTO reqA = new UserRequestDTO();
        reqA.setUsername("userA");
        reqA.setPassword("passwordA");
        reqA.setEmail("userA@example.com");
        UserResponseDTO userA = userService.createUser(reqA);

        // Create user B (regular user)
        UserRequestDTO reqB = new UserRequestDTO();
        reqB.setUsername("userB");
        reqB.setPassword("passwordB");
        reqB.setEmail("userB@example.com");
        UserResponseDTO userB = userService.createUser(reqB);

        // Create admin user
        UserRequestDTO adminReq = new UserRequestDTO();
        adminReq.setUsername("admin");
        adminReq.setPassword("adminPass");
        adminReq.setEmail("admin@example.com");
        userService.createUser(adminReq);
        userRepository.findByUsername("admin").ifPresent(u -> {
            u.setRole(Role.ADMIN);
            userRepository.save(u);
        });

        // Create one account per user
        accountAId = accountService.createAccount(new CreateAccountRequest(userA.getId())).getId();
        accountBId = accountService.createAccount(new CreateAccountRequest(userB.getId())).getId();

        // Deposit into account A so transactions exist
        CreateTransactionRequest deposit = new CreateTransactionRequest();
        deposit.setToAccountId(accountAId);
        deposit.setAmount(new BigDecimal("500.00"));
        deposit.setType(TransactionType.DEPOSIT);
        transactionService.createTransaction(deposit);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // --- GET /api/accounts/{id} ---

    @Test
    void userB_cannotGetUserA_accountById() throws Exception {
        mockMvc.perform(get("/api/accounts/{id}", accountAId)
                        .with(user("userB").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void userA_canGetOwnAccountById() throws Exception {
        mockMvc.perform(get("/api/accounts/{id}", accountAId)
                        .with(user("userA").roles("USER")))
                .andExpect(status().isOk());
    }

    @Test
    void admin_canGetAnyAccountById() throws Exception {
        mockMvc.perform(get("/api/accounts/{id}", accountAId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    // --- GET /api/accounts/{id}/balance ---

    @Test
    void userB_cannotGetUserA_accountBalance() throws Exception {
        mockMvc.perform(get("/api/accounts/{id}/balance", accountAId)
                        .with(user("userB").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void userA_canGetOwnAccountBalance() throws Exception {
        mockMvc.perform(get("/api/accounts/{id}/balance", accountAId)
                        .with(user("userA").roles("USER")))
                .andExpect(status().isOk());
    }

    // --- DELETE /api/accounts/{id} ---

    @Test
    void userB_cannotDeleteUserA_account() throws Exception {
        mockMvc.perform(delete("/api/accounts/{id}", accountAId)
                        .with(user("userB").roles("USER")))
                .andExpect(status().isForbidden());
    }

    // --- GET /api/accounts ---

    @Test
    void userB_cannotGetAllAccounts() throws Exception {
        mockMvc.perform(get("/api/accounts")
                        .with(user("userB").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_canGetAllAccounts() throws Exception {
        mockMvc.perform(get("/api/accounts")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    // --- GET /api/transactions ---

    @Test
    void userB_cannotGetAllTransactions() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .with(user("userB").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_canGetAllTransactions() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    // --- GET /api/transactions/from/{fromAccountId} ---

    @Test
    void userB_cannotGetTransactionsFromUserA_account() throws Exception {
        mockMvc.perform(get("/api/transactions/from/{id}", accountAId)
                        .with(user("userB").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void userA_canGetOwnTransactions() throws Exception {
        mockMvc.perform(get("/api/transactions/from/{id}", accountAId)
                        .with(user("userA").roles("USER")))
                .andExpect(status().isOk());
    }
}
