package com.example.puratechsolutions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.puratechsolutions.databinding.ActivityMainBinding
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.io.image.ImageDataFactory
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: WebViewViewModel by viewModels()
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraPhotoUri: Uri? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        viewModel.handlePermissionsResult(permissions)
        if (permissions.all { it.value }) {
            Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Some permissions denied, some features may not work", Toast.LENGTH_LONG).show()
            showPermissionRetryDialog()
        }
        setupWebView()
    }


    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data: Intent? = result.data
            if (data?.data != null) {
                // File selected from gallery or documents
                filePathCallback?.onReceiveValue(arrayOf(data.data!!))
            } else if (cameraPhotoUri != null) {
                // Photo captured via camera, automatically gets convert to PDF
                val pdfUri = convertPhotoToPdf(cameraPhotoUri!!)
                filePathCallback?.onReceiveValue(if (pdfUri != null) arrayOf(pdfUri) else null)
            } else {
                filePathCallback?.onReceiveValue(null)
            }
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
        cameraPhotoUri = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkPermissions()
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isEmpty()) {
            Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            setupWebView()
        } else {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    private fun showPermissionRetryDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("Camera and storage permissions are needed for full functionality. Would you like to try again?")
            .setPositiveButton("Retry") { _, _ -> checkPermissions() }
            .setNegativeButton("Continue") { _, _ -> }
            .setCancelable(false)
            .show()
    }


    private fun convertPhotoToPdf(photoUri: Uri): Uri? {
        try {
            val inputStream = contentResolver.openInputStream(photoUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            //Create PDF file in external files directory
            val pdfFile = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "captured_photo_${System.currentTimeMillis()}.pdf")
            val pdfWriter = PdfWriter(FileOutputStream(pdfFile))
            val pdfDocument = PdfDocument(pdfWriter)
            val document = Document(pdfDocument)

            val bitmapFile = File.createTempFile("temp", ".jpg", cacheDir)
            val bitmapOutputStream = FileOutputStream(bitmapFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bitmapOutputStream)
            bitmapOutputStream.close()

            val imageData = ImageDataFactory.create(bitmapFile.absolutePath)
            val pdfImage = Image(imageData)
            document.add(pdfImage)

            document.close()
            pdfDocument.close()

            // Return the URI
            return FileProvider.getUriForFile(this, "${packageName}.fileprovider", pdfFile)
        } catch (e: Exception) {
            return null
        }
    }

    private fun setupWebView() {
        binding.webView.apply {
            settings.javaScriptEnabled = true //for emails
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true //to restrict files other than PDF or Doc

            webViewClient = object : WebViewClient() {}
            webChromeClient = object : WebChromeClient() {

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = filePathCallback

                    val acceptTypes = fileChooserParams?.acceptTypes?.joinToString(",") ?: "/"

                    // Intent for file selection (PDF or Doc)
                   val fileIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = if (acceptTypes.contains("pdf") || acceptTypes.contains("doc")) {
                            "application/pdf,application/msword"
                        } else {
                            "*/*"
                        }
                    }

                    // Intent for photo capture
                    val cameraPhotoIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        // Create a temporary file for the photo
                        val photoFile = File.createTempFile("photo", ".jpg", externalCacheDir)
                        cameraPhotoUri = FileProvider.getUriForFile(this@MainActivity, "${packageName}.fileprovider", photoFile)
                        putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri)
                    }

                    // Intent for video capture
                    val cameraVideoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)

                    val intentChooser = Intent.createChooser(fileIntent, "Select File").apply {
                        putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraPhotoIntent, cameraVideoIntent))
                    }

                    try {
                        fileChooserLauncher.launch(intentChooser)
                        return true
                    } catch (e: Exception) {
                        return false
                    }
                }
            }
            loadUrl("https://puratech.co.in")
        }
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}