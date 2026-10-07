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
        return createSyntheticFaceWithAngle(width, height, skinColor, eyeSize, mouthWidth, eyeOffsetY, 0.0);
    }

    private BufferedImage createSyntheticFaceWithAngle(int width, int height, Color skinColor, int eyeSize, int mouthWidth, int eyeOffsetY, double angleDegrees) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background
        g.setColor(new Color(240, 240, 245));
        g.fillRect(0, 0, width, height);

        int cx = width / 2;
        int cy = height / 2;
        int fw = (int) (width * 0.65);
        int fh = (int) (height * 0.80);

        if (angleDegrees != 0.0) {
            g.rotate(Math.toRadians(angleDegrees), cx, cy);
        }

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

    private BufferedImage createGlareSpoofFace(int width, int height) {
        BufferedImage face = createSyntheticFace(width, height, new Color(220, 165, 130), 14, 36, 0);
        Graphics2D g = face.createGraphics();
        // Add strong specular glare hotspots across face
        g.setColor(new Color(255, 255, 255));
        g.fillRect(width / 4, height / 4, width / 2, height / 3);
        g.dispose();
        return face;
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
        assertThat(result.score()).isGreaterThanOrEqualTo(0.40);
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
    @DisplayName("Should reject screen glare or glass reflection in liveness check")
    void testEvaluateLiveness_RejectsSpecularGlare() {
        BufferedImage glareImg = createGlareSpoofFace(240, 280);
        FaceDetectionResult detection = service.detectFace(glareImg);

        if (detection.detected()) {
            LivenessResult liveness = service.evaluateLiveness(glareImg, detection);
            assertThat(liveness.isLive()).isFalse();
            assertThat(liveness.reason()).contains("glare");
        }
    }

    @Test
    @DisplayName("Should validate single live human face via validateFaceSelfie")
    void testValidateFaceSelfie_AcceptsLiveHuman() throws IOException {
        BufferedImage face = createSyntheticFace(240, 280, new Color(220, 165, 130), 14, 36, 0);
        byte[] bytes = toJpegBytes(face);

        FaceDetectionResult result = service.validateFaceSelfie(bytes);
        assertThat(result.detected()).isTrue();
        assertThat(result.faceCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should reject non-face objects via validateFaceSelfie")
    void testValidateFaceSelfie_RejectsObject() throws IOException {
        BufferedImage obj = createObjectImage(240, 280);
        byte[] bytes = toJpegBytes(obj);

        FaceDetectionResult result = service.validateFaceSelfie(bytes);
        assertThat(result.detected()).isFalse();
    }

    @Test
    @DisplayName("Should verify natural biological motion between temporal live camera frames")
    void testVerifyLiveMotion_AcceptsLiveMovement() throws IOException {
        Color skin = new Color(220, 165, 130);
        BufferedImage frame1 = createSyntheticFace(240, 280, skin, 14, 36, 0);
        // Frame 2 with slight natural micro-movement (slight eye/head angle variation)
        BufferedImage frame2 = createSyntheticFace(240, 280, skin, 12, 34, 2);

        byte[] f1Bytes = toJpegBytes(frame1);
        byte[] f2Bytes = toJpegBytes(frame2);

        MotionLivenessResult result = service.verifyLiveMotion(f1Bytes, f2Bytes);
        assertThat(result.isLive()).isTrue();
        assertThat(result.identityMatchScore()).isGreaterThanOrEqualTo(0.70);
        assertThat(result.message()).contains("Live human movement verified");
    }

    @Test
    @DisplayName("Should reject static photo replay with zero motion between frames")
    void testVerifyLiveMotion_RejectsStaticPhotoReplay() throws IOException {
        BufferedImage frame1 = createSyntheticFace(240, 280, new Color(220, 165, 130), 14, 36, 0);
        byte[] f1Bytes = toJpegBytes(frame1);

        // Frame 2 is exact identical static image
        MotionLivenessResult result = service.verifyLiveMotion(f1Bytes, f1Bytes);
        assertThat(result.isLive()).isFalse();
        assertThat(result.message()).contains("Static image or photo replay detected");
    }

    @Test
    @DisplayName("Should reject identity mismatch between motion frames")
    void testVerifyLiveMotion_RejectsIdentitySwap() throws IOException {
        // Person A
        BufferedImage frame1 = createSyntheticFace(240, 280, new Color(235, 190, 155), 10, 26, -5);
        // Person B
        BufferedImage frame2 = createSyntheticFace(240, 280, new Color(170, 110, 80), 22, 54, 15);

        byte[] f1Bytes = toJpegBytes(frame1);
        byte[] f2Bytes = toJpegBytes(frame2);

        MotionLivenessResult result = service.verifyLiveMotion(f1Bytes, f2Bytes);
        assertThat(result.isLive()).isFalse();
        assertThat(result.message()).contains("Identity mismatch");
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
    @DisplayName("Should match same person under lighting differences (low light vs normal)")
    void testVerifyFace_SamePersonLowLight() throws IOException {
        Color normalSkin = new Color(220, 165, 130);
        Color dimSkin = new Color(175, 130, 100);

        BufferedImage kycImg = createSyntheticFace(240, 280, normalSkin, 14, 36, 0);
        BufferedImage liveImg = createSyntheticFace(240, 280, dimSkin, 14, 36, 0);

        byte[] kycBytes = toJpegBytes(kycImg);
        byte[] liveBytes = toJpegBytes(liveImg);

        FaceVerificationResult result = service.verifyFace(liveBytes, kycBytes);
        assertThat(result.verified()).isTrue();
        assertThat(result.confidenceScore()).isGreaterThanOrEqualTo(0.70);
    }

    @Test
    @DisplayName("Should match same person under bright light differences")
    void testVerifyFace_SamePersonBrightLight() throws IOException {
        Color normalSkin = new Color(220, 165, 130);
        Color brightSkin = new Color(245, 195, 160);

        BufferedImage kycImg = createSyntheticFace(240, 280, normalSkin, 14, 36, 0);
        BufferedImage liveImg = createSyntheticFace(240, 280, brightSkin, 14, 36, 0);

        byte[] kycBytes = toJpegBytes(kycImg);
        byte[] liveBytes = toJpegBytes(liveImg);

        FaceVerificationResult result = service.verifyFace(liveBytes, kycBytes);
        assertThat(result.verified()).isTrue();
        assertThat(result.confidenceScore()).isGreaterThanOrEqualTo(0.70);
    }

    @Test
    @DisplayName("Should match same person with head tilt / small angle variation via standardized affine alignment")
    void testVerifyFace_SamePersonWithTilt() throws IOException {
        Color skin = new Color(220, 165, 130);
        // KYC Photo normal upright
        BufferedImage kycImg = createSyntheticFace(240, 280, skin, 14, 36, 0);
        // Live Photo with 6-degree head tilt
        BufferedImage liveImg = createSyntheticFaceWithAngle(240, 280, skin, 14, 36, 0, 6.0);

        byte[] kycBytes = toJpegBytes(kycImg);
        byte[] liveBytes = toJpegBytes(liveImg);

        FaceVerificationResult result = service.verifyFace(liveBytes, kycBytes);
        assertThat(result.verified()).isTrue();
        assertThat(result.confidenceScore()).isGreaterThanOrEqualTo(0.70);
    }

    @Test
    @DisplayName("Should match same person with different expression (smile/wider mouth)")
    void testVerifyFace_SamePersonExpressionDifference() throws IOException {
        Color skin = new Color(220, 165, 130);
        // KYC neutral
        BufferedImage kycImg = createSyntheticFace(240, 280, skin, 14, 36, 0);
        // Live smiling (wider mouth)
        BufferedImage liveImg = createSyntheticFace(240, 280, skin, 14, 44, 0);

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
    @DisplayName("Should match same person under distance/scale variations (closer camera vs farther)")
    void testVerifyFace_SamePersonDifferentDistance() throws IOException {
        Color skin = new Color(220, 165, 130);
        // KYC at standard distance
        BufferedImage kycImg = createSyntheticFace(240, 280, skin, 14, 36, 0);
        // Live Photo captured closer to camera (scaled up with camera zoom/closer distance)
        BufferedImage liveImg = new BufferedImage(360, 420, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = liveImg.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(kycImg, 0, 0, 360, 420, null);
        g.dispose();

        byte[] kycBytes = toJpegBytes(kycImg);
        byte[] liveBytes = toJpegBytes(liveImg);

        FaceVerificationResult result = service.verifyFace(liveBytes, kycBytes);
        assertThat(result.verified()).isTrue();
        assertThat(result.confidenceScore()).isGreaterThanOrEqualTo(0.70);
    }

    @Test
    @DisplayName("Should match same person under realistic unilateral side lighting and camera noise")
    void testVerifyFace_SamePersonSideLightingAndNoise() throws IOException {
        Color baseSkin = new Color(220, 165, 130);
        BufferedImage kycImg = createSyntheticFace(240, 280, baseSkin, 14, 36, 0);

        // Create live image with side lighting (left side brighter, right side shadowed) + camera shot noise
        BufferedImage liveImg = createSyntheticFace(240, 280, baseSkin, 14, 36, 0);
        int w = liveImg.getWidth();
        int h = liveImg.getHeight();
        java.util.Random rnd = new java.util.Random(42);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = liveImg.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Lighting gradient: +20 on left, -20 on right
                double lightFactor = 1.0 + 0.18 * ((double) (w / 2 - x) / (w / 2));
                // Add ±4 noise
                int noise = rnd.nextInt(9) - 4;

                int nr = Math.min(255, Math.max(0, (int) (r * lightFactor + noise)));
                int ng = Math.min(255, Math.max(0, (int) (g * lightFactor + noise)));
                int nb = Math.min(255, Math.max(0, (int) (b * lightFactor + noise)));

                liveImg.setRGB(x, y, (nr << 16) | (ng << 8) | nb);
            }
        }

        byte[] kycBytes = toJpegBytes(kycImg);
        byte[] liveBytes = toJpegBytes(liveImg);

        FaceVerificationResult result = service.verifyFace(liveBytes, kycBytes);
        assertThat(result.verified()).isTrue();
        assertThat(result.confidenceScore()).isGreaterThanOrEqualTo(0.70);
    }

    @Test
    @DisplayName("Should match same person with warm vs cool camera white-balance shift")
    void testVerifyFace_SamePersonWhiteBalanceShift() throws IOException {
        // KYC: warm indoor white balance (higher red, lower blue)
        BufferedImage kycImg = createSyntheticFace(240, 280, new Color(230, 165, 120), 14, 36, 0);
        // Live: cool daylight white balance (lower red, higher blue)
        BufferedImage liveImg = createSyntheticFace(240, 280, new Color(210, 165, 145), 14, 36, 0);

        byte[] kycBytes = toJpegBytes(kycImg);
        byte[] liveBytes = toJpegBytes(liveImg);

        FaceVerificationResult result = service.verifyFace(liveBytes, kycBytes);
        assertThat(result.verified()).isTrue();
        assertThat(result.confidenceScore()).isGreaterThanOrEqualTo(0.70);
    }

    @Test
    @DisplayName("Should verify that alignAndNormalizeFace produces standard 128x128 canonical dimensions and eye placement")
    void testAlignAndNormalizeFace_CanonicalStructure() {
        BufferedImage face = createSyntheticFace(240, 280, new Color(220, 165, 130), 14, 36, 0);
        FaceDetectionResult detection = service.detectFace(face);
        assertThat(detection.detected()).isTrue();

        CanonicalFace canonical = service.alignAndNormalizeFace(face, detection);
        assertThat(canonical.width()).isEqualTo(128);
        assertThat(canonical.height()).isEqualTo(128);
        assertThat(canonical.leftEye().x()).isEqualTo(42);
        assertThat(canonical.leftEye().y()).isEqualTo(48);
        assertThat(canonical.rightEye().x()).isEqualTo(86);
        assertThat(canonical.rightEye().y()).isEqualTo(48);
        assertThat(canonical.grayFace().getWidth()).isEqualTo(128);
        assertThat(canonical.grayFace().getHeight()).isEqualTo(128);
        assertThat(canonical.colorFace().getWidth()).isEqualTo(128);
        assertThat(canonical.colorFace().getHeight()).isEqualTo(128);
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

    @Test
    @DisplayName("Regression: Same person with combined lighting, scale, and head angle variations passes verification")
    void testVerifyFace_Regression_SamePersonCombinedLightingScaleAngle() throws IOException {
        Color baseSkin = new Color(220, 165, 130);
        // KYC upright standard
        BufferedImage kycImg = createSyntheticFace(240, 280, baseSkin, 14, 36, 0);

        // Live photo of the same person with angle tilt (12 deg), larger resolution (scale), and warmer lighting
        Color warmSkin = new Color(230, 170, 125);
        BufferedImage baseLive = createSyntheticFaceWithAngle(300, 350, warmSkin, 18, 45, 0, 12.0);

        byte[] kycBytes = toJpegBytes(kycImg);
        byte[] liveBytes = toJpegBytes(baseLive);

        FaceVerificationResult result = service.verifyFace(liveBytes, kycBytes);
        assertThat(result.verified()).isTrue();
        assertThat(result.confidenceScore()).isGreaterThanOrEqualTo(0.70);
    }

    @Test
    @DisplayName("Regression: Same person with 90-degree un-oriented mobile camera capture passes verification")
    void testVerifyFace_Regression_SamePersonWithRotatedMobileOrientation() throws IOException {
        Color baseSkin = new Color(220, 165, 130);
        // KYC upright photo
        BufferedImage kycImg = createSyntheticFace(240, 280, baseSkin, 14, 36, 0);

        // Live capture rotated 90 degrees by mobile sensor without EXIF
        BufferedImage uprightLive = createSyntheticFace(240, 280, baseSkin, 14, 36, 0);
        BufferedImage rotatedLive = new BufferedImage(280, 240, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rotatedLive.createGraphics();
        g.translate(280 / 2.0, 240 / 2.0);
        g.rotate(Math.toRadians(90), 0, 0);
        g.translate(-240 / 2.0, -280 / 2.0);
        g.drawImage(uprightLive, 0, 0, null);
        g.dispose();

        byte[] kycBytes = toJpegBytes(kycImg);
        byte[] liveBytes = toJpegBytes(rotatedLive);

        FaceVerificationResult result = service.verifyFace(liveBytes, kycBytes);
        assertThat(result.verified()).isTrue();
        assertThat(result.confidenceScore()).isGreaterThanOrEqualTo(0.70);
    }

    @Test
    @DisplayName("Regression: Different person is rejected with confidence < 0.70 and identity threshold is strictly preserved")
    void testVerifyFace_Regression_DifferentPersonRejected() throws IOException {
        // Person 1 (KYC)
        BufferedImage kycImg = createSyntheticFace(240, 280, new Color(235, 190, 155), 10, 26, -5);
        // Person 2 (Live) - distinct anatomical traits
        BufferedImage liveImg = createSyntheticFace(240, 280, new Color(185, 125, 95), 20, 50, 12);

        byte[] kycBytes = toJpegBytes(kycImg);
        byte[] liveBytes = toJpegBytes(liveImg);

        FaceVerificationResult result = service.verifyFace(liveBytes, kycBytes);
        assertThat(result.verified()).isFalse();
        assertThat(result.confidenceScore()).isLessThan(0.70);
    }

    @Test
    @DisplayName("Regression: Liveness checks remain strictly enforced against non-face or flat spoof images")
    void testVerifyFace_Regression_LivenessChecksEnforced() throws IOException {
        BufferedImage kycImg = createSyntheticFace(240, 280, new Color(220, 165, 130), 14, 36, 0);
        BufferedImage flatObjectImg = createHandOrFlatSkinImage(240, 280);

        byte[] kycBytes = toJpegBytes(kycImg);
        byte[] liveBytes = toJpegBytes(flatObjectImg);

        FaceVerificationResult result = service.verifyFace(liveBytes, kycBytes);
        assertThat(result.verified()).isFalse();
    }
}

