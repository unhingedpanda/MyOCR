package com.yashr.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel : ViewModel() {
    var imagePreviews: List<Uri> by mutableStateOf(emptyList())
    var showLoading by mutableStateOf(false)
    var extractedText by mutableStateOf("")
    var snackbarMessage by mutableStateOf<String?>(null)

    fun processImages(context: Context, imageUris: List<Uri>) {
        imagePreviews = imageUris
        showLoading = true
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val stringBuilder = StringBuilder()

        viewModelScope.launch(Dispatchers.IO) {
            imageUris.forEachIndexed { index, uri ->
                try {
                    val image = InputImage.fromFilePath(context, uri)
                    val result = recognizer.process(image).await()
                    val fileName = File(uri.path ?: "").name

                    withContext(Dispatchers.Main) {
                        stringBuilder.append("$fileName:\n")
                        stringBuilder.append(result.text)
                        stringBuilder.append("\n\n---------------------------\n\n")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        snackbarMessage = "Error recognizing text in image ${index + 1}: ${e.localizedMessage}"
                    }
                }
            }

            withContext(Dispatchers.Main) {
                showLoading = false
                extractedText = stringBuilder.toString()
            }
        }
    }

    fun processPdf(context: Context, pdfUri: Uri) {
        showLoading = true
        imagePreviews = emptyList() // Clear image previews when processing a PDF
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val formattedTexts = mutableListOf<String>()

                        for (pageIndex in 0 until renderer.pageCount) {
                            renderer.openPage(pageIndex).use { page ->
                                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                val image = InputImage.fromBitmap(bitmap, 0)
                                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                                val result = recognizer.process(image).await()

                                val formattedPageText = result.textBlocks.joinToString(separator = "\n") { block ->
                                    val blockText = block.lines.joinToString(separator = "\n") { line ->
                                        line.elements.joinToString(separator = " ") { element -> element.text }
                                    }
                                    blockText
                                }
                                formattedTexts.add(formattedPageText)
                                bitmap.recycle() // Recycle the bitmap after use
                            }
                        }

                        withContext(Dispatchers.Main) {
                            showLoading = false
                            extractedText = formattedTexts.joinToString(separator = "\n\n")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading = false
                    snackbarMessage = "Error processing PDF: ${e.localizedMessage}"
                }
            }
        }
    }
}