package com.stagepass.notification.service;

import java.util.List;

public record RenderedEmail(String subject, String html, String text, List<EmailAttachment> attachments) {

    public RenderedEmail(String subject, String html, String text) {
        this(subject, html, text, List.of());
    }

    public RenderedEmail withAttachments(List<EmailAttachment> files) {
        return new RenderedEmail(subject, html, text, List.copyOf(files));
    }
}
