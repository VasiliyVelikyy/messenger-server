package ru.moskalev.server.dto;

public record ChatMessage(
        String from,
        String to,
        String text,
        long timestamp
) {
}