package com.memes.api.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MemeRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static HikariDataSource dataSource;
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
    }

    @AfterAll
    static void tearDownDatabase() {
        dataSource.close();
    }

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE memes");
        repository = new MemeRepository(jdbc);
    }

    @Test
    void upsert_insertsRecord() {
        repository.upsert(sampleMeme("cat-world-cup", "argentina-football"));

        assertThat(repository.countAll()).isEqualTo(1);
    }

    @Test
    void upsert_isIdempotent() {
        MemeRecord meme = sampleMeme("cat-world-cup", "argentina-football");
        repository.upsert(meme);
        repository.upsert(meme);

        assertThat(repository.countAll()).isEqualTo(1);
    }

    @Test
    void upsert_updatesExistingRecord() {
        repository.upsert(sampleMeme("cat-world-cup", "argentina-football"));

        MemeRecord updated = new MemeRecord(
            "cat-world-cup", "argentina-football", "Updated Title",
            null, null, "argentina", 9999, null, null, null, null, List.of()
        );
        repository.upsert(updated);

        Optional<MemeRecord> result = repository.findBySlugAndCategory("cat-world-cup", "argentina-football");
        assertThat(result).isPresent();
        assertThat(result.get().title()).isEqualTo("Updated Title");
        assertThat(result.get().score()).isEqualTo(9999);
        assertThat(repository.countAll()).isEqualTo(1);
    }

    @Test
    void findBySlugAndCategory_returnsEmpty_whenNotFound() {
        Optional<MemeRecord> result = repository.findBySlugAndCategory("nonexistent", "category");
        assertThat(result).isEmpty();
    }

    @Test
    void findAll_respectsPagination() {
        for (int i = 0; i < 5; i++) {
            repository.upsert(sampleMeme("meme-" + i, "test-cat"));
        }

        List<MemeRecord> page0 = repository.findAll(0, 3, null, null, "score");
        List<MemeRecord> page1 = repository.findAll(3, 3, null, null, "score");

        assertThat(page0).hasSize(3);
        assertThat(page1).hasSize(2);
    }

    @Test
    void findAll_filtersByCategory() {
        repository.upsert(sampleMeme("meme-1", "argentina-football"));
        repository.upsert(sampleMeme("meme-2", "cursed-emoji"));

        List<MemeRecord> results = repository.findAll(0, 10, "argentina-football", null, "score");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).slug()).isEqualTo("meme-1");
    }

    @Test
    void upsertAll_insertsMultiple() {
        List<MemeRecord> memes = List.of(
            sampleMeme("meme-1", "cat-a"),
            sampleMeme("meme-2", "cat-b"),
            sampleMeme("meme-3", "cat-a")
        );

        int count = repository.upsertAll(memes);

        assertThat(repository.countAll()).isEqualTo(3);
        assertThat(repository.countCategories()).isEqualTo(2);
    }

    @Test
    void countFiltered_withCategoryFilter() {
        repository.upsert(sampleMeme("m1", "football"));
        repository.upsert(sampleMeme("m2", "football"));
        repository.upsert(sampleMeme("m3", "humor"));

        assertThat(repository.countFiltered("football", null)).isEqualTo(2);
        assertThat(repository.countFiltered("humor", null)).isEqualTo(1);
        assertThat(repository.countFiltered(null, null)).isEqualTo(3);
    }

    @Test
    void search_findsByTitle() {
        repository.upsert(new MemeRecord(
            "cat-world-cup", "argentina-football",
            "Cat at the World Cup", "A cat in a jersey",
            "user1", "argentina", 2840,
            null, null, null, null, List.of("argentina")
        ));
        repository.upsert(sampleMeme("unrelated", "other-cat"));

        List<MemeRecord> results = repository.search("Cat World Cup", 0, 10);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).slug()).isEqualTo("cat-world-cup");
    }

    private MemeRecord sampleMeme(String slug, String category) {
        return new MemeRecord(
            slug, category, "Test Meme " + slug, "A test meme",
            "testuser", "argentina", 100,
            "2025-01-01T00:00:00Z",
            "https://example.com/img.jpg",
            "https://reddit.com/r/argentina/comments/test",
            "memes/" + category + "/" + slug + ".jpg",
            List.of("argentina", category)
        );
    }
}
