package com.example.springbootapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountStatementDTO {
    private Long accountId;
    private String accountNumber;
    private LocalDate from;
    private LocalDate to;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private Page<TransactionDTO> transactions;
}
