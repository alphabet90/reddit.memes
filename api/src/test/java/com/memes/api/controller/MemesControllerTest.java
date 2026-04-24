package com.memes.api.controller;

import com.memes.api.filter.ApiKeyAuthFilter;
import com.memes.api.generated.model.CategorySummary;
import com.memes.api.generated.model.Meme;
import com.memes.api.generated.model.MemePage;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@Import({MemesApiDelegateImpl.class, AdminApiDelegateImpl.class, ApiKeyAuthFilter.class})
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
        when(memeService.listCategories()).thenReturn(List.of(cs));

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
        when(memeService.listMemes(anyInt(), anyInt(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/memes?page=0&limit=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.limit").value(20));
    }

    @Test
    void getMeme_returns404_whenNotFound() throws Exception {
        when(memeService.getMeme(anyString(), anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/memes/unknown-category/unknown-slug"))
            .andExpect(status().isNotFound());
    }

    @Test
    void getMeme_returns200_whenFound() throws Exception {
        Meme meme = new Meme();
        meme.setSlug("cat-world-cup");
        meme.setCategory("argentina-football");
        meme.setTitle("Cat at the World Cup");
        when(memeService.getMeme("argentina-football", "cat-world-cup")).thenReturn(Optional.of(meme));

        mockMvc.perform(get("/memes/argentina-football/cat-world-cup"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slug").value("cat-world-cup"))
            .andExpect(jsonPath("$.category").value("argentina-football"));
    }

    @Test
    void searchMemes_returns200() throws Exception {
        MemePage page = new MemePage();
        page.setData(List.of());
        page.setTotal(0);
        page.setTotalPages(0);
        when(memeService.search(anyString(), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/search?q=jubilado"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0));
    }
}
