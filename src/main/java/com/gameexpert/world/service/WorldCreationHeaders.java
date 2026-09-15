package com.gameexpert.world.service;

import com.gameexpert.qa.AuthorityEvidenceCreationReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorldCreationHeaders {
    private final AuthorityEvidenceCreationReceiptService receipts;

    public HttpHeaders forCreation(Long debugSeed, long worldId, String nickname) {
        HttpHeaders headers = new HttpHeaders();
        if (debugSeed != null) {
            String receipt = receipts.issueAfterCommittedCreation(worldId, nickname);
            if (receipt == null || receipt.isBlank()) {
                throw new IllegalStateException("authority evidence receipt was empty");
            }
            headers.set("X-Game-Expert-Qa-World-Creation-Receipt", receipt);
        }
        return headers;
    }
}
