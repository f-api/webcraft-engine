package com.gameexpert.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * 기기(쿠키)마다 실제로 쓸 닉네임을 정한다.
 *
 * 처음 그 이름을 쓰는 기기는 입력한 이름을 그대로 갖고, 같은 이름을 쓰는 다른 기기는
 * 뒤에 짧은 꼬리표가 붙은 이름을 받는다. 이름이 같아도 사람은 쿠키로 구분되므로,
 * 다시 접속하면 늘 자기 캐릭터로 돌아온다.
 */
@Component
@RequiredArgsConstructor
public class DeviceNicknames {

    /** 학생 프로젝트의 닉네임 규칙(2~12자, 영문·숫자·밑줄)을 넘지 않게 맞춘다. */
    static final int MAX_LENGTH = 12;
    private static final int TAG_LENGTH = 4;
    private static final int MAX_ATTEMPTS = 12;

    private final DeviceNicknameRepository repository;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    /** 이 기기가 이 이름으로 접속할 때 실제로 쓸 닉네임입니다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String storedFor(String deviceHash, String displayNickname) {
        String key = deviceHash + '\u0000' + displayNickname;
        String cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        String stored = repository.findByDeviceHashAndDisplayNickname(deviceHash, displayNickname)
                .map(DeviceNickname::getStoredNickname)
                .orElseGet(() -> claim(deviceHash, displayNickname));
        cache.put(key, stored);
        return stored;
    }

    private String claim(String deviceHash, String displayNickname) {
        // 이미 이 기기가 쓰는 이름을 그대로 물어보면(꼬리표가 붙은 이름으로 다시 접속하는 경우)
        // 새로 집지 않고 그 이름을 돌려준다.
        var mine = repository.findByStoredNickname(displayNickname);
        if (mine.isPresent()) {
            return mine.get().getDeviceHash().equals(deviceHash) ? displayNickname
                    : tagged(displayNickname, deviceHash, 0);
        }
        boolean taken = repository.existsByDisplayNickname(displayNickname);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = (!taken && attempt == 0)
                    ? displayNickname
                    : tagged(displayNickname, deviceHash, attempt);
            try {
                repository.saveAndFlush(new DeviceNickname(deviceHash, displayNickname, candidate));
                return candidate;
            } catch (DataIntegrityViolationException collision) {
                // 같은 이름을 동시에 집었거나 꼬리표가 겹쳤다. 다음 후보로 넘어간다.
                var existing = repository.findByDeviceHashAndDisplayNickname(deviceHash, displayNickname);
                if (existing.isPresent()) {
                    return existing.get().getStoredNickname();
                }
                taken = true;
            }
        }
        // 꼬리표를 12번 시도해도 비어 있는 이름을 못 찾는 경우는 사실상 없다. 그때는 입력한 이름을 쓴다.
        return displayNickname;
    }

    /** 입력한 이름 뒤에 기기별 꼬리표를 붙인 이름. 영문·숫자·밑줄만 쓰고 길이 제한을 지킨다. */
    static String tagged(String displayNickname, String deviceHash, int attempt) {
        String tag = tag(deviceHash, attempt);
        int room = MAX_LENGTH - 1 - tag.length();
        String base = displayNickname.length() > room ? displayNickname.substring(0, room) : displayNickname;
        return base + '_' + tag;
    }

    private static String tag(String deviceHash, int attempt) {
        byte[] digest = sha256(deviceHash + '#' + attempt);
        long value = 0;
        for (int index = 0; index < 8; index++) {
            value = (value << 8) | (digest[index] & 0xFFL);
        }
        String base36 = Long.toString(value >>> 1, 36);
        return base36.length() >= TAG_LENGTH
                ? base36.substring(base36.length() - TAG_LENGTH)
                : "0".repeat(TAG_LENGTH - base36.length()) + base36;
    }

    /** 쿠키 값을 그대로 저장하지 않도록 해시로 바꾼다. */
    public static String hash(String deviceKey) {
        byte[] digest = sha256(deviceKey);
        StringBuilder text = new StringBuilder(64);
        for (byte b : digest) {
            text.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return text.toString();
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
