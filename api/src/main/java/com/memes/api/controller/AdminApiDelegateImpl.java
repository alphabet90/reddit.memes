package com.memes.api.controller;

import com.memes.api.generated.api.AdminApiDelegate;
import com.memes.api.generated.model.ReindexResult;
import com.memes.api.service.IndexResult;
import com.memes.api.service.IndexerService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class AdminApiDelegateImpl implements AdminApiDelegate {

    private final IndexerService indexerService;

    public AdminApiDelegateImpl(IndexerService indexerService) {
        this.indexerService = indexerService;
    }

    @Override
    public ResponseEntity<ReindexResult> reindex() {
        IndexResult result = indexerService.reindex();
        ReindexResult dto = new ReindexResult();
        dto.setIndexed(result.indexed());
        dto.setDurationMs(result.durationMs());
        dto.setErrors(result.errors());
        return ResponseEntity.ok(dto);
    }
}
