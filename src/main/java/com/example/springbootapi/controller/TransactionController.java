package com.example.springbootapi.controller;

import com.example.springbootapi.dto.CreateTransactionRequest;
import com.example.springbootapi.dto.TransactionDTO;
import com.example.springbootapi.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<List<TransactionDTO>> getAllTransactions(){
        List<TransactionDTO> transactions = transactionService.getAllTransactions();
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionDTO> getTransactionById(@PathVariable Long id){
        TransactionDTO transaction = transactionService.getTransactionById(id);
        return ResponseEntity.ok(transaction);
    }

    @GetMapping("/from/{fromAccountId}")
    public ResponseEntity<List<TransactionDTO>> getTransactionFromAccount(@PathVariable Long fromAccountId){
        List<TransactionDTO> transactions = transactionService.getTransactionsByFromAccountId(fromAccountId);
        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/to/{toAccountId}")
    public ResponseEntity<List<TransactionDTO>> getTransactionToAccount(@PathVariable Long toAccountId){
        List<TransactionDTO> transactions = transactionService.getTransactionsByToAccountId(toAccountId);
        return ResponseEntity.ok(transactions);
    }
}
