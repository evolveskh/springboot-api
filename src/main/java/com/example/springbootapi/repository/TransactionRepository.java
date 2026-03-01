package com.example.springbootapi.repository;

import com.example.springbootapi.entity.Transaction;
import com.example.springbootapi.enums.TransactionStatus;
import com.example.springbootapi.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Page<Transaction> findByFromAccountId(Long accountId, Pageable pageable);
    Page<Transaction> findByToAccountId(Long accountId, Pageable pageable);
    List<Transaction> findByType(TransactionType type);

    Page<Transaction> findByStatus(TransactionStatus status, Pageable pageable);

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.status = :status
        AND (
            (t.fromAccount IS NOT NULL AND t.fromAccount.user.username = :username)
            OR (t.toAccount IS NOT NULL AND t.toAccount.user.username = :username)
        )
    """)
    Page<Transaction> findByStatusAndOwner(
            @Param("status") TransactionStatus status,
            @Param("username") String username,
            Pageable pageable);

    @Query("SELECT t FROM Transaction t LEFT JOIN FETCH t.fromAccount fa LEFT JOIN FETCH fa.user LEFT JOIN FETCH t.toAccount ta LEFT JOIN FETCH ta.user WHERE t.id = :id")
    Optional<Transaction> findByIdWithAccounts(@Param("id") Long id);

    @Query("""
        SELECT t FROM Transaction t
        WHERE t.status = com.example.springbootapi.enums.TransactionStatus.COMPLETED
        AND t.createdAt >= :fromInstant AND t.createdAt < :toExclusive
        AND (t.fromAccount.id = :accountId OR t.toAccount.id = :accountId)
    """)
    Page<Transaction> findStatementTransactions(
            @Param("accountId") Long accountId,
            @Param("fromInstant") LocalDateTime fromInstant,
            @Param("toExclusive") LocalDateTime toExclusive,
            Pageable pageable);

    @Query("""
        SELECT COALESCE(SUM(
            CASE WHEN t.toAccount.id = :accountId THEN t.amount ELSE -t.amount END
        ), 0)
        FROM Transaction t
        WHERE t.status = com.example.springbootapi.enums.TransactionStatus.COMPLETED
        AND t.createdAt >= :fromInstant
        AND (t.fromAccount.id = :accountId OR t.toAccount.id = :accountId)
    """)
    BigDecimal sumNetEffectFrom(
            @Param("accountId") Long accountId,
            @Param("fromInstant") LocalDateTime fromInstant);

    @Query("""
        SELECT COALESCE(SUM(
            CASE WHEN t.toAccount.id = :accountId THEN t.amount ELSE -t.amount END
        ), 0)
        FROM Transaction t
        WHERE t.status = com.example.springbootapi.enums.TransactionStatus.COMPLETED
        AND t.createdAt >= :fromInstant AND t.createdAt < :toExclusive
        AND (t.fromAccount.id = :accountId OR t.toAccount.id = :accountId)
    """)
    BigDecimal sumNetEffectInRange(
            @Param("accountId") Long accountId,
            @Param("fromInstant") LocalDateTime fromInstant,
            @Param("toExclusive") LocalDateTime toExclusive);
}
