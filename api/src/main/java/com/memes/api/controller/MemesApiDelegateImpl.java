package com.memes.api.controller;

import com.memes.api.generated.api.MemesApiDelegate;
import com.memes.api.generated.model.CategorySummary;
import com.memes.api.generated.model.LocaleCode;
import com.memes.api.generated.model.Meme;
import com.memes.api.generated.model.MemePage;
import com.memes.api.generated.model.Stats;
import com.memes.api.service.MemeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MemesApiDelegateImpl implements MemesApiDelegate {

    private final MemeService memeService;

    @Override
    public ResponseEntity<Stats> getStats() {
        return ResponseEntity.ok(memeService.getStats());
    }

    @Override
    public ResponseEntity<List<CategorySummary>> listCategories(LocaleCode locale) {
        return ResponseEntity.ok(memeService.listCategories(localeValue(locale)));
    }

    @Override
    public ResponseEntity<MemePage> listMemes(
            Integer page, Integer limit,
            String category, String subreddit, String sort,
            LocaleCode locale) {
        return ResponseEntity.ok(memeService.listMemes(
            Optional.ofNullable(page).orElse(0),
            Optional.ofNullable(limit).orElse(20),
            category, subreddit,
            Optional.ofNullable(sort).orElse("score"),
            localeValue(locale)
        ));
    }

    @Override
    public ResponseEntity<MemePage> listMemesByCategory(
            String category, Integer page, Integer limit, String sort, LocaleCode locale) {
        MemePage result = memeService.listMemes(
            Optional.ofNullable(page).orElse(0),
            Optional.ofNullable(limit).orElse(20),
            category, null,
            Optional.ofNullable(sort).orElse("score"),
            localeValue(locale)
        );
        return Optional.of(result)
            .filter(r -> r.getTotal() != null && r.getTotal() > 0)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<Meme> getMeme(String category, String slug, LocaleCode locale) {
        return memeService.getMeme(category, slug, localeValue(locale))
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<MemePage> searchMemes(String q, Integer page, Integer limit, LocaleCode locale) {
        return ResponseEntity.ok(memeService.search(
            q,
            Optional.ofNullable(page).orElse(0),
            Optional.ofNullable(limit).orElse(20),
            localeValue(locale)
        ));
    }

    private static String localeValue(@Nullable LocaleCode locale) {
        return Optional.ofNullable(locale).orElse(LocaleCode.EN).getValue();
    }
}
