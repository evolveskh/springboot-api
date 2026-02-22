package com.example.springbootapi.service;

import com.example.springbootapi.dto.UserRequestDTO;
import com.example.springbootapi.dto.UserResponseDTO;
import com.example.springbootapi.entity.User;
import com.example.springbootapi.exception.ResourceNotFoundException;
import com.example.springbootapi.exception.UserAlreadyExistsException;
import com.example.springbootapi.mapper.UserMapper;
import com.example.springbootapi.repository.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserResponseDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(userMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "users", key = "#id")
    public UserResponseDTO getUserById(Long id) {
        return userRepository.findById(id)
                .map(userMapper::toResponseDTO)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    public UserResponseDTO createUser(UserRequestDTO userRequestDTO) {
        if(userRepository.existsByUsername(userRequestDTO.getUsername())) {
            throw new UserAlreadyExistsException("Username '" + userRequestDTO.getUsername() + "' already exists!");
        }
        User user = userMapper.toEntity(userRequestDTO);
        user.setPassword(passwordEncoder.encode(userRequestDTO.getPassword()));
        User savedUser = userRepository.save(user);
        return userMapper.toResponseDTO(savedUser);
    }

    @CachePut(value = "users", key = "#id")
    public UserResponseDTO updateUser(Long id, UserRequestDTO userRequestDTO) {
        return userRepository.findById(id).map(user -> {
            user.setUsername(userRequestDTO.getUsername());
            user.setPassword(passwordEncoder.encode(userRequestDTO.getPassword()));
            user.setEmail(userRequestDTO.getEmail());
            User updatedUser = userRepository.save(user);
            return userMapper.toResponseDTO(updatedUser);
        }).orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @CacheEvict(value = "users", key = "#id")
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
}
