package com.java.once4j.dto;

public record IdempotentRecord(String payloadHash, String responseString) {}
