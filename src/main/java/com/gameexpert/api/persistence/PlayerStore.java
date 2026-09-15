package com.gameexpert.api.persistence;

import java.util.Optional;

public interface PlayerStore {
    Optional<PlayerAccess> findById(Long id);
    PlayerAccess getReferenceById(Long id);
    Optional<PlayerAccess> findByNickname(String nickname);
}
