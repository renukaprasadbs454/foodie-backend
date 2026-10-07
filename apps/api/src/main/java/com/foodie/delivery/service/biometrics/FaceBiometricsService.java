package com.foodie.delivery.service.biometrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class FaceBiometricsService {

    private static final Logger log = LoggerFactory.getLogger(FaceBiometricsService.class);

    private static final double MATCH_CONFIDENCE_THRESHOLD = 0.70;
    private static final double MIN_LIVENESS_SCORE = 0.50;
    private static final double MIN_FACE_DETECTION_SCORE = 0.40;

    public record Point(int x, int y) {}

    public record Rectangle(int x, int y, int width, int height) {
        public int centerX() { return x + width / 2; }
        public int centerY() { return y + height / 2; }
    }

    public record LandmarkData(
            Point leftEye,
            Point rightEye,
            Point nose,
            Point mouth,
            double eyeDistance,
            double eyeMouthDistance,
            double symmetryScore
    ) {}

    public record FaceDetectionResult(
            boolean detected,
            Rectangle boundingBox,
            double score,
            String message,
            LandmarkData landmarks,
            int faceCount
    ) {
        public static FaceDetectionResult failed(String message) {
            return new FaceDetectionResult(false, null, 0.0, message, null, 0);
        }
    }

    public record LivenessResult(
            boolean isLive,
            double livenessScore,
            String reason
    ) {}

    public record MotionLivenessResult(
            boolean isLive,
            double motionScore,
            double identityMatchScore,
            String message
    ) {}

    public record FaceComparisonResult(
            boolean isMatch,
            double confidenceScore,
            double lbpSimilarity,
            double hogSimilarity,
            double geometricSimilarity,
            double colorSimilarity,
            String message
    ) {}

    public record FaceVerificationResult(
            boolean verified,
            double confidenceScore,
            String message
    ) {}

    /**
     * Main entry point for verifying a live selfie against approved KYC reference photo.
     */
    public FaceVerificationResult verifyFace(byte[] livePhotoBytes, byte[] kycPhotoBytes) {
        if (livePhotoBytes == null || livePhotoBytes.length == 0) {
            return new FaceVerificationResult(false, 0.0, "Live selfie capture is empty or missing.");
        }
        if (kycPhotoBytes == null || kycPhotoBytes.length == 0) {
            return new FaceVerificationResult(false, 0.0, "Verified KYC reference photo is missing. Please complete KYC registration.");
        }

        BufferedImage liveImg = decodeAndOrientImage(livePhotoBytes);
        BufferedImage kycImg = decodeAndOrientImage(kycPhotoBytes);

        if (liveImg == null) {
            return new FaceVerificationResult(false, 0.0, "Could not decode live camera image. Please retake the photo.");
        }
        if (kycImg == null) {
            return new FaceVerificationResult(false, 0.0, "Could not decode KYC profile photo from storage.");
        }

        // 1. Detect Human Face in Live Photo
        FaceDetectionResult liveDetection = detectFace(liveImg);
        log.info("[FaceDiag] verifyFace live detection: detected={}, faceCount={}, score={}, bBox={}",
                liveDetection.detected(), liveDetection.faceCount(), String.format("%.2f", liveDetection.score()),
                liveDetection.boundingBox() != null ? liveDetection.boundingBox().toString() : "null");

        if (!liveDetection.detected()) {
            return new FaceVerificationResult(false, 0.0, liveDetection.message());
        }
        if (liveDetection.faceCount() > 1) {
            return new FaceVerificationResult(false, 0.0, "Multiple faces detected in the camera frame. Ensure only you are visible.");
        }

        // 2. Perform Liveness / Anti-spoof check on Live Photo
        LivenessResult liveness = evaluateLiveness(liveImg, liveDetection);
        log.info("[FaceDiag] verifyFace liveness: isLive={}, score={}, reason={}",
                liveness.isLive(), String.format("%.2f", liveness.livenessScore()), liveness.reason());

        if (!liveness.isLive()) {
            log.warn("Liveness check failed with score {}: {}", String.format("%.2f", liveness.livenessScore()), liveness.reason());
            return new FaceVerificationResult(false, 0.0, liveness.reason());
        }

        // 3. Detect Human Face in KYC Reference Photo
        FaceDetectionResult kycDetection = detectFace(kycImg);
        log.info("[FaceDiag] verifyFace KYC detection: detected={}, score={}, bBox={}",
                kycDetection.detected(), String.format("%.2f", kycDetection.score()),
                kycDetection.boundingBox() != null ? kycDetection.boundingBox().toString() : "null");

        if (!kycDetection.detected()) {
            log.warn("KYC reference photo did not pass face detection: {}", kycDetection.message());
            return new FaceVerificationResult(false, 0.0, "KYC profile photo is unclear or missing a valid face. Please re-upload KYC photo.");
        }

        // 4. Extract and Compare Biometric Signatures between Live and KYC Faces
        FaceComparisonResult comparison = compareFaces(liveImg, liveDetection, kycImg, kycDetection);

        // Required Structured Diagnostic Log
        log.info("MATCH DEBUG:\nLBP={}\nHOG={}\nLANDMARK={}\nCOLOR={}\nFINAL={}\nQUALITY={}\nLIVENESS={}",
                String.format("%.2f", comparison.lbpSimilarity()),
                String.format("%.2f", comparison.hogSimilarity()),
                String.format("%.2f", comparison.geometricSimilarity()),
                String.format("%.2f", comparison.colorSimilarity()),
                String.format("%.2f", comparison.confidenceScore()),
                String.format("%.2f", liveDetection.score()),
                String.format("%.2f", liveness.livenessScore()));

        if (!comparison.isMatch()) {
            return new FaceVerificationResult(
                    false,
                    comparison.confidenceScore(),
                    String.format("Face verification failed. The scanned face does not match your KYC-approved profile photo (Confidence: %.0f%%).",
                            comparison.confidenceScore() * 100));
        }

        return new FaceVerificationResult(
                true,
                comparison.confidenceScore(),
                "Face identity successfully verified against KYC photo.");
    }

    /**
     * Validates that an incoming selfie photo contains a valid, single live human face.
     */
    public FaceDetectionResult validateFaceSelfie(byte[] photoBytes) {
        if (photoBytes == null || photoBytes.length == 0) {
            return FaceDetectionResult.failed("Live selfie capture is empty or missing.");
        }
        BufferedImage img = decodeAndOrientImage(photoBytes);
        if (img == null) {
            return FaceDetectionResult.failed("Could not decode live camera image. Please retake the photo.");
        }

        FaceDetectionResult detection = detectFace(img);
        log.info("[FaceDiag] validateFaceSelfie: detected={}, faceCount={}, score={}, message={}",
                detection.detected(), detection.faceCount(), String.format("%.2f", detection.score()), detection.message());

        if (!detection.detected()) {
            return detection;
        }
        if (detection.faceCount() > 1) {
            return FaceDetectionResult.failed("Multiple faces detected in the camera frame. Ensure only you are visible.");
        }

        // Run comprehensive liveness anti-spoofing evaluation on the detected face
        LivenessResult liveness = evaluateLiveness(img, detection);
        if (!liveness.isLive()) {
            log.warn("validateFaceSelfie liveness failed: {}", liveness.reason());
            return FaceDetectionResult.failed(liveness.reason());
        }

        return detection;
    }

    /**
     * Verifies live biological movement between two temporal camera frames.
     */
    public MotionLivenessResult verifyLiveMotion(byte[] frame1Bytes, byte[] frame2Bytes) {
        if (frame1Bytes == null || frame1Bytes.length == 0 || frame2Bytes == null || frame2Bytes.length == 0) {
            return new MotionLivenessResult(false, 0.0, 0.0, "Liveness frame sequence is incomplete or missing.");
        }

        BufferedImage img1 = decodeAndOrientImage(frame1Bytes);
        BufferedImage img2 = decodeAndOrientImage(frame2Bytes);

        if (img1 == null || img2 == null) {
            return new MotionLivenessResult(false, 0.0, 0.0, "Invalid image data in motion verification sequence.");
        }

        FaceDetectionResult det1 = detectFace(img1);
        if (!det1.detected() || det1.faceCount() != 1) {
            return new MotionLivenessResult(false, 0.0, 0.0, det1.detected() ? "Multiple faces detected in initial frame." : det1.message());
        }

        FaceDetectionResult det2 = detectFace(img2);
        if (!det2.detected() || det2.faceCount() != 1) {
            return new MotionLivenessResult(false, 0.0, 0.0, det2.detected() ? "Multiple faces detected in action frame." : det2.message());
        }

        LivenessResult live1 = evaluateLiveness(img1, det1);
        if (!live1.isLive()) {
            return new MotionLivenessResult(false, 0.0, 0.0, live1.reason());
        }

        LivenessResult live2 = evaluateLiveness(img2, det2);
        if (!live2.isLive()) {
            return new MotionLivenessResult(false, 0.0, 0.0, live2.reason());
        }

        // Compare identity between Frame 1 and Frame 2
        FaceComparisonResult comp = compareFaces(img1, det1, img2, det2);
        if (comp.confidenceScore() < MATCH_CONFIDENCE_THRESHOLD) {
            return new MotionLivenessResult(false, 0.0, comp.confidenceScore(), "Identity mismatch between motion frames.");
        }

        // Compute natural temporal micro-motion between the two frames
        double motionMagnitude = computeFrameMotionDifference(img1, det1, img2, det2);
        log.info("[FaceDiag] verifyLiveMotion: identityMatch={}, motionMagnitude={}",
                String.format("%.2f", comp.confidenceScore()), String.format("%.4f", motionMagnitude));

        // Exactly identical frame (or paper held still with zero micro-variance)
        if (motionMagnitude < 0.002) {
            return new MotionLivenessResult(false, motionMagnitude, comp.confidenceScore(),
                    "Static image or photo replay detected. Please move or blink naturally in front of the camera.");
        }

        // Wildly erratic motion (paper jerked away / foreign object wave)
        if (motionMagnitude > 0.65) {
            return new MotionLivenessResult(false, motionMagnitude, comp.confidenceScore(),
                    "Excessive motion or scene disruption detected. Please keep your face centered in the camera.");
        }

        return new MotionLivenessResult(true, motionMagnitude, comp.confidenceScore(), "Live human movement verified.");
    }

    private double computeFrameMotionDifference(BufferedImage img1, FaceDetectionResult det1, BufferedImage img2, FaceDetectionResult det2) {
        Rectangle b1 = det1 != null && det1.boundingBox() != null ? det1.boundingBox() : new Rectangle(0, 0, img1.getWidth(), img1.getHeight());
        Rectangle b2 = det2 != null && det2.boundingBox() != null ? det2.boundingBox() : new Rectangle(0, 0, img2.getWidth(), img2.getHeight());

        BufferedImage face1 = resizeToStandard(cropImage(img1, b1), 128);
        BufferedImage face2 = resizeToStandard(cropImage(img2, b2), 128);

        int w = face1.getWidth();
        int h = face1.getHeight();
        long diffSum = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p1 = getGray(face1, x, y);
                int p2 = getGray(face2, x, y);
                diffSum += Math.abs(p1 - p2);
            }
        }

        double avgPixelDiff = (double) diffSum / (w * h * 255.0);

        double eyeDelta = 0.0;
        if (det1 != null && det2 != null && det1.landmarks() != null && det2.landmarks() != null) {
            double dLeft = Math.hypot(det1.landmarks().leftEye().x() - det2.landmarks().leftEye().x(),
                    det1.landmarks().leftEye().y() - det2.landmarks().leftEye().y());
            double dRight = Math.hypot(det1.landmarks().rightEye().x() - det2.landmarks().rightEye().x(),
                    det1.landmarks().rightEye().y() - det2.landmarks().rightEye().y());
            eyeDelta = (dLeft + dRight) / Math.max(20.0, det1.landmarks().eyeDistance() * 2.0);
        }

        return 0.65 * avgPixelDiff + 0.35 * Math.min(1.0, eyeDelta);
    }

    /**
     * Decodes raw image bytes, applies EXIF rotation if present, and ensures RGB color model.
     */
    public BufferedImage decodeAndOrientImage(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        BufferedImage rawImg;
        try {
            rawImg = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            log.error("Failed to decode image bytes", e);
            return null;
        }
        if (rawImg == null) return null;

        int exifOrientation = parseJpegExifOrientation(bytes);
        BufferedImage oriented = applyExifOrientation(rawImg, exifOrientation);
        return ensureRgb(oriented);
    }

    private static int parseJpegExifOrientation(byte[] bytes) {
        if (bytes == null || bytes.length < 14) return 1;
        if ((bytes[0] & 0xFF) != 0xFF || (bytes[1] & 0xFF) != 0xD8) return 1; // Not JPEG

        int offset = 2;
        while (offset + 4 < bytes.length) {
            if ((bytes[offset] & 0xFF) != 0xFF) break;
            int marker = bytes[offset + 1] & 0xFF;
            offset += 2;
            if (marker == 0xD9 || marker == 0xDA) break; // EOI or SOS

            if (offset + 2 > bytes.length) break;
            int length = ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
            if (length < 2 || offset + length > bytes.length) break;

            if (marker == 0xE1 && length >= 14) { // APP1 Exif
                if (bytes[offset + 2] == 'E' && bytes[offset + 3] == 'x' && bytes[offset + 4] == 'i' &&
                        bytes[offset + 5] == 'f' && bytes[offset + 6] == 0 && bytes[offset + 7] == 0) {
                    int tiff = offset + 8;
                    boolean littleEndian = (bytes[tiff] == 'I' && bytes[tiff + 1] == 'I');
                    int firstIfdOffset = readInt(bytes, tiff + 4, 4, littleEndian);
                    int ifdStart = tiff + firstIfdOffset;
                    if (ifdStart + 2 <= bytes.length) {
                        int entries = readInt(bytes, ifdStart, 2, littleEndian);
                        int entryOffset = ifdStart + 2;
                        for (int i = 0; i < entries && entryOffset + 12 <= bytes.length; i++, entryOffset += 12) {
                            int tag = readInt(bytes, entryOffset, 2, littleEndian);
                            if (tag == 0x0112) { // Orientation tag
                                return readInt(bytes, entryOffset + 8, 2, littleEndian);
                            }
                        }
                    }
                }
            }
            offset += length;
        }
        return 1;
    }

    private static int readInt(byte[] bytes, int offset, int length, boolean littleEndian) {
        int val = 0;
        for (int i = 0; i < length; i++) {
            int b = bytes[offset + (littleEndian ? i : (length - 1 - i))] & 0xFF;
            val |= (b << (i * 8));
        }
        return val;
    }

    private BufferedImage applyExifOrientation(BufferedImage img, int orientation) {
        if (orientation <= 1) return img;
        int w = img.getWidth();
        int h = img.getHeight();

        int angle = 0;
        boolean mirror = false;
        switch (orientation) {
            case 3 -> angle = 180;
            case 6 -> angle = 90;
            case 8 -> angle = 270;
            case 2 -> mirror = true;
            case 4 -> { mirror = true; angle = 180; }
            case 5 -> { mirror = true; angle = 90; }
            case 7 -> { mirror = true; angle = 270; }
            default -> { return img; }
        }

        int newW = (angle == 90 || angle == 270) ? h : w;
        int newH = (angle == 90 || angle == 270) ? w : h;

        BufferedImage transformed = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = transformed.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        if (angle != 0) {
            g.translate((newW - w) / 2.0, (newH - h) / 2.0);
            g.rotate(Math.toRadians(angle), w / 2.0, h / 2.0);
        }
        if (mirror) {
            g.translate(w, 0);
            g.scale(-1, 1);
        }

        g.drawImage(img, 0, 0, null);
        g.dispose();
        return transformed;
    }

    /**
     * Shared face detector.
     */
    public FaceDetectionResult detectFace(BufferedImage originalImg) {
        if (originalImg == null || originalImg.getWidth() < 30 || originalImg.getHeight() < 30) {
            return FaceDetectionResult.failed("Image is too small for face detection.");
        }

        FaceDetectionResult result = runFaceDetectionOnImage(originalImg);
        if (result.detected() && result.score() >= 0.85) {
            return result;
        }

        // Test fallback angles 90°, 270°, 180° in case image was un-oriented mobile camera
        FaceDetectionResult bestResult = result.detected() ? result : null;
        int bestAngle = 0;
        int[] fallbackAngles = {90, 270, 180};
        for (int angle : fallbackAngles) {
            BufferedImage rotated = rotateImage(originalImg, angle);
            FaceDetectionResult rotResult = runFaceDetectionOnImage(rotated);
            if (rotResult.detected()) {
                if (bestResult == null || rotResult.score() > bestResult.score() + 0.05) {
                    bestResult = rotResult;
                    bestAngle = angle;
                }
            }
        }

        if (bestResult != null && bestResult.detected()) {
            if (bestAngle != 0) {
                log.info("[FaceDiag] Face detected after {} deg fallback rotation with score {}", bestAngle, String.format("%.2f", bestResult.score()));
                return mapDetectionResultBackFromRotation(bestResult, originalImg.getWidth(), originalImg.getHeight(), bestAngle);
            }
            return bestResult;
        }

        return result;
    }

    private FaceDetectionResult mapDetectionResultBackFromRotation(FaceDetectionResult rotResult, int origW, int origH, int angle) {
        if (!rotResult.detected()) return rotResult;

        Rectangle rBox = rotResult.boundingBox();
        Rectangle mappedBox = rBox != null ? mapRectangleBack(rBox, origW, origH, angle) : null;

        LandmarkData rLm = rotResult.landmarks();
        LandmarkData mappedLm = null;
        if (rLm != null) {
            Point mappedLeftEye = mapPointBack(rLm.leftEye(), origW, origH, angle);
            Point mappedRightEye = mapPointBack(rLm.rightEye(), origW, origH, angle);
            Point mappedNose = mapPointBack(rLm.nose(), origW, origH, angle);
            Point mappedMouth = mapPointBack(rLm.mouth(), origW, origH, angle);
            mappedLm = new LandmarkData(
                    mappedLeftEye,
                    mappedRightEye,
                    mappedNose,
                    mappedMouth,
                    rLm.eyeDistance(),
                    rLm.eyeMouthDistance(),
                    rLm.symmetryScore()
            );
        }

        return new FaceDetectionResult(
                true,
                mappedBox,
                rotResult.score(),
                rotResult.message(),
                mappedLm,
                rotResult.faceCount()
        );
    }

    private Point mapPointBack(Point pt, int origW, int origH, int angle) {
        if (pt == null) return null;
        return switch (angle) {
            case 90 -> new Point(pt.y(), origH - 1 - pt.x());
            case 180 -> new Point(origW - 1 - pt.x(), origH - 1 - pt.y());
            case 270 -> new Point(origW - 1 - pt.y(), pt.x());
            default -> pt;
        };
    }

    private Rectangle mapRectangleBack(Rectangle r, int origW, int origH, int angle) {
        if (r == null) return null;
        return switch (angle) {
            case 90 -> new Rectangle(r.y(), origH - (r.x() + r.width()), r.height(), r.width());
            case 180 -> new Rectangle(origW - (r.x() + r.width()), origH - (r.y() + r.height()), r.width(), r.height());
            case 270 -> new Rectangle(origW - (r.y() + r.height()), r.x(), r.height(), r.width());
            default -> r;
        };
    }

    private FaceDetectionResult runFaceDetectionOnImage(BufferedImage originalImg) {
        BufferedImage img = resizeToStandard(ensureRgb(originalImg), 480);
        int width = img.getWidth();
        int height = img.getHeight();

        int[][] ycbcr = new int[height][width];
        boolean[][] skinMask = new boolean[height][width];
        int skinPixelCount = 0;

        // 1. Multi-modal Skin Color Segmentation
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                int yVal = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                ycbcr[y][x] = yVal;

                boolean isSkin = isSkinColor(r, g, b);
                skinMask[y][x] = isSkin;
                if (isSkin) skinPixelCount++;
            }
        }

        double overallSkinRatio = (double) skinPixelCount / (width * height);
        if (overallSkinRatio < 0.015) {
            return FaceDetectionResult.failed("No human face detected. Please ensure your face is clearly visible.");
        }

        // 2. Discover Face Candidate Regions via Connected Skin Component Analysis
        List<Rectangle> rawBlobs = findSkinBlobs(skinMask, width, height);
        List<Rectangle> skinBlobs = mergeAdjacentBlobs(rawBlobs);
        skinBlobs.sort((a, b) -> Integer.compare(b.width() * b.height(), a.width() * a.height()));

        if (skinBlobs.isEmpty()) {
            return FaceDetectionResult.failed("No human face detected. Please center your face in the camera frame.");
        }

        List<CandidateFace> validFaces = new ArrayList<>();
        int totalImageArea = width * height;

        for (Rectangle blob : skinBlobs) {
            int blobArea = blob.width() * blob.height();
            double areaFraction = (double) blobArea / totalImageArea;
            double aspectRatio = (double) blob.width() / Math.max(1, blob.height());

            // A valid selfie face should not cover 95%+ of screen as flat featureless skin
            if (areaFraction > 0.95 && overallSkinRatio > 0.90) {
                continue;
            }
            if (areaFraction < 0.015 && blobArea < 1500) {
                continue;
            }
            if (aspectRatio < 0.35 || aspectRatio > 2.5) {
                continue;
            }

            CandidateEvaluation eval = evaluateFacialAnatomy(img, ycbcr, skinMask, blob.x(), blob.y(), blob.width(), blob.height());
            if (eval.isValidFace) {
                validFaces.add(new CandidateFace(blob, eval.overallScore, eval.landmarks));
            }
        }

        if (validFaces.isEmpty()) {
            return FaceDetectionResult.failed("No human face detected. Please avoid hands, objects, or partial views.");
        }

        // Sort descending by score
        validFaces.sort((a, b) -> Double.compare(b.score, a.score));

        CandidateFace best = validFaces.get(0);
        if (best.score < MIN_FACE_DETECTION_SCORE) {
            return FaceDetectionResult.failed("Could not detect a clear human face. Please ensure proper lighting.");
        }

        // 3. Non-Maximum Suppression (NMS) & True Distinct Face Counting
        List<CandidateFace> distinctRegions = new ArrayList<>();
        for (CandidateFace candidate : validFaces) {
            boolean isDuplicateOrSubRegion = false;
            for (CandidateFace accepted : distinctRegions) {
                double iou = computeIoU(candidate.bbox, accepted.bbox);
                int cx1 = candidate.bbox.centerX();
                int cy1 = candidate.bbox.centerY();
                int cx2 = accepted.bbox.centerX();
                int cy2 = accepted.bbox.centerY();
                double centerDist = Math.hypot(cx1 - cx2, cy1 - cy2);
                double minDim = Math.min(accepted.bbox.width(), accepted.bbox.height());

                boolean isContained = isInsideOrOverlapping(candidate.bbox, accepted.bbox);
                boolean isVerticalTorso = Math.abs(cx1 - cx2) < 0.40 * accepted.bbox.width();

                if (iou > 0.05 || centerDist < 0.75 * minDim || isContained || isVerticalTorso) {
                    isDuplicateOrSubRegion = true;
                    break;
                }
            }
            if (!isDuplicateOrSubRegion) {
                distinctRegions.add(candidate);
            }
        }

        // Enforce strict secondary face criteria (must be a genuine independent person of substantial size and high confidence)
        int distinctFaceCount = 0;
        int bestArea = best.bbox.width() * best.bbox.height();

        for (CandidateFace face : distinctRegions) {
            if (distinctFaceCount == 0) {
                distinctFaceCount++;
            } else {
                int faceArea = face.bbox.width() * face.bbox.height();
                boolean isEdgeTouching = (face.bbox.x <= 4 || face.bbox.y <= 4
                        || (face.bbox.x + face.bbox.width()) >= width - 4
                        || (face.bbox.y + face.bbox.height()) >= height - 4);

                boolean substantialSize = face.bbox.width() >= 0.45 * best.bbox.width()
                        && face.bbox.height() >= 0.45 * best.bbox.height()
                        && faceArea >= 0.25 * bestArea
                        && faceArea >= 8000
                        && !isEdgeTouching;

                boolean strongScore = face.score >= 0.75;

                if (substantialSize && strongScore) {
                    distinctFaceCount++;
                }
            }
        }

        // 4. Map candidate bounding box and landmarks back to original image coordinates
        double scaleX = (double) originalImg.getWidth() / width;
        double scaleY = (double) originalImg.getHeight() / height;
        double scaleAvg = (scaleX + scaleY) / 2.0;

        Rectangle origBBox = new Rectangle(
                (int) (best.bbox.x * scaleX),
                (int) (best.bbox.y * scaleY),
                (int) (best.bbox.width * scaleX),
                (int) (best.bbox.height * scaleY)
        );

        Point origLeftEye = new Point((int) (best.landmarks.leftEye.x * scaleX), (int) (best.landmarks.leftEye.y * scaleY));
        Point origRightEye = new Point((int) (best.landmarks.rightEye.x * scaleX), (int) (best.landmarks.rightEye.y * scaleY));
        Point origNose = new Point((int) (best.landmarks.nose.x * scaleX), (int) (best.landmarks.nose.y * scaleY));
        Point origMouth = new Point((int) (best.landmarks.mouth.x * scaleX), (int) (best.landmarks.mouth.y * scaleY));

        LandmarkData origLandmarks = new LandmarkData(
                origLeftEye,
                origRightEye,
                origNose,
                origMouth,
                best.landmarks.eyeDistance * scaleAvg,
                best.landmarks.eyeMouthDistance * scaleAvg,
                best.landmarks.symmetryScore
        );

        return new FaceDetectionResult(
                true,
                origBBox,
                best.score,
                "Human face detected",
                origLandmarks,
                distinctFaceCount
        );
    }

    private BufferedImage rotateImage(BufferedImage src, int angle) {
        int w = src.getWidth();
        int h = src.getHeight();
        int newW = (angle == 90 || angle == 270) ? h : w;
        int newH = (angle == 90 || angle == 270) ? w : h;

        BufferedImage rotated = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rotated.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.translate((newW - w) / 2.0, (newH - h) / 2.0);
        g.rotate(Math.toRadians(angle), w / 2.0, h / 2.0);
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rotated;
    }

    private double computeIoU(Rectangle a, Rectangle b) {
        int xA = Math.max(a.x(), b.x());
        int yA = Math.max(a.y(), b.y());
        int xB = Math.min(a.x() + a.width(), b.x() + b.width());
        int yB = Math.min(a.y() + a.height(), b.y() + b.height());

        int interArea = Math.max(0, xB - xA) * Math.max(0, yB - yA);
        if (interArea <= 0) return 0.0;

        int boxAArea = a.width() * a.height();
        int boxBArea = b.width() * b.height();
        return (double) interArea / (boxAArea + boxBArea - interArea);
    }

    private boolean isInsideOrOverlapping(Rectangle a, Rectangle b) {
        int xOverlap = Math.max(0, Math.min(a.x() + a.width(), b.x() + b.width()) - Math.max(a.x(), b.x()));
        int yOverlap = Math.max(0, Math.min(a.y() + a.height(), b.y() + b.height()) - Math.max(a.y(), b.y()));
        if (xOverlap > 0 && yOverlap > 0) {
            return true;
        }
        int margin = 15;
        boolean aNearB = (a.centerX() >= b.x() - margin && a.centerX() <= b.x() + b.width() + margin)
                && (a.centerY() >= b.y() - margin && a.centerY() <= b.y() + b.height() + margin);
        boolean bNearA = (b.centerX() >= a.x() - margin && b.centerX() <= a.x() + a.width() + margin)
                && (b.centerY() >= a.y() - margin && b.centerY() <= a.y() + a.height() + margin);
        return aNearB || bNearA;
    }

    private List<Rectangle> mergeAdjacentBlobs(List<Rectangle> rawBlobs) {
        List<Rectangle> merged = new ArrayList<>(rawBlobs);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < merged.size(); i++) {
                for (int j = i + 1; j < merged.size(); j++) {
                    Rectangle a = merged.get(i);
                    Rectangle b = merged.get(j);

                    int xDist = Math.max(0, Math.max(a.x(), b.x()) - Math.min(a.x() + a.width(), b.x() + b.width()));
                    int yDist = Math.max(0, Math.max(a.y(), b.y()) - Math.min(a.y() + a.height(), b.y() + b.height()));

                    int xOverlap = Math.max(0, Math.min(a.x() + a.width(), b.x() + b.width()) - Math.max(a.x(), b.x()));
                    int yOverlap = Math.max(0, Math.min(a.y() + a.height(), b.y() + b.height()) - Math.max(a.y(), b.y()));

                    boolean canMerge = (xDist <= 16 && yOverlap >= 0.40 * Math.min(a.height(), b.height()))
                            || (yDist <= 16 && xOverlap >= 0.40 * Math.min(a.width(), b.width()))
                            || (xOverlap > 0 && yOverlap > 0);

                    if (canMerge) {
                        int minX = Math.min(a.x(), b.x());
                        int minY = Math.min(a.y(), b.y());
                        int maxX = Math.max(a.x() + a.width(), b.x() + b.width());
                        int maxY = Math.max(a.y() + a.height(), b.y() + b.height());

                        merged.remove(j);
                        merged.set(i, new Rectangle(minX, minY, maxX - minX, maxY - minY));
                        changed = true;
                        break;
                    }
                }
                if (changed) break;
            }
        }
        return merged;
    }

    private static class CandidateFace {
        Rectangle bbox;
        double score;
        LandmarkData landmarks;

        CandidateFace(Rectangle bbox, double score, LandmarkData landmarks) {
            this.bbox = bbox;
            this.score = score;
            this.landmarks = landmarks;
        }
    }

    private List<Rectangle> findSkinBlobs(boolean[][] skinMask, int width, int height) {
        boolean[][] visited = new boolean[height][width];
        List<Rectangle> blobs = new ArrayList<>();

        for (int y = 0; y < height; y += 2) {
            for (int x = 0; x < width; x += 2) {
                if (skinMask[y][x] && !visited[y][x]) {
                    int minX = x, maxX = x, minY = y, maxY = y;
                    int count = 0;

                    int[] qx = new int[width * height];
                    int[] qy = new int[width * height];
                    int head = 0, tail = 0;

                    qx[tail] = x;
                    qy[tail] = y;
                    tail++;
                    visited[y][x] = true;

                    while (head < tail) {
                        int cx = qx[head];
                        int cy = qy[head];
                        head++;
                        count++;

                        if (cx < minX) minX = cx;
                        if (cx > maxX) maxX = cx;
                        if (cy < minY) minY = cy;
                        if (cy > maxY) maxY = cy;

                        int[] dxs = {-2, 2, 0, 0};
                        int[] dys = {0, 0, -2, 2};
                        for (int i = 0; i < 4; i++) {
                            int nx = cx + dxs[i];
                            int ny = cy + dys[i];
                            if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                                if (skinMask[ny][nx] && !visited[ny][nx]) {
                                    visited[ny][nx] = true;
                                    qx[tail] = nx;
                                    qy[tail] = ny;
                                    tail++;
                                }
                            }
                        }
                    }

                    int bw = maxX - minX + 1;
                    int bh = maxY - minY + 1;
                    if (count >= 100 && bw >= 36 && bh >= 40) {
                        blobs.add(new Rectangle(minX, minY, bw, bh));
                    }
                }
            }
        }
        return blobs;
    }

    private static class CandidateEvaluation {
        boolean isValidFace;
        double overallScore;
        LandmarkData landmarks;

        CandidateEvaluation(boolean isValidFace, double overallScore, LandmarkData landmarks) {
            this.isValidFace = isValidFace;
            this.overallScore = overallScore;
            this.landmarks = landmarks;
        }
    }

    private CandidateEvaluation evaluateFacialAnatomy(
            BufferedImage img,
            int[][] lum,
            boolean[][] skinMask,
            int fx, int fy, int fw, int fh) {

        int effectiveFh = Math.min(fh, (int) (fw * 1.45));

        // Eyes zone (vertical 12% - 58%)
        int eyeTop = fy + (int) (effectiveFh * 0.12);
        int eyeBottom = fy + (int) (effectiveFh * 0.58);

        int leftEyeX1 = fx + (int) (fw * 0.08);
        int leftEyeX2 = fx + (int) (fw * 0.48);

        int rightEyeX1 = fx + (int) (fw * 0.52);
        int rightEyeX2 = fx + (int) (fw * 0.92);

        // Nose zone (vertical 35% - 75%, x: 35% - 65%)
        int noseX1 = fx + (int) (fw * 0.35);
        int noseX2 = fx + (int) (fw * 0.65);
        int noseY1 = fy + (int) (effectiveFh * 0.35);
        int noseY2 = fy + (int) (effectiveFh * 0.75);

        // Mouth zone (vertical 50% - 95%, x: 18% - 82%)
        int mouthX1 = fx + (int) (fw * 0.18);
        int mouthX2 = fx + (int) (fw * 0.82);
        int mouthY1 = fy + (int) (effectiveFh * 0.50);
        int mouthY2 = fy + (int) (effectiveFh * 0.95);

        Point[] eyes = findBilateralEyes(lum, leftEyeX1, leftEyeX2, rightEyeX1, rightEyeX2, eyeTop, eyeBottom, fw, effectiveFh);
        Point leftEye = eyes[0];
        Point rightEye = eyes[1];
        Point nose = new Point((noseX1 + noseX2) / 2, (noseY1 + noseY2) / 2);
        Point mouth = findMouthCenter(img, mouthX1, mouthX2, mouthY1, mouthY2);

        double leftEyeLum = averageLuminanceAround(lum, leftEye.x, leftEye.y, 6);
        double rightEyeLum = averageLuminanceAround(lum, rightEye.x, rightEye.y, 6);
        double leftCheekLum = averageLuminanceAround(lum, leftEye.x, Math.min(lum.length - 1, leftEye.y + (int) (effectiveFh * 0.22)), 8);
        double rightCheekLum = averageLuminanceAround(lum, rightEye.x, Math.min(lum.length - 1, rightEye.y + (int) (effectiveFh * 0.22)), 8);
        double cheekLum = (leftCheekLum + rightCheekLum) / 2.0;

        double eyeCheekContrast = cheekLum - ((leftEyeLum + rightEyeLum) / 2.0);

        int deltaEyeY = Math.abs(leftEye.y - rightEye.y);
        double eyeDistance = Math.hypot(rightEye.x - leftEye.x, rightEye.y - leftEye.y);
        double eyeDistanceRatio = eyeDistance / fw;
        double aspectRatio = (double) fw / effectiveFh;

        int eyeCenterX = (leftEye.x + rightEye.x) / 2;
        int eyeCenterY = (leftEye.y + rightEye.y) / 2;
        int faceCenterX = fx + fw / 2;
        double eyeAlignmentOffset = Math.abs(eyeCenterX - faceCenterX) / (double) fw;
        boolean validEyeCentering = eyeAlignmentOffset <= 0.30;

        boolean validAspectRatio = (aspectRatio >= 0.38 && aspectRatio <= 1.75);
        boolean validEyeGeometry = validAspectRatio
                && (deltaEyeY <= Math.max(20, (int) (eyeDistance * 0.45)))
                && (deltaEyeY <= effectiveFh * 0.25)
                && (eyeDistanceRatio >= 0.18 && eyeDistanceRatio <= 0.92)
                && validEyeCentering;

        double eyeMouthDist = Math.hypot(mouth.x - eyeCenterX, mouth.y - eyeCenterY);
        double eyeMouthRatio = eyeMouthDist / effectiveFh;
        double mouthCentering = Math.abs(mouth.x - eyeCenterX) / Math.max(1.0, eyeDistance);
        boolean validMouthGeometry = (eyeMouthRatio >= 0.15 && eyeMouthRatio <= 0.80)
                && (mouth.y >= eyeCenterY)
                && (mouthCentering <= 0.35);

        double symmetry = computeBilateralSymmetry(lum, fx, fy, fw, effectiveFh, leftEye, rightEye);
        double mouthHorizontalGradient = computeHorizontalGradient(lum, mouthX1, mouthX2, mouthY1, mouthY2);

        double eyeScore = (eyeCheekContrast >= 5.0 ? 0.5 : (eyeCheekContrast >= 0.8 ? 0.35 : 0.20)) + (validEyeGeometry ? 0.5 : 0.0);
        double geomScore = (validMouthGeometry ? 0.5 : 0.20) + Math.min(0.5, mouthHorizontalGradient / 6.0);
        double symScore = Math.max(0.10, Math.min(1.0, (symmetry - 0.05) / 0.80));

        double overall = 0.35 * eyeScore + 0.35 * geomScore + 0.30 * symScore;
        boolean isValid = validEyeGeometry && validMouthGeometry && (symmetry >= 0.05) && (overall >= MIN_FACE_DETECTION_SCORE);

        LandmarkData landmarks = new LandmarkData(
                leftEye,
                rightEye,
                nose,
                mouth,
                eyeDistance,
                eyeMouthDist,
                symmetry
        );

        return new CandidateEvaluation(isValid, overall, landmarks);
    }

    private Point[] findBilateralEyes(int[][] lum, int leftX1, int leftX2, int rightX1, int rightX2, int topY, int bottomY, int fw, int effectiveFh) {
        int maxDeltaY = Math.max(16, (int) (effectiveFh * 0.22));
        int radius = Math.max(2, (int) (fw * 0.025));

        Point leftDarkest = findDarkestClusterInBand(lum, leftX1, leftX2, topY, bottomY, radius);
        Point rightDarkest = findDarkestClusterInBand(lum, rightX1, rightX2, topY, bottomY, radius);

        int dY = Math.abs(leftDarkest.y - rightDarkest.y);
        double dist = Math.hypot(rightDarkest.x - leftDarkest.x, rightDarkest.y - leftDarkest.y);
        double distRatio = dist / fw;

        if (dY <= maxDeltaY && distRatio >= 0.25 && distRatio <= 0.88) {
            return new Point[]{leftDarkest, rightDarkest};
        }

        int bestSum = Integer.MAX_VALUE;
        Point bestLeft = leftDarkest;
        Point bestRight = rightDarkest;

        for (int yL = topY + radius; yL <= bottomY - radius; yL += 2) {
            Point leftCandidate = findDarkestClusterInBand(lum, leftX1, leftX2, yL - 2, yL + 2, radius);
            for (int yR = Math.max(topY + radius, yL - maxDeltaY); yR <= Math.min(bottomY - radius, yL + maxDeltaY); yR += 2) {
                Point rightCandidate = findDarkestClusterInBand(lum, rightX1, rightX2, yR - 2, yR + 2, radius);

                int curDY = Math.abs(leftCandidate.y - rightCandidate.y);
                double curDist = Math.hypot(rightCandidate.x - leftCandidate.x, rightCandidate.y - leftCandidate.y);
                double curDistRatio = curDist / fw;

                if (curDY <= maxDeltaY && curDistRatio >= 0.25 && curDistRatio <= 0.88) {
                    int sumL = getClusterLuminance(lum, leftCandidate.x, leftCandidate.y, radius);
                    int sumR = getClusterLuminance(lum, rightCandidate.x, rightCandidate.y, radius);
                    int totalSum = sumL + sumR;

                    if (totalSum < bestSum) {
                        bestSum = totalSum;
                        bestLeft = leftCandidate;
                        bestRight = rightCandidate;
                    }
                }
            }
        }

        return new Point[]{bestLeft, bestRight};
    }

    private Point findDarkestClusterInBand(int[][] lum, int x1, int x2, int y1, int y2, int radius) {
        int minY = Math.max(radius, y1);
        int maxY = Math.min(lum.length - 1 - radius, y2);
        int minX = Math.max(radius, x1);
        int maxX = Math.min(lum[0].length - 1 - radius, x2);

        double bestScore = -1e9;
        int bestX = (minX + maxX) / 2;
        int bestY = (minY + maxY) / 2;
        int scleraOffset = Math.max(4, radius * 2);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                int sum = 0;
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        sum += lum[y + dy][x + dx];
                    }
                }
                int count = (2 * radius + 1) * (2 * radius + 1);
                double avgLum = (double) sum / count;

                int leftX = Math.max(0, x - scleraOffset);
                int rightX = Math.min(lum[0].length - 1, x + scleraOffset);
                double flankLum = (lum[y][leftX] + lum[y][rightX]) / 2.0;
                double scleraContrast = Math.max(0.0, flankLum - avgLum);

                double score = (255.0 - avgLum) + 1.2 * scleraContrast;
                if (score > bestScore) {
                    bestScore = score;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        return new Point(bestX, bestY);
    }

    private int getClusterLuminance(int[][] lum, int cx, int cy, int radius) {
        int sum = 0;
        for (int dy = -radius; dy <= radius; dy++) {
            int y = Math.max(0, Math.min(lum.length - 1, cy + dy));
            for (int dx = -radius; dx <= radius; dx++) {
                int x = Math.max(0, Math.min(lum[0].length - 1, cx + dx));
                sum += lum[y][x];
            }
        }
        return sum;
    }

    private double averageLuminanceAround(int[][] lum, int cx, int cy, int radius) {
        long sum = 0;
        int count = 0;
        int minY = Math.max(0, cy - radius);
        int maxY = Math.min(lum.length - 1, cy + radius);
        int minX = Math.max(0, cx - radius);
        int maxX = Math.min(lum[0].length - 1, cx + radius);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                sum += lum[y][x];
                count++;
            }
        }
        return count > 0 ? (double) sum / count : 128.0;
    }

    private Point findMouthCenter(BufferedImage img, int x1, int x2, int y1, int y2) {
        int bestX = (x1 + x2) / 2;
        int bestY = (y1 + y2) / 2;
        double maxRedness = -1000;

        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                double redness = r - (g + b) / 2.0;
                if (redness > maxRedness) {
                    maxRedness = redness;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        return new Point(bestX, bestY);
    }

    private double computeBilateralSymmetry(int[][] lum, int fx, int fy, int fw, int fh, Point leftEye, Point rightEye) {
        int halfW = fw / 2;
        int startY = Math.max(0, fy + (int) (fh * 0.15));
        int endY = Math.min(lum.length, fy + (int) (fh * 0.75));

        int nominalCenter = (leftEye != null && rightEye != null && leftEye.x != rightEye.x)
                ? (leftEye.x + rightEye.x) / 2
                : (fx + halfW);

        double bestSymmetry = 0.0;
        int searchRange = Math.max(2, (int) (fw * 0.08));

        for (int offset = -searchRange; offset <= searchRange; offset += 2) {
            int centerX = nominalCenter + offset;
            long diffSum = 0;
            long totalPixels = 0;

            for (int y = startY; y < endY; y++) {
                for (int d = 2; d < halfW - 2; d += 2) {
                    int leftX = centerX - d;
                    int rightX = centerX + d;
                    if (leftX >= 0 && rightX < lum[0].length && y >= 0 && y < lum.length) {
                        int valL = lum[y][leftX];
                        int valR = lum[y][rightX];
                        diffSum += Math.abs(valL - valR);
                        totalPixels++;
                    }
                }
            }

            if (totalPixels > 0) {
                double avgDiff = (double) diffSum / totalPixels;
                double sym = Math.max(0.0, 1.0 - (avgDiff / 160.0));
                if (sym > bestSymmetry) {
                    bestSymmetry = sym;
                }
            }
        }

        return bestSymmetry;
    }

    private double computeHorizontalGradient(int[][] lum, int x1, int x2, int y1, int y2) {
        double gradSum = 0;
        int count = 0;
        for (int y = y1 + 1; y < y2 - 1; y++) {
            for (int x = x1; x < x2; x++) {
                int dy = Math.abs(lum[y + 1][x] - lum[y - 1][x]);
                gradSum += dy;
                count++;
            }
        }
        return count > 0 ? gradSum / count : 0;
    }

    /**
     * Evaluates liveness and anti-spoofing indicators.
     */
    public LivenessResult evaluateLiveness(BufferedImage img, FaceDetectionResult detection) {
        Rectangle bbox = detection.boundingBox();
        if (bbox == null) {
            return new LivenessResult(false, 0.0, "Face region unavailable for liveness check.");
        }

        BufferedImage faceCrop = cropImage(img, bbox);

        // 1. High-Frequency Texture & Screen Noise
        double laplacianVar = computeLaplacianVariance(faceCrop);
        boolean naturalSharpness = (laplacianVar >= 15.0 && laplacianVar <= 18000.0);

        // 2. 3D Ellipsoidal Luminance Curvature
        double curvature3D = compute3DCurvature(faceCrop);
        boolean has3DDepth = curvature3D >= 0.20;

        // 3. Color Gamut & Specular Glare Analysis
        double glareRatio = computeSpecularGlareRatio(faceCrop);
        boolean noSevereGlare = glareRatio < 0.10;

        // 4. Skin Chrominance Dispersion
        double chrominanceStdDev = computeSkinChrominanceVariance(faceCrop);
        boolean naturalSkinSpectrum = chrominanceStdDev >= 3.0 && chrominanceStdDev <= 75.0;

        double livenessScore = 0.30 * (naturalSharpness ? 1.0 : 0.3)
                + 0.30 * (has3DDepth ? 1.0 : 0.3)
                + 0.20 * (noSevereGlare ? 1.0 : 0.0)
                + 0.20 * (naturalSkinSpectrum ? 1.0 : 0.3);

        if (!noSevereGlare) {
            return new LivenessResult(false, livenessScore, "Excessive glare or screen reflection detected. Please avoid bright screen glare.");
        }
        if (!has3DDepth && !naturalSharpness) {
            return new LivenessResult(false, livenessScore, "2D photo or screen spoofing detected. Please capture a live face in person.");
        }
        if (livenessScore < MIN_LIVENESS_SCORE) {
            return new LivenessResult(false, livenessScore, "Liveness check failed. Please ensure adequate lighting and look straight at the camera.");
        }

        return new LivenessResult(true, livenessScore, "Liveness verified");
    }

    private double computeLaplacianVariance(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        double mean = 0;
        int count = 0;
        double[] lap = new double[w * h];

        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int c = getGray(img, x, y);
                int up = getGray(img, x, y - 1);
                int down = getGray(img, x, y + 1);
                int left = getGray(img, x - 1, y);
                int right = getGray(img, x + 1, y);
                double val = Math.abs(4 * c - up - down - left - right);
                lap[count] = val;
                mean += val;
                count++;
            }
        }
        if (count == 0) return 0;
        mean /= count;

        double varSum = 0;
        for (int i = 0; i < count; i++) {
            double diff = lap[i] - mean;
            varSum += diff * diff;
        }
        return varSum / count;
    }

    private double compute3DCurvature(BufferedImage faceCrop) {
        int w = faceCrop.getWidth();
        int h = faceCrop.getHeight();
        if (w < 10 || h < 10) return 0.5;

        int cx = w / 2;
        int cy = h / 2;

        double centerLum = 0;
        int cCount = 0;
        for (int y = cy - h / 6; y <= cy + h / 6; y++) {
            for (int x = cx - w / 8; x <= cx + w / 8; x++) {
                if (x >= 0 && x < w && y >= 0 && y < h) {
                    centerLum += getGray(faceCrop, x, y);
                    cCount++;
                }
            }
        }
        centerLum /= Math.max(1, cCount);

        double cheekLum = 0;
        int eCount = 0;
        int leftCheekX = cx - (int) (w * 0.28);
        int rightCheekX = cx + (int) (w * 0.28);
        for (int y = cy - h / 6; y <= cy + h / 6; y++) {
            if (y >= 0 && y < h) {
                if (leftCheekX >= 0 && leftCheekX < w) {
                    cheekLum += getGray(faceCrop, leftCheekX, y);
                    eCount++;
                }
                if (rightCheekX >= 0 && rightCheekX < w) {
                    cheekLum += getGray(faceCrop, rightCheekX, y);
                    eCount++;
                }
            }
        }
        cheekLum /= Math.max(1, eCount);

        double diff = centerLum - cheekLum;
        return Math.max(0.0, Math.min(1.0, (diff + 15.0) / 30.0));
    }

    private double computeSpecularGlareRatio(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int glarePixels = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (r > 248 && g > 248 && b > 248) {
                    glarePixels++;
                }
            }
        }
        return (double) glarePixels / (w * h);
    }

    private double computeSkinChrominanceVariance(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        double meanCr = 0;
        int count = 0;
        int[] crVals = new int[w * h];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int cr = (int) (128 + 0.5 * r - 0.418688 * g - 0.081312 * b);
                crVals[count] = cr;
                meanCr += cr;
                count++;
            }
        }
        if (count == 0) return 0;
        meanCr /= count;

        double varSum = 0;
        for (int i = 0; i < count; i++) {
            double d = crVals[i] - meanCr;
            varSum += d * d;
        }
        return Math.sqrt(varSum / count);
    }

    public record CanonicalFace(
            BufferedImage grayFace,
            BufferedImage colorFace,
            int width,
            int height,
            Point leftEye,
            Point rightEye,
            Rectangle faceBox,
            Point origLeftEye,
            Point origRightEye,
            Rectangle origFaceBox,
            double rotationAngleDeg,
            double scaleFactor,
            int sourceWidth,
            int sourceHeight
    ) {
        public CanonicalFace(
                BufferedImage grayFace,
                BufferedImage colorFace,
                int width,
                int height,
                Point leftEye,
                Point rightEye,
                Rectangle faceBox,
                Point origLeftEye,
                Point origRightEye,
                Rectangle origFaceBox
        ) {
            this(grayFace, colorFace, width, height, leftEye, rightEye, faceBox, origLeftEye, origRightEye, origFaceBox, 0.0, 1.0, 128, 128);
        }
    }

    /**
     * Compares two detected faces using Landmark-Aligned Multi-Block Uniform LBP,
     * Fine-Grained HOG across spatial cells, Landmark Proportions, and Lighting-Invariant Inner Face Chrominance.
     */
    public FaceComparisonResult compareFaces(
            BufferedImage imgA, FaceDetectionResult detA,
            BufferedImage imgB, FaceDetectionResult detB) {

        // 1. Canonical Landmark Alignment & Illumination Normalization to standard 128x128
        CanonicalFace canonicalLive = alignCanonicalFace(imgA, detA);
        CanonicalFace canonicalKyc = alignCanonicalFace(imgB, detB);

        // Required safe diagnostic logging for BOTH KYC Baseline and LIVE Photo
        log.info("[FaceDiag] KYC Baseline Processing:\n" +
                        "source dimensions: {}x{}\n" +
                        "detected face bbox: {}\n" +
                        "source landmarks: leftEye={}, rightEye={}, nose={}, mouth={}, eyeDistance={}\n" +
                        "canonical dimensions: {}x{}\n" +
                        "canonical face bbox: [x={}, y={}, w={}, h={}]\n" +
                        "eye positions after alignment: left=({}, {}), right=({}, {})\n" +
                        "rotation angle: {} deg\n" +
                        "scale factor: {}",
                canonicalKyc.sourceWidth(), canonicalKyc.sourceHeight(),
                detB != null && detB.boundingBox() != null ? detB.boundingBox().toString() : "null",
                detB != null && detB.landmarks() != null ? detB.landmarks().leftEye() : "null",
                detB != null && detB.landmarks() != null ? detB.landmarks().rightEye() : "null",
                detB != null && detB.landmarks() != null ? detB.landmarks().nose() : "null",
                detB != null && detB.landmarks() != null ? detB.landmarks().mouth() : "null",
                detB != null && detB.landmarks() != null ? String.format("%.2f", detB.landmarks().eyeDistance()) : "0.0",
                canonicalKyc.width(), canonicalKyc.height(),
                canonicalKyc.faceBox().x(), canonicalKyc.faceBox().y(), canonicalKyc.faceBox().width(), canonicalKyc.faceBox().height(),
                canonicalKyc.leftEye().x(), canonicalKyc.leftEye().y(),
                canonicalKyc.rightEye().x(), canonicalKyc.rightEye().y(),
                String.format("%.2f", canonicalKyc.rotationAngleDeg()),
                String.format("%.4f", canonicalKyc.scaleFactor()));

        log.info("[FaceDiag] LIVE Capture Processing:\n" +
                        "source dimensions: {}x{}\n" +
                        "detected face bbox: {}\n" +
                        "source landmarks: leftEye={}, rightEye={}, nose={}, mouth={}, eyeDistance={}\n" +
                        "canonical dimensions: {}x{}\n" +
                        "canonical face bbox: [x={}, y={}, w={}, h={}]\n" +
                        "eye positions after alignment: left=({}, {}), right=({}, {})\n" +
                        "rotation angle: {} deg\n" +
                        "scale factor: {}",
                canonicalLive.sourceWidth(), canonicalLive.sourceHeight(),
                detA != null && detA.boundingBox() != null ? detA.boundingBox().toString() : "null",
                detA != null && detA.landmarks() != null ? detA.landmarks().leftEye() : "null",
                detA != null && detA.landmarks() != null ? detA.landmarks().rightEye() : "null",
                detA != null && detA.landmarks() != null ? detA.landmarks().nose() : "null",
                detA != null && detA.landmarks() != null ? detA.landmarks().mouth() : "null",
                detA != null && detA.landmarks() != null ? String.format("%.2f", detA.landmarks().eyeDistance()) : "0.0",
                canonicalLive.width(), canonicalLive.height(),
                canonicalLive.faceBox().x(), canonicalLive.faceBox().y(), canonicalLive.faceBox().width(), canonicalLive.faceBox().height(),
                canonicalLive.leftEye().x(), canonicalLive.leftEye().y(),
                canonicalLive.rightEye().x(), canonicalLive.rightEye().y(),
                String.format("%.2f", canonicalLive.rotationAngleDeg()),
                String.format("%.4f", canonicalLive.scaleFactor()));

        // Compute and log safe preprocessing diagnostic values for both KYC and LIVE canonical faces
        CanonicalDiagnostics diagKyc = computeCanonicalDiagnostics(canonicalKyc, detB);
        CanonicalDiagnostics diagLive = computeCanonicalDiagnostics(canonicalLive, detA);

        log.info("[FaceDiag] Canonical Preprocessing Diagnostics:\n" +
                        "KYC:  meanLum={}, stdDev={}, contrast=[{}, {}], chrom=[r={}, g={}, Cb={}, Cr={}], geomRatios=[eyeMouth={}, eyeNose={}, triAngle={} deg]\n" +
                        "LIVE: meanLum={}, stdDev={}, contrast=[{}, {}], chrom=[r={}, g={}, Cb={}, Cr={}], geomRatios=[eyeMouth={}, eyeNose={}, triAngle={} deg]",
                String.format("%.1f", diagKyc.meanLum), String.format("%.1f", diagKyc.stdDevLum), diagKyc.minLum, diagKyc.maxLum,
                String.format("%.3f", diagKyc.normR), String.format("%.3f", diagKyc.normG), String.format("%.1f", diagKyc.cb), String.format("%.1f", diagKyc.cr),
                String.format("%.2f", diagKyc.eyeMouthRatio), String.format("%.2f", diagKyc.eyeNoseRatio), String.format("%.1f", diagKyc.triangleAngleDeg),
                String.format("%.1f", diagLive.meanLum), String.format("%.1f", diagLive.stdDevLum), diagLive.minLum, diagLive.maxLum,
                String.format("%.3f", diagLive.normR), String.format("%.3f", diagLive.normG), String.format("%.1f", diagLive.cb), String.format("%.1f", diagLive.cr),
                String.format("%.2f", diagLive.eyeMouthRatio), String.format("%.2f", diagLive.eyeNoseRatio), String.format("%.1f", diagLive.triangleAngleDeg));

        // 2. Extract Multi-Block Uniform LBP Feature Descriptors with Multi-Scale Spatial Pooling & Soft Thresholding
        double[][] lbpA = extractMultiBlockLBP(canonicalLive.grayFace());
        double[][] lbpB = extractMultiBlockLBP(canonicalKyc.grayFace());
        double lbpSimilarity = computeBlockWeightedLBPSimilarity(lbpA, lbpB);

        // 3. Extract Histogram of Oriented Gradients (HOG) across 6x6 spatial cells with L2-Hys normalization
        double[] hogA = extractHOG(canonicalLive.grayFace());
        double[] hogB = extractHOG(canonicalKyc.grayFace());
        double hogSimilarity = computeCosineSimilarity(hogA, hogB);

        // 4. Compare Facial Geometric Landmark Vectors & Anatomical Proportions
        double geomSimilarity = compareFacialGeometry(canonicalLive, detA, canonicalKyc, detB);

        // 5. Compare Inner-Face Skin Chrominance Profile from the canonically aligned faces
        double colorSimilarity = compareSkinToneAndColor(canonicalLive, canonicalKyc);

        // 6. Compute Unified Identity Matching Confidence Score
        double overallConfidence = 0.35 * lbpSimilarity + 0.30 * hogSimilarity + 0.20 * geomSimilarity + 0.15 * colorSimilarity;
        boolean isMatch = overallConfidence >= MATCH_CONFIDENCE_THRESHOLD;

        String message = isMatch
                ? "Faces match with high biometric confidence."
                : "Faces do not match. Identity verification failed.";

        log.info("[FaceDiag] Biometric Matching Output:\nLBP={}\nHOG={}\nGEOM={}\nCOLOR={}\nFINAL={}",
                String.format("%.2f", lbpSimilarity),
                String.format("%.2f", hogSimilarity),
                String.format("%.2f", geomSimilarity),
                String.format("%.2f", colorSimilarity),
                String.format("%.2f", overallConfidence));

        return new FaceComparisonResult(
                isMatch,
                overallConfidence,
                lbpSimilarity,
                hogSimilarity,
                geomSimilarity,
                colorSimilarity,
                message
        );
    }

    private static class CanonicalDiagnostics {
        double meanLum;
        double stdDevLum;
        int minLum;
        int maxLum;
        double normR;
        double normG;
        double cb;
        double cr;
        double eyeMouthRatio;
        double eyeNoseRatio;
        double triangleAngleDeg;
    }

    private CanonicalDiagnostics computeCanonicalDiagnostics(CanonicalFace face, FaceDetectionResult det) {
        CanonicalDiagnostics d = new CanonicalDiagnostics();
        BufferedImage gray = face.grayFace();
        long sum = 0;
        int min = 255;
        int max = 0;
        int w = gray.getWidth();
        int h = gray.getHeight();
        int[] vals = new int[w * h];
        int count = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int val = gray.getRaster().getSample(x, y, 0);
                vals[count++] = val;
                sum += val;
                if (val < min) min = val;
                if (val > max) max = val;
            }
        }
        d.meanLum = (double) sum / Math.max(1, count);
        d.minLum = min;
        d.maxLum = max;

        double varSum = 0;
        for (int i = 0; i < count; i++) {
            double diff = vals[i] - d.meanLum;
            varSum += diff * diff;
        }
        d.stdDevLum = Math.sqrt(varSum / Math.max(1, count));

        double[] chrom = computeCanonicalFaceSkinChrominance(face.colorFace());
        d.normR = chrom[0];
        d.normG = chrom[1];
        d.cb = chrom[2];
        d.cr = chrom[3];

        LandmarkData lm = det != null ? det.landmarks() : null;
        if (lm != null && lm.eyeDistance() > 1.0) {
            d.eyeMouthRatio = lm.eyeMouthDistance() / lm.eyeDistance();
            double eyeMidY = (lm.leftEye().y() + lm.rightEye().y()) / 2.0;
            double noseY = lm.nose() != null ? lm.nose().y() : eyeMidY + lm.eyeDistance() * 0.5;
            d.eyeNoseRatio = Math.abs(noseY - eyeMidY) / lm.eyeDistance();
            d.triangleAngleDeg = 2.0 * Math.toDegrees(Math.atan2(lm.eyeDistance() / 2.0, Math.max(1.0, lm.eyeMouthDistance())));
        } else {
            d.eyeMouthRatio = 1.05;
            d.eyeNoseRatio = 0.52;
            d.triangleAngleDeg = 48.0;
        }

        return d;
    }

    /**
     * Lighting-invariant skin chromaticity comparison using normalized rg-chromaticity,
     * YCbCr chrominance, and HSV hue/saturation extracted directly from the canonically aligned 128x128 inner face region.
     */
    private double compareSkinToneAndColor(CanonicalFace faceA, CanonicalFace faceB) {
        double[] chromA = computeCanonicalFaceSkinChrominance(faceA.colorFace());
        double[] chromB = computeCanonicalFaceSkinChrominance(faceB.colorFace());

        // 1. Normalized rg-chromaticity distance (r = R/(R+G+B), g = G/(R+G+B)) - illumination intensity invariant
        double dNormR = chromA[0] - chromB[0];
        double dNormG = chromA[1] - chromB[1];
        double rgDist = Math.hypot(dNormR, dNormG);
        double rgSim = Math.exp(-rgDist / 0.08);

        // 2. YCbCr Chrominance distance (Cb, Cr isolate hue/saturation from luminance Y)
        double dCb = chromA[2] - chromB[2];
        double dCr = chromA[3] - chromB[3];
        double cbCrDist = Math.hypot(dCb, dCr);
        double cbCrSim = Math.exp(-cbCrDist / 26.0);

        // 3. HSV Skin Hue & Saturation distance
        double hueA = chromA[4];
        double hueB = chromB[4];
        double dHue = Math.min(Math.abs(hueA - hueB), 360.0 - Math.abs(hueA - hueB));
        double satA = chromA[5];
        double satB = chromB[5];
        double dSat = Math.abs(satA - satB);
        double hsvSim = Math.exp(-(dHue / 30.0 + dSat / 0.40) / 2.0);

        // 4. Skin undertone ratio (warm vs cool undertone indicator)
        double undertoneA = chromA[6];
        double undertoneB = chromB[6];
        double dUndertone = Math.abs(undertoneA - undertoneB);
        double undertoneSim = Math.exp(-dUndertone / 1.0);

        return 0.35 * rgSim + 0.35 * cbCrSim + 0.15 * hsvSim + 0.15 * undertoneSim;
    }

    private double[] computeCanonicalFaceSkinChrominance(BufferedImage canonicalColorFace) {
        if (canonicalColorFace == null) {
            return new double[]{0.40, 0.33, 115.0, 145.0, 25.0, 0.45, 1.2};
        }

        double sumNormR = 0, sumNormG = 0;
        double sumCb = 0, sumCr = 0;
        double sumHue = 0, sumSat = 0;
        double sumUndertone = 0;
        int count = 0;

        // Sample symmetrical inner face regions on canonical 128x128 face
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                boolean inInnerFaceRegion = (x >= 24 && x <= 50 && y >= 52 && y <= 82)   // Left cheek
                        || (x >= 78 && x <= 104 && y >= 52 && y <= 82)                   // Right cheek
                        || (x >= 38 && x <= 90 && y >= 18 && y <= 36)                    // Forehead
                        || (x >= 52 && x <= 76 && y >= 50 && y <= 72);                   // Nose bridge

                if (inInnerFaceRegion) {
                    int rgb = canonicalColorFace.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;

                    if (isSkinColor(r, g, b)) {
                        double totalRgb = Math.max(1, r + g + b);
                        sumNormR += (double) r / totalRgb;
                        sumNormG += (double) g / totalRgb;

                        double cb = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b;
                        double cr = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b;
                        sumCb += cb;
                        sumCr += cr;

                        float[] hsv = Color.RGBtoHSB(r, g, b, null);
                        sumHue += hsv[0] * 360.0;
                        sumSat += hsv[1];

                        double undertone = (r - g) / Math.max(3.0, (double) Math.abs(g - b));
                        sumUndertone += Math.max(-5.0, Math.min(5.0, undertone));

                        count++;
                    }
                }
            }
        }

        if (count == 0) {
            for (int y = 44; y <= 84; y++) {
                for (int x = 44; x <= 84; x++) {
                    int rgb = canonicalColorFace.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    double totalRgb = Math.max(1, r + g + b);
                    sumNormR += (double) r / totalRgb;
                    sumNormG += (double) g / totalRgb;
                    double cb = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b;
                    double cr = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b;
                    sumCb += cb;
                    sumCr += cr;
                    float[] hsv = Color.RGBtoHSB(r, g, b, null);
                    sumHue += hsv[0] * 360.0;
                    sumSat += hsv[1];
                    double undertone = (r - g) / Math.max(3.0, (double) Math.abs(g - b));
                    sumUndertone += Math.max(-5.0, Math.min(5.0, undertone));
                    count++;
                }
            }
        }

        return new double[]{
                sumNormR / count,
                sumNormG / count,
                sumCb / count,
                sumCr / count,
                sumHue / count,
                sumSat / count,
                sumUndertone / count
        };
    }

    private boolean isSkinColor(int r, int g, int b) {
        int maxRgb = Math.max(r, Math.max(g, b));
        int minRgb = Math.min(r, Math.min(g, b));
        if (maxRgb - minRgb < 5) {
            return false;
        }

        int yVal = (int) (0.299 * r + 0.587 * g + 0.114 * b);
        int cbVal = (int) (128 - 0.168736 * r - 0.331264 * g + 0.5 * b);
        int crVal = (int) (128 + 0.5 * r - 0.418688 * g - 0.081312 * b);

        boolean ycbcrSkin = (cbVal >= 55 && cbVal <= 160)
                && (crVal >= 110 && crVal <= 200)
                && (yVal >= 10 && yVal <= 250);

        boolean rgbSkin = (r + 20 >= b) && (r >= g - 30) && (r - Math.min(g, b) >= 4);

        return ycbcrSkin && rgbSkin;
    }

    /**
     * Standardizes and canonically aligns face using detected landmarks so bilateral eyes
     * map to horizontal orientation and standard canonical coordinates (42, 48) and (86, 48) with inter-eye distance 44px.
     */
    public CanonicalFace alignAndNormalizeFace(BufferedImage srcImg, FaceDetectionResult det) {
        return alignCanonicalFace(srcImg, det);
    }

    private CanonicalFace alignCanonicalFace(BufferedImage srcImg, FaceDetectionResult det) {
        BufferedImage rgbImg = ensureRgb(srcImg);
        LandmarkData lm = det != null ? det.landmarks() : null;
        Rectangle bbox = det != null ? det.boundingBox() : null;

        BufferedImage colorTarget = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D gColor = colorTarget.createGraphics();
        gColor.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        gColor.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        gColor.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        BufferedImage grayTarget = new BufferedImage(128, 128, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D gGray = grayTarget.createGraphics();
        gGray.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        gGray.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        gGray.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Point origLeft = (lm != null && lm.leftEye() != null) ? lm.leftEye() : (bbox != null ? new Point(bbox.x + bbox.width / 3, bbox.y + bbox.height / 3) : new Point(42, 48));
        Point origRight = (lm != null && lm.rightEye() != null) ? lm.rightEye() : (bbox != null ? new Point(bbox.x + 2 * bbox.width / 3, bbox.y + bbox.height / 3) : new Point(86, 48));
        Rectangle origBox = bbox != null ? bbox : new Rectangle(0, 0, rgbImg.getWidth(), rgbImg.getHeight());

        double theta = 0.0;
        double scale = 1.0;

        if (lm != null && lm.leftEye() != null && lm.rightEye() != null && lm.eyeDistance() >= 10.0) {
            double eyeMidX = (lm.leftEye().x() + lm.rightEye().x()) / 2.0;
            double eyeMidY = (lm.leftEye().y() + lm.rightEye().y()) / 2.0;
            double eyeDist = Math.max(10.0, Math.hypot(lm.rightEye().x() - lm.leftEye().x(), lm.rightEye().y() - lm.leftEye().y()));

            double dX = lm.rightEye().x() - lm.leftEye().x();
            double dY = lm.rightEye().y() - lm.leftEye().y();
            theta = Math.atan2(dY, dX);

            double desiredEyeDist = 44.0;
            scale = desiredEyeDist / eyeDist;
            double targetMidX = 64.0;
            double targetMidY = 48.0;

            AffineTransform at = new AffineTransform();
            at.translate(targetMidX, targetMidY);
            at.scale(scale, scale);
            at.rotate(-theta);
            at.translate(-eyeMidX, -eyeMidY);

            gColor.drawImage(rgbImg, at, null);
            gGray.drawImage(rgbImg, at, null);
        } else if (bbox != null) {
            int padX = (int) (bbox.width * 0.10);
            int padY = (int) (bbox.height * 0.10);
            int sx1 = Math.max(0, bbox.x - padX);
            int sy1 = Math.max(0, bbox.y - padY);
            int sx2 = Math.min(rgbImg.getWidth(), bbox.x + bbox.width + padX);
            int sy2 = Math.min(rgbImg.getHeight(), bbox.y + bbox.height + padY);

            gColor.drawImage(rgbImg, 0, 0, 128, 128, sx1, sy1, sx2, sy2, null);
            gGray.drawImage(rgbImg, 0, 0, 128, 128, sx1, sy1, sx2, sy2, null);
        } else {
            gColor.drawImage(rgbImg, 0, 0, 128, 128, null);
            gGray.drawImage(rgbImg, 0, 0, 128, 128, null);
        }

        gColor.dispose();
        gGray.dispose();

        BufferedImage normalizedGray = applyIlluminationNormalization(grayTarget);

        return new CanonicalFace(
                normalizedGray,
                colorTarget,
                128,
                128,
                new Point(42, 48),
                new Point(86, 48),
                new Rectangle(0, 0, 128, 128),
                origLeft,
                origRight,
                origBox,
                Math.toDegrees(theta),
                scale,
                rgbImg.getWidth(),
                rgbImg.getHeight()
        );
    }

    /**
     * Contrast-Limited Adaptive Histogram Equalization (CLAHE) illumination normalization
     * over 8x8 spatial tiles with bilinear interpolation to remove side shadows and normalize lighting.
     */
    private BufferedImage applyIlluminationNormalization(BufferedImage gray) {
        int w = gray.getWidth();
        int h = gray.getHeight();
        int grid = 8;
        int tileW = w / grid;
        int tileH = h / grid;

        int[][][] cdfs = new int[grid][grid][256];
        int tilePixels = tileW * tileH;
        int clipLimit = Math.max(4, (int) (tilePixels * 0.05));

        for (int gy = 0; gy < grid; gy++) {
            for (int gx = 0; gx < grid; gx++) {
                int[] hist = new int[256];
                int startX = gx * tileW;
                int startY = gy * tileH;

                for (int y = startY; y < startY + tileH; y++) {
                    for (int x = startX; x < startX + tileW; x++) {
                        hist[gray.getRaster().getSample(x, y, 0)]++;
                    }
                }

                int excess = 0;
                for (int i = 0; i < 256; i++) {
                    if (hist[i] > clipLimit) {
                        excess += hist[i] - clipLimit;
                        hist[i] = clipLimit;
                    }
                }
                int addPerBin = excess / 256;
                for (int i = 0; i < 256; i++) {
                    hist[i] += addPerBin;
                }

                int sum = 0;
                for (int i = 0; i < 256; i++) {
                    sum += hist[i];
                    cdfs[gy][gx][i] = (int) (((double) sum / tilePixels) * 255);
                }
            }
        }

        BufferedImage equalized = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double gx = ((double) x / tileW) - 0.5;
                double gy = ((double) y / tileH) - 0.5;

                int gx1 = Math.max(0, Math.min(grid - 1, (int) Math.floor(gx)));
                int gx2 = Math.max(0, Math.min(grid - 1, gx1 + 1));
                int gy1 = Math.max(0, Math.min(grid - 1, (int) Math.floor(gy)));
                int gy2 = Math.max(0, Math.min(grid - 1, gy1 + 1));

                double fx = Math.max(0.0, Math.min(1.0, gx - gx1));
                double fy = Math.max(0.0, Math.min(1.0, gy - gy1));

                int val = gray.getRaster().getSample(x, y, 0);
                double v11 = cdfs[gy1][gx1][val];
                double v12 = cdfs[gy1][gx2][val];
                double v21 = cdfs[gy2][gx1][val];
                double v22 = cdfs[gy2][gx2][val];

                double top = (1.0 - fx) * v11 + fx * v12;
                double bottom = (1.0 - fx) * v21 + fx * v22;
                int newVal = (int) ((1.0 - fy) * top + fy * bottom);

                equalized.getRaster().setSample(x, y, 0, Math.min(255, Math.max(0, newVal)));
            }
        }
        return equalized;
    }

    /**
     * Extracts multi-scale uniform Local Binary Patterns (LBP) with noise-tolerant thresholding
     * across 8x8 fine blocks (64 blocks) and 4x4 regional blocks (16 blocks) for scale stability.
     */
    private double[][] extractMultiBlockLBP(BufferedImage face) {
        int gridSize = 8;
        int blockSize = 128 / gridSize; // 16x16
        int numBins = 59;
        int numFineBlocks = gridSize * gridSize; // 64
        int numRegionalBlocks = 16;              // 4x4 = 16
        int totalBlocks = numFineBlocks + numRegionalBlocks; // 80 blocks total
        double[][] descriptor = new double[totalBlocks][numBins];

        int noiseThreshold = 2; // Noise margin to prevent flat skin sensor jitter

        // 1. Fine 8x8 blocks
        int blockIdx = 0;
        for (int gy = 0; gy < gridSize; gy++) {
            for (int gx = 0; gx < gridSize; gx++) {
                int bx = gx * blockSize;
                int by = gy * blockSize;

                double[] blockHist = new double[numBins];
                for (int y = by + 1; y < by + blockSize - 1; y++) {
                    for (int x = bx + 1; x < bx + blockSize - 1; x++) {
                        int center = face.getRaster().getSample(x, y, 0);
                        int pattern = 0;

                        // Thresholded LBP
                        pattern |= ((face.getRaster().getSample(x - 1, y - 1, 0) - center) >= -noiseThreshold ? 1 : 0) << 7;
                        pattern |= ((face.getRaster().getSample(x,     y - 1, 0) - center) >= -noiseThreshold ? 1 : 0) << 6;
                        pattern |= ((face.getRaster().getSample(x + 1, y - 1, 0) - center) >= -noiseThreshold ? 1 : 0) << 5;
                        pattern |= ((face.getRaster().getSample(x + 1, y,     0) - center) >= -noiseThreshold ? 1 : 0) << 4;
                        pattern |= ((face.getRaster().getSample(x + 1, y + 1, 0) - center) >= -noiseThreshold ? 1 : 0) << 3;
                        pattern |= ((face.getRaster().getSample(x,     y + 1, 0) - center) >= -noiseThreshold ? 1 : 0) << 2;
                        pattern |= ((face.getRaster().getSample(x - 1, y + 1, 0) - center) >= -noiseThreshold ? 1 : 0) << 1;
                        pattern |= ((face.getRaster().getSample(x - 1, y,     0) - center) >= -noiseThreshold ? 1 : 0);

                        int uBin = mapToUniformLBPBin(pattern);
                        blockHist[uBin]++;
                    }
                }

                double norm = 0;
                for (double v : blockHist) norm += v;
                if (norm > 0) {
                    for (int i = 0; i < numBins; i++) {
                        blockHist[i] /= norm;
                    }
                }
                descriptor[blockIdx++] = blockHist;
            }
        }

        // 2. Regional 4x4 blocks (32x32 pixels each) for spatial shift tolerance
        int regionalBlockSize = 32;
        for (int rgy = 0; rgy < 4; rgy++) {
            for (int rgx = 0; rgx < 4; rgx++) {
                int rbx = rgx * regionalBlockSize;
                int rby = rgy * regionalBlockSize;

                double[] regHist = new double[numBins];
                for (int y = rby + 2; y < rby + regionalBlockSize - 2; y += 2) {
                    for (int x = rbx + 2; x < rbx + regionalBlockSize - 2; x += 2) {
                        int center = face.getRaster().getSample(x, y, 0);
                        int pattern = 0;

                        pattern |= ((face.getRaster().getSample(x - 2, y - 2, 0) - center) >= -noiseThreshold ? 1 : 0) << 7;
                        pattern |= ((face.getRaster().getSample(x,     y - 2, 0) - center) >= -noiseThreshold ? 1 : 0) << 6;
                        pattern |= ((face.getRaster().getSample(x + 2, y - 2, 0) - center) >= -noiseThreshold ? 1 : 0) << 5;
                        pattern |= ((face.getRaster().getSample(x + 2, y,     0) - center) >= -noiseThreshold ? 1 : 0) << 4;
                        pattern |= ((face.getRaster().getSample(x + 2, y + 2, 0) - center) >= -noiseThreshold ? 1 : 0) << 3;
                        pattern |= ((face.getRaster().getSample(x,     y + 2, 0) - center) >= -noiseThreshold ? 1 : 0) << 2;
                        pattern |= ((face.getRaster().getSample(x - 2, y + 2, 0) - center) >= -noiseThreshold ? 1 : 0) << 1;
                        pattern |= ((face.getRaster().getSample(x - 2, y,     0) - center) >= -noiseThreshold ? 1 : 0);

                        int uBin = mapToUniformLBPBin(pattern);
                        regHist[uBin]++;
                    }
                }

                double norm = 0;
                for (double v : regHist) norm += v;
                if (norm > 0) {
                    for (int i = 0; i < numBins; i++) {
                        regHist[i] /= norm;
                    }
                }
                descriptor[blockIdx++] = regHist;
            }
        }

        return descriptor;
    }

    private static final int[] UNIFORM_LBP_TABLE = new int[256];
    static {
        int nextBin = 0;
        for (int i = 0; i < 256; i++) {
            int transitions = 0;
            for (int b = 0; b < 8; b++) {
                int bitCurrent = (i >> b) & 1;
                int bitNext = (i >> ((b + 1) % 8)) & 1;
                if (bitCurrent != bitNext) transitions++;
            }
            if (transitions <= 2) {
                UNIFORM_LBP_TABLE[i] = nextBin++;
            } else {
                UNIFORM_LBP_TABLE[i] = 58;
            }
        }
    }

    private int mapToUniformLBPBin(int pattern) {
        return UNIFORM_LBP_TABLE[pattern & 0xFF];
    }

    private double[] extractHOG(BufferedImage face) {
        int cells = 6;
        int cellSize = 128 / cells;
        int numBins = 8;
        double[] hog = new double[cells * cells * numBins];

        int idx = 0;
        for (int cy = 0; cy < cells; cy++) {
            for (int cx = 0; cx < cells; cx++) {
                int startX = cx * cellSize;
                int startY = cy * cellSize;

                double[] cellHist = new double[numBins];
                for (int y = startY; y < startY + cellSize; y++) {
                    for (int x = startX; x < startX + cellSize; x++) {
                        int prevX = Math.max(0, x - 1);
                        int nextX = Math.min(127, x + 1);
                        int prevY = Math.max(0, y - 1);
                        int nextY = Math.min(127, y + 1);

                        int dx = face.getRaster().getSample(nextX, y, 0) - face.getRaster().getSample(prevX, y, 0);
                        int dy = face.getRaster().getSample(x, nextY, 0) - face.getRaster().getSample(prevX, y, 0);

                        double magnitude = Math.hypot(dx, dy);
                        if (magnitude < 1.0) continue;

                        double theta = Math.toDegrees(Math.atan2(dy, dx));
                        if (theta < 0) theta += 180.0;
                        while (theta >= 180.0) theta -= 180.0;

                        double binPos = (theta / 180.0) * numBins;
                        int bin1 = ((int) Math.floor(binPos)) % numBins;
                        int bin2 = (bin1 + 1) % numBins;
                        double frac = binPos - Math.floor(binPos);

                        cellHist[bin1] += magnitude * (1.0 - frac);
                        cellHist[bin2] += magnitude * frac;
                    }
                }

                double weight = 1.0;
                if ((cy >= 1 && cy <= 4) && (cx >= 1 && cx <= 4)) {
                    weight = 2.0; // Inner facial features (eyes, nose, mouth)
                }

                double sumSq = 0;
                for (double v : cellHist) sumSq += v * v;
                double l2 = Math.sqrt(sumSq + 1e-6);
                for (int b = 0; b < numBins; b++) {
                    double val = (cellHist[b] / l2) * weight;
                    hog[idx++] = Math.min(0.2, val); // L2-Hys clipping
                }
            }
        }

        // Global L2 normalization
        double globalSumSq = 0;
        for (double v : hog) globalSumSq += v * v;
        double globalL2 = Math.sqrt(globalSumSq + 1e-6);
        for (int i = 0; i < hog.length; i++) {
            hog[i] /= globalL2;
        }

        return hog;
    }

    /**
     * Computes block-weighted LBP similarity using Bhattacharyya histogram affinity coefficient
     * and Histogram Intersection over multi-scale fine and regional blocks.
     */
    private double computeBlockWeightedLBPSimilarity(double[][] lbpA, double[][] lbpB) {
        double totalWeightedSim = 0.0;
        double totalWeight = 0.0;

        int totalBlocks = Math.min(lbpA.length, lbpB.length);

        for (int block = 0; block < totalBlocks; block++) {
            double weight = 1.0;
            if (block < 64) {
                int gy = block / 8;
                if (gy == 2 || gy == 3) weight = 2.5;      // Eyes zone
                else if (gy == 4 || gy == 5) weight = 2.0; // Nose zone
                else if (gy == 6) weight = 1.8;            // Mouth zone
            } else {
                int rgy = (block - 64) / 4;
                if (rgy == 1 || rgy == 2) weight = 2.2;    // Regional face center
            }

            double bhattacharyya = 0.0;
            double intersection = 0.0;
            double chiSquare = 0.0;

            for (int bin = 0; bin < 59; bin++) {
                double a = lbpA[block][bin];
                double b = lbpB[block][bin];
                bhattacharyya += Math.sqrt(Math.max(0.0, a * b));
                intersection += Math.min(a, b);
                double diff = a - b;
                double sum = a + b;
                if (sum > 1e-6) {
                    chiSquare += (diff * diff) / sum;
                }
            }

            double chiSim = Math.max(0.0, 1.0 - 0.5 * chiSquare);
            double blockSim = 0.45 * bhattacharyya + 0.35 * intersection + 0.20 * chiSim;

            totalWeightedSim += weight * blockSim;
            totalWeight += weight;
        }

        return totalWeight > 0 ? (totalWeightedSim / totalWeight) : 0.0;
    }

    private double computeCosineSimilarity(double[] v1, double[] v2) {
        double dot = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dot += v1[i] * v2[i];
            norm1 += v1[i] * v1[i];
            norm2 += v2[i] * v2[i];
        }
        if (norm1 <= 0 || norm2 <= 0) return 0.0;
        double cos = dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
        return Math.max(0.0, Math.min(1.0, cos));
    }

    /**
     * Compares multi-dimensional scale-invariant, rotation-invariant facial landmark proportions
     * and facial triangle geometry.
     */
    private double compareFacialGeometry(CanonicalFace faceA, FaceDetectionResult detA, CanonicalFace faceB, FaceDetectionResult detB) {
        LandmarkData lmA = detA != null ? detA.landmarks() : null;
        LandmarkData lmB = detB != null ? detB.landmarks() : null;

        if (lmA == null || lmB == null) {
            return 0.50;
        }

        double eyeDistA = Math.max(1.0, lmA.eyeDistance());
        double eyeDistB = Math.max(1.0, lmB.eyeDistance());

        // 1. Eye-to-Mouth distance normalized by inter-eye distance
        double eyeMouthRatioA = lmA.eyeMouthDistance() / eyeDistA;
        double eyeMouthRatioB = lmB.eyeMouthDistance() / eyeDistB;
        double dEyeMouth = Math.abs(eyeMouthRatioA - eyeMouthRatioB);
        double sEyeMouth = Math.exp(-dEyeMouth / 0.35);

        // 2. Eye-to-Nose Euclidean distance ratio (rotation invariant)
        double eyeMidXA = (lmA.leftEye().x() + lmA.rightEye().x()) / 2.0;
        double eyeMidYA = (lmA.leftEye().y() + lmA.rightEye().y()) / 2.0;
        double eyeMidXB = (lmB.leftEye().x() + lmB.rightEye().x()) / 2.0;
        double eyeMidYB = (lmB.leftEye().y() + lmB.rightEye().y()) / 2.0;

        double eyeNoseDistA = lmA.nose() != null
                ? Math.hypot(lmA.nose().x() - eyeMidXA, lmA.nose().y() - eyeMidYA)
                : eyeDistA * 0.52;
        double eyeNoseDistB = lmB.nose() != null
                ? Math.hypot(lmB.nose().x() - eyeMidXB, lmB.nose().y() - eyeMidYB)
                : eyeDistB * 0.52;

        double eyeNoseRatioA = eyeNoseDistA / eyeDistA;
        double eyeNoseRatioB = eyeNoseDistB / eyeDistB;
        double dEyeNose = Math.abs(eyeNoseRatioA - eyeNoseRatioB);
        double sEyeNose = Math.exp(-dEyeNose / 0.30);

        // 3. Facial Triangle Internal Angle (LeftEye-Mouth-RightEye)
        double angleA = 2.0 * Math.atan2(eyeDistA / 2.0, Math.max(1.0, lmA.eyeMouthDistance()));
        double angleB = 2.0 * Math.atan2(eyeDistB / 2.0, Math.max(1.0, lmB.eyeMouthDistance()));
        double dAngle = Math.abs(angleA - angleB);
        double sAngle = Math.exp(-dAngle / 0.28);

        // 4. Facial Symmetry
        double dSym = Math.abs(lmA.symmetryScore() - lmB.symmetryScore());
        double sSym = Math.exp(-dSym / 0.35);

        return 0.35 * sEyeMouth + 0.30 * sEyeNose + 0.25 * sAngle + 0.10 * sSym;
    }

    private double compareLandmarks(LandmarkData a, LandmarkData b) {
        if (a == null || b == null) return 0.5;

        double eyeDistA = Math.max(1.0, a.eyeDistance());
        double eyeDistB = Math.max(1.0, b.eyeDistance());

        double eyeMouthRatioA = a.eyeMouthDistance() / eyeDistA;
        double eyeMouthRatioB = b.eyeMouthDistance() / eyeDistB;
        double dEyeMouth = Math.abs(eyeMouthRatioA - eyeMouthRatioB);
        double sEyeMouth = Math.exp(-dEyeMouth / 0.35);

        double dSym = Math.abs(a.symmetryScore() - b.symmetryScore());
        double sSym = Math.exp(-dSym / 0.35);

        return 0.75 * sEyeMouth + 0.25 * sSym;
    }

    private BufferedImage cropImage(BufferedImage src, Rectangle r) {
        int x = Math.max(0, r.x);
        int y = Math.max(0, r.y);
        int w = Math.min(src.getWidth() - x, r.width);
        int h = Math.min(src.getHeight() - y, r.height);
        return src.getSubimage(x, y, w, h);
    }

    private int getGray(BufferedImage img, int x, int y) {
        int rgb = img.getRGB(x, y);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (int) (0.299 * r + 0.587 * g + 0.114 * b);
    }

    private BufferedImage resizeToStandard(BufferedImage original, int maxDim) {
        int w = original.getWidth();
        int h = original.getHeight();
        if (w <= maxDim && h <= maxDim) return original;

        double scale = (double) maxDim / Math.max(w, h);
        int newW = (int) (w * scale);
        int newH = (int) (h * scale);

        BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, newW, newH, null);
        g.dispose();
        return resized;
    }

    public BufferedImage ensureRgb(BufferedImage src) {
        if (src == null) return null;
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rgb;
    }
}
