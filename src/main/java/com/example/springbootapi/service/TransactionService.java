package com.example.springbootapi.service;

import com.example.springbootapi.dto.CreateTransactionRequest;
import com.example.springbootapi.dto.TransactionDTO;
import com.example.springbootapi.entity.Account;
import com.example.springbootapi.entity.Transaction;
import com.example.springbootapi.enums.TransactionStatus;
import com.example.springbootapi.exception.InsufficientFundsException;
import com.example.springbootapi.exception.ResourceNotFoundException;
import com.example.springbootapi.mapper.TransactionMapper;
import com.example.springbootapi.repository.AccountRepository;
import com.example.springbootapi.repository.TransactionRepository;
import com.example.springbootapi.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final TransactionMapper transactionMapper;
    private final UserRepository userRepository;

    private boolean isAdmin() {
        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @Transactional
    @CacheEvict(value = "balances", allEntries = true)
    public TransactionDTO createTransaction(CreateTransactionRequest request) {

        // Upfront validation — these throw before any save (no record persisted)
        Account fromAccount = null;
        Account toAccount = null;

        switch (request.getType()) {
            case TRANSFER:
                if (request.getFromAccountId() == null || request.getToAccountId() == null) {
                    throw new IllegalArgumentException("Transfer transaction requires both from and to accounts");
                }
                if (request.getFromAccountId().equals(request.getToAccountId())) {
                    throw new IllegalArgumentException("Cannot transfer to the same account");
                }
                fromAccount = findAccount(request.getFromAccountId());
                if (!isAdmin() && !fromAccount.getUser().getUsername().equals(currentUsername())) {
                    throw new AccessDeniedException("Access denied");
                }
                toAccount = findAccount(request.getToAccountId());
                break;
            case DEPOSIT:
                if (request.getToAccountId() == null) {
                    throw new IllegalArgumentException("Deposit transaction requires to account");
                }
                toAccount = findAccount(request.getToAccountId());
                if (!isAdmin() && !toAccount.getUser().getUsername().equals(currentUsername())) {
                    throw new AccessDeniedException("Access denied");
                }
                break;
            case WITHDRAWAL:
                if (request.getFromAccountId() == null) {
                    throw new IllegalArgumentException("Withdrawal transaction requires from account");
                }
                fromAccount = findAccount(request.getFromAccountId());
                if (!isAdmin() && !fromAccount.getUser().getUsername().equals(currentUsername())) {
                    throw new AccessDeniedException("Access denied");
                }
                break;
        }

        // Save PENDING record to get an ID
        Transaction transaction = Transaction.builder()
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(request.getAmount())
                .type(request.getType())
                .status(TransactionStatus.PENDING)
                .build();
        transactionRepository.save(transaction);
        transactionRepository.flush();

        // Attempt balance update; catch failures so FAILED record commits
        try {
            executeBalanceUpdate(fromAccount, toAccount, request.getAmount(), request.getType());
            transaction.setStatus(TransactionStatus.COMPLETED);
        } catch (InsufficientFundsException | ResourceNotFoundException | ObjectOptimisticLockingFailureException e) {
            transaction.setStatus(TransactionStatus.FAILED);
        }

        Transaction savedTransaction = transactionRepository.save(transaction);
        return transactionMapper.toDTO(savedTransaction);
    }

    @Transactional
    @CacheEvict(value = "balances", allEntries = true)
    public TransactionDTO retryTransaction(Long id) {
        Transaction transaction = transactionRepository.findByIdWithAccounts(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));

        if (!isAdmin()) {
            String username = currentUsername();
            boolean ownsFrom = transaction.getFromAccount() != null && transaction.getFromAccount().getUser().getUsername().equals(username);
            boolean ownsTo = transaction.getToAccount() != null && transaction.getToAccount().getUser().getUsername().equals(username);
            if (!ownsFrom && !ownsTo) {
                throw new AccessDeniedException("Access denied");
            }
        }

        if (transaction.getStatus() == TransactionStatus.COMPLETED) {
            throw new IllegalStateException("Cannot retry a completed transaction");
        }

        // Re-fetch accounts fresh to avoid stale balances
        Account fromAccount = transaction.getFromAccount() != null
                ? accountRepository.findById(transaction.getFromAccount().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + transaction.getFromAccount().getId()))
                : null;
        Account toAccount = transaction.getToAccount() != null
                ? accountRepository.findById(transaction.getToAccount().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + transaction.getToAccount().getId()))
                : null;

        transaction.setStatus(TransactionStatus.PENDING);
        transactionRepository.save(transaction);

        try {
            executeBalanceUpdate(fromAccount, toAccount, transaction.getAmount(), transaction.getType());
            transaction.setStatus(TransactionStatus.COMPLETED);
        } catch (InsufficientFundsException | ResourceNotFoundException | ObjectOptimisticLockingFailureException e) {
            transaction.setStatus(TransactionStatus.FAILED);
        }

        Transaction savedTransaction = transactionRepository.save(transaction);
        return transactionMapper.toDTO(savedTransaction);
    }

    public Page<TransactionDTO> getTransactionsByStatus(TransactionStatus status, Pageable pageable) {
        if (isAdmin()) {
            return transactionRepository.findByStatus(status, pageable)
                    .map(transactionMapper::toDTO);
        }
        return transactionRepository.findByStatusAndOwner(status, currentUsername(), pageable)
                .map(transactionMapper::toDTO);
    }

    public TransactionDTO getTransactionById(Long id) {
        Transaction transaction = transactionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
        if (!isAdmin()) {
            String username = currentUsername();
            boolean ownsFrom = transaction.getFromAccount() != null && transaction.getFromAccount().getUser().getUsername().equals(username);
            boolean ownsTo = transaction.getToAccount() != null && transaction.getToAccount().getUser().getUsername().equals(username);
            if (!ownsFrom && !ownsTo) {
                throw new AccessDeniedException("Access denied");
            }
        }
        return transactionMapper.toDTO(transaction);
    }

    public Page<TransactionDTO> getAllTransactions(Pageable pageable) {
        if (!isAdmin()) {
            throw new AccessDeniedException("Access denied");
        }
        return transactionRepository.findAll(pageable)
                .map(transactionMapper::toDTO);
    }

    public Page<TransactionDTO> getTransactionsByFromAccountId(Long fromAccountId, Pageable pageable) {
        Account account = accountRepository.findById(fromAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + fromAccountId));
        if (!isAdmin() && !account.getUser().getUsername().equals(currentUsername())) {
            throw new AccessDeniedException("Access denied");
        }
        return transactionRepository.findByFromAccountId(fromAccountId, pageable)
                .map(transactionMapper::toDTO);
    }

    public Page<TransactionDTO> getTransactionsByToAccountId(Long toAccountId, Pageable pageable) {
        Account account = accountRepository.findById(toAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + toAccountId));
        if (!isAdmin() && !account.getUser().getUsername().equals(currentUsername())) {
            throw new AccessDeniedException("Access denied");
        }
        return transactionRepository.findByToAccountId(toAccountId, pageable)
                .map(transactionMapper::toDTO);
    }

    private void executeBalanceUpdate(Account fromAccount, Account toAccount, java.math.BigDecimal amount, com.example.springbootapi.enums.TransactionType type) {
        switch (type) {
            case TRANSFER:
                if (fromAccount.getBalance().compareTo(amount) < 0) {
                    throw new InsufficientFundsException("Insufficient funds. Available: " + fromAccount.getBalance() + ", Requested: " + amount);
                }
                fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
                toAccount.setBalance(toAccount.getBalance().add(amount));
                accountRepository.save(fromAccount);
                accountRepository.save(toAccount);
                break;
            case DEPOSIT:
                toAccount.setBalance(toAccount.getBalance().add(amount));
                accountRepository.save(toAccount);
                break;
            case WITHDRAWAL:
                if (fromAccount.getBalance().compareTo(amount) < 0) {
                    throw new InsufficientFundsException("Insufficient funds. Available: " + fromAccount.getBalance() + ", Requested: " + amount);
                }
                fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
                accountRepository.save(fromAccount);
                break;
        }
    }

    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account not found with id:" + accountId));
    }
}
