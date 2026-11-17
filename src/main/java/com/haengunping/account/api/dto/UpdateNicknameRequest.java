package com.haengunping.account.api.dto;
public record UpdateNicknameRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=10) String nickname) {}
