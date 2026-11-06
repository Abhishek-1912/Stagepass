package com.stagepass.catalog;

import com.stagepass.catalog.config.CloudinaryProperties;
import com.stagepass.catalog.service.ImageUrlPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageUrlPolicyTest {

    private final ImageUrlPolicy policy = new ImageUrlPolicy(new CloudinaryProperties("demo", "1", "s"));
    private static final String FOLDER = "stagepass/events/0b7f0e5e-1c2d-4e5f-8a9b-0c1d2e3f4a5b";

    @Test
    void acceptsOwnFolderWithVersionAndTransform() {
        assertThat(policy.isOwned("https://res.cloudinary.com/demo/image/upload/" + FOLDER + "/cover.jpg", FOLDER)).isTrue();
        assertThat(policy.isOwned("https://res.cloudinary.com/demo/image/upload/v1712345/" + FOLDER + "/a_b-1.webp", FOLDER))
                .isTrue();
        assertThat(policy.isOwned("https://res.cloudinary.com/demo/image/upload/f_auto,q_auto/v17/" + FOLDER + "/x.png", FOLDER))
                .isTrue();
    }

    @Test
    void rejectsOtherCloud() {
        assertThat(policy.isOwned("https://res.cloudinary.com/other/image/upload/" + FOLDER + "/cover.jpg", FOLDER)).isFalse();
    }

    @Test
    void rejectsOtherEventsFolder() {
        assertThat(policy.isOwned("https://res.cloudinary.com/demo/image/upload/stagepass/events/other-id/cover.jpg", FOLDER))
                .isFalse();
        assertThat(policy.isOwned("https://res.cloudinary.com/demo/image/upload/" + FOLDER + "-evil/cover.jpg", FOLDER))
                .isFalse();
        assertThat(policy.isOwned("https://res.cloudinary.com/demo/image/upload/junk/" + FOLDER + "/cover.jpg", FOLDER))
                .isFalse();
    }

    @Test
    void rejectsNonImageAndHttp() {
        assertThat(policy.isOwned("http://res.cloudinary.com/demo/image/upload/" + FOLDER + "/cover.jpg", FOLDER)).isFalse();
        assertThat(policy.isOwned("https://res.cloudinary.com/demo/image/upload/" + FOLDER + "/cover.svg", FOLDER)).isFalse();
        assertThat(policy.isOwned("https://res.cloudinary.com/demo/raw/upload/" + FOLDER + "/cover.jpg", FOLDER)).isFalse();
        assertThat(policy.isOwned("https://evil.example/" + FOLDER + "/cover.jpg?x=res.cloudinary.com/demo", FOLDER)).isFalse();
    }
}
