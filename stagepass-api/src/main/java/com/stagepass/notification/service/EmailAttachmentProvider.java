package com.stagepass.notification.service;

import java.util.List;
import java.util.Map;

/**
 * Supplies attachments for a template at send time, so files reflect current data (e.g. a ticket refunded after
 * the email was queued is left out). Implemented outside the notification module.
 */
public interface EmailAttachmentProvider {

    boolean supports(EmailTemplate template);

    /** May be empty: the email is then sent without attachments. */
    List<EmailAttachment> attachmentsFor(EmailTemplate template, Map<String, ?> data);
}
