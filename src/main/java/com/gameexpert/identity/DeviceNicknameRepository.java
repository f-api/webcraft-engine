package com.gameexpert.identity;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** 기기와 닉네임 연결 저장소. */
public interface DeviceNicknameRepository extends JpaRepository<DeviceNickname, Long> {

    Optional<DeviceNickname> findByDeviceHashAndDisplayNickname(String deviceHash, String displayNickname);

    Optional<DeviceNickname> findByStoredNickname(String storedNickname);

    boolean existsByDisplayNickname(String displayNickname);
}
