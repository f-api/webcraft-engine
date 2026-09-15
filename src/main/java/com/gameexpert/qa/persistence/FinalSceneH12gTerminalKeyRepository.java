package com.gameexpert.qa.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinalSceneH12gTerminalKeyRepository
        extends JpaRepository<FinalSceneH12gTerminalKey, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select terminalKey from FinalSceneH12gTerminalKey terminalKey "
            + "where terminalKey.id = :id")
    Optional<FinalSceneH12gTerminalKey> findByIdForUpdate(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @Query(value = """
            insert into `final_scene_h12g_terminal_keys`
                (id, version, key_epoch, key_identity, mac_key)
            select :id, 0, :keyEpoch, :keyIdentity, :macKey
            where not exists (select 1 from `final_scene_h12g_terminals`)
            on duplicate key update id = id
            """, nativeQuery = true)
    int installFirstKey(@Param("id") Long id, @Param("keyEpoch") long keyEpoch,
            @Param("keyIdentity") String keyIdentity, @Param("macKey") byte[] macKey);
}
