package com.example.springbootapi.mapper;

import com.example.springbootapi.dto.AccountDTO;
import com.example.springbootapi.dto.CreateAccountRequest;
import com.example.springbootapi.entity.Account;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface AccountMapper {
    AccountMapper INSTANCE = Mappers.getMapper(AccountMapper.class);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "accountNumber", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(source = "userId", target = "user.id")
    Account toEntity(CreateAccountRequest createAccountRequest);

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.username", target = "username")
    AccountDTO toDTO(Account account);
}
