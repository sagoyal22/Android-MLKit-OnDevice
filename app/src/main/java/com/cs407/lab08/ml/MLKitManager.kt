package com.cs407.lab08.ml

import android.graphics.Bitmap
import android.graphics.Rect
import com.cs407.lab08.TextRecognitionResult 
import com.cs407.lab08.FaceDetectionResult 
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume


class MLKitManager {
    // 1. Text Recognition
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // RETURN TYPE IS NOW THE CONCRETE DATA CLASS
    suspend fun runTextRecognition(bitmap: Bitmap): TextRecognitionResult {
        val image = InputImage.fromBitmap(bitmap, 0)


        return suspendCancellableCoroutine { continuation ->
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val fullText = visionText.text ?: ""

                    // 1. Collect ALL line-level bounding boxes
                    val allRects = mutableListOf<Rect>()

                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            line.boundingBox?.let { allRects.add(it) }
                        }
                    }

                    if (allRects.isEmpty()) {
                        continuation.resume(
                            TextRecognitionResult(
                                text = "No text detected.",
                                boundingBoxes = emptyList()
                            )
                        )
                        return@addOnSuccessListener
                    }

                    val minLeft = allRects.minOf { it.left }
                    val minTop = allRects.minOf { it.top }
                    val maxRight = allRects.maxOf { it.right }
                    val maxBottom = allRects.maxOf { it.bottom }

                    val unionRect = Rect(minLeft, minTop, maxRight, maxBottom)

                    continuation.resume(
                        TextRecognitionResult(
                            text = fullText,
                            boundingBoxes = listOf(unionRect)
                        )
                    )
                }
                .addOnFailureListener { e ->
                    continuation.resume(
                        TextRecognitionResult(
                            text = "Error: ${e.message}",
                            boundingBoxes = emptyList()
                        )
                    )
                }
        }
    }

// -----------------------------------------------------------------------------------
// 2. Face Detection

    private val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .build()

    private val faceDetector = FaceDetection.getClient(faceDetectorOptions)

    //
    suspend fun runFaceDetection(bitmap: Bitmap): List<FaceDetectionResult> {
        val image = InputImage.fromBitmap(bitmap, 0)

        return suspendCancellableCoroutine { continuation ->
            // TODO: Implement face detection using faceDetector.process(image)
            // - Extract bounding box, smile probability, and contours for each face
            // - Return List<FaceDetectionResult> with face data
            // - Handle failures and return empty list
        }
    }
// -----------------------------------------------------------------------------------
// 3. Image Labeling (No change needed)

    private val imageLabeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    suspend fun runImageLabeling(bitmap: Bitmap): List<String> {
        val image = InputImage.fromBitmap(bitmap, 0)

        return suspendCancellableCoroutine { continuation ->
            // TODO: Implement image labeling using imageLabeler.process(image)
            // - Map labels to strings with format: "label (confidence%)"
            // - Return list of label strings
            // - Handle failures and return error message list
        }
    }
}
