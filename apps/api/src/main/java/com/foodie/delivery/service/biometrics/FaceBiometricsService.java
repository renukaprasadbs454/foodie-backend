package com.foodie.delivery.service.biometrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class FaceBiometricsService {

    private static final Logger log = LoggerFactory.getLogger(FaceBiometricsService.class);

    private static final double MATCH_CONFIDENCE_THRESHOLD = 0.70;
    private static final double MIN_LIVENESS_SCORE = 0.50;
    private static final double MIN_FACE_DETECTION_SCORE = 0.55;

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

        BufferedImage liveImg;
        BufferedImage kycImg;
        try {
            liveImg = ImageIO.read(new ByteArrayInputStream(livePhotoBytes));
            kycImg = ImageIO.read(new ByteArrayInputStream(kycPhotoBytes));
        } catch (Exception e) {
            log.error("Failed to decode image data", e);
            return new FaceVerificationResult(false, 0.0, "Invalid image format. Could not decode photo.");
        }

        if (liveImg == null) {
            return new FaceVerificationResult(false, 0.0, "Could not decode live camera image. Please retake the photo.");
        }
        if (kycImg == null) {
            return new FaceVerificationResult(false, 0.0, "Could not decode KYC profile photo from storage.");
        }

        liveImg = ensureRgb(liveImg);
        kycImg = ensureRgb(kycImg);

        // 1. Detect Human Face in Live Photo
        FaceDetectionResult liveDetection = detectFace(liveImg);
        log.info("verifyFace: liveDetection detected={}, faceCount={}, score={}", liveDetection.detected(), liveDetection.faceCount(), liveDetection.score());
        if (!liveDetection.detected()) {
            return new FaceVerificationResult(false, 0.0, liveDetection.message());
        }
        if (liveDetection.faceCount() > 1) {
            return new FaceVerificationResult(false, 0.0, "Multiple faces detected in the camera frame. Ensure only you are visible.");
        }

        // 2. Perform Liveness / Anti-spoof check on Live Photo
        LivenessResult liveness = evaluateLiveness(liveImg, liveDetection);
        log.info("verifyFace: liveness isLive={}, score={}, reason={}", liveness.isLive(), liveness.livenessScore(), liveness.reason());
        if (!liveness.isLive()) {
            log.warn("Liveness check failed with score {}: {}", liveness.livenessScore(), liveness.reason());
            return new FaceVerificationResult(false, 0.0, liveness.reason());
        }

        // 3. Detect Human Face in KYC Reference Photo
        FaceDetectionResult kycDetection = detectFace(kycImg);
        log.info("verifyFace: kycDetection detected={}, score={}", kycDetection.detected(), kycDetection.score());
        if (!kycDetection.detected()) {
            log.warn("KYC reference photo did not pass face detection: {}", kycDetection.message());
            return new FaceVerificationResult(false, 0.0, "KYC profile photo is unclear or missing a valid face. Please re-upload KYC photo.");
        }

        // 4. Extract and Compare Biometric Signatures between Live and KYC Faces
        FaceComparisonResult comparison = compareFaces(liveImg, liveDetection, kycImg, kycDetection);
        log.info("Face comparison result: isMatch={}, confidence={} (LBP={}, HOG={}, Geom={}, Color={})",
                comparison.isMatch(),
                String.format("%.2f", comparison.confidenceScore()),
                String.format("%.2f", comparison.lbpSimilarity()),
                String.format("%.2f", comparison.hogSimilarity()),
                String.format("%.2f", comparison.geometricSimilarity()),
                String.format("%.2f", comparison.colorSimilarity()));

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
     * Validates that an incoming selfie photo contains a valid, single human face.
     */
    public FaceDetectionResult validateFaceSelfie(byte[] photoBytes) {
        if (photoBytes == null || photoBytes.length == 0) {
            return FaceDetectionResult.failed("Live selfie capture is empty or missing.");
        }
        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(photoBytes));
        } catch (Exception e) {
            log.error("Failed to decode selfie image bytes", e);
            return FaceDetectionResult.failed("Invalid image format. Could not decode photo.");
        }
        if (img == null) {
            return FaceDetectionResult.failed("Could not decode live camera image. Please retake the photo.");
        }
        img = ensureRgb(img);
        FaceDetectionResult detection = detectFace(img);
        if (!detection.detected()) {
            return detection;
        }
        if (detection.faceCount() > 1) {
            return FaceDetectionResult.failed("Multiple faces detected in the camera frame. Ensure only you are visible.");
        }
        return detection;
    }

    /**
     * Detects human face in an image and verifies anatomical features.
     * Rejects hands, non-face objects, bodies without facial organs, and blank scenes.
     */
    public FaceDetectionResult detectFace(BufferedImage originalImg) {
        if (originalImg == null || originalImg.getWidth() < 30 || originalImg.getHeight() < 30) {
            return FaceDetectionResult.failed("Image is too small for face detection.");
        }

        BufferedImage img = resizeToStandard(originalImg, 480);
        int width = img.getWidth();
        int height = img.getHeight();

        int[][] ycbcr = new int[height][width];
        boolean[][] skinMask = new boolean[height][width];
        int skinPixelCount = 0;

        // 1. Skin Color Segmentation
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
        log.info("[FaceDiag] detectFace: origW={}, origH={}, resW={}, resH={}, skinPixels={}, skinRatio={}",
                originalImg.getWidth(), originalImg.getHeight(), width, height, skinPixelCount, String.format("%.3f", overallSkinRatio));

        if (overallSkinRatio < 0.05) {
            log.warn("[FaceDiag] detectFace rejected: skin ratio {} < 0.05", String.format("%.3f", overallSkinRatio));
            return FaceDetectionResult.failed("No human face detected. Please ensure your face is clearly visible.");
        }

        // 2. Discover Face Candidate Regions via Connected Skin Analysis
        List<Rectangle> skinBlobs = findSkinBlobs(skinMask, width, height);
        log.info("[FaceDiag] findSkinBlobs found {} candidate blobs", skinBlobs.size());

        if (skinBlobs.isEmpty()) {
            log.warn("[FaceDiag] detectFace rejected: no skin blobs found with min dimensions");
            return FaceDetectionResult.failed("No human face detected. Please center your face in the camera frame.");
        }

        List<CandidateFace> validFaces = new ArrayList<>();
        for (Rectangle blob : skinBlobs) {
            CandidateEvaluation eval = evaluateFacialAnatomy(img, ycbcr, skinMask, blob.x(), blob.y(), blob.width(), blob.height());
            if (eval.isValidFace) {
                validFaces.add(new CandidateFace(blob, eval.overallScore, eval.landmarks));
            }
        }

        log.info("[FaceDiag] evaluateFacialAnatomy valid faces count: {}", validFaces.size());

        if (validFaces.isEmpty()) {
            return FaceDetectionResult.failed("No human face detected. Please avoid hands, objects, or partial views.");
        }

        // Sort descending by score
        validFaces.sort((a, b) -> Double.compare(b.score, a.score));

        CandidateFace best = validFaces.get(0);
        if (best.score < MIN_FACE_DETECTION_SCORE) {
            return FaceDetectionResult.failed("Could not detect a clear human face. Please ensure proper lighting.");
        }

        // Map candidate bounding box and landmarks back to original image coordinates
        double scaleX = (double) originalImg.getWidth() / width;
        double scaleY = (double) originalImg.getHeight() / height;

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
                best.landmarks.eyeDistance * scaleX,
                best.landmarks.eyeMouthDistance * scaleY,
                best.landmarks.symmetryScore
        );

        int distinctFaceCount = validFaces.size();

        return new FaceDetectionResult(
                true,
                origBBox,
                best.score,
                "Human face detected",
                origLandmarks,
                distinctFaceCount
        );
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
                    if (count >= 100 && bw >= 35 && bh >= 45) {
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

        // Bound head height to avoid neck/chest distortion in connected skin blobs
        int effectiveFh = Math.min(fh, (int) (fw * 1.45));

        // Eyes zone
        int eyeTop = fy + (int) (effectiveFh * 0.15);
        int eyeBottom = fy + (int) (effectiveFh * 0.55);

        int leftEyeX1 = fx + (int) (fw * 0.10);
        int leftEyeX2 = fx + (int) (fw * 0.48);

        int rightEyeX1 = fx + (int) (fw * 0.52);
        int rightEyeX2 = fx + (int) (fw * 0.90);

        // Cheeks zone (vertical 40% - 70%)
        int cheekTop = fy + (int) (effectiveFh * 0.40);
        int cheekBottom = fy + (int) (effectiveFh * 0.70);

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

        double leftEyeLum = averageLuminance(lum, leftEyeX1, leftEyeX2, eyeTop, eyeBottom);
        double rightEyeLum = averageLuminance(lum, rightEyeX1, rightEyeX2, eyeTop, eyeBottom);
        double cheekLum = (averageLuminance(lum, leftEyeX1, leftEyeX2, cheekTop, cheekBottom) +
                averageLuminance(lum, rightEyeX1, rightEyeX2, cheekTop, cheekBottom)) / 2.0;

        Point leftEye = findDarkestCluster(lum, leftEyeX1, leftEyeX2, eyeTop, eyeBottom);
        Point rightEye = findDarkestCluster(lum, rightEyeX1, rightEyeX2, eyeTop, eyeBottom);
        Point nose = new Point((noseX1 + noseX2) / 2, (noseY1 + noseY2) / 2);
        Point mouth = findMouthCenter(img, mouthX1, mouthX2, mouthY1, mouthY2);

        // In human faces, eye regions contain dark pupils/sockets contrasting with cheek skin
        double eyeCheekContrast = cheekLum - ((leftEyeLum + rightEyeLum) / 2.0);
        boolean hasEyeValley = (eyeCheekContrast >= 1.0);

        int deltaEyeY = Math.abs(leftEye.y - rightEye.y);
        double eyeDistance = Math.hypot(rightEye.x - leftEye.x, rightEye.y - leftEye.y);
        double eyeDistanceRatio = eyeDistance / fw;

        boolean validEyeGeometry = (deltaEyeY <= effectiveFh * 0.22) && (eyeDistanceRatio >= 0.15 && eyeDistanceRatio <= 0.80);

        int eyeCenterY = (leftEye.y + rightEye.y) / 2;
        int eyeMouthDist = mouth.y - eyeCenterY;
        double eyeMouthRatio = (double) eyeMouthDist / effectiveFh;
        boolean validMouthGeometry = (eyeMouthRatio >= 0.12 && eyeMouthRatio <= 0.90) && (mouth.y > eyeCenterY + (int) (effectiveFh * 0.08));

        double symmetry = computeBilateralSymmetry(lum, fx, fy, fw, effectiveFh);
        double mouthHorizontalGradient = computeHorizontalGradient(lum, mouthX1, mouthX2, mouthY1, mouthY2);

        double eyeScore = (eyeCheekContrast >= 3.0 ? 0.5 : (eyeCheekContrast >= 0.5 ? 0.35 : 0.0)) + (validEyeGeometry ? 0.5 : 0.0);
        double geomScore = (validMouthGeometry ? 0.5 : 0.0) + Math.min(0.5, mouthHorizontalGradient / 8.0);
        double symScore = Math.max(0.0, Math.min(1.0, (symmetry - 0.25) / 0.65));

        double overall = 0.35 * eyeScore + 0.30 * geomScore + 0.35 * symScore;
        boolean isValid = hasEyeValley && validEyeGeometry && validMouthGeometry && (symmetry >= 0.25) && (overall >= MIN_FACE_DETECTION_SCORE);

        log.info("[FaceDiag] Anatomy blob(x={}, y={}, w={}, h={}): eyeCheekContrast={}, hasEyeValley={}, deltaEyeY={}, eyeDistRatio={}, validEyeGeom={}, eyeMouthDist={}, eyeMouthRatio={}, validMouthGeom={}, symmetry={}, overall={}, isValid={}",
                fx, fy, fw, fh, String.format("%.2f", eyeCheekContrast), hasEyeValley, deltaEyeY, String.format("%.2f", eyeDistanceRatio), validEyeGeometry,
                eyeMouthDist, String.format("%.2f", eyeMouthRatio), validMouthGeometry, String.format("%.2f", symmetry), String.format("%.2f", overall), isValid);

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

    private Point findDarkestCluster(int[][] lum, int x1, int x2, int y1, int y2) {
        int minSum = Integer.MAX_VALUE;
        int bestX = (x1 + x2) / 2;
        int bestY = (y1 + y2) / 2;
        int radius = 2;

        for (int y = y1 + radius; y <= y2 - radius; y++) {
            for (int x = x1 + radius; x <= x2 - radius; x++) {
                int sum = 0;
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        sum += lum[y + dy][x + dx];
                    }
                }
                if (sum < minSum) {
                    minSum = sum;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        return new Point(bestX, bestY);
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

    private double computeBilateralSymmetry(int[][] lum, int fx, int fy, int fw, int fh) {
        int centerX = fx + fw / 2;
        int halfW = fw / 2;
        int startY = fy + (int) (fh * 0.15);
        int endY = fy + (int) (fh * 0.75);

        long diffSum = 0;
        long totalPixels = 0;

        for (int y = startY; y < endY; y++) {
            for (int d = 1; d < halfW - 2; d++) {
                int leftX = centerX - d;
                int rightX = centerX + d;
                if (leftX >= 0 && rightX < lum[0].length && y < lum.length) {
                    int diff = Math.abs(lum[y][leftX] - lum[y][rightX]);
                    diffSum += diff;
                    totalPixels++;
                }
            }
        }
        if (totalPixels == 0) return 0.0;
        double avgDiff = (double) diffSum / totalPixels;
        return Math.max(0.0, 1.0 - (avgDiff / 50.0));
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

    private double averageLuminance(int[][] lum, int x1, int x2, int y1, int y2) {
        long sum = 0;
        int count = 0;
        for (int y = Math.max(0, y1); y <= Math.min(lum.length - 1, y2); y++) {
            for (int x = Math.max(0, x1); x <= Math.min(lum[0].length - 1, x2); x++) {
                sum += lum[y][x];
                count++;
            }
        }
        return count > 0 ? (double) sum / count : 128.0;
    }

    /**
     * Evaluates liveness and anti-spoofing indicators:
     * - Rejects 2D paper photos, phone screens, flat displays, and glare.
     * - Detects 3D facial curvature, natural skin frequency distribution, and color gamut.
     */
    public LivenessResult evaluateLiveness(BufferedImage img, FaceDetectionResult detection) {
        Rectangle bbox = detection.boundingBox();
        if (bbox == null) {
            return new LivenessResult(false, 0.0, "Face region unavailable for liveness check.");
        }

        BufferedImage faceCrop = cropImage(img, bbox);

        // 1. High-Frequency Texture & Screen Noise
        double laplacianVar = computeLaplacianVariance(faceCrop);
        boolean naturalSharpness = (laplacianVar >= 15.0 && laplacianVar <= 15000.0);

        // 2. 3D Ellipsoidal Luminance Curvature
        double curvature3D = compute3DCurvature(faceCrop);
        boolean has3DDepth = curvature3D >= 0.20;

        // 3. Color Gamut & Specular Glare Analysis
        double glareRatio = computeSpecularGlareRatio(faceCrop);
        boolean noSevereGlare = glareRatio < 0.12;

        // 4. Skin Chrominance Dispersion
        double chrominanceStdDev = computeSkinChrominanceVariance(faceCrop);
        boolean naturalSkinSpectrum = chrominanceStdDev >= 3.0 && chrominanceStdDev <= 75.0;

        double livenessScore = 0.30 * (naturalSharpness ? 1.0 : 0.4)
                + 0.30 * (has3DDepth ? 1.0 : 0.4)
                + 0.20 * (noSevereGlare ? 1.0 : 0.0)
                + 0.20 * (naturalSkinSpectrum ? 1.0 : 0.4);

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
        int cx = w / 2;
        int cy = h / 2;

        double centerLum = 0;
        int cCount = 0;
        for (int y = cy - h / 6; y <= cy + h / 6; y++) {
            for (int x = cx - w / 6; x <= cx + w / 6; x++) {
                centerLum += getGray(faceCrop, x, y);
                cCount++;
            }
        }
        centerLum /= Math.max(1, cCount);

        double edgeLum = 0;
        int eCount = 0;
        for (int y = 0; y < h; y += 4) {
            edgeLum += getGray(faceCrop, 2, y) + getGray(faceCrop, w - 3, y);
            eCount += 2;
        }
        edgeLum /= Math.max(1, eCount);

        double diff = centerLum - edgeLum;
        return Math.max(0.0, Math.min(1.0, (diff + 10.0) / 40.0));
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

    /**
     * Compares two detected faces using Landmark-Aligned Multi-Block Uniform LBP,
     * Spatial Histogram of Oriented Gradients (HOG), Landmark Proportions, and Skin Tone.
     */
    public FaceComparisonResult compareFaces(
            BufferedImage imgA, FaceDetectionResult detA,
            BufferedImage imgB, FaceDetectionResult detB) {

        // 1. Canonical Landmark Alignment & Illumination Normalization to standard 128x128
        BufferedImage faceA = alignAndNormalizeFace(imgA, detA);
        BufferedImage faceB = alignAndNormalizeFace(imgB, detB);

        // 2. Extract Multi-Block Uniform LBP Feature Descriptors (8x8 blocks = 64 blocks)
        double[][] lbpA = extractMultiBlockLBP(faceA);
        double[][] lbpB = extractMultiBlockLBP(faceB);
        double lbpSimilarity = computeBlockWeightedLBPSimilarity(lbpA, lbpB);

        // 3. Extract Histogram of Oriented Gradients (HOG) across 4x4 spatial cells
        double[] hogA = extractHOG(faceA);
        double[] hogB = extractHOG(faceB);
        double hogSimilarity = computeCosineSimilarity(hogA, hogB);

        // 4. Compare Facial Geometric Landmark Vectors
        double geomSimilarity = compareLandmarks(detA.landmarks(), detB.landmarks());

        // 5. Compare Skin Tone & Color Profile
        double colorSimilarity = compareSkinToneAndColor(imgA, detA, imgB, detB);

        // 6. Compute Unified Identity Matching Confidence Score
        double overallConfidence = 0.30 * lbpSimilarity + 0.30 * hogSimilarity + 0.20 * geomSimilarity + 0.20 * colorSimilarity;
        boolean isMatch = overallConfidence >= MATCH_CONFIDENCE_THRESHOLD;

        String message = isMatch
                ? "Faces match with high biometric confidence."
                : "Faces do not match. Identity verification failed.";

        log.info("Face comparison result: isMatch={}, confidence={} (LBP={}, HOG={}, Geom={}, Color={})",
                isMatch,
                String.format("%.2f", overallConfidence),
                String.format("%.2f", lbpSimilarity),
                String.format("%.2f", hogSimilarity),
                String.format("%.2f", geomSimilarity),
                String.format("%.2f", colorSimilarity));

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

    private double compareSkinToneAndColor(BufferedImage imgA, FaceDetectionResult detA, BufferedImage imgB, FaceDetectionResult detB) {
        double[] colorA = computeAverageSkinColor(imgA, detA != null ? detA.boundingBox() : null);
        double[] colorB = computeAverageSkinColor(imgB, detB != null ? detB.boundingBox() : null);
        double dR = colorA[0] - colorB[0];
        double dG = colorA[1] - colorB[1];
        double dB = colorA[2] - colorB[2];
        double dist = Math.sqrt(dR * dR + dG * dG + dB * dB);
        return Math.exp(-dist / 28.0);
    }

    private double[] computeAverageSkinColor(BufferedImage img, Rectangle bbox) {
        int width = img.getWidth();
        int height = img.getHeight();

        double sumR = 0, sumG = 0, sumB = 0;
        int count = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                if (isSkinColor(r, g, b)) {
                    sumR += r;
                    sumG += g;
                    sumB += b;
                    count++;
                }
            }
        }

        if (count == 0) return new double[]{128.0, 128.0, 128.0};
        return new double[]{sumR / count, sumG / count, sumB / count};
    }

    private boolean isSkinColor(int r, int g, int b) {
        int yVal = (int) (0.299 * r + 0.587 * g + 0.114 * b);
        int cbVal = (int) (128 - 0.168736 * r - 0.331264 * g + 0.5 * b);
        int crVal = (int) (128 + 0.5 * r - 0.418688 * g - 0.081312 * b);
        return (cbVal >= 70 && cbVal <= 140)
                && (crVal >= 125 && crVal <= 185)
                && (yVal >= 25 && yVal <= 250)
                && (r > b)
                && (r >= g - 12);
    }

    /**
     * Aligns face using detected landmarks so bilateral eyes map to standard canonical coordinates.
     */
    private BufferedImage alignAndNormalizeFace(BufferedImage img, FaceDetectionResult det) {
        LandmarkData lm = det.landmarks();
        Rectangle bbox = det.boundingBox();

        BufferedImage target = new BufferedImage(128, 128, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (lm != null && lm.leftEye() != null && lm.rightEye() != null) {
            double eyeMidX = (lm.leftEye().x() + lm.rightEye().x()) / 2.0;
            double eyeMidY = (lm.leftEye().y() + lm.rightEye().y()) / 2.0;
            double eyeDist = Math.max(15.0, lm.eyeDistance());

            double desiredEyeDist = 48.0;
            double scale = desiredEyeDist / eyeDist;
            double targetMidX = 64.0;
            double targetMidY = 46.0;

            double dx = targetMidX - eyeMidX * scale;
            double dy = targetMidY - eyeMidY * scale;

            g.translate(dx, dy);
            g.scale(scale, scale);
            g.drawImage(img, 0, 0, null);
        } else if (bbox != null) {
            g.drawImage(img, 0, 0, 128, 128, bbox.x(), bbox.y(), bbox.x() + bbox.width(), bbox.y() + bbox.height(), null);
        } else {
            g.drawImage(img, 0, 0, 128, 128, null);
        }
        g.dispose();

        return applyIlluminationNormalization(target);
    }

    private BufferedImage applyIlluminationNormalization(BufferedImage gray) {
        int w = gray.getWidth();
        int h = gray.getHeight();

        int[][] smooth = new int[h][w];
        int[] kernel = {1, 2, 1, 2, 4, 2, 1, 2, 1};
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sum = 0;
                int kIdx = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = Math.max(0, Math.min(w - 1, x + dx));
                        int ny = Math.max(0, Math.min(h - 1, y + dy));
                        sum += gray.getRaster().getSample(nx, ny, 0) * kernel[kIdx++];
                    }
                }
                smooth[y][x] = sum / 16;
            }
        }

        int[] hist = new int[256];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                hist[smooth[y][x]]++;
            }
        }

        int total = w * h;
        int clipLimit = (int) (total * 0.04);
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

        int[] cdf = new int[256];
        cdf[0] = hist[0];
        for (int i = 1; i < 256; i++) {
            cdf[i] = cdf[i - 1] + hist[i];
        }

        BufferedImage equalized = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int oldVal = smooth[y][x];
                int newVal = (int) (((float) cdf[oldVal] / total) * 255);
                equalized.getRaster().setSample(x, y, 0, Math.min(255, Math.max(0, newVal)));
            }
        }
        return equalized;
    }

    private double[][] extractMultiBlockLBP(BufferedImage face) {
        int gridSize = 8;
        int blockSize = 128 / gridSize;
        int numBins = 59;
        double[][] descriptor = new double[gridSize * gridSize][numBins];

        int blockIdx = 0;
        for (int gy = 0; gy < gridSize; gy++) {
            for (int gx = 0; gx < gridSize; gx++) {
                int bx = gx * blockSize;
                int by = gy * blockSize;

                double[] blockHist = new double[numBins];
                for (int y = by + 1; y < by + blockSize - 1; y++) {
                    for (int x = bx + 1; x < bx + blockSize - 1; x++) {
                        int center = face.getRaster().getSample(x, y, 0);
                        int thresh = Math.max(0, center - 2);
                        int pattern = 0;
                        pattern |= (face.getRaster().getSample(x - 1, y - 1, 0) >= thresh ? 1 : 0) << 7;
                        pattern |= (face.getRaster().getSample(x, y - 1, 0) >= thresh ? 1 : 0) << 6;
                        pattern |= (face.getRaster().getSample(x + 1, y - 1, 0) >= thresh ? 1 : 0) << 5;
                        pattern |= (face.getRaster().getSample(x + 1, y, 0) >= thresh ? 1 : 0) << 4;
                        pattern |= (face.getRaster().getSample(x + 1, y + 1, 0) >= thresh ? 1 : 0) << 3;
                        pattern |= (face.getRaster().getSample(x, y + 1, 0) >= thresh ? 1 : 0) << 2;
                        pattern |= (face.getRaster().getSample(x - 1, y + 1, 0) >= thresh ? 1 : 0) << 1;
                        pattern |= (face.getRaster().getSample(x - 1, y, 0) >= thresh ? 1 : 0);

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
        int cells = 4;
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
                        int dy = face.getRaster().getSample(x, nextY, 0) - face.getRaster().getSample(x, prevY, 0);

                        double magnitude = Math.hypot(dx, dy);
                        if (magnitude < 2.0) continue;

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
                if (cy == 1) weight = 2.0;
                else if (cy == 2) weight = 1.8;

                double sumSq = 0;
                for (double v : cellHist) sumSq += v * v;
                double l2 = Math.sqrt(sumSq + 25.0);
                for (int b = 0; b < numBins; b++) {
                    hog[idx++] = (cellHist[b] / l2) * weight;
                }
            }
        }
        return hog;
    }

    private double computeBlockWeightedLBPSimilarity(double[][] lbpA, double[][] lbpB) {
        double totalWeightedSim = 0.0;
        double totalWeight = 0.0;

        for (int block = 0; block < 64; block++) {
            int gy = block / 8;
            double weight = 1.0;
            if (gy == 2 || gy == 3) weight = 2.0;
            else if (gy == 4 || gy == 5) weight = 1.8;
            else if (gy == 6) weight = 1.4;

            double intersection = 0.0;
            for (int bin = 0; bin < 59; bin++) {
                intersection += Math.min(lbpA[block][bin], lbpB[block][bin]);
            }
            totalWeightedSim += weight * intersection;
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

    private double compareLandmarks(LandmarkData a, LandmarkData b) {
        if (a == null || b == null) return 0.5;
        double ratioA = a.eyeMouthDistance() / Math.max(1.0, a.eyeDistance());
        double ratioB = b.eyeMouthDistance() / Math.max(1.0, b.eyeDistance());

        double geomDiff = Math.abs(ratioA - ratioB);
        double symDiff = Math.abs(a.symmetryScore() - b.symmetryScore());

        double geomSim = Math.exp(-geomDiff * 2.5);
        double symSim = Math.exp(-symDiff * 1.5);
        return 0.80 * geomSim + 0.20 * symSim;
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

    private BufferedImage ensureRgb(BufferedImage src) {
        if (src == null) return null;
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rgb;
    }
}
