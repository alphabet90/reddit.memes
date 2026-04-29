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

    // ===== V2 format ==========================================================

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
    void parseMdx_missingTranslationsBlockAndNoTitle_returnsEmpty() throws IOException {
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

    // ===== Flat format ========================================================

    @Test
    void parseMdx_flatFormatBaseFile_treatedAsEnglish() throws IOException {
        Path categoryDir = tempDir.resolve("reaction");
        Files.createDirectories(categoryDir);
        Path mdx = categoryDir.resolve("homer-bar.mdx");
        Files.writeString(mdx, """
            ---
            title: "Homer Walks Into Bar"
            description: "Homer Simpson enters a bar looking confused"
            author: redditor1
            subreddit: argentina
            category: reaction
            slug: homer-bar
            score: 500
            created_at: "2025-01-01T00:00:00Z"
            source_url: https://i.redd.it/homer.jpg
            post_url: https://reddit.com/r/argentina/comments/abc
            image: "./homer-bar.jpg"
            tags: [simpsons, reaction]
            ---

            # Homer Walks Into Bar
            """);

        Optional<MemeUpsert> result = indexerService.parseMdx(mdx);

        assertThat(result).isPresent();
        MemeUpsert u = result.get();
        assertThat(u.slug()).isEqualTo("homer-bar");
        assertThat(u.defaultLocale()).isEqualTo("en");
        assertThat(u.translations()).hasSize(1);
        assertThat(u.translations().get(0).locale()).isEqualTo("en");
        assertThat(u.translations().get(0).title()).isEqualTo("Homer Walks Into Bar");
        assertThat(u.images().get(0).path()).isEqualTo("memes/reaction/homer-bar.jpg");
        assertThat(u.tagSlugs()).containsExactlyInAnyOrder("simpsons", "reaction");
    }

    @Test
    void parseMdx_flatFormatLocaleFile_extractsLocaleFromFilename() throws IOException {
        Path categoryDir = tempDir.resolve("reaction");
        Files.createDirectories(categoryDir);
        Path mdx = categoryDir.resolve("homer-bar.es-AR.mdx");
        Files.writeString(mdx, """
            ---
            title: "Homer Entra Al Bar"
            description: "Homer Simpson entra a un bar con cara de confundido"
            author: redditor1
            subreddit: argentina
            category: reaction
            slug: homer-bar
            score: 500
            created_at: "2025-01-01T00:00:00Z"
            source_url: https://i.redd.it/homer.jpg
            post_url: https://reddit.com/r/argentina/comments/abc
            image: "./homer-bar.jpg"
            tags: [simpsons, reaccion]
            ---
            """);

        Optional<MemeUpsert> result = indexerService.parseMdx(mdx);

        assertThat(result).isPresent();
        MemeUpsert u = result.get();
        assertThat(u.defaultLocale()).isEqualTo("es");
        assertThat(u.translations()).hasSize(1);
        assertThat(u.translations().get(0).locale()).isEqualTo("es");
        assertThat(u.translations().get(0).title()).isEqualTo("Homer Entra Al Bar");
        // Image path must use the base stem, not the locale stem
        assertThat(u.images().get(0).path()).isEqualTo("memes/reaction/homer-bar.jpg");
    }

    @Test
    void parseMdx_flatFormatLocaleFile_ptBR_normalizedToPt() throws IOException {
        Path categoryDir = tempDir.resolve("humor");
        Files.createDirectories(categoryDir);
        Path mdx = categoryDir.resolve("pepe-cry.pt-BR.mdx");
        Files.writeString(mdx, """
            ---
            title: "Pepe Chorando"
            category: humor
            slug: pepe-cry
            ---
            """);

        Optional<MemeUpsert> result = indexerService.parseMdx(mdx);

        assertThat(result).isPresent();
        assertThat(result.get().translations().get(0).locale()).isEqualTo("pt");
    }

    // ===== Locale grouping (scanMdxFiles / parseMdxGroup) ====================

    @Test
    void parseMdxGroup_basePlusLocale_mergesTranslations() throws IOException {
        Path categoryDir = tempDir.resolve("reaction");
        Files.createDirectories(categoryDir);

        Path baseMdx = categoryDir.resolve("homer-bar.mdx");
        Files.writeString(baseMdx, """
            ---
            title: "Homer Walks Into Bar"
            description: "Homer enters a bar"
            category: reaction
            slug: homer-bar
            score: 500
            image: "./homer-bar.jpg"
            tags: [simpsons]
            ---
            """);

        Path esMdx = categoryDir.resolve("homer-bar.es-AR.mdx");
        Files.writeString(esMdx, """
            ---
            title: "Homer Entra Al Bar"
            description: "Homer entra a un bar"
            category: reaction
            slug: homer-bar
            score: 500
            image: "./homer-bar.jpg"
            tags: [simpsons, reaccion]
            ---
            """);

        Path baseKey = categoryDir.resolve("homer-bar.mdx");
        Optional<MemeUpsert> result = indexerService.parseMdxGroup(baseKey, List.of(baseMdx, esMdx));

        assertThat(result).isPresent();
        MemeUpsert u = result.get();
        assertThat(u.slug()).isEqualTo("homer-bar");
        assertThat(u.defaultLocale()).isEqualTo("en");
        assertThat(u.translations()).extracting(MemeTranslationRow::locale)
            .containsExactlyInAnyOrder("en", "es");
        assertThat(u.images()).hasSize(1);
        assertThat(u.images().get(0).path()).isEqualTo("memes/reaction/homer-bar.jpg");
    }

    @Test
    void parseMdxGroup_localeOnlyNoBase_parsesSuccessfully() throws IOException {
        Path categoryDir = tempDir.resolve("reaction");
        Files.createDirectories(categoryDir);

        Path esMdx = categoryDir.resolve("homer-bar.es-AR.mdx");
        Files.writeString(esMdx, """
            ---
            title: "Homer Entra Al Bar"
            category: reaction
            slug: homer-bar
            ---
            """);

        Path baseKey = categoryDir.resolve("homer-bar.mdx");
        Optional<MemeUpsert> result = indexerService.parseMdxGroup(baseKey, List.of(esMdx));

        assertThat(result).isPresent();
        assertThat(result.get().translations().get(0).locale()).isEqualTo("es");
    }

    @Test
    void scanMdxFiles_groupsLocaleFilesWithBase_producesOneMemePerSlug() throws IOException {
        Path categoryDir = tempDir.resolve("reaction");
        Files.createDirectories(categoryDir);

        writeMinimalMdx(categoryDir.resolve("meme1.mdx"), "reaction", "meme1");
        // Locale sidecar — must be merged into meme1, not indexed as a separate meme
        Path localeMdx = categoryDir.resolve("meme1.es-AR.mdx");
        Files.writeString(localeMdx, """
            ---
            title: "Meme Uno"
            category: reaction
            slug: meme1
            ---
            """);

        when(memeRepository.upsertAll(any())).thenReturn(1);
        IndexResult result = indexerService.reindex();

        assertThat(result.indexed()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();
    }

    // ===== normalizeLocale helper =============================================

    @Test
    void normalizeLocale_regionalVariant_stripsRegion() {
        assertThat(IndexerService.normalizeLocale("es-AR")).isEqualTo("es");
        assertThat(IndexerService.normalizeLocale("pt-BR")).isEqualTo("pt");
        assertThat(IndexerService.normalizeLocale("en-US")).isEqualTo("en");
    }

    @Test
    void normalizeLocale_unknownLanguage_fallsBackToEn() {
        assertThat(IndexerService.normalizeLocale("jp")).isEqualTo("en");
        assertThat(IndexerService.normalizeLocale(null)).isEqualTo("en");
    }

    @Test
    void extractLocaleFromFilename_localeFile_returnsLocale() {
        assertThat(IndexerService.extractLocaleFromFilename("slug.es-AR.mdx"))
            .hasValue("es-AR");
        assertThat(IndexerService.extractLocaleFromFilename("slug.pt.mdx"))
            .hasValue("pt");
    }

    @Test
    void extractLocaleFromFilename_baseFile_returnsEmpty() {
        assertThat(IndexerService.extractLocaleFromFilename("slug.mdx")).isEmpty();
        assertThat(IndexerService.extractLocaleFromFilename("some-meme.mdx")).isEmpty();
    }

    // ===== reindex / async ====================================================

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

    // ===== helpers ============================================================

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
