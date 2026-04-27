package com.memes.api.service;

import com.memes.api.generated.model.LocaleCode;
import com.memes.api.generated.model.MemeIndexRequest;
import com.memes.api.generated.model.MemeTranslation;
import com.memes.api.repository.MemeImageRow;
import com.memes.api.repository.MemeRepository;
import com.memes.api.repository.MemeTranslationRow;
import com.memes.api.repository.MemeUpsert;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IndexerServiceTest {

    @Mock MemeRepository memeRepository;
    @Mock CacheManager cacheManager;
    @Mock Cache cache;

    @InjectMocks IndexerService indexerService;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(indexerService, "memesRoot", tempDir.toString());
        when(cacheManager.getCache(anyString())).thenReturn(cache);
        when(memeRepository.upsertAll(any())).thenReturn(0);
    }

    @Test
    void parseMdx_validV2Frontmatter_returnsRecord() throws IOException {
        Path categoryDir = tempDir.resolve("argentina-football");
        Files.createDirectories(categoryDir);
        Path mdx = categoryDir.resolve("cat-world-cup.mdx");
        Files.writeString(mdx, """
            ---
            category: argentina-football
            slug: cat-world-cup
            subreddit: argentina
            author: redditor123
            score: 2840
            created_at: "2025-02-04T07:28:21Z"
            source_url: https://i.redd.it/example.jpg
            post_url: https://reddit.com/r/argentina/comments/abc123
            default_locale: en
            tags: [argentina, argentina-football]
            images:
              - path: ./cat-world-cup.jpg
                is_primary: true
            translations:
              en:
                title: Cat at the World Cup
                description: A cat in a jersey
              es:
                title: Gato en el Mundial
                description: Un gato con camiseta
            ---

            # Cat at the World Cup
            """);

        Optional<MemeUpsert> result = indexerService.parseMdx(mdx);

        assertThat(result).isPresent();
        MemeUpsert u = result.get();
        assertThat(u.slug()).isEqualTo("cat-world-cup");
        assertThat(u.categorySlug()).isEqualTo("argentina-football");
        assertThat(u.defaultLocale()).isEqualTo("en");
        assertThat(u.score()).isEqualTo(2840);
        assertThat(u.tagSlugs()).containsExactlyInAnyOrder("argentina", "argentina-football");
        assertThat(u.translations()).extracting(MemeTranslationRow::locale)
            .containsExactlyInAnyOrder("en", "es");
        assertThat(u.images()).hasSize(1);
        assertThat(u.images().get(0).isPrimary()).isTrue();
        assertThat(u.images().get(0).path()).isEqualTo("memes/argentina-football/cat-world-cup.jpg");
    }

    @Test
    void parseMdx_noFrontmatterDelimiters_returnsEmpty() throws IOException {
        Path mdx = tempDir.resolve("bad.mdx");
        Files.writeString(mdx, "Just plain text with no frontmatter.");

        assertThat(indexerService.parseMdx(mdx)).isEmpty();
    }

    @Test
    void parseMdx_missingCategoryOrSlug_returnsEmpty() throws IOException {
        Path mdx = tempDir.resolve("incomplete.mdx");
        Files.writeString(mdx, """
            ---
            translations:
              en:
                title: No category or slug
            ---
            """);

        assertThat(indexerService.parseMdx(mdx)).isEmpty();
    }

    @Test
    void parseMdx_missingTranslationsBlock_returnsEmpty() throws IOException {
        Path mdx = tempDir.resolve("no-translations.mdx");
        Files.writeString(mdx, """
            ---
            category: test-cat
            slug: my-meme
            ---
            """);

        assertThat(indexerService.parseMdx(mdx)).isEmpty();
    }

    @Test
    void parseMdx_missingDefaultLocaleEntry_returnsEmpty() throws IOException {
        Path mdx = tempDir.resolve("missing-default.mdx");
        Files.writeString(mdx, """
            ---
            category: test-cat
            slug: my-meme
            default_locale: es
            translations:
              en:
                title: Only English here
            ---
            """);

        assertThat(indexerService.parseMdx(mdx)).isEmpty();
    }

    @Test
    void parseMdx_unknownLocaleSkipped() throws IOException {
        Path mdx = tempDir.resolve("partial.mdx");
        Files.writeString(mdx, """
            ---
            category: test-cat
            slug: my-meme
            default_locale: en
            translations:
              en:
                title: English title
              jp:
                title: Tokyo title
            ---
            """);

        Optional<MemeUpsert> result = indexerService.parseMdx(mdx);

        assertThat(result).isPresent();
        assertThat(result.get().translations()).hasSize(1);
        assertThat(result.get().translations().get(0).locale()).isEqualTo("en");
    }

    @Test
    void parseMdx_badSlugRejected() throws IOException {
        Path mdx = tempDir.resolve("bad-slug.mdx");
        Files.writeString(mdx, """
            ---
            category: test-cat
            slug: "Bad Slug!"
            default_locale: en
            translations:
              en:
                title: Whatever
            ---
            """);

        assertThat(indexerService.parseMdx(mdx)).isEmpty();
    }

    @Test
    void parseMdx_noImagesBlock_defaultsToSlugJpg() throws IOException {
        Path categoryDir = tempDir.resolve("test-cat");
        Files.createDirectories(categoryDir);
        Path mdx = categoryDir.resolve("my-meme.mdx");
        Files.writeString(mdx, """
            ---
            category: test-cat
            slug: my-meme
            default_locale: en
            translations:
              en:
                title: My Meme
            ---
            """);

        Optional<MemeUpsert> result = indexerService.parseMdx(mdx);

        assertThat(result).isPresent();
        List<MemeImageRow> images = result.get().images();
        assertThat(images).hasSize(1);
        assertThat(images.get(0).path()).isEqualTo("memes/test-cat/my-meme.jpg");
        assertThat(images.get(0).isPrimary()).isTrue();
    }

    @Test
    void reindex_callsRefreshStatsAndInvalidatesCaches() throws IOException {
        Path cat1 = tempDir.resolve("argentina-football");
        Files.createDirectories(cat1);
        writeMinimalMdx(cat1.resolve("meme1.mdx"), "argentina-football", "meme1");
        writeMinimalMdx(cat1.resolve("meme2.mdx"), "argentina-football", "meme2");
        when(memeRepository.upsertAll(any())).thenReturn(2);

        IndexResult result = indexerService.reindex();

        assertThat(result.indexed()).isEqualTo(2);
        assertThat(result.errors()).isEmpty();
        verify(memeRepository).refreshStats();
        verify(cacheManager, atLeast(5)).getCache(anyString());
        verify(cache, atLeast(5)).clear();
    }

    @Test
    void reindexAsync_withNullBody_dispatchesFullReindex() {
        when(memeRepository.upsertAll(any())).thenReturn(0);

        indexerService.reindexAsync(null);

        verify(memeRepository).upsertAll(any());
        verify(memeRepository).refreshStats();
    }

    @Test
    void reindexAsync_withSlug_dispatchesSingleIndex() {
        MemeIndexRequest req = new MemeIndexRequest();
        req.setSlug("test-slug");
        req.setCategory("test-cat");
        req.setDefaultLocale(LocaleCode.EN);
        MemeTranslation t = new MemeTranslation();
        t.setLocale(LocaleCode.EN);
        t.setTitle("Hello");
        req.setTranslations(List.of(t));

        indexerService.reindexAsync(req);

        verify(memeRepository).upsertMeme(any());
        verify(memeRepository).refreshStats();
    }

    @Test
    void indexSingle_rejectsRequestWithoutTranslations() {
        MemeIndexRequest req = new MemeIndexRequest();
        req.setSlug("test-slug");
        req.setCategory("test-cat");
        req.setDefaultLocale(LocaleCode.EN);

        assertThatThrownBy(() -> indexerService.indexSingle(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("translations");
    }

    private void writeMinimalMdx(Path path, String category, String slug) throws IOException {
        Files.writeString(path, String.format("""
            ---
            category: %s
            slug: %s
            default_locale: en
            translations:
              en:
                title: Test Meme
            ---
            """, category, slug));
    }
}
