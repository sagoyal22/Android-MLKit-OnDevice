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
            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    val results = faces.map { face ->
                        val box = face.boundingBox

                        // 2. Smiling probability
                        val smileProb = face.smilingProbability ?: 0f
                        val isSmiling = smileProb >= 0.5f

                        val contourTypes = listOf(
                            FaceContour.FACE,
                            FaceContour.LEFT_EYEBROW_TOP,
                            FaceContour.LEFT_EYEBROW_BOTTOM,
                            FaceContour.RIGHT_EYEBROW_TOP,
                            FaceContour.RIGHT_EYEBROW_BOTTOM,
                            FaceContour.LEFT_EYE,
                            FaceContour.RIGHT_EYE,
                            FaceContour.UPPER_LIP_TOP,
                            FaceContour.UPPER_LIP_BOTTOM,
                            FaceContour.LOWER_LIP_TOP,
                            FaceContour.LOWER_LIP_BOTTOM
                        )

                        val allContours = mutableListOf<List<Int>>()

                        contourTypes.forEach { type ->
                            val contour = face.getContour(type)
                            if (contour != null && contour.points.isNotEmpty()) {
                                val intList = contour.points.flatMap { point ->
                                    listOf(point.x.toInt(), point.y.toInt())
                                }
                                allContours.add(intList)
                            }
                        }


                        // 4. Result object
                        FaceDetectionResult(
                            boundingBox = box,
                            isSmiling = isSmiling,
                            smileProbability = smileProb,
                            contours = allContours
                        )
                    }

                    continuation.resume(results)
                }
                .addOnFailureListener { e ->
                    continuation.resume(emptyList())
                }
            
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
