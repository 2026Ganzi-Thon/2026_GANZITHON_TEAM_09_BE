package com.haengunping.group.api.dto;
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) public record MatchResponse(String status,Long groupId,java.time.Instant queuedAt) {}
