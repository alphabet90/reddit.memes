package com.memes.api.controller;

import com.memes.api.generated.api.AdminApiDelegate;
import com.memes.api.generated.model.MemeIndexRequest;
import com.memes.api.generated.model.ReindexResult;
import com.memes.api.service.IndexResult;
import com.memes.api.service.IndexerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AdminApiDelegateImpl implements AdminApiDelegate {

    private final IndexerService indexerService;

    @Override
    public ResponseEntity<ReindexResult> reindex(MemeIndexRequest body) {
        IndexResult result = Optional.ofNullable(body)
            .filter(r -> r.getSlug() != null && !r.getSlug().isBlank())
            .map(indexerService::indexSingle)
            .orElseGet(indexerService::reindex);

        ReindexResult dto = new ReindexResult();
        dto.setIndexed(result.indexed());
        dto.setDurationMs(result.durationMs());
        dto.setErrors(result.errors());
        return ResponseEntity.ok(dto);
    }
}
