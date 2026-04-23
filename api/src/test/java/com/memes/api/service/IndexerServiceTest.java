package com.memes.api.service;

import com.memes.api.repository.MemeRecord;
import com.memes.api.repository.MemeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IndexerServiceTest {

    @Mock MemeRepository memeRepository;
    @Mock CacheManager cacheManager;
    @Mock Cache cache;

    @InjectMocks IndexerService indexerService;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        ReflectionTestUtils.setField(indexerService, "memesRoot", tempDir.toString());
        when(cacheManager.getCache(anyString())).thenReturn(cache);
        when(memeRepository.upsertAll(any())).thenReturn(0);
    }

    @Test
    void parseMdx_valid_returnsRecord() throws IOException {
        Path categoryDir = tempDir.resolve("argentina-football");
        Files.createDirectories(categoryDir);
        Path mdx = categoryDir.resolve("cat-world-cup.mdx");
        Files.writeString(mdx, """
            ---
            title: "Cat at the World Cup"
            description: "A cat in an Argentina jersey"
            author: "redditor123"
            subreddit: "argentina"
            category: "argentina-football"
            slug: "cat-world-cup"
            score: 2840
            created_at: "2025-02-04T07:28:21Z"
            source_url: "https://i.redd.it/example.jpg"
            post_url: "https://reddit.com/r/argentina/comments/abc123"
            image: "./cat-world-cup.jpg"
            tags: ["argentina", "argentina-football"]
            ---

            # Cat at the World Cup
            """);

        Optional<MemeRecord> result = indexerService.parseMdx(mdx);

        assertThat(result).isPresent();
        MemeRecord r = result.get();
        assertThat(r.slug()).isEqualTo("cat-world-cup");
        assertThat(r.category()).isEqualTo("argentina-football");
        assertThat(r.title()).isEqualTo("Cat at the World Cup");
        assertThat(r.score()).isEqualTo(2840);
        assertThat(r.tags()).containsExactly("argentina", "argentina-football");
    }

    @Test
    void parseMdx_noFrontmatterDelimiters_returnsEmpty() throws IOException {
        Path mdx = tempDir.resolve("bad.mdx");
        Files.writeString(mdx, "Just plain text with no frontmatter.");

        Optional<MemeRecord> result = indexerService.parseMdx(mdx);

        assertThat(result).isEmpty();
    }

    @Test
    void parseMdx_imagePathNormalized() throws IOException {
        Path categoryDir = tempDir.resolve("test-cat");
        Files.createDirectories(categoryDir);
        Path mdx = categoryDir.resolve("my-meme.mdx");
        Files.writeString(mdx, """
            ---
            category: "test-cat"
            slug: "my-meme"
            title: "My Meme"
            subreddit: "argentina"
            image: "./my-meme.jpg"
            ---
            """);

        Optional<MemeRecord> result = indexerService.parseMdx(mdx);

        assertThat(result).isPresent();
        assertThat(result.get().imagePath()).isEqualTo("memes/test-cat/my-meme.jpg");
    }

    @Test
    void parseMdx_missingCategoryOrSlug_returnsEmpty() throws IOException {
        Path mdx = tempDir.resolve("incomplete.mdx");
        Files.writeString(mdx, """
            ---
            title: "No category or slug"
            ---
            """);

        Optional<MemeRecord> result = indexerService.parseMdx(mdx);

        assertThat(result).isEmpty();
    }

    @Test
    void reindex_countsIndexedFiles() throws IOException {
        Path cat1 = tempDir.resolve("argentina-football");
        Files.createDirectories(cat1);
        writeMinimalMdx(cat1.resolve("meme1.mdx"), "argentina-football", "meme1");
        writeMinimalMdx(cat1.resolve("meme2.mdx"), "argentina-football", "meme2");

        when(memeRepository.upsertAll(any())).thenReturn(2);

        IndexResult result = indexerService.reindex();

        assertThat(result.indexed()).isEqualTo(2);
        assertThat(result.errors()).isEmpty();
        verify(memeRepository).upsertAll(argThat(list -> list.size() == 2));
    }

    @Test
    void reindex_invalidatesAllCaches() {
        indexerService.reindex();

        verify(cacheManager, atLeast(5)).getCache(anyString());
        verify(cache, atLeast(5)).clear();
    }

    private void writeMinimalMdx(Path path, String category, String slug) throws IOException {
        Files.writeString(path, String.format("""
            ---
            category: "%s"
            slug: "%s"
            title: "Test Meme"
            subreddit: "argentina"
            image: "./%s.jpg"
            ---
            """, category, slug, slug));
    }
}
