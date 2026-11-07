package com.stagepass.engagement.api;

/** Whether the signed-in user may review the event, and their review if they wrote one. */
public record MyReview(boolean eligible, PublicReview review) {
}
