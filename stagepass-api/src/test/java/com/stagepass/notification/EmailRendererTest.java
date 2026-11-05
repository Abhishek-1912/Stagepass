package com.stagepass.notification;

import com.stagepass.common.config.StagepassProperties;
import com.stagepass.notification.service.EmailRenderer;
import com.stagepass.notification.service.EmailTemplate;
import com.stagepass.notification.service.RenderedEmail;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmailRendererTest {

    private final EmailRenderer renderer = new EmailRenderer(new StagepassProperties("https://stagepass.example",
            new StagepassProperties.Cors(List.of()), new StagepassProperties.Bootstrap(List.of()), 10, "q", "k",
            new StagepassProperties.Payout(3)));

    @Test
    void welcomeEmailGreetsByNameAndLinksToSite() {
        RenderedEmail email = renderer.render(EmailTemplate.WELCOME, Map.of("name", "Asha"));

        assertThat(email.subject()).isEqualTo("Welcome to Stagepass, Asha");
        assertThat(email.html()).contains("Hi Asha").contains("https://stagepass.example/explore");
        assertThat(email.text()).contains("Hi Asha").contains("https://stagepass.example/explore");
    }

    @Test
    void userSuppliedNameIsHtmlEscaped() {
        RenderedEmail email = renderer.render(EmailTemplate.WELCOME, Map.of("name", "<script>alert(1)</script>"));

        assertThat(email.html()).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    void missingNameFallsBackToFriendlyGreeting() {
        RenderedEmail email = renderer.render(EmailTemplate.WELCOME, Map.of());

        assertThat(email.subject()).isEqualTo("Welcome to Stagepass");
        assertThat(email.html()).contains("Hi there");
    }
}
