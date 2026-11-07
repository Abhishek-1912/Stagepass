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
    private static Map<String, Object> booking(Map<String, Object> extra) {
        Map<String, Object> m = new java.util.HashMap<>(Map.of("bookingId", "b-42", "bookingCode", "SP-ABC234",
                "title", "Sunburn Goa", "slug", "sunburn-goa", "startsAt", "2026-11-14T13:00:00Z",
                "venue", "Vagator Beach, Goa", "attendeeName", "Asha Rao"));
        m.putAll(extra);
        return m;
    }

    @Test
    void bookingConfirmedListsEveryTicket() {
        List<Map<String, String>> tickets = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tickets.add(Map.of("code", "SP-AAAA-" + (2000 + i), "type", "General"));
        }
        RenderedEmail email = renderer.render(EmailTemplate.BOOKING_CONFIRMED,
                booking(Map.of("tickets", tickets, "totalPaise", 105104, "invoiceNumber", "SP/2026-27/000042")));

        assertThat(email.subject()).isEqualTo("You're going to Sunburn Goa");
        for (int i = 0; i < 20; i++) {
            assertThat(email.html()).contains("SP-AAAA-" + (2000 + i));
            assertThat(email.text()).contains("SP-AAAA-" + (2000 + i));
        }
        assertThat(email.text()).contains("Sat, 14 Nov 2026 · 6:30 PM").contains("SP-ABC234").contains("1,051.04")
                .contains("https://stagepass.example/account/bookings/b-42");
    }

    @Test
    void refundProcessedSaysWhetherFeeCameBack() {
        RenderedEmail kept = renderer.render(EmailTemplate.REFUND_PROCESSED,
                booking(Map.of("amountPaise", 49900, "includesFee", false, "ticketCount", 1)));
        assertThat(kept.subject()).isEqualTo("Refund of Rs. 499.00 processed");
        assertThat(kept.text()).contains("convenience fee");

        RenderedEmail free = renderer.render(EmailTemplate.REFUND_PROCESSED,
                booking(Map.of("amountPaise", 0, "includesFee", false, "ticketCount", 2)));
        assertThat(free.subject()).isEqualTo("Your free tickets were cancelled");
    }

    @Test
    void otherBookingEmailSubjects() {
        assertThat(renderer.render(EmailTemplate.BOOKING_EXPIRED, booking(Map.of())).subject())
                .isEqualTo("Your checkout for Sunburn Goa expired");
        assertThat(renderer.render(EmailTemplate.REFUND_DECLINED, booking(Map.of("note", "Too close to the show"))))
                .satisfies(e -> {
                    assertThat(e.subject()).isEqualTo("Update on your refund request");
                    assertThat(e.text()).contains("Too close to the show");
                });
        RenderedEmail cancelled = renderer.render(EmailTemplate.EVENT_CANCELLED,
                booking(Map.of("refunded", true, "amountPaise", 105104)));
        assertThat(cancelled.subject()).isEqualTo("Sunburn Goa has been cancelled");
        assertThat(cancelled.text()).contains("1,051.04").contains("5-7 working days");
    }

    @Test
    void eventChangedShowsRefundDeadline() {
        RenderedEmail email = renderer.render(EmailTemplate.EVENT_CHANGED,
                booking(Map.of("refundUntil", "2026-11-13T12:00:00Z")));
        assertThat(email.subject()).isEqualTo("Sunburn Goa has changed");
        assertThat(email.text()).contains("13 Nov 2026").contains("full refund");
    }

    @Test
    void payoutRecordedNamesAmountAndReference() {
        RenderedEmail email = renderer.render(EmailTemplate.PAYOUT_RECORDED,
                Map.of("orgName", "Acme <Events>", "amountPaise", 1234500L, "reference", "UTR998877", "paidOn", "2026-12-03"));

        assertThat(email.subject()).isEqualTo("Stagepass payout of Rs. 12,345.00 sent");
        assertThat(email.text()).contains("UTR998877").contains("03 Dec 2026");
        assertThat(email.html()).contains("Acme &lt;Events&gt;").contains("https://stagepass.example/organizer/payouts");
    }

    @Test
    void reminderSaysTomorrowOrToday() {
        RenderedEmail email = renderer.render(EmailTemplate.EVENT_REMINDER, booking(Map.of("day", "Tomorrow")));
        assertThat(email.subject()).startsWith("Tomorrow: ");
        assertThat(email.html()).contains("/account/bookings/");
        RenderedEmail today = renderer.render(EmailTemplate.EVENT_REMINDER, booking(Map.of("day", "Today")));
        assertThat(today.subject()).startsWith("Today: ");
    }

    @Test
    void templatesEscapeHtml() {
        RenderedEmail email = renderer.render(EmailTemplate.BOOKING_EXPIRED, booking(Map.of("title", "<b>x</b>")));
        assertThat(email.html()).contains("&lt;b&gt;x&lt;/b&gt;").doesNotContain("<b>x</b>");
    }

    @Test
    void expiredEmailDoesNotPromiseNoCharge() {
        RenderedEmail email = renderer.render(EmailTemplate.BOOKING_EXPIRED, booking(Map.of()));
        assertThat(email.text()).doesNotContain("No money was taken")
                .contains("If a payment did go through, we'll confirm your tickets or refund you automatically");
    }
}
