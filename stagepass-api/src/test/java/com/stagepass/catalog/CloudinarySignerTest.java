package com.stagepass.catalog;

import com.stagepass.catalog.config.CloudinaryProperties;
import com.stagepass.catalog.service.CloudinarySigner;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CloudinarySignerTest {

    @Test
    void matchesCloudinaryDocumentedSignature() {
        // Example from Cloudinary's "Generating authentication signatures" docs.
        CloudinarySigner signer = new CloudinarySigner(new CloudinaryProperties("demo", "1234", "abcd"));

        assertThat(signer.sign(Map.of("public_id", "sample_image"), 1315060510L))
                .isEqualTo("b4ad47fb4e25c7bf5f92a20089f9db59bc302313");
    }

    @Test
    void sortsParametersAndSkipsEmptyValues() {
        CloudinarySigner signer = new CloudinarySigner(new CloudinaryProperties("demo", "1234", "abcd"));

        assertThat(signer.sign(Map.of("folder", "f", "allowed_formats", "jpg", "eager", ""), 10L))
                .isEqualTo(signer.sign(Map.of("allowed_formats", "jpg", "folder", "f"), 10L));
    }
}
