package com.example.springbootapi.integration;

import com.example.springbootapi.dto.UserRequestDTO;
import com.example.springbootapi.dto.UserResponseDTO;
import com.example.springbootapi.repository.UserRepository;
import com.example.springbootapi.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class UserServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    void createUser_ShouldSaveToRealDatabase() {
        // ARRANGE
        UserRequestDTO request = new UserRequestDTO();
        request.setUsername("testuser");
        request.setPassword("password123");
        request.setEmail("test@example.com");

        // ACT
        UserResponseDTO savedUser = userService.createUser(request);

        // ASSERT
        assertNotNull(savedUser.getId());
        assertEquals("testuser", savedUser.getUsername());

        // Verify it exists in the real DB
        assertTrue(userRepository.existsById(savedUser.getId()));
        
        List<UserResponseDTO> allUsers = userService.getAllUsers();
        assertEquals(1, allUsers.size());
    }

    @Test
    void createUser_WithExistingUsername_ShouldThrowException() {
        // ARRANGE
        UserRequestDTO request = new UserRequestDTO();
        request.setUsername("duplicate");
        request.setPassword("password123");
        request.setEmail("test1@example.com");
        userService.createUser(request);

        UserRequestDTO duplicateRequest = new UserRequestDTO();
        duplicateRequest.setUsername("duplicate");
        duplicateRequest.setPassword("newpassword");
        duplicateRequest.setEmail("test2@example.com");

        // ACT & ASSERT
        assertThrows(RuntimeException.class, () -> userService.createUser(duplicateRequest));
    }
}
