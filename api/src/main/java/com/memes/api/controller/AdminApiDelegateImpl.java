package com.memes.api.controller;

import com.memes.api.generated.api.AdminApiDelegate;
import com.memes.api.generated.model.MemeIndexRequest;
import com.memes.api.generated.model.ReindexAccepted;
import com.memes.api.service.IndexerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminApiDelegateImpl implements AdminApiDelegate {

    private final IndexerService indexerService;

    @Override
    public ResponseEntity<ReindexAccepted> reindex(MemeIndexRequest body) {
        indexerService.reindexAsync(body);
        ReindexAccepted accepted = new ReindexAccepted();
        accepted.setStatus(ReindexAccepted.StatusEnum.ACCEPTED);
        return ResponseEntity.ok(accepted);
    }
}
