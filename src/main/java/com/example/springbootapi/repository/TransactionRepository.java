package com.example.springbootapi.repository;

import com.example.springbootapi.entity.Transaction;
import com.example.springbootapi.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByFromAccountId(Long accountId);
    List<Transaction> findByToAccountId(Long accountId);
    List<Transaction> findByType(TransactionType type);
}
