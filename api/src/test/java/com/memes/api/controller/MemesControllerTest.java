package com.memes.api.controller;

import com.memes.api.config.LocaleCodeConverter;
import com.memes.api.config.LoggingProperties;
import com.memes.api.filter.ApiKeyAuthFilter;
import com.memes.api.generated.model.CategorySummary;
import com.memes.api.generated.model.LocaleCode;
import com.memes.api.generated.model.Meme;
import com.memes.api.generated.model.MemeImage;
import com.memes.api.generated.model.MemePage;
import com.memes.api.generated.model.MemeTranslation;
import com.memes.api.generated.model.SearchResult;
import com.memes.api.generated.model.Stats;
import com.memes.api.service.IndexerService;
import com.memes.api.service.MemeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import({MemesApiDelegateImpl.class, AdminApiDelegateImpl.class, ApiKeyAuthFilter.class,
    LoggingProperties.class, LocaleCodeConverter.class})
@TestPropertySource(properties = {
    "memes.admin-api-key=test-secret",
    "spring.cache.type=none"
})
class MemesControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean MemeService memeService;
    @MockBean IndexerService indexerService;

    @Test
    void getStats_returns200() throws Exception {
        Stats stats = new Stats();
        stats.setTotalMemes(100);
        stats.setTotalCategories(10);
        when(memeService.getStats()).thenReturn(stats);

        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total_memes").value(100))
            .andExpect(jsonPath("$.total_categories").value(10));
    }

    @Test
    void listCategories_returns200() throws Exception {
        CategorySummary cs = new CategorySummary();
        cs.setCategory("argentina-football");
        cs.setCount(45);
        cs.setTopScore(5200);
        when(memeService.listCategories(anyString())).thenReturn(List.of(cs));

        mockMvc.perform(get("/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].category").value("argentina-football"))
            .andExpect(jsonPath("$[0].count").value(45));
    }

    @Test
    void listMemes_returns200WithPagination() throws Exception {
        MemePage page = new MemePage();
        page.setData(List.of());
        page.setPage(0);
        page.setLimit(20);
        page.setTotal(0);
        page.setTotalPages(0);
        when(memeService.listMemes(anyInt(), anyInt(), any(), any(), anyString(), anyString())).thenReturn(page);

        mockMvc.perform(get("/memes?page=0&limit=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.limit").value(20));
    }

    @Test
    void getMeme_returns404_whenNotFound() throws Exception {
        when(memeService.getMeme(anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/memes/unknown-category/unknown-slug"))
            .andExpect(status().isNotFound());
    }

    @Test
    void getMeme_returns200_withTranslationsAndImages() throws Exception {
        Meme meme = new Meme();
        meme.setSlug("cat-world-cup");
        meme.setCategory("argentina-football");
        meme.setDefaultLocale(LocaleCode.EN);
        MemeTranslation t = new MemeTranslation();
        t.setLocale(LocaleCode.EN);
        t.setTitle("Cat at the World Cup");
        meme.setTranslations(List.of(t));
        MemeImage img = new MemeImage();
        img.setPath("memes/argentina-football/cat-world-cup.jpg");
        img.setPosition(0);
        img.setIsPrimary(true);
        meme.setImages(List.of(img));
        when(memeService.getMeme("argentina-football", "cat-world-cup", "en"))
            .thenReturn(Optional.of(meme));

        mockMvc.perform(get("/memes/argentina-football/cat-world-cup"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slug").value("cat-world-cup"))
            .andExpect(jsonPath("$.category").value("argentina-football"))
            .andExpect(jsonPath("$.translations[0].title").value("Cat at the World Cup"))
            .andExpect(jsonPath("$.images[0].is_primary").value(true));
    }

    @Test
    void searchMemes_returns200_withLocaleResolvedFields() throws Exception {
        SearchResult sr = new SearchResult();
        sr.setSlug("afip-peeking-over-wall-crying");
        sr.setCategory("argentina-afip");
        sr.setAuthor("Mati4s_rp");
        sr.setScore(3035);
        sr.setTitle("AFIP Tax Man Cries While Peeking Over Wall");
        sr.setDescription("A figure wearing an AFIP cap peers over a brick wall with tears");
        sr.setPath("memes/argentina-afip/afip-peeking-over-wall-crying.jpg");
        sr.setTags(List.of("argentina", "argentina-afip"));
        when(memeService.search(anyString(), anyInt(), anyInt(), anyString())).thenReturn(List.of(sr));

        mockMvc.perform(get("/search?q=afip"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].slug").value("afip-peeking-over-wall-crying"))
            .andExpect(jsonPath("$[0].category").value("argentina-afip"))
            .andExpect(jsonPath("$[0].author").value("Mati4s_rp"))
            .andExpect(jsonPath("$[0].score").value(3035))
            .andExpect(jsonPath("$[0].title").value("AFIP Tax Man Cries While Peeking Over Wall"))
            .andExpect(jsonPath("$[0].path").value("memes/argentina-afip/afip-peeking-over-wall-crying.jpg"))
            .andExpect(jsonPath("$[0].tags[0]").value("argentina"))
            .andExpect(jsonPath("$[0].tags[1]").value("argentina-afip"));
    }

    @Test
    void searchMemes_defaultsToEnLocale() throws Exception {
        when(memeService.search(anyString(), anyInt(), anyInt(), anyString())).thenReturn(List.of());

        mockMvc.perform(get("/search?q=jubilado"))
            .andExpect(status().isOk());

        org.mockito.Mockito.verify(memeService).search(
            org.mockito.ArgumentMatchers.eq("jubilado"),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.eq("en")
        );
    }
}
