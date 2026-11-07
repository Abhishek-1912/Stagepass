package com.stagepass.ticketing;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.stagepass.ticketing.service.QrImages;
import com.stagepass.ticketing.service.QrTokens;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QrImagesTest {

    @Test
    void pngDecodesBackToTheToken() throws Exception {
        String token = new QrTokens("test-qr-secret-test-qr-secret-0000").sign(UUID.randomUUID(), UUID.randomUUID(),
                "ABCDEFGHJKMNPQRSTUVWXYZ2");
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(new QrImages().png(token, 320)));

        assertThat(img.getWidth()).isEqualTo(320);
        assertThat(img.getHeight()).isEqualTo(320);
        String decoded = new MultiFormatReader()
                .decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(img))),
                        java.util.Map.of(com.google.zxing.DecodeHintType.PURE_BARCODE, Boolean.TRUE)).getText();
        assertThat(decoded).isEqualTo(token);
    }

    /** PURE_BARCODE: these are clean generated images; ZXing's general detector misses ~1-2% of them regardless of size. */
    @Test
    void twoHundredRandomTokensDecode() throws Exception {
        QrTokens tokens = new QrTokens("test-qr-secret-test-qr-secret-0000");
        QrImages images = new QrImages();
        java.util.Map<com.google.zxing.DecodeHintType, Object> hints = java.util.Map.of(
                com.google.zxing.DecodeHintType.PURE_BARCODE, Boolean.TRUE);
        for (int i = 0; i < 200; i++) {
            String token = tokens.sign(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID().toString().replace("-", "").substring(0, 24));
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(images.png(token, 320)));
            String decoded = new MultiFormatReader().decode(
                    new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(img))), hints).getText();
            assertThat(decoded).as("token %d", i).isEqualTo(token);
        }
    }
}
