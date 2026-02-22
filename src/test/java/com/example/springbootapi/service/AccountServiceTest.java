package com.example.springbootapi.service;

import com.example.springbootapi.dto.AccountDTO;
import com.example.springbootapi.dto.CreateAccountRequest;
import com.example.springbootapi.entity.Account;
import com.example.springbootapi.entity.User;
import com.example.springbootapi.exception.ResourceNotFoundException;
import com.example.springbootapi.mapper.AccountMapper;
import com.example.springbootapi.repository.AccountRepository;
import com.example.springbootapi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountMapper accountMapper;

    @InjectMocks
    private AccountService accountService;

    @Test
    void createAccount_Success() {
        // ARRANGE
        Long userId = 1L;
        CreateAccountRequest request = new CreateAccountRequest(userId);
        User user = new User();
        user.setId(userId);

        Account savedAccount = Account.builder()
                .id(1L)
                .accountNumber("0x123")
                .user(user)
                .balance(BigDecimal.ZERO)
                .build();

        AccountDTO expectedDTO = new AccountDTO();
        expectedDTO.setId(1L);
        expectedDTO.setAccountNumber("0x123");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);
        when(accountMapper.toDTO(savedAccount)).thenReturn(expectedDTO);

        // ACT
        AccountDTO result = accountService.createAccount(request);

        // ASSERT
        assertNotNull(result);
        assertEquals(1L, result.getId());
        verify(userRepository).findById(userId);
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void createAccount_UserNotFound_ThrowsException() {
        // ARRANGE
        Long userId = 99L;
        CreateAccountRequest request = new CreateAccountRequest(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // ACT & ASSERT
        assertThrows(ResourceNotFoundException.class, () -> accountService.createAccount(request));
        verify(accountRepository, never()).save(any());
    }

    @Test
    void getAccountById_Success() {
        // ARRANGE
        Long accountId = 1L;
        Account account = Account.builder().id(accountId).build();
        AccountDTO dto = new AccountDTO();
        dto.setId(accountId);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountMapper.toDTO(account)).thenReturn(dto);

        // ACT
        AccountDTO result = accountService.getAccountById(accountId);

        // ASSERT
        assertNotNull(result);
        assertEquals(accountId, result.getId());
    }

    @Test
    void getAccountById_NotFound_ThrowsException() {
        // ARRANGE
        Long accountId = 1L;
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        // ACT & ASSERT
        assertThrows(ResourceNotFoundException.class, () -> accountService.getAccountById(accountId));
    }

    @Test
    void getAccountBalance_Success() {
        // ARRANGE
        Long accountId = 1L;
        BigDecimal balance = new BigDecimal("1500.00");
        Account account = Account.builder().id(accountId).balance(balance).build();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        // ACT
        BigDecimal result = accountService.getAccountBalance(accountId);

        // ASSERT
        assertEquals(balance, result);
    }

    @Test
    void getAccountsByUserId_Success() {
        // ARRANGE
        Long userId = 1L;
        Account account = Account.builder().id(1L).build();
        AccountDTO dto = new AccountDTO();
        dto.setId(1L);

        when(userRepository.existsById(userId)).thenReturn(true);
        when(accountRepository.findByUserId(userId)).thenReturn(Collections.singletonList(account));
        when(accountMapper.toDTO(account)).thenReturn(dto);

        // ACT
        List<AccountDTO> results = accountService.getAccountsByUserId(userId);

        // ASSERT
        assertEquals(1, results.size());
        assertEquals(1L, results.get(0).getId());
    }

    @Test
    void deleteAccount_Success() {
        // ARRANGE
        Long accountId = 1L;
        Account account = Account.builder().id(accountId).accountNumber("0x123").build();

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        // ACT
        String result = accountService.deleteAccount(accountId);

        // ASSERT
        assertTrue(result.contains("successfully deleted"));
        verify(accountRepository).delete(account);
    }
}
