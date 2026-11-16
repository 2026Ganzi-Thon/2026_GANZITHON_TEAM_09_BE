package com.haengunping.account.api.dto;
public record MeResponse(String nickname, boolean adultConfirmed, boolean sensitiveConsent, java.time.Instant createdAt, java.time.LocalDate today) {}
