package com.gameexpert.api.persistence;

import java.time.LocalDateTime;

public interface PlayerAccess {
    Long getId();
    String getNickname();
    LocalDateTime getCreatedAt();
}
