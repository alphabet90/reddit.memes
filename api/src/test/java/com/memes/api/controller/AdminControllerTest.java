package com.memes.api.controller;

import com.memes.api.config.LocaleCodeConverter;
import com.memes.api.config.LoggingProperties;
import com.memes.api.filter.ApiKeyAuthFilter;
import com.memes.api.service.IndexerService;
import com.memes.api.service.MemeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
@Import({MemesApiDelegateImpl.class, AdminApiDelegateImpl.class, ApiKeyAuthFilter.class,
    LoggingProperties.class, LocaleCodeConverter.class})
@TestPropertySource(properties = {
    "memes.admin-api-key=test-secret",
    "spring.cache.type=none"
})
class AdminControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean IndexerService indexerService;
    @MockBean MemeService memeService;

    @Test
    void reindex_withoutKey_returns401() throws Exception {
        mockMvc.perform(post("/admin/reindex"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void reindex_withWrongKey_returns401() throws Exception {
        mockMvc.perform(post("/admin/reindex")
                .header("X-Api-Key", "wrong-key"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void reindex_withCorrectKey_returns200WithAccepted() throws Exception {
        doNothing().when(indexerService).reindexAsync(any());

        mockMvc.perform(post("/admin/reindex")
                .header("X-Api-Key", "test-secret"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("accepted"));
    }
}
