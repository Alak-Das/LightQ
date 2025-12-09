package com.al.simplequeueservice.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Data
@NoArgsConstructor
public class MessageResponse {
    private String id;
    private String content;
    private LocalDateTime createdAt;

    public MessageResponse(Message message) {
        this.id = message.getId();
        this.content = message.getContent();
        this.createdAt = message.getCreatedAt().toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}
