package com.memes.api.service;

import com.memes.api.config.RedisConfig;
import com.memes.api.generated.model.CategorySummary;
import com.memes.api.generated.model.Meme;
import com.memes.api.generated.model.MemePage;
import com.memes.api.generated.model.Stats;
import com.memes.api.repository.MemeRecord;
import com.memes.api.repository.MemeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class MemeService {

    private static final Logger log = LoggerFactory.getLogger(MemeService.class);

    private final MemeRepository memeRepository;

    public MemeService(MemeRepository memeRepository) {
        this.memeRepository = memeRepository;
    }

    @Cacheable(value = RedisConfig.CACHE_STATS)
    public Stats getStats() {
        Stats stats = new Stats();
        stats.setTotalMemes(memeRepository.countAll());
        stats.setTotalCategories(memeRepository.countCategories());
        stats.setTotalSubreddits(memeRepository.countSubreddits());
        memeRepository.topCategory().ifPresent(stats::setTopCategory);
        String lastIndexed = memeRepository.findLastIndexedAt();
        if (lastIndexed != null) {
            stats.setIndexedAt(parseDateTime(lastIndexed));
        }
        return stats;
    }

    @Cacheable(value = RedisConfig.CACHE_CATEGORIES)
    public List<CategorySummary> listCategories() {
        return memeRepository.findAllCategories().stream()
            .map(row -> {
                CategorySummary cs = new CategorySummary();
                cs.setCategory(row.category());
                cs.setCount(row.count());
                cs.setTopScore(row.topScore());
                return cs;
            })
            .toList();
    }

    @Cacheable(value = RedisConfig.CACHE_MEME_LIST,
               key = "#page + '-' + #limit + '-' + #category + '-' + #subreddit + '-' + #sort")
    public MemePage listMemes(int page, int limit, String category, String subreddit, String sort) {
        int offset = page * limit;
        List<MemeRecord> records = memeRepository.findAll(offset, limit, category, subreddit, sort);
        int total = memeRepository.countFiltered(category, subreddit);
        return toMemePage(records, page, limit, total);
    }

    @Cacheable(value = RedisConfig.CACHE_MEME, key = "#category + '/' + #slug")
    public Optional<Meme> getMeme(String category, String slug) {
        return memeRepository.findBySlugAndCategory(slug, category).map(this::toMeme);
    }

    @Cacheable(value = RedisConfig.CACHE_SEARCH,
               key = "#query + '-' + #page + '-' + #limit")
    public MemePage search(String query, int page, int limit) {
        int offset = page * limit;
        List<MemeRecord> records = memeRepository.search(query, offset, limit);
        int total = memeRepository.countSearch(query);
        return toMemePage(records, page, limit, total);
    }

    private MemePage toMemePage(List<MemeRecord> records, int page, int limit, int total) {
        MemePage mp = new MemePage();
        mp.setData(records.stream().map(this::toMeme).toList());
        mp.setPage(page);
        mp.setLimit(limit);
        mp.setTotal(total);
        mp.setTotalPages(limit > 0 ? (int) Math.ceil((double) total / limit) : 0);
        return mp;
    }

    private OffsetDateTime parseDateTime(String s) {
        try {
            return OffsetDateTime.parse(s);
        } catch (Exception e) {
            log.warn("Could not parse datetime: {}", s);
            return null;
        }
    }

    private Meme toMeme(MemeRecord r) {
        Meme m = new Meme();
        m.setSlug(r.slug());
        m.setCategory(r.category());
        m.setTitle(r.title());
        m.setDescription(r.description());
        m.setAuthor(r.author());
        m.setSubreddit(r.subreddit());
        m.setScore(r.score());
        if (r.createdAt() != null) m.setCreatedAt(parseDateTime(r.createdAt()));
        m.setSourceUrl(r.sourceUrl());
        m.setPostUrl(r.postUrl());
        m.setImagePath(r.imagePath());
        m.setTags(r.tags());
        return m;
    }
}
