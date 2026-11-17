package com.haengunping.account.api.dto;
public record CreateAccountRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=10) String nickname) {}
