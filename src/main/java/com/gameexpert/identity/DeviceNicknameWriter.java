package com.gameexpert.identity;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * 기기-닉네임 연결을 한 건 쓴다.
 *
 * 이름이 이미 있으면 제약 위반이 나는데, 그 예외가 난 뒤의 영속성 컨텍스트로는 아무 것도 더
 * 할 수 없다. 그래서 시도 한 번이 트랜잭션 하나이고, 실패는 그대로 밖으로 던져 롤백시킨다.
 * 다음 후보는 깨끗한 트랜잭션에서 다시 시도한다.
 */
@Component
@RequiredArgsConstructor
class DeviceNicknameWriter {

    private final DeviceNicknameRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void insert(String deviceHash, String displayNickname, String storedNickname) {
        repository.saveAndFlush(new DeviceNickname(deviceHash, displayNickname, storedNickname));
    }
}
