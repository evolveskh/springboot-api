package com.example.springbootapi.mapper;

import com.example.springbootapi.dto.TransactionDTO;
import com.example.springbootapi.entity.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface TransactionMapper {
    TransactionMapper INSTANCE = Mappers.getMapper(TransactionMapper.class);

    @Mapping(source = "fromAccount.id", target = "fromAccountId")
    @Mapping(source = "fromAccount.accountNumber", target = "fromAccountNumber")
    @Mapping(source = "toAccount.id", target = "toAccountId")
    @Mapping(source = "toAccount.accountNumber", target = "toAccountNumber")
    TransactionDTO toDTO(Transaction transaction);
}