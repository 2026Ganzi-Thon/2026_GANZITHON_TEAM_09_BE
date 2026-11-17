package com.haengunping.group.api.dto;
public record CreateGroupRequest(@jakarta.validation.constraints.NotNull com.haengunping.group.domain.GroupType type, String name) {}
