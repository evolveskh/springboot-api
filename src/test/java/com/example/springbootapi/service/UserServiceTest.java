package com.example.springbootapi.service;

import com.example.springbootapi.dto.UserRequestDTO;
import com.example.springbootapi.dto.UserResponseDTO;
import com.example.springbootapi.entity.User;
import com.example.springbootapi.exception.ResourceNotFoundException;
import com.example.springbootapi.exception.UserAlreadyExistsException;
import com.example.springbootapi.mapper.UserMapper;
import com.example.springbootapi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void createUser_Success() {
        // ARRANGE
        UserRequestDTO request = new UserRequestDTO();
        request.setUsername("testuser");
        request.setPassword("password");

        User user = new User();
        user.setUsername("testuser");

        User savedUser = new User();
        savedUser.setId(1L);
        savedUser.setUsername("testuser");

        UserResponseDTO expectedDTO = new UserResponseDTO();
        expectedDTO.setId(1L);
        expectedDTO.setUsername("testuser");

        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userMapper.toEntity(request)).thenReturn(user);
        when(passwordEncoder.encode("password")).thenReturn("encodedPassword");
        when(userRepository.save(user)).thenReturn(savedUser);
        when(userMapper.toResponseDTO(savedUser)).thenReturn(expectedDTO);

        // ACT
        UserResponseDTO result = userService.createUser(request);

        // ASSERT
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("testuser", result.getUsername());
        verify(userRepository).save(user);
        assertEquals("encodedPassword", user.getPassword());
    }

    @Test
    void createUser_AlreadyExists_ThrowsException() {
        // ARRANGE
        UserRequestDTO request = new UserRequestDTO();
        request.setUsername("existinguser");

        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        // ACT & ASSERT
        assertThrows(UserAlreadyExistsException.class, () -> userService.createUser(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void getUserById_Success() {
        // ARRANGE
        Long userId = 1L;
        User user = new User();
        user.setId(userId);
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userMapper.toResponseDTO(user)).thenReturn(dto);

        // ACT
        UserResponseDTO result = userService.getUserById(userId);

        // ASSERT
        assertNotNull(result);
        assertEquals(userId, result.getId());
    }

    @Test
    void getUserById_NotFound_ThrowsException() {
        // ARRANGE
        Long userId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // ACT & ASSERT
        assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(userId));
    }

    @Test
    void updateUser_Success() {
        // ARRANGE
        Long userId = 1L;
        UserRequestDTO request = new UserRequestDTO();
        request.setUsername("newname");
        request.setPassword("newpass");

        User existingUser = new User();
        existingUser.setId(userId);
        existingUser.setUsername("oldname");

        User savedUser = new User();
        savedUser.setId(userId);
        savedUser.setUsername("newname");

        UserResponseDTO responseDTO = new UserResponseDTO();
        responseDTO.setId(userId);
        responseDTO.setUsername("newname");

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.encode("newpass")).thenReturn("encodedNewPass");
        when(userRepository.save(existingUser)).thenReturn(savedUser);
        when(userMapper.toResponseDTO(savedUser)).thenReturn(responseDTO);

        // ACT
        UserResponseDTO result = userService.updateUser(userId, request);

        // ASSERT
        assertNotNull(result);
        assertEquals("newname", result.getUsername());
        assertEquals("encodedNewPass", existingUser.getPassword());
    }

    @Test
    void deleteUser_Success() {
        // ARRANGE
        Long userId = 1L;

        // ACT
        userService.deleteUser(userId);

        // ASSERT
        verify(userRepository).deleteById(userId);
    }
}
