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
        String text = greeting + ",\n\n" + paragraph + "\n" + (note == null ? "" : "\n" + note + "\n")
                + "\n" + ctaLabel + ": " + ctaUrl + "\n\n— Team Stagepass\n";
        return new RenderedEmail(subject, layout(subject, body.toString(), ctaLabel, ctaUrl), text);
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
