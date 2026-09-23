package com.gameexpert.identity;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 이 기기가 실제로 쓰게 될 닉네임을 알려 준다.
 *
 * 요청은 {@link DeviceIdentityFilter} 를 이미 지나왔으므로 여기 들어온 값이 곧 그 이름이다.
 * 클라이언트는 접속 전에 이 이름을 받아 자기 이름표·겉모습·소유 판정에 그대로 쓴다.
 */
@RestController
public class DeviceIdentityController {

    @GetMapping("/webcraft/identity")
    public Map<String, String> identity(@RequestParam("nickname") String nickname) {
        return Map.of("nickname", nickname);
    }
}
