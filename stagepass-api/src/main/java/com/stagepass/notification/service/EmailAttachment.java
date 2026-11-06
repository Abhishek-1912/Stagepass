package com.stagepass.notification.service;

/** A file attached to an email (Resend sends it base64-encoded). */
public record EmailAttachment(String filename, byte[] content) {
}
