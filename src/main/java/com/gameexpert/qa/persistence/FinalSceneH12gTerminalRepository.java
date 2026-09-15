package com.gameexpert.qa.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinalSceneH12gTerminalRepository
        extends JpaRepository<FinalSceneH12gTerminal, Long> {
    Optional<FinalSceneH12gTerminal> findByBindingDigest(String bindingDigest);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select terminal from FinalSceneH12gTerminal terminal "
            + "where terminal.bindingDigest = :bindingDigest")
    Optional<FinalSceneH12gTerminal> findBindingForUpdate(
            @Param("bindingDigest") String bindingDigest);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from FinalSceneH12gTerminal terminal where terminal.worldId = :worldId")
    int deleteAllByWorldId(@Param("worldId") Long worldId);
}
