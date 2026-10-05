package com.foodie.delivery;

import com.foodie.delivery.service.biometrics.FaceBiometricsService;
import com.foodie.delivery.service.biometrics.FaceBiometricsService.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class FaceBiometricsServiceTest {

    private FaceBiometricsService service;

    @BeforeEach
    void setUp() {
        service = new FaceBiometricsService();
    }

    /**
     * Helper to render a realistic synthetic test human face with anatomical features.
     */
    private BufferedImage createSyntheticFace(int width, int height, Color skinColor, int eyeSize, int mouthWidth, int eyeOffsetY) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        // Background
        g.setColor(new Color(240, 240, 245));
        g.fillRect(0, 0, width, height);

        int cx = width / 2;
        int cy = height / 2;
        int fw = (int) (width * 0.65);
        int fh = (int) (height * 0.80);

        // Face Oval (Skin)
        g.setColor(skinColor);
        g.fillOval(cx - fw / 2, cy - fh / 2, fw, fh);

        // 3D Shading gradient (cheeks slightly darker than nose center)
        g.setColor(new Color(Math.max(0, skinColor.getRed() - 10), Math.max(0, skinColor.getGreen() - 10), Math.max(0, skinColor.getBlue() - 10)));
        g.fillOval(cx - fw / 2, cy - fh / 2 + 10, 20, fh - 20);
        g.fillOval(cx + fw / 2 - 20, cy - fh / 2 + 10, 20, fh - 20);

        // Hair (Top)
        g.setColor(new Color(40, 30, 25));
        g.fillArc(cx - fw / 2 - 5, cy - fh / 2 - 10, fw + 10, (int) (fh * 0.45), 0, 180);

        // Eyebrows
        g.setColor(new Color(45, 35, 30));
        int eyeY = cy - (int) (fh * 0.12) + eyeOffsetY;
        int leftEyeX = cx - (int) (fw * 0.22);
        int rightEyeX = cx + (int) (fw * 0.22);

        g.fillRect(leftEyeX - eyeSize, eyeY - eyeSize - 8, eyeSize * 2, 4);
        g.fillRect(rightEyeX - eyeSize, eyeY - eyeSize - 8, eyeSize * 2, 4);

        // Bilateral Eyes (dark pupil/lashes)
        g.setColor(new Color(30, 25, 25));
        g.fillOval(leftEyeX - eyeSize / 2, eyeY - eyeSize / 2, eyeSize, eyeSize);
        g.fillOval(rightEyeX - eyeSize / 2, eyeY - eyeSize / 2, eyeSize, eyeSize);

        // Eye sclera white
        g.setColor(new Color(235, 235, 240));
        g.fillOval(leftEyeX - eyeSize / 4, eyeY - eyeSize / 4, eyeSize / 2, eyeSize / 2);
        g.fillOval(rightEyeX - eyeSize / 4, eyeY - eyeSize / 4, eyeSize / 2, eyeSize / 2);

        // Eye center
        g.setColor(new Color(20, 15, 15));
        g.fillOval(leftEyeX - 2, eyeY - 2, 4, 4);
        g.fillOval(rightEyeX - 2, eyeY - 2, 4, 4);

        // Nose Bridge & Tip (vertical ridge)
        int noseY = cy + (int) (fh * 0.08);
        g.setColor(new Color(Math.min(255, skinColor.getRed() + 15), Math.min(255, skinColor.getGreen() + 12), Math.min(255, skinColor.getBlue() + 10)));
        g.fillRect(cx - 3, eyeY + 6, 6, noseY - eyeY);
        g.setColor(new Color(Math.max(0, skinColor.getRed() - 15), Math.max(0, skinColor.getGreen() - 15), Math.max(0, skinColor.getBlue() - 15)));
        g.fillOval(cx - 8, noseY, 16, 8); // Nose base

        // Mouth (Lips with red tones and horizontal line)
        int mouthY = cy + (int) (fh * 0.28);
        g.setColor(new Color(190, 80, 85)); // Lip red
        g.fillOval(cx - mouthWidth / 2, mouthY - 4, mouthWidth, 12);
        g.setColor(new Color(90, 30, 35)); // Mouth slit
        g.drawLine(cx - mouthWidth / 2 + 2, mouthY + 2, cx + mouthWidth / 2 - 2, mouthY + 2);

        g.dispose();
        return img;
    }

    private BufferedImage createMultipleFacesImage(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(240, 240, 245));
        g.fillRect(0, 0, width, height);

        int halfW = width / 2;
        BufferedImage face1 = createSyntheticFace(halfW, height, new Color(220, 165, 130), 12, 30, 0);
        BufferedImage face2 = createSyntheticFace(halfW, height, new Color(220, 165, 130), 12, 30, 0);

        g.drawImage(face1, 0, 0, null);
        g.drawImage(face2, halfW, 0, null);
        g.dispose();
        return img;
    }

    private BufferedImage createHandOrFlatSkinImage(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        // Flat uniform skin (like a palm, arm, or leg without facial features)
        g.setColor(new Color(220, 160, 130));
        g.fillRect(0, 0, width, height);

        // Some vertical finger-like strokes
        g.setColor(new Color(200, 140, 110));
        for (int i = 0; i < 4; i++) {
            g.fillRect(50 + i * 40, 30, 25, height - 60);
        }

        g.dispose();
        return img;
    }

    private BufferedImage createObjectImage(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        // Blue background with a coffee mug / box (non-skin object)
        g.setColor(new Color(40, 70, 120));
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(200, 200, 200));
        g.fillRect(width / 4, height / 4, width / 2, height / 2);

        g.dispose();
        return img;
    }

    private byte[] toJpegBytes(BufferedImage img) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Should successfully detect valid human face")
    void testDetectFace_ValidHumanFace() {
        BufferedImage face = createSyntheticFace(240, 280, new Color(225, 170, 135), 14, 36, 0);
        FaceDetectionResult result = service.detectFace(face);

        assertThat(result.detected()).isTrue();
        assertThat(result.score()).isGreaterThanOrEqualTo(0.55);
        assertThat(result.landmarks()).isNotNull();
        assertThat(result.landmarks().eyeDistance()).isGreaterThan(20);
        assertThat(result.faceCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should reject non-face objects (e.g. coffee mug, wall, blank)")
    void testDetectFace_RejectsNonFaceObject() {
        BufferedImage objectImg = createObjectImage(240, 280);
        FaceDetectionResult result = service.detectFace(objectImg);

        assertThat(result.detected()).isFalse();
        assertThat(result.message()).contains("No human face detected");
    }

    @Test
    @DisplayName("Should reject hands, limbs, or flat skin surfaces without facial organs")
    void testDetectFace_RejectsHandOrLimb() {
        BufferedImage handImg = createHandOrFlatSkinImage(240, 280);
        FaceDetectionResult result = service.detectFace(handImg);

        assertThat(result.detected()).isFalse();
    }

    @Test
    @DisplayName("Should pass liveness on natural 3D face")
    void testEvaluateLiveness_LiveFace() {
        BufferedImage face = createSyntheticFace(240, 280, new Color(225, 170, 135), 14, 36, 0);
        FaceDetectionResult detection = service.detectFace(face);

        assertThat(detection.detected()).isTrue();
        LivenessResult liveness = service.evaluateLiveness(face, detection);
        assertThat(liveness.isLive()).isTrue();
        assertThat(liveness.livenessScore()).isGreaterThanOrEqualTo(0.50);
    }

    @Test
    @DisplayName("Should match the same person's face across photos with high confidence (>= 70%)")
    void testVerifyFace_SamePersonMatches() throws IOException {
        Color skin = new Color(220, 165, 130);
        // KYC Photo
        BufferedImage kycImg = createSyntheticFace(240, 280, skin, 14, 36, 0);
        // Live Photo of the same person (slight natural micro-variation)
        BufferedImage liveImg = createSyntheticFace(240, 280, skin, 14, 36, 2);

        byte[] kycBytes = toJpegBytes(kycImg);
        byte[] liveBytes = toJpegBytes(liveImg);

        FaceVerificationResult result = service.verifyFace(liveBytes, kycBytes);

        assertThat(result.verified()).isTrue();
        assertThat(result.confidenceScore()).isGreaterThanOrEqualTo(0.70);
    }

    @Test
    @DisplayName("Should reject a different person with mismatched facial geometry and features (< 70%)")
    void testVerifyFace_DifferentPersonRejected() throws IOException {
        // Person A (KYC)
        BufferedImage kycImg = createSyntheticFace(240, 280, new Color(235, 190, 155), 10, 26, -5);
        // Person B (Live) - completely different skin tone, eye size, mouth width, feature positioning
        BufferedImage liveImg = createSyntheticFace(240, 280, new Color(170, 110, 80), 22, 54, 15);

        byte[] kycBytes = toJpegBytes(kycImg);
        byte[] liveBytes = toJpegBytes(liveImg);

        FaceVerificationResult result = service.verifyFace(liveBytes, kycBytes);

        assertThat(result.verified()).isFalse();
        assertThat(result.confidenceScore()).isLessThan(0.70);
        assertThat(result.message()).contains("Face verification failed");
    }

    @Test
    @DisplayName("Should reject when live capture is a hand instead of a human face")
    void testVerifyFace_RejectsHandAgainstKyc() throws IOException {
        BufferedImage kycImg = createSyntheticFace(240, 280, new Color(220, 165, 130), 14, 36, 0);
        BufferedImage handImg = createHandOrFlatSkinImage(240, 280);

        byte[] kycBytes = toJpegBytes(kycImg);
        byte[] liveBytes = toJpegBytes(handImg);

        FaceVerificationResult result = service.verifyFace(liveBytes, kycBytes);

        assertThat(result.verified()).isFalse();
        assertThat(result.message()).contains("No human face detected");
    }

    @Test
    @DisplayName("Should reject when multiple faces are detected in the live camera frame")
    void testVerifyFace_RejectsMultipleFaces() throws IOException {
        BufferedImage kycImg = createSyntheticFace(240, 280, new Color(220, 165, 130), 14, 36, 0);
        BufferedImage multiFaceImg = createMultipleFacesImage(480, 280);

        byte[] kycBytes = toJpegBytes(kycImg);
        byte[] liveBytes = toJpegBytes(multiFaceImg);

        FaceVerificationResult result = service.verifyFace(liveBytes, kycBytes);

        assertThat(result.verified()).isFalse();
        assertThat(result.message()).contains("Multiple faces detected");
    }

    @Test
    @DisplayName("Should reject empty or corrupted image data gracefully")
    void testVerifyFace_RejectsCorruptedOrEmptyImages() throws IOException {
        BufferedImage kycImg = createSyntheticFace(240, 280, new Color(220, 165, 130), 14, 36, 0);
        byte[] kycBytes = toJpegBytes(kycImg);

        // Empty live bytes
        FaceVerificationResult emptyLive = service.verifyFace(new byte[0], kycBytes);
        assertThat(emptyLive.verified()).isFalse();
        assertThat(emptyLive.message()).contains("empty or missing");

        // Null live bytes
        FaceVerificationResult nullLive = service.verifyFace(null, kycBytes);
        assertThat(nullLive.verified()).isFalse();

        // Corrupted live bytes
        byte[] corruptedBytes = new byte[]{1, 2, 3, 4, 5};
        FaceVerificationResult corruptedLive = service.verifyFace(corruptedBytes, kycBytes);
        assertThat(corruptedLive.verified()).isFalse();
        assertThat(corruptedLive.message()).contains("Could not decode");
    }
}
