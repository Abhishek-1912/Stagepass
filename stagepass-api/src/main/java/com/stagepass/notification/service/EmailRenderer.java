package com.stagepass.notification.service;

import com.stagepass.common.config.StagepassProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.Map;

/**
 * Builds subject, HTML and plain-text bodies. HTML uses inline styles and tables so it renders in Gmail/Outlook.
 * Every value from the payload is HTML-escaped.
 */
@Component
public class EmailRenderer {

    private final String webBaseUrl;

    public EmailRenderer(StagepassProperties props) {
        this.webBaseUrl = props.webBaseUrl() == null ? "" : props.webBaseUrl().replaceAll("/+$", "");
    }

    public RenderedEmail render(EmailTemplate template, Map<String, ?> data) {
        return switch (template) {
            case WELCOME -> welcome(data);
            case ORGANIZER_APPROVED -> organizerApproved(data);
            case ORGANIZER_REJECTED -> organizerRejected(data);
            case EVENT_APPROVED -> eventApproved(data);
            case EVENT_REJECTED -> eventRejected(data);
            case BOOKING_CONFIRMED -> bookingConfirmed(data);
            case BOOKING_EXPIRED -> bookingExpired(data);
            case REFUND_PROCESSED -> refundProcessed(data);
            case REFUND_DECLINED -> refundDeclined(data);
            case EVENT_CANCELLED -> eventCancelled(data);
            case EVENT_CHANGED -> eventChanged(data);
        };
    }

    private RenderedEmail welcome(Map<String, ?> data) {
        String name = firstName(data.get("name"));
        String subject = name == null ? "Welcome to Stagepass" : "Welcome to Stagepass, " + name;
        String greeting = "Hi " + (name == null ? "there" : name);
        return build(subject, greeting,
                "Your Stagepass account is ready. Discover concerts, comedy, tech meetups and food festivals near you, "
                        + "book in seconds, and walk in with a QR ticket.",
                null, "Explore events", webBaseUrl + "/explore");
    }

    private RenderedEmail organizerApproved(Map<String, ?> data) {
        String org = str(data.get("orgName"), "your organization");
        return build("You’re approved to sell on Stagepass", "Great news",
                org + " is now an approved organizer on Stagepass. Create your first event, set ticket tiers, "
                        + "and submit it for review — we’ll publish it once it’s checked.",
                null, "Open organizer dashboard", webBaseUrl + "/organizer");
    }

    private RenderedEmail organizerRejected(Map<String, ?> data) {
        String org = str(data.get("orgName"), "your organization");
        return build("Update on your Stagepass organizer application", "Hello",
                "We couldn’t approve the organizer application for " + org + " yet. Here’s what needs to change:",
                str(data.get("reason"), "Please review your details."),
                "Update and reapply", webBaseUrl + "/become-organizer");
    }

    private RenderedEmail eventApproved(Map<String, ?> data) {
        String title = str(data.get("title"), "Your event");
        return build("Your event is live: " + title, "It’s live",
                title + " has been approved and is now on sale on Stagepass. Share the link to start selling tickets.",
                null, "View event page", webBaseUrl + "/events/" + str(data.get("slug"), ""));
    }

    private RenderedEmail eventRejected(Map<String, ?> data) {
        String title = str(data.get("title"), "your event");
        return build("Changes needed for " + title, "Almost there",
                "Our team reviewed " + title + " and needs a few changes before it can go live:",
                str(data.get("reason"), "Please review the event details."),
                "Edit event", webBaseUrl + "/organizer/events/" + str(data.get("eventId"), "") + "/review");
    }

    /** One greeting, one paragraph, an optional highlighted note, and a button. */
    private RenderedEmail build(String subject, String greeting, String paragraph, String note, String ctaLabel,
            String ctaUrl) {
        return build(subject, greeting, paragraph, note, null, ctaLabel, ctaUrl);
    }

    /** As above, plus an optional block of label/value rows (booking details) under the note. */
    private RenderedEmail build(String subject, String greeting, String paragraph, String note, Details details,
            String ctaLabel, String ctaUrl) {
        StringBuilder body = new StringBuilder()
                .append("<p style=\"margin:0 0 16px;font-size:16px;line-height:24px;color:#171923\">")
                .append(HtmlUtils.htmlEscape(greeting)).append(",</p>")
                .append("<p style=\"margin:0 0 16px;font-size:16px;line-height:24px;color:#3a3f51\">")
                .append(HtmlUtils.htmlEscape(paragraph)).append("</p>");
        if (note != null) {
            body.append("<div style=\"margin:0 0 16px;padding:14px 16px;border-radius:12px;background:#F0ECFE;"
                    + "color:#3D26B8;font-size:15px;line-height:22px\">").append(HtmlUtils.htmlEscape(note))
                    .append("</div>");
        }
        if (details != null) {
            body.append(details.html());
        }
        String text = greeting + ",\n\n" + paragraph + "\n" + (note == null ? "" : "\n" + note + "\n")
                + (details == null ? "" : "\n" + details.text())
                + "\n" + ctaLabel + ": " + ctaUrl + "\n\n— Team Stagepass\n";
        return new RenderedEmail(subject, layout(subject, body.toString(), ctaLabel, ctaUrl), text);
    }

    // ---------------------------------------------------------------- booking emails (Plan 5)

    private static final java.time.ZoneId IST = java.time.ZoneId.of("Asia/Kolkata");
    private static final java.time.format.DateTimeFormatter WHEN =
            java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM yyyy · h:mm a", java.util.Locale.ENGLISH);
    private static final java.time.format.DateTimeFormatter DAY =
            java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH);

    /** Label/value rows rendered as a small table (HTML) and aligned lines (text). */
    private record Details(java.util.List<String[]> rows) {

        String html() {
            StringBuilder h = new StringBuilder("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" "
                    + "style=\"width:100%;margin:0 0 8px;font-size:14px;line-height:20px;border-top:1px solid #E4E6EE\">");
            for (String[] r : rows) {
                h.append("<tr><td style=\"padding:8px 12px 8px 0;color:#5B6072;vertical-align:top;white-space:nowrap\">")
                        .append(HtmlUtils.htmlEscape(r[0])).append("</td><td style=\"padding:8px 0;color:#171923\">")
                        .append(HtmlUtils.htmlEscape(r[1]).replace("\n", "<br>")).append("</td></tr>");
            }
            return h.append("</table>").toString();
        }

        String text() {
            StringBuilder t = new StringBuilder();
            for (String[] r : rows) {
                t.append(r[0]).append(": ").append(r[1].replace("\n", "\n  ")).append('\n');
            }
            return t.toString();
        }
    }

    private Details eventDetails(Map<String, ?> data, String... extra) {
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        rows.add(new String[] {"Event", str(data.get("title"), "Your event")});
        String when = when(data.get("startsAt"));
        if (when != null) {
            rows.add(new String[] {"When", when});
        }
        rows.add(new String[] {"Where", str(data.get("venue"), "Online")});
        rows.add(new String[] {"Booking", str(data.get("bookingCode"), "")});
        for (int i = 0; i + 1 < extra.length; i += 2) {
            rows.add(new String[] {extra[i], extra[i + 1]});
        }
        return new Details(rows);
    }

    private String bookingUrl(Map<String, ?> data) {
        return webBaseUrl + "/account/bookings/" + str(data.get("bookingId"), "");
    }

    private RenderedEmail bookingConfirmed(Map<String, ?> data) {
        String title = str(data.get("title"), "your event");
        StringBuilder tickets = new StringBuilder();
        if (data.get("tickets") instanceof java.util.List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> t) {
                    tickets.append(tickets.isEmpty() ? "" : "\n").append(t.get("code")).append(" · ").append(t.get("type"));
                }
            }
        }
        long total = num(data.get("totalPaise"));
        String invoice = str(data.get("invoiceNumber"), null);
        java.util.List<String> extra = new java.util.ArrayList<>(java.util.List.of("Tickets", tickets.toString(),
                "Paid", total == 0 ? "Free" : rupees(total)));
        if (invoice != null) {
            extra.add("GST invoice");
            extra.add(invoice);
        }
        return build("You're going to " + title, "Hi " + str(firstName(data.get("attendeeName")), "there"),
                "Your booking is confirmed. Your tickets are attached as a PDF and are always in My tickets - show the "
                        + "QR code at the entrance.",
                null, eventDetails(data, extra.toArray(String[]::new)), "View my tickets", bookingUrl(data));
    }

    private RenderedEmail bookingExpired(Map<String, ?> data) {
        String title = str(data.get("title"), "your event");
        return build("Your checkout for " + title + " expired", "Hi " + str(firstName(data.get("attendeeName")), "there"),
                "We held your tickets for a few minutes, but the payment didn't complete, so they were released. "
                        + "No money was taken. If tickets are still available you can book again.",
                null, eventDetails(data), "Book again", webBaseUrl + "/events/" + str(data.get("slug"), ""));
    }

    private RenderedEmail refundProcessed(Map<String, ?> data) {
        long amount = num(data.get("amountPaise"));
        boolean fee = Boolean.TRUE.equals(data.get("includesFee"));
        long count = num(data.get("ticketCount"));
        String subject = amount == 0 ? "Your free tickets were cancelled" : "Refund of " + rupees(amount) + " processed";
        String paragraph = amount == 0
                ? "We've cancelled " + count + (count == 1 ? " ticket" : " tickets") + " as you asked."
                : "Your refund of " + rupees(amount) + " for " + count + (count == 1 ? " ticket" : " tickets")
                        + " has been processed. It usually reaches your account in 5-7 working days."
                        + (fee ? " The convenience fee was refunded too." : " The convenience fee is not refundable.");
        return build(subject, "Hi " + str(firstName(data.get("attendeeName")), "there"), paragraph, null,
                eventDetails(data), "View booking", bookingUrl(data));
    }

    private RenderedEmail refundDeclined(Map<String, ?> data) {
        String note = str(data.get("note"), null);
        return build("Update on your refund request", "Hi " + str(firstName(data.get("attendeeName")), "there"),
                "The organizer of " + str(data.get("title"), "the event") + " couldn't approve your refund request. "
                        + "Your tickets are still valid.",
                note == null ? null : "Organizer's note: " + note, eventDetails(data), "View booking", bookingUrl(data));
    }

    private RenderedEmail eventCancelled(Map<String, ?> data) {
        String title = str(data.get("title"), "Your event");
        long amount = num(data.get("amountPaise"));
        String paragraph = amount > 0
                ? "We're sorry - the organizer has cancelled " + title + ". We've refunded " + rupees(amount)
                        + " in full, including the convenience fee. It usually reaches your account in 5-7 working days."
                : "We're sorry - the organizer has cancelled " + title + ". Your booking has been cancelled"
                        + " and nothing was charged.";
        return build(title + " has been cancelled", "Hi " + str(firstName(data.get("attendeeName")), "there"), paragraph,
                null, eventDetails(data), "View booking", bookingUrl(data));
    }

    private RenderedEmail eventChanged(Map<String, ?> data) {
        String title = str(data.get("title"), "Your event");
        String until = day(data.get("refundUntil"));
        return build(title + " has changed", "Hi " + str(firstName(data.get("attendeeName")), "there"),
                "The organizer changed the date, time or venue of " + title + ". Your tickets are still valid for the "
                        + "new details below.",
                until == null ? null : "Can't make it now? You can cancel for a full refund, fees included, until " + until + ".",
                eventDetails(data), "View booking", bookingUrl(data));
    }

    private static String when(Object iso) {
        try {
            return iso instanceof String s ? WHEN.format(java.time.OffsetDateTime.parse(s).atZoneSameInstant(IST)) : null;
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    private static String day(Object iso) {
        try {
            return iso instanceof String s ? DAY.format(java.time.OffsetDateTime.parse(s).atZoneSameInstant(IST)) : null;
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    private static long num(Object value) {
        return value instanceof Number n ? n.longValue() : 0;
    }

    /** {@code 49900} → {@code "Rs. 499.00"} (Indian digit grouping). */
    private static String rupees(long paise) {
        java.text.NumberFormat f = java.text.NumberFormat.getNumberInstance(new java.util.Locale("en", "IN"));
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return "Rs. " + f.format(paise / 100.0);
    }

    private static String str(Object value, String fallback) {
        return value instanceof String s && !s.isBlank() ? s.trim() : fallback;
    }

    private static String firstName(Object raw) {
        if (!(raw instanceof String s) || s.isBlank()) {
            return null;
        }
        return s.trim().split("\\s+")[0];
    }

    private String layout(String title, String bodyHtml, String ctaLabel, String ctaUrl) {
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width">
                <title>%s</title></head>
                <body style="margin:0;padding:0;background:#F8F9FC;font-family:Inter,Segoe UI,Arial,sans-serif">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#F8F9FC;padding:32px 12px">
                <tr><td align="center">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#ffffff;border-radius:16px;overflow:hidden;border:1px solid #E4E6EE">
                <tr><td style="background:linear-gradient(135deg,#6C4CF1,#377DFF);background-color:#6C4CF1;padding:28px 32px">
                <span style="font-size:22px;font-weight:700;color:#ffffff;letter-spacing:-0.02em">Stagepass</span></td></tr>
                <tr><td style="padding:32px">%s
                <table role="presentation" cellpadding="0" cellspacing="0" style="margin:24px 0 8px"><tr>
                <td style="border-radius:999px;background:#6C4CF1"><a href="%s" style="display:inline-block;padding:12px 24px;font-size:15px;font-weight:600;color:#ffffff;text-decoration:none">%s</a></td>
                </tr></table></td></tr>
                <tr><td style="padding:20px 32px;border-top:1px solid #E4E6EE;font-size:12px;line-height:18px;color:#5B6072">
                You’re receiving this because you have a Stagepass account. &copy; Stagepass</td></tr>
                </table></td></tr></table></body></html>
                """.formatted(HtmlUtils.htmlEscape(title), bodyHtml, HtmlUtils.htmlEscape(ctaUrl),
                HtmlUtils.htmlEscape(ctaLabel));
    }
}
