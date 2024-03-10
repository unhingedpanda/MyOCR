package com.yashr.ocr

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var pickImageLauncher: ActivityResultLauncher<String>
    private lateinit var pickPdfLauncher: ActivityResultLauncher<String>
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>
    private lateinit var requestCameraPermissionLauncher: ActivityResultLauncher<String>
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeLaunchers()

        setContent {
            MyAppContent(viewModel = viewModel, pickImage = { pickImageLauncher.launch("image/*") }, pickPdf = { pickPdfLauncher.launch("application/pdf") }, takePicture = this::checkPermissionsAndTakePicture)
        }
    }

    private fun initializeLaunchers() {
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                viewModel.processImages(this, uris)
            }
        }

        pickPdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { viewModel.processPdf(this, it) }
        }

        takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                viewModel.imagePreviews.firstOrNull()?.let { uri ->
                    viewModel.processImages(this, listOf(uri))
                }
            }
        }

        requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                takePicture()
            } else {
                viewModel.snackbarMessage = "Camera permission is required to take pictures for OCR"
            }
        }
    }

    private fun checkPermissionsAndTakePicture() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                takePicture()
            }
            else -> {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun takePicture() {
        val photoFile = createImageFile() ?: return
        // Ensure the authority here matches the one in your AndroidManifest.xml
        val photoUri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.file-provider", photoFile)
        viewModel.imagePreviews = listOf(photoUri)
        takePictureLauncher.launch(photoUri)
    }

    private fun createImageFile(): File? {
        return try {
            val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            File.createTempFile("JPEG_${System.currentTimeMillis()}_", ".jpg", storageDir)
        } catch (ex: Exception) {
            viewModel.snackbarMessage = "Error occurred while creating the file"
            null
        }
    }

    @Composable
    fun MyAppContent(viewModel: MainViewModel, pickImage: () -> Unit, pickPdf: () -> Unit, takePicture: () -> Unit) {
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Button(onClick = pickImage) {
                    Text("Select Image")
                }
                Button(onClick = pickPdf) {
                    Text("Select PDF")
                }
                Button(onClick = takePicture) {
                    Text("Take a Picture")
                }

                if (viewModel.showLoading) {
                    CircularProgressIndicator()
                } else if (viewModel.extractedText.isNotEmpty()) {
                    val scrollState = rememberScrollState()
                    Column(modifier = Modifier.verticalScroll(scrollState)) {
                        Text(
                            text = viewModel.extractedText,
                            modifier = Modifier.padding(16.dp)
                        )
                        val clipboardManager = LocalClipboardManager.current
                        Button(onClick = {
                            clipboardManager.setText(AnnotatedString(viewModel.extractedText))
                        }) {
                            Text("Copy to Clipboard")
                        }
                    }
                }

                // Only display image previews if there are items in the imagePreviews list
                if (viewModel.imagePreviews.isNotEmpty()) {
                    viewModel.imagePreviews.forEach { uri ->
                        Image(
                            painter = rememberAsyncImagePainter(model = uri),
                            contentDescription = "Selected Content",
                            modifier = Modifier
                                .size(200.dp)
                                .padding(8.dp)
                        )
                    }
                }
            }

            LaunchedEffect(viewModel.snackbarMessage) {
                viewModel.snackbarMessage?.let { message ->
                    scope.launch {
                        snackbarHostState.showSnackbar(message)
                        viewModel.snackbarMessage = null // Reset the message to avoid repeated snackbar displays
                    }
                }
            }
        }
    }
}
