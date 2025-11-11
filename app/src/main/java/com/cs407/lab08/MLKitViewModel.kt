package com.cs407.lab08

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cs407.lab08.ml.MLKitManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Data Classes for Text and Face Recognition Result (Used by both Manager and ViewModel)

data class TextRecognitionResult(
    val text: String,
    val boundingBoxes: List<Rect>
)

data class FaceDetectionResult(
    val boundingBox: Rect,
    val isSmiling: Boolean,
    val smileProbability: Float,
    val contours: List<List<Int>>
)

// UI STATE
data class MLKitUiState(
    val currentImageBitmap: Bitmap? = null,
    val imageIndex: Int = 0, // Retains index for navigation logic in the UI
    val boundingBoxes: List<Rect> = emptyList(),
    val textOutput: String = "Press a button to analyze the image.",
    val faceResults: List<FaceDetectionResult> = emptyList(),
)

class MLKitViewModel(
    private val mlKitManager: MLKitManager = MLKitManager()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MLKitUiState())
    val uiState: StateFlow<MLKitUiState> = _uiState

    // --- Image Management ---

    /**
     * Updates the current image. The newIndex is passed from the UI
     * after it calculates the wrapped index.
     */
    fun updateImageFromUI(bitmap: Bitmap, newIndex: Int) {
        _uiState.update {
            it.copy(
                currentImageBitmap = bitmap,
                imageIndex = newIndex,
                textOutput = if (newIndex != -1) "Image loaded. Select a task." else "Camera image loaded.",
                boundingBoxes = emptyList(),
                faceResults = emptyList()
            )
        }
    }

    // These functions simply request an index change. The UI handles the wrapping (modulo).
    fun nextImage() {
        _uiState.update { it.copy(imageIndex = it.imageIndex + 1) }
    }

    fun previousImage() {
        _uiState.update { it.copy(imageIndex = it.imageIndex - 1) }
    }

    // --- ML Kit Operations ---

    fun runTextRecognition() {
        val bitmap = _uiState.value.currentImageBitmap ?: return
        _uiState.update {
            it.copy(textOutput = "Analyzing text...", boundingBoxes = emptyList(), faceResults = emptyList())
        }

        viewModelScope.launch {
            val result = mlKitManager.runTextRecognition(bitmap)

            val output = "${result.text}\nFinished: Text Recognition Complete"
            _uiState.update {
                it.copy(
                    textOutput = output,
                    boundingBoxes = result.boundingBoxes
                )
            }
        }
    }

    fun runFaceDetection() {
        val bitmap = _uiState.value.currentImageBitmap ?: return
        _uiState.update {
            it.copy(textOutput = "Detecting faces...", boundingBoxes = emptyList(), faceResults = emptyList())
        }

        viewModelScope.launch {
            val faceResults = mlKitManager.runFaceDetection(bitmap)

            val boundingBoxes = faceResults.map { it.boundingBox }

            val output = buildString {
                if (faceResults.isEmpty()) {
                    append("No faces detected.\n")
                } else {
                    append("Detected ${faceResults.size} face(s).\n")
                    // Loop through ALL faces to display their statistics
                    faceResults.forEachIndexed { index, face ->
                        val smileProbPercent = String.format("%.2f", face.smileProbability * 100)

                        append("Face ${index + 1} Bounds: (${face.boundingBox.left}, ${face.boundingBox.top})\n")
                        append("Face ${index + 1} Smile: $smileProbPercent% -> ${if (face.isSmiling) "Smiling :D" else "Not Smiling :("}\n")
                    }
                }
                append("Finished: Face Detection Complete")
            }

            _uiState.update {
                it.copy(
                    textOutput = output,
                    boundingBoxes = boundingBoxes,
                    faceResults = faceResults
                )
            }
        }
    }

    fun runImageLabeling() {
        val bitmap = _uiState.value.currentImageBitmap ?: return
        _uiState.update { it.copy(textOutput = "Running Image Labeling...", boundingBoxes = emptyList(), faceResults = emptyList()) }

        viewModelScope.launch {
            val labels = mlKitManager.runImageLabeling(bitmap)

            val output = buildString {
                labels.forEach { append("$it\n") }
                append("Finished: Image Labeling Complete")
            }

            _uiState.update {
                it.copy(textOutput = output)
            }
        }
    }
}