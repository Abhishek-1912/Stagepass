package com.stagepass.discovery.service;

import com.stagepass.catalog.domain.Event;
import com.stagepass.common.config.StagepassProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Tiny server-rendered page for share links: Open Graph / Twitter tags for link previews, then an immediate redirect
 * to the web app. Every interpolated value is HTML-escaped; redirect targets are always our own web origin.
 */
@Component
public class SharePageRenderer {

    private final String web;

    public SharePageRenderer(StagepassProperties props) {
        this.web = props.webBaseUrl() == null ? "" : props.webBaseUrl().replaceAll("/+$", "");
    }

    public String render(Event e, String shareUrl) {
        String target = web + "/events/" + e.getSlug();
        String description = e.getSummary() != null && !e.getSummary().isBlank() ? e.getSummary()
                : e.getDescription() == null ? "Get tickets on Stagepass" : truncate(e.getDescription(), 160);
        StringBuilder head = new StringBuilder()
                .append(meta("og:type", "website"))
                .append(meta("og:site_name", "Stagepass"))
                .append(meta("og:title", e.getTitle()))
                .append(meta("og:description", description))
                .append(meta("og:url", shareUrl))
                .append("<meta name=\"twitter:card\" content=\"summary_large_image\">\n")
                .append("<meta name=\"description\" content=\"").append(esc(description)).append("\">\n");
        if (e.getCoverImageUrl() != null) {
            head.append(meta("og:image", socialImage(e.getCoverImageUrl())));
        }
        return page(e.getTitle() + " · Stagepass", head.toString(), target, "Open " + e.getTitle() + " on Stagepass");
    }

    public String notFound() {
        return page("Event not found · Stagepass", "", web + "/explore", "Explore events on Stagepass");
    }

    /** Cloudinary 1200×630 crop for link previews. */
    static String socialImage(String url) {
        return url.contains("/image/upload/") ? url.replaceFirst("/image/upload/", "/image/upload/c_fill,w_1200,h_630,f_jpg/")
                : url;
    }

    private String page(String title, String head, String target, String linkText) {
        String t = esc(target);
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title>
                %s<link rel="canonical" href="%s">
                <meta http-equiv="refresh" content="0; url=%s">
                </head>
                <body style="font-family:system-ui,sans-serif;text-align:center;padding:48px">
                <p><a href="%s">%s</a></p>
                </body></html>
                """.formatted(esc(title), head, t, t, t, esc(linkText));
    }

    private static String meta(String property, String value) {
        return "<meta property=\"" + property + "\" content=\"" + esc(value) + "\">\n";
    }

    private static String esc(String s) {
        return HtmlUtils.htmlEscape(s == null ? "" : s, "UTF-8");
    }

    private static String truncate(String s, int max) {
        String flat = s.replaceAll("\\s+", " ").trim();
        return flat.length() <= max ? flat : flat.substring(0, max - 1).trim() + "…";
    }
}
