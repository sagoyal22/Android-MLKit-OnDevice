package com.cs407.lab08.ui

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cs407.lab08.FaceDetectionResult
import com.cs407.lab08.MLKitViewModel
import com.cs407.lab08.R
import java.lang.reflect.Field


private val CustomCyan = Color(0xFF00FFFF) // Bright, high-contrast cyan
private val CustomMagenta = Color(0xFFFF00FF)   // Standard Magenta

// Helper function to get the bitmap from a drawable resource ID (unchanged)
@Composable
fun getBitmapFromDrawable(resId: Int): Bitmap? {
    val context = LocalContext.current
    return remember(resId) {
        try {
            val drawable = ContextCompat.getDrawable(context, resId)
            (drawable as? BitmapDrawable)?.bitmap
        } catch (e: Exception) {
            null
        }
    }
}

// Dynamically fetches all drawable resource IDs starting with "pic"
@Composable
fun getDynamicSampleImages(): List<Int> {
    val context = LocalContext.current
    return remember {
        val drawableClass = R.drawable::class.java
        drawableClass.fields.filter { field: Field ->
            field.name.startsWith("pic")
        }
            .mapNotNull { field: Field ->
                try {
                    field.getInt(null)
                } catch (e: Exception) {
                    null
                }
            }
            .sortedBy { resId ->
                val name = context.resources.getResourceEntryName(resId)
                name.removePrefix("pic").toIntOrNull() ?: Int.MAX_VALUE
            }
    }
}


@Composable
fun MLKitScreen(viewModel: MLKitViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // 1. Dynamic Image Loading
    val sampleImages = getDynamicSampleImages()

    if (sampleImages.isEmpty()) {
        Text("No sample images (picX.png) found in drawable resources.")
        return
    }

    // Calculate the wrapped index based on the actual image count (dynamic list size)
    val imageCount = sampleImages.size
    // This handles wrapping for both positive (next) and negative (previous) indices
    val currentImageIndex = (uiState.imageIndex % imageCount + imageCount) % imageCount

    // Use the correctly wrapped index to get the resource ID
    val currentImageResId = sampleImages[currentImageIndex]

    // Load Bitmap when index changes or the component is first composed
    LaunchedEffect(currentImageIndex) {
        val drawable = ContextCompat.getDrawable(context, currentImageResId)
        val bitmap = (drawable as? BitmapDrawable)?.bitmap
        // Update ViewModel with the correctly calculated index
        bitmap?.let { viewModel.updateImageFromUI(it, currentImageIndex) }
    }

    // 2. Camera Launchers (unchanged)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        // Use -1 for the index to signal it's a camera photo, not a sample image
        bitmap?.let { viewModel.updateImageFromUI(it, -1) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            // Fallback action if permission is denied
            viewModel.updateImageFromUI(
                uiState.currentImageBitmap ?:
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
                uiState.imageIndex)
            viewModel.runTextRecognition()
        }
    }


    // --- UI Layout ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(top = 32.dp, start = 16.dp, end = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionButton("Text") { viewModel.runTextRecognition() }
            ActionButton("Face") { viewModel.runFaceDetection() }
            ActionButton("Label") { viewModel.runImageLabeling() }
        }

        // 2. Image + Overlays
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            val currentBitmap = uiState.currentImageBitmap
            if (currentBitmap != null) {
                // Display the analyzed image
                ImageWithOverlays(
                    bitmap = currentBitmap,
                    boxes = uiState.boundingBoxes,
                    faceResults = uiState.faceResults,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Display the default image from resources
                Image(
                    painter = painterResource(id = currentImageResId),
                    // Use the calculated currentImageIndex for content description
                    contentDescription = "Sample image pic$currentImageIndex",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 3. Navigation & Camera Buttons
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionButton("Previous") { viewModel.previousImage() }
            ActionButton("Camera") {
                val permissionCheckResult = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                if (permissionCheckResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    cameraLauncher.launch(null)
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
            ActionButton("Next") { viewModel.nextImage() }
        }

        // 4. Output Text
        Text(
            text = uiState.textOutput,
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// Helper Composable for action buttons (unchanged)
@Composable
fun RowScope.ActionButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f)
    ) {
        Text(text)
    }
}

// Image Display with Custom Logic
@Composable
fun ImageWithOverlays(
    bitmap: Bitmap,
    boxes: List<Rect>,
    faceResults: List<FaceDetectionResult>,
    modifier: Modifier = Modifier
) {
    // Define the custom colors outside the Canvas for efficiency
    val boxColor = CustomCyan // For Bounding Box
    val contourColor = CustomMagenta // For detailed Contours

    Box(modifier) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Image to analyze",
            modifier = Modifier.matchParentSize()
        )

        Canvas(Modifier.matchParentSize()) {

            // 1. Calculate Scaling and Offsets for ContentScale.Fit (centering)
            val scaleX = size.width / bitmap.width.toFloat()
            val scaleY = size.height / bitmap.height.toFloat()

            val uniformScale = minOf(scaleX, scaleY)

            val scaledBitmapWidth = bitmap.width * uniformScale
            val scaledBitmapHeight = bitmap.height * uniformScale

            // Offset required to center the scaled image within the Canvas area
            val offsetX = (size.width - scaledBitmapWidth) / 2f
            val offsetY = (size.height - scaledBitmapHeight) / 2f

            // 2. Draw Bounding Boxes
            boxes.forEach { r ->
                // Apply uniformScale to dimensions and apply offset to position
                val left = r.left * uniformScale + offsetX
                val top = r.top * uniformScale + offsetY
                val w = r.width() * uniformScale
                val h = r.height() * uniformScale

                drawRect( // drawing parameters
                    color = boxColor,
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // 3. Draw Contours: iterates over faceResults
            faceResults.forEach { result -> // Loop 1: Iterates through EVERY detected face
                // Loop 2: Iterates through EVERY contour of that face
                result.contours.forEach { contourPoints ->
                    if (contourPoints.size >= 2) {
                            val path = Path()

                            // Apply uniformScale and offset to the starting point
                            path.moveTo(
                                contourPoints[0] * uniformScale + offsetX,
                                contourPoints[1] * uniformScale + offsetY
                            )

                        // Draw lines through all other contour points
                            for (i in 2 until contourPoints.size step 2) {
                                path.lineTo(
                                    contourPoints[i] * uniformScale + offsetX,
                                    contourPoints[i+1] * uniformScale + offsetY
                                )
                            }

                            path.close() // Keep path close for better accuracy

                            drawPath(
                                path = path,
                                color = contourColor , // Draw the contour
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
            }
        }
    }
}