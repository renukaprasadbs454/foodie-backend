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

    private BufferedImage createSyntheticFace(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(220, 165, 130));
        g.fillRect(0, 0, width, height);
        g.dispose();
        return img;
    }

    private byte[] toJpegBytes(BufferedImage img) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Should successfully detect valid human face selfie")
    void testDetectFace_ValidHumanFace() {
        BufferedImage face = createSyntheticFace(240, 280);
        FaceDetectionResult result = service.detectFace(face);

        assertThat(result.detected()).isTrue();
        assertThat(result.score()).isGreaterThanOrEqualTo(0.40);
        assertThat(result.landmarks()).isNotNull();
        assertThat(result.faceCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should reject empty or un-decodable image")
    void testDetectFace_RejectsNonFaceObject() {
        FaceDetectionResult result = service.detectFace(null);

        assertThat(result.detected()).isFalse();
        assertThat(result.message()).contains("No human face detected");
    }

    @Test
    @DisplayName("Should validate single live human face via validateFaceSelfie")
    void testValidateFaceSelfie_AcceptsLiveHuman() throws IOException {
        BufferedImage face = createSyntheticFace(240, 280);
        byte[] bytes = toJpegBytes(face);

        FaceDetectionResult result = service.validateFaceSelfie(bytes);
        assertThat(result.detected()).isTrue();
        assertThat(result.faceCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should reject empty image bytes via validateFaceSelfie")
    void testValidateFaceSelfie_RejectsObject() {
        FaceDetectionResult result = service.validateFaceSelfie(new byte[0]);
        assertThat(result.detected()).isFalse();
    }

    @Test
    @DisplayName("Should verify natural biological motion between temporal live camera frames")
    void testVerifyLiveMotion_AcceptsLiveMovement() throws IOException {
        BufferedImage frame1 = createSyntheticFace(240, 280);
        BufferedImage frame2 = createSyntheticFace(240, 280);

        byte[] f1Bytes = toJpegBytes(frame1);
        byte[] f2Bytes = toJpegBytes(frame2);

        MotionLivenessResult result = service.verifyLiveMotion(f1Bytes, f2Bytes, f1Bytes);
        assertThat(result.isLive()).isTrue();
    }

    @Test
    @DisplayName("Should match the same person's face photo with stored KYC photo")
    void testVerifyFace_SamePersonMatches() throws IOException {
        BufferedImage kycImg = createSyntheticFace(240, 280);
        BufferedImage liveImg = createSyntheticFace(240, 280);

        byte[] kycBytes = toJpegBytes(kycImg);
        byte[] liveBytes = toJpegBytes(liveImg);

        FaceVerificationResult result = service.verifyFace(liveBytes, kycBytes);

        assertThat(result.verified()).isTrue();
        assertThat(result.confidenceScore()).isGreaterThanOrEqualTo(0.70);
    }

    @Test
    @DisplayName("Should verify that alignAndNormalizeFace produces standard 128x128 canonical dimensions")
    void testAlignAndNormalizeFace_CanonicalStructure() {
        BufferedImage face = createSyntheticFace(240, 280);
        FaceDetectionResult detection = service.detectFace(face);
        assertThat(detection.detected()).isTrue();

        CanonicalFace canonical = service.alignAndNormalizeFace(face, detection);
        assertThat(canonical.width()).isEqualTo(128);
        assertThat(canonical.height()).isEqualTo(128);
        assertThat(canonical.grayFace().getWidth()).isEqualTo(128);
        assertThat(canonical.grayFace().getHeight()).isEqualTo(128);
    }

    @Test
    @DisplayName("Should reject empty or corrupted image data gracefully")
    void testVerifyFace_RejectsCorruptedOrEmptyImages() throws IOException {
        BufferedImage kycImg = createSyntheticFace(240, 280);
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
