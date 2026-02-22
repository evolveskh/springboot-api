package com.example.springbootapi.controller;

import com.example.springbootapi.dto.CreateTransactionRequest;
import com.example.springbootapi.dto.TransactionDTO;
import com.example.springbootapi.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<TransactionDTO> createTransaction(@Valid @RequestBody CreateTransactionRequest request){
        TransactionDTO transaction = transactionService.createTransaction(request);
        return new ResponseEntity<>(transaction, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<Page<TransactionDTO>> getAllTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,desc") String sort){
        Pageable pageable = createPageable(page, size, sort);
        Page<TransactionDTO> transactions = transactionService.getAllTransactions(pageable);
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionDTO> getTransactionById(@PathVariable Long id){
        TransactionDTO transaction = transactionService.getTransactionById(id);
        return ResponseEntity.ok(transaction);
    }

    @GetMapping("/from/{fromAccountId}")
    public ResponseEntity<Page<TransactionDTO>> getTransactionFromAccount(
            @PathVariable Long fromAccountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,desc") String sort){
        Pageable pageable = createPageable(page, size, sort);
        Page<TransactionDTO> transactions = transactionService.getTransactionsByFromAccountId(fromAccountId, pageable);
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/to/{toAccountId}")
    public ResponseEntity<Page<TransactionDTO>> getTransactionToAccount(
            @PathVariable Long toAccountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,desc") String sort){
        Pageable pageable = createPageable(page, size, sort);
        Page<TransactionDTO> transactions = transactionService.getTransactionsByToAccountId(toAccountId, pageable);
        return ResponseEntity.ok(transactions);
    }

    private Pageable createPageable(int page, int size, String sort) {
        String[] sortParams = sort.split(",");
        String sortField = sortParams[0];
        Sort.Direction direction = Sort.Direction.DESC;
        if (sortParams.length > 1 && "asc".equalsIgnoreCase(sortParams[1])) {
            direction = Sort.Direction.ASC;
        }
        return PageRequest.of(page, size, Sort.by(direction, sortField));
    }
}
