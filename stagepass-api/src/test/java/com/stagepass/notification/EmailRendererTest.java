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
    void organizerRejectedEscapesReason() {
        RenderedEmail email = renderer.render(EmailTemplate.ORGANIZER_REJECTED,
                Map.of("orgName", "Acme", "reason", "<b>PAN mismatch</b>"));

        assertThat(email.subject()).isEqualTo("Update on your Stagepass organizer application");
        assertThat(email.html()).contains("&lt;b&gt;PAN mismatch&lt;/b&gt;").contains("https://stagepass.example/become-organizer");
    }

    @Test
    void organizerApprovedLinksToDashboard() {
        RenderedEmail email = renderer.render(EmailTemplate.ORGANIZER_APPROVED, Map.of("orgName", "Acme"));

        assertThat(email.subject()).isEqualTo("You’re approved to sell on Stagepass");
        assertThat(email.html()).contains("https://stagepass.example/organizer");
    }

    @Test
    void eventApprovedSubjectHasTitle() {
        RenderedEmail email = renderer.render(EmailTemplate.EVENT_APPROVED,
                Map.of("title", "Sunburn Goa", "slug", "sunburn-goa-abc123"));

        assertThat(email.subject()).isEqualTo("Your event is live: Sunburn Goa");
        assertThat(email.html()).contains("https://stagepass.example/events/sunburn-goa-abc123");
    }

    @Test
    void eventRejectedIncludesReasonAndEditLink() {
        RenderedEmail email = renderer.render(EmailTemplate.EVENT_REJECTED,
                Map.of("title", "Sunburn Goa", "reason", "Add venue photos", "eventId", "42"));

        assertThat(email.subject()).isEqualTo("Changes needed for Sunburn Goa");
        assertThat(email.text()).contains("Add venue photos").contains("https://stagepass.example/organizer/events/42/review");
    }

    @Test
    void missingNameFallsBackToFriendlyGreeting() {
        RenderedEmail email = renderer.render(EmailTemplate.WELCOME, Map.of());

        assertThat(email.subject()).isEqualTo("Welcome to Stagepass");
        assertThat(email.html()).contains("Hi there");
    }
}
