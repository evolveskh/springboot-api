package com.example.springbootapi.service;

import com.example.springbootapi.dto.AccountDTO;
import com.example.springbootapi.dto.AccountStatementDTO;
import com.example.springbootapi.dto.CreateAccountRequest;
import com.example.springbootapi.dto.TransactionDTO;
import com.example.springbootapi.entity.Account;
import com.example.springbootapi.entity.User;
import com.example.springbootapi.exception.ResourceNotFoundException;
import com.example.springbootapi.mapper.AccountMapper;
import com.example.springbootapi.mapper.TransactionMapper;
import com.example.springbootapi.repository.AccountRepository;
import com.example.springbootapi.repository.TransactionRepository;
import com.example.springbootapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int ACCOUNT_NUMBER_LENGTH = 42; // Similar to crypto wallet addresses
    private final AccountMapper accountMapper;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    /**
     * Generate a random account number similar to crypto wallet address
     * Format: 0x + 40 random alphanumeric characters
     */
    private String generateAccountNumber() {
        StringBuilder accountNumber = new StringBuilder("0x");

        for (int i = 0; i < ACCOUNT_NUMBER_LENGTH - 2; i++) {
            int randomIndex = SECURE_RANDOM.nextInt(CHARACTERS.length());
            accountNumber.append(CHARACTERS.charAt(randomIndex));
        }

        // Ensure uniqueness
        while (accountRepository.existsByAccountNumber(accountNumber.toString())) {
            accountNumber = new StringBuilder("0x");
            for (int i = 0; i < ACCOUNT_NUMBER_LENGTH - 2; i++) {
                int randomIndex = SECURE_RANDOM.nextInt(CHARACTERS.length());
                accountNumber.append(CHARACTERS.charAt(randomIndex));
            }
        }

        return accountNumber.toString();
    }

    @Transactional
    public AccountDTO createAccount(CreateAccountRequest request) {
        // Check if user exists
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.getUserId()));

        // Generate unique account number
        String accountNumber = generateAccountNumber();

        // Create account
        Account account = Account.builder()
                .accountNumber(accountNumber)
                .user(user)
                .build();

        Account savedAccount = accountRepository.save(account);

        return accountMapper.toDTO(savedAccount);
    }

    public AccountDTO getAccountById(Long id) {
        Account account = accountRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + id));
        if (!isAdmin() && !account.getUser().getUsername().equals(currentUsername())) {
            throw new AccessDeniedException("Access denied");
        }
        return accountMapper.toDTO(account);
    }

    public BigDecimal getAccountBalance(Long id) {
        Account account = accountRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + id));
        if (!isAdmin() && !account.getUser().getUsername().equals(currentUsername())) {
            throw new AccessDeniedException("Access denied");
        }
        return account.getBalance();
    }

    public AccountDTO getAccountByAccountNumber(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(() -> new ResourceNotFoundException("Account not found with account number: " + accountNumber));
        if (!isAdmin() && !account.getUser().getUsername().equals(currentUsername())) {
            throw new AccessDeniedException("Access denied");
        }
        return accountMapper.toDTO(account);
    }

    public List<AccountDTO> getAccountsByUserId(Long userId) {
        // Check if user exists
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with id: " + userId);
        }

        if (!isAdmin()) {
            User currentUser = userRepository.findByUsername(currentUsername())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            if (!currentUser.getId().equals(userId)) {
                throw new AccessDeniedException("Access denied");
            }
        }

        List<Account> accounts = accountRepository.findByUserId(userId);
        return accounts.stream()
                .map(accountMapper::toDTO)
                .collect(Collectors.toList());
    }

    public List<AccountDTO> getAllAccounts() {
        if (!isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        List<Account> accounts = accountRepository.findAll();
        return accounts.stream()
                .map(accountMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public String deleteAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + id));

        if (!isAdmin() && !account.getUser().getUsername().equals(currentUsername())) {
            throw new AccessDeniedException("Access denied");
        }

        String accountNumber = account.getAccountNumber();
        accountRepository.delete(account);

        return "Account with ID " + id + " and account number " + accountNumber + " has been successfully deleted";
    }

    public AccountStatementDTO getAccountStatement(Long accountId, LocalDate from, LocalDate to, Pageable pageable) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' date must not be after 'to' date");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId));
        if (!isAdmin() && !account.getUser().getUsername().equals(currentUsername())) {
            throw new AccessDeniedException("Access denied");
        }

        LocalDateTime fromInstant = from.atStartOfDay();
        LocalDateTime toExclusive = to.plusDays(1).atStartOfDay();

        Page<TransactionDTO> txPage = transactionRepository
                .findStatementTransactions(accountId, fromInstant, toExclusive, pageable)
                .map(transactionMapper::toDTO);

        BigDecimal netFromFrom = transactionRepository.sumNetEffectFrom(accountId, fromInstant);
        BigDecimal openingBalance = account.getBalance().subtract(netFromFrom);

        BigDecimal netInRange = transactionRepository.sumNetEffectInRange(accountId, fromInstant, toExclusive);
        BigDecimal closingBalance = openingBalance.add(netInRange);

        return AccountStatementDTO.builder()
                .accountId(account.getId())
                .accountNumber(account.getAccountNumber())
                .from(from)
                .to(to)
                .openingBalance(openingBalance)
                .closingBalance(closingBalance)
                .transactions(txPage)
                .build();
    }
}

