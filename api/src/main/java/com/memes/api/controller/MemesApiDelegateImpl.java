package com.memes.api.controller;

import com.memes.api.generated.api.MemesApiDelegate;
import com.memes.api.generated.model.CategorySummary;
import com.memes.api.generated.model.Meme;
import com.memes.api.generated.model.MemePage;
import com.memes.api.generated.model.Stats;
import com.memes.api.service.MemeService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MemesApiDelegateImpl implements MemesApiDelegate {

    private final MemeService memeService;

    public MemesApiDelegateImpl(MemeService memeService) {
        this.memeService = memeService;
    }

    @Override
    public ResponseEntity<Stats> getStats() {
        return ResponseEntity.ok(memeService.getStats());
    }

    @Override
    public ResponseEntity<List<CategorySummary>> listCategories() {
        return ResponseEntity.ok(memeService.listCategories());
    }

    @Override
    public ResponseEntity<MemePage> listMemes(
            Integer page, Integer limit,
            String category, String subreddit, String sort) {
        return ResponseEntity.ok(memeService.listMemes(
            page != null ? page : 0,
            limit != null ? limit : 20,
            category, subreddit,
            sort != null ? sort : "score"
        ));
    }

    @Override
    public ResponseEntity<MemePage> listMemesByCategory(
            String category, Integer page, Integer limit, String sort) {
        int p = page != null ? page : 0;
        int l = limit != null ? limit : 20;
        String s = sort != null ? sort : "score";
        MemePage result = memeService.listMemes(p, l, category, null, s);
        if (result.getTotal() == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<Meme> getMeme(String category, String slug) {
        return memeService.getMeme(category, slug)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<MemePage> searchMemes(String q, Integer page, Integer limit) {
        return ResponseEntity.ok(memeService.search(
            q,
            page != null ? page : 0,
            limit != null ? limit : 20
        ));
    }
}
