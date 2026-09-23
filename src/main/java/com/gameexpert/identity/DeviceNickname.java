package com.gameexpert.identity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 브라우저 기기(쿠키)와 그 기기가 쓰는 실제 닉네임의 연결입니다.
 *
 * 플레이어가 입력한 이름({@code displayNickname})이 이미 다른 기기의 것이면, 저장에는
 * 뒤에 짧은 꼬리를 붙인 이름({@code storedNickname})을 쓴다. 화면에는 언제나 입력한
 * 이름만 보이므로, 서로 다른 사람이 같은 이름으로 동시에 플레이할 수 있다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "webcraft_device_nicknames", uniqueConstraints = {
        @UniqueConstraint(name = "uk_device_nickname_device_display", columnNames = {"device_hash", "display_nickname"}),
        @UniqueConstraint(name = "uk_device_nickname_stored", columnNames = {"stored_nickname"})})
public class DeviceNickname {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_hash", nullable = false, length = 64)
    private String deviceHash;

    @Column(name = "display_nickname", nullable = false, length = 16)
    private String displayNickname;

    @Column(name = "stored_nickname", nullable = false, length = 16)
    private String storedNickname;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    DeviceNickname(String deviceHash, String displayNickname, String storedNickname) {
        this.deviceHash = deviceHash;
        this.displayNickname = displayNickname;
        this.storedNickname = storedNickname;
        this.createdAt = LocalDateTime.now();
    }
}
