package com.example.springbootapi.repository;

import com.example.springbootapi.entity.Transaction;
import com.example.springbootapi.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Page<Transaction> findByFromAccountId(Long accountId, Pageable pageable);
    Page<Transaction> findByToAccountId(Long accountId, Pageable pageable);
    List<Transaction> findByType(TransactionType type);
}
