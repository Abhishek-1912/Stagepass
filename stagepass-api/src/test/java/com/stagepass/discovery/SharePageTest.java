package com.stagepass.discovery;

import com.stagepass.support.CatalogFixtures;
import com.stagepass.support.PostgresIntegrationTest;
import com.stagepass.support.TestUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SharePageTest extends PostgresIntegrationTest {

    @Autowired
    TestUsers users;

    @Autowired
    CatalogFixtures catalog;

    @Autowired
    JdbcTemplate jdbc;

    private String[] liveEvent(String title) {
        UUID id = catalog.publishedEvent(users.approvedOrganizer(), title, OffsetDateTime.now().plusDays(5), "Sharecity", 100);
        String slug = jdbc.queryForObject("select slug from events where id = ?", String.class, id);
        return new String[] {id.toString(), slug};
    }

    @Test
    void sharePageHasOpenGraphTagsAndRedirects() throws Exception {
        String slug = liveEvent("Sunburn Share Night")[1];

        String html = mvc.perform(get("/e/" + slug))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/html")))
                .andExpect(header().string("Cache-Control", containsString("max-age=300")))
                .andReturn().getResponse().getContentAsString();

        assertThat(html)
                .contains("<meta property=\"og:title\" content=\"Sunburn Share Night\">")
                .contains("og:description\" content=\"A great night out\"")
                .contains("https://res.cloudinary.com/demo/image/upload/c_fill,w_1200,h_630,f_jpg/stagepass/events/x/c.jpg")
                .contains("twitter:card\" content=\"summary_large_image\"")
                .contains("url=http://localhost:5173/events/" + slug + "\"")
                .contains("<link rel=\"canonical\" href=\"http://localhost:5173/events/" + slug + "\">");
    }

    @Test
    void titleAndSummaryAreEscaped() throws Exception {
        String[] e = liveEvent("Rock \"n\" <script>alert(1)</script> & Roll");
        jdbc.update("update events set summary = ? where id = ?::uuid", "\"><img src=x onerror=alert(2)>", e[0]);

        String html = mvc.perform(get("/e/" + e[1])).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContain("<script>alert(1)").doesNotContain("<img src=x")
                .contains("Rock &quot;n&quot; &lt;script&gt;alert(1)&lt;/script&gt; &amp; Roll");
    }

    @Test
    void unknownSlugIs404WithExploreLink() throws Exception {
        mvc.perform(get("/e/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("http://localhost:5173/explore")));
    }

    @Test
    void invalidSlugCharactersAre404() throws Exception {
        mvc.perform(get(URI.create("/e/%3Cscript%3E"))).andExpect(status().isNotFound());
        mvc.perform(get(URI.create("/e/UPPER_case"))).andExpect(status().isNotFound());
    }

    @Test
    void draftEventSharePageIs404() throws Exception {
        String[] e = liveEvent("Secret draft");
        catalog.setStatus(UUID.fromString(e[0]), "DRAFT");
        mvc.perform(get("/e/" + e[1])).andExpect(status().isNotFound());
    }
}
