package com.memes.api.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class MemeRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbc;
    private MemeRepository repository;

    @BeforeAll
    static void setupDatabase() {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(postgres.getJdbcUrl());
        cfg.setUsername(postgres.getUsername());
        cfg.setPassword(postgres.getPassword());
        cfg.setMaximumPoolSize(5);
        dataSource = new HikariDataSource(cfg);

        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();

        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterAll
    static void tearDownDatabase() {
        dataSource.close();
    }

    @BeforeEach
    void setUp() {
        // Wipe in dependency order; restart identities so tests can predict ids if needed.
        jdbc.execute("""
            TRUNCATE meme_tags, meme_images, meme_translations, memes,
                     category_translations, tag_translations,
                     categories, subreddits, authors, tags
            RESTART IDENTITY CASCADE
            """);
        // Wrap MemeRepository in a Spring transaction template so @Transactional works.
        PlatformTransactionManager txMgr = new DataSourceTransactionManager(dataSource);
        repository = new TransactionalMemeRepository(jdbc, new TransactionTemplate(txMgr));
    }

    @Test
    void upsertMeme_insertsRecord() {
        long id = repository.upsertMeme(sample("cat-world-cup", "argentina-football"));

        assertThat(id).isPositive();
        Optional<MemeRow> found = repository.findBySlugAndCategory("cat-world-cup", "argentina-football");
        assertThat(found).isPresent();
        assertThat(found.get().translations()).hasSize(1);
        assertThat(found.get().translations().get(0).title()).startsWith("Test Meme ");
        assertThat(found.get().images()).hasSize(1);
        assertThat(found.get().images().get(0).isPrimary()).isTrue();
    }

    @Test
    void upsertMeme_isIdempotent() {
        long id1 = repository.upsertMeme(sample("cat-world-cup", "argentina-football"));
        long id2 = repository.upsertMeme(sample("cat-world-cup", "argentina-football"));
        assertThat(id1).isEqualTo(id2);

        repository.refreshStats();
        StatsRow stats = repository.findStats().orElseThrow();
        assertThat(stats.totalMemes()).isEqualTo(1);
    }

    @Test
    void upsertMeme_updatesTranslations() {
        repository.upsertMeme(sample("cat-world-cup", "argentina-football"));

        MemeUpsert updated = MemeUpsert.builder()
            .slug("cat-world-cup")
            .categorySlug("argentina-football")
            .defaultLocale("en")
            .subredditName("argentina")
            .score(9999)
            .translations(List.of(
                MemeTranslationRow.builder().locale("en").title("Updated Title").description("New").build(),
                MemeTranslationRow.builder().locale("es").title("Nuevo titulo").description(null).build()
            ))
            .images(List.of(MemeImageRow.builder().path("/img.jpg").position(0).isPrimary(true).build()))
            .tagSlugs(List.of("argentina"))
            .build();
        repository.upsertMeme(updated);

        MemeRow row = repository.findBySlugAndCategory("cat-world-cup", "argentina-football").orElseThrow();
        assertThat(row.score()).isEqualTo(9999);
        assertThat(row.translations()).extracting(MemeTranslationRow::locale)
            .containsExactlyInAnyOrder("en", "es");
        assertThat(row.translations()).filteredOn(t -> "en".equals(t.locale()))
            .extracting(MemeTranslationRow::title)
            .containsExactly("Updated Title");
    }

    @Test
    void findBySlugAndCategory_returnsEmpty_whenNotFound() {
        Optional<MemeRow> result = repository.findBySlugAndCategory("nonexistent", "category");
        assertThat(result).isEmpty();
    }

    @Test
    void findAll_respectsPaginationAndCategoryFilter() {
        for (int i = 0; i < 5; i++) {
            repository.upsertMeme(sample("meme-" + i, "test-cat"));
        }
        repository.upsertMeme(sample("other", "other-cat"));

        List<MemeRow> page0 = repository.findAll(0, 3, null, null, "score", "en");
        List<MemeRow> page1 = repository.findAll(3, 3, null, null, "score", "en");
        List<MemeRow> filtered = repository.findAll(0, 10, "test-cat", null, "score", "en");

        assertThat(page0).hasSize(3);
        assertThat(page1).hasSize(3);
        assertThat(filtered).hasSize(5);
        assertThat(filtered).extracting(MemeRow::categorySlug).containsOnly("test-cat");
    }

    @Test
    void countFiltered_matchesFindAll() {
        repository.upsertMeme(sample("m1", "football"));
        repository.upsertMeme(sample("m2", "football"));
        repository.upsertMeme(sample("m3", "humor"));

        assertThat(repository.countFiltered("football", null)).isEqualTo(2);
        assertThat(repository.countFiltered("humor", null)).isEqualTo(1);
        assertThat(repository.countFiltered(null, null)).isEqualTo(3);
    }

    @Test
    void search_findsByTitleInLocale() {
        repository.upsertMeme(MemeUpsert.builder()
            .slug("cat-world-cup")
            .categorySlug("argentina-football")
            .defaultLocale("en")
            .subredditName("argentina")
            .score(2840)
            .translations(List.of(
                MemeTranslationRow.builder().locale("en").title("Cat at the World Cup").description("A cat in a jersey").build(),
                MemeTranslationRow.builder().locale("es").title("Gato en el Mundial").description("Un gato con camiseta").build()
            ))
            .images(List.of(MemeImageRow.builder().path("/cat.jpg").position(0).isPrimary(true).build()))
            .tagSlugs(List.of("argentina"))
            .build());
        repository.upsertMeme(sample("unrelated", "other-cat"));

        List<MemeRepository.SearchHit> en = repository.search("Cat World Cup", "en", 10, 0);
        List<MemeRepository.SearchHit> es = repository.search("Mundial", "es", 10, 0);

        assertThat(en).hasSize(1);
        assertThat(en.get(0).meme().slug()).isEqualTo("cat-world-cup");
        assertThat(en.get(0).totalCount()).isEqualTo(1);

        assertThat(es).hasSize(1);
        assertThat(es.get(0).meme().slug()).isEqualTo("cat-world-cup");
    }

    @Test
    void refreshStats_populatesMaterializedView() {
        repository.upsertMeme(sample("m1", "cat-a"));
        repository.upsertMeme(sample("m2", "cat-b"));
        repository.refreshStats();

        StatsRow stats = repository.findStats().orElseThrow();
        assertThat(stats.totalMemes()).isEqualTo(2);
        assertThat(stats.totalCategories()).isEqualTo(2);
    }

    @Test
    void upsertMeme_rejectsBadSlug() {
        MemeUpsert bad = MemeUpsert.builder()
            .slug("Bad Slug!")
            .categorySlug("cat-a")
            .defaultLocale("en")
            .score(0)
            .translations(List.of(MemeTranslationRow.builder().locale("en").title("x").build()))
            .images(List.of())
            .tagSlugs(List.of())
            .build();

        assertThatThrownBy(() -> repository.upsertMeme(bad))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private MemeUpsert sample(String slug, String category) {
        return MemeUpsert.builder()
            .slug(slug)
            .categorySlug(category)
            .defaultLocale("en")
            .subredditName("argentina")
            .authorUsername("testuser")
            .score(100)
            .createdAt(OffsetDateTime.parse("2025-01-01T00:00:00Z"))
            .sourceUrl("https://example.com/img.jpg")
            .postUrl("https://reddit.com/r/argentina/comments/test")
            .translations(List.of(
                MemeTranslationRow.builder()
                    .locale("en")
                    .title("Test Meme " + slug)
                    .description("A test meme")
                    .build()
            ))
            .images(List.of(
                MemeImageRow.builder()
                    .path("memes/" + category + "/" + slug + ".jpg")
                    .position(0)
                    .isPrimary(true)
                    .build()
            ))
            .tagSlugs(List.of("argentina"))
            .build();
    }

    /**
     * Subclass that wraps every public method in a TransactionTemplate. The
     * production code relies on Spring AOP for {@code @Transactional}; in unit
     * tests we don't have an AOP proxy, so we manually start a transaction.
     */
    static class TransactionalMemeRepository extends MemeRepository {
        private final TransactionTemplate tx;

        TransactionalMemeRepository(JdbcTemplate jdbc, TransactionTemplate tx) {
            super(jdbc);
            this.tx = tx;
        }

        @Override
        public long upsertMeme(MemeUpsert u) {
            return tx.execute(s -> super.upsertMeme(u));
        }

        @Override
        public int upsertAll(List<MemeUpsert> memes) {
            return tx.execute(s -> super.upsertAll(memes));
        }
    }
}
