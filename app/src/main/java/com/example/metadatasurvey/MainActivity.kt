package com.example.metadatasurvey

import android.Manifest
import android.content.Context
import android.graphics.Typeface

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.exp
import androidx.compose.ui.res.painterResource
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {

    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>
    private lateinit var storagePermissionLauncher: ActivityResultLauncher<Array<String>>

    private val capturedImage = mutableStateOf<Bitmap?>(null)
    private val classificationResult = mutableStateOf<List<List<Float>>?>(null)
    private var classificationDRResult: Float? = null
    private val metadata = mutableStateOf(LongArray(5) { 0L }) // 8 metadata features
    private lateinit var model: Module
    private lateinit var modelDR: Module
    private lateinit var blur_model: Module
    private val patientName = mutableStateOf("Jane Doe")
    private val patientEmail = mutableStateOf("insightscreening1000@gmail.com")

    private val currentScreen = mutableStateOf("metadata") // Track current screen
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storagePermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true &&
                permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true) {
                Toast.makeText(this, "Storage permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Storage permissions denied", Toast.LENGTH_SHORT).show()
            }
        }
        setContent {
            requestStoragePermissions() // Ask for storage permissions on app launch
        }
        model = Module.load(assetFilePath(this, "traced_metafusion_model3.pth"))
        modelDR = Module.load(assetFilePath(this, "traced_metafusion_dr_model2.pth"))
        blur_model = Module.load(assetFilePath(this, "traced_blur_detector2.pth"))
        cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            capturedImage.value = bitmap

        }


        galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri: Uri? = result.data?.data
            uri?.let {
                val inputStream: InputStream? = contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                capturedImage.value = bitmap

            }
        }

        setContent {
            var currentScreen by remember { mutableStateOf("home") }

            MaterialTheme {
                when (currentScreen) {
                    "home" -> HomeScreen(onStartClick = { currentScreen = "personalInfo" })

                    "personalInfo" -> PersonalInfoScreen(
                        patientName = patientName.value,
                        onNameChange = { patientName.value = it },
                        patientEmail = patientEmail.value,
                        onEmailChange = { patientEmail.value = it },
                        onNextClick = { currentScreen = "metadata" }
                    )

                    "metadata" -> MetadataSelectionScreen(onMetadataSelected = { selectedMetadata ->
                        metadata.value = selectedMetadata
                        currentScreen = "imagePicker"
                    })

                    "imagePicker" -> ImagePickerScreen(
                        capturedImage = capturedImage.value,
                        onCaptureClick = { captureImage() },
                        onPickFromGalleryClick = { pickImageFromGallery() },
                        onProceedToResults = { currentScreen = "imageQualityCheck" }
                    )

                    "imageQualityCheck" -> ImageQualityCheckScreen(
                        isImageHighQuality = isImageHighQuality(capturedImage.value),
                        onRetakeImage = { currentScreen = "imagePicker" },
                        onProceedToClassification = {
                            classifyImage(capturedImage.value!!, metadata.value)
                            classifyDR(capturedImage.value!!, metadata.value)
                            currentScreen = "result"
                        }
                    )

                    "result" -> ResultScreen(
                        classificationResult = classificationResult.value,
                        onBackToHome = { currentScreen = "DeliverResult" },
                        classificationDRResult
                    )

                    "DeliverResult" -> DeliverResultScreen(
                        onSaveAsPDF = { generatePDF(this, classificationResult.value,classificationDRResult, capturedImage.value, patientName.value, patientEmail.value) },
                        onEmailPDF = {
                            generatePDF(this, classificationResult.value,classificationDRResult, capturedImage.value, patientName.value, patientEmail.value)
                            sendPDFByEmail(this, patientEmail.value) },
                        onBackToHome = {
                            capturedImage.value = null
                            classificationResult.value = null
                            metadata.value = LongArray(5) { 0L }
                            currentScreen = "home"
                        },
                        patientEmail.value
                    )
                }
            }
        }
    }
    private fun createTensorListFromBitmaps(bitmaps: List<Bitmap>): List<Tensor> {
        // Ensure the input list contains exactly 4 images
        require(bitmaps.size == 1) { "Exactly 1 bitmaps are required." }

        return bitmaps.map { bitmap ->
            // Resize each bitmap to the model's input size (e.g., 224x224)
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 256, 256, true)

            // Preprocess each bitmap to create a tensor
            TensorImageUtils.bitmapToFloat32Tensor(
                resizedBitmap,
                TensorImageUtils.TORCHVISION_NORM_MEAN_RGB, // Normalization mean
                TensorImageUtils.TORCHVISION_NORM_STD_RGB   // Normalization std
            )
        }
    }
    fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            storagePermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }
    // Function to resize the Bitmap to 256x256
    fun resizeBitmap(bitmap: Bitmap, width: Int = 256, height: Int = 256): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }



    private fun createTensorListFromBitmaps2(bitmaps: List<Bitmap>): List<Tensor> {
        require(bitmaps.size == 1) { "Exactly 1 bitmap is required." }

        val mean = TensorImageUtils.TORCHVISION_NORM_MEAN_RGB
        val std = TensorImageUtils.TORCHVISION_NORM_STD_RGB

        return bitmaps.map { bitmap ->
            val width = 256
            val height = 256
            val inputChannels = 3

            // Resize the bitmap to 256x256
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)

            // Allocate a direct ByteBuffer and wrap it as a FloatBuffer
            val byteBuffer = ByteBuffer.allocateDirect(4 * inputChannels * width * height)
            byteBuffer.order(ByteOrder.nativeOrder())
            val floatBuffer: FloatBuffer = byteBuffer.asFloatBuffer()

            val pixels = IntArray(width * height)
            resizedBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            // Extract and normalize pixel values in CHW order
            for (c in 0 until inputChannels) {
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val pixel = pixels[y * width + x]
                        val value = when (c) {
                            0 -> ((pixel shr 16) and 0xFF) / 255f  // Red
                            1 -> ((pixel shr 8) and 0xFF) / 255f   // Green
                            else -> (pixel and 0xFF) / 255f        // Blue
                        }
                        //  Normalize: (value - mean) / std
                        //floatBuffer.put((value - mean[c]) / std[c])
                        floatBuffer.put((value))
                    }
                }
            }
            floatBuffer.rewind()

            //  Create tensor using the direct FloatBuffer (this should now work)
            Tensor.fromBlob(floatBuffer, longArrayOf(1, inputChannels.toLong(), height.toLong(), width.toLong()))
        }
    }

    fun isImageHighQuality(bitmap: Bitmap?): Boolean {
        if (bitmap == null) return false

        val bitmaps: List<Bitmap> = listOf(bitmap) // Your 1 bitmaps
        val tensorList: List<Tensor> = createTensorListFromBitmaps2(bitmaps)

        val outputIValue = blur_model.forward(IValue.from(tensorList[0])).toTuple()
        val finalOutput = outputIValue[2].toTensor()
        val outputArray = finalOutput.dataAsFloatArray
        return outputArray[0] < outputArray[1]
    }

    private fun classifyImage(bitmap: Bitmap, metadata: LongArray) {
        val bitmaps: List<Bitmap> = listOf(bitmap) // Your 1 bitmaps
        val tensorList: List<Tensor> = createTensorListFromBitmaps(bitmaps)

        val inputIValue = IValue.listFrom(*tensorList.map { IValue.from(it) }.toTypedArray())
        val metadataTensor = Tensor.fromBlob(metadata, longArrayOf(1, metadata.size.toLong()))



        val outputTensors =
            model.forward(inputIValue, IValue.from(metadataTensor)).toTensorList()
//        val outputTensors = model.forward(inputIValue).toTensorList()
        // Apply softmax to each tensor's output
        val probabilities = outputTensors.map { tensor ->
            val scores = tensor.dataAsFloatArray
            require(scores.size == 2) { "Each tensor should contain exactly 2 elements" }
            softmax(scores) // Convert to probabilities
        }
        // Store probabilities as a list of lists (each containing two probabilities)
        classificationResult.value = probabilities.map { probs ->
            listOf(probs[0], probs[1]) // Class A and Class B probabilities
        }

        currentScreen.value = "result"

    }
    private fun classifyDR(bitmap: Bitmap, metadata: LongArray) {
        val bitmaps: List<Bitmap> = listOf(bitmap) // Your 1 bitmaps
        val tensorList: List<Tensor> = createTensorListFromBitmaps(bitmaps)

        val inputIValue = IValue.listFrom(*tensorList.map { IValue.from(it) }.toTypedArray())
        val metadataTensor = Tensor.fromBlob(metadata, longArrayOf(1, metadata.size.toLong()))



        val outputTensors =
            modelDR.forward(inputIValue, IValue.from(metadataTensor)).toTensorList()
//        val outputTensors = model.forward(inputIValue).toTensorList()
        // Apply softmax to each tensor's output
        val probabilities = outputTensors.map { tensor ->
            val scores = tensor.dataAsFloatArray
            require(scores.size == 2) { "Each tensor should contain exactly 2 elements" }
            softmax(scores) // Convert to probabilities
        }
        // Store probabilities as a list of lists (each containing two probabilities)
        classificationDRResult = probabilities[0][0]

        currentScreen.value = "result"

    }
    private fun softmax(scores: FloatArray): FloatArray {
        val expScores = scores.map { exp(it) } // Compute exponentials
        val sumExpScores = expScores.sum() // Sum of exponentials
        return expScores.map { it / sumExpScores }.toFloatArray() // Normalize
    }
    private fun captureImage() {
        if (checkCameraPermission(this)) {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            cameraLauncher.launch(intent)
        } else {
            requestCameraPermission(this)
        }
    }

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            putExtra("android.intent.extra.ALLOW_MULTIPLE", false)
            putExtra(
                "android.intent.extra.INITIAL_URI",
                Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "DCIM/Fundus")
            )
        }
        galleryLauncher.launch(intent)
    }

    private fun checkCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission(activity: ComponentActivity) {
        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
    }

    private fun assetFilePath(context: Context, assetName: String): String {
        val file = java.io.File(context.filesDir, assetName)
        if (file.exists() && file.length() > 0) {
            return file.absolutePath
        }
        context.assets.open(assetName).use { inputStream ->
            java.io.FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return file.absolutePath
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1
    }
}

@Composable
fun HomeScreen(onStartClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = Color(0xFF98CCF5)
            ) // Gradient replaces solid color
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Welcome Message (Single line with consistent font size)
        Text(
            text = "Welcome to",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif, // Fancy Font
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16


            .dp))
        Text(
            text = "InSight Screening",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif, // Fancy Font
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Larger Image Icon
        Image(
            painter = painterResource(id = R.drawable.icon), // Replace with your actual image resource
            contentDescription = "Insight Screening Icon",
            modifier = Modifier
                .height(300.dp) // Increased size
                .width(300.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Subtitle
        Text(
            text = "Your Eye Health Is InSight",
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Cursive, // Stylish font
            color = Color.DarkGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Start Button
        Button(
            onClick = onStartClick,
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(0.6f), // Making it slightly wide
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12334D)) // Darker blue button
        ) {
            Text(text = "Get Started", fontSize = 20.sp, color = Color.White)
        }
    }
}

@Composable
fun PersonalInfoScreen(
    patientName: String,
    onNameChange: (String) -> Unit,
    patientEmail: String,
    onEmailChange: (String) -> Unit,
    onNextClick: () -> Unit
) {
    val isEmailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(patientEmail).matches()
    val isFormValid = patientName.isNotEmpty() && isEmailValid

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = Color(0xFF98CCF5)
            ) // Gradient replaces solid color
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(200.dp))

        Text(
            text = "Enter Your Personal Information",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            color = Color.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = patientName,
            onValueChange = onNameChange,
            label = { Text("Patient Name") },
            modifier = Modifier.fillMaxWidth(0.8f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = patientEmail,
            onValueChange = onEmailChange,
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth(0.8f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            isError = !isEmailValid && patientEmail.isNotEmpty()
        )

        if (!isEmailValid && patientEmail.isNotEmpty()) {
            Text(
                text = "Invalid email address",
                color = Color.Red,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNextClick,
            enabled = isFormValid,
            modifier = Modifier.fillMaxWidth(0.6f),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12334D))
        ) {
            Text("Next", fontSize = 18.sp, color = Color.White)
        }
    }
}

@Composable
fun ImagePickerScreen(
    capturedImage: Bitmap?,
    onCaptureClick: () -> Unit,
    onPickFromGalleryClick: () -> Unit,
    onProceedToResults: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = Color(0xFF98CCF5)
            ) // Gradient replaces solid color
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title at the top
        Text(
            text = "Capture or Select Fundus Image",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            color = Color.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        )

        // Image Box Section
        Box(
            modifier = Modifier
                .size(340.dp) // Fixed size box for image display
                .background(Color.White, shape = RoundedCornerShape(12.dp))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (capturedImage != null) {
                Image(
                    bitmap = capturedImage.asImageBitmap(),
                    contentDescription = "Selected Image",
                    contentScale = ContentScale.Crop, // Fills the box fully
                    modifier = Modifier.fillMaxSize() // Ensures full coverage of the box
                )
            } else {
                Text(
                    text = "No image selected",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Classification Result Section (If Available)\
        // Lets use this for image quality checking instead.


        Button(
            onClick = onProceedToResults,
            enabled = capturedImage != null,
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(0.6f),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12334D))
        ) {
            Text(
                text = "Proceed to Image Quality Assessment",
                fontSize = 18.sp,
                color = Color.White,
                fontFamily = FontFamily.SansSerif
            )

        }

        // Buttons for Capture and Gallery Selection
        Button(
            onClick = onCaptureClick,
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(0.6f),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12334D))
        ) {
            Text(
                text = "Capture Image",
                fontSize = 18.sp,
                color = Color.White,
                fontFamily = FontFamily.SansSerif
            )
        }

        Button(
            onClick = onPickFromGalleryClick,
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(0.6f),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12334D))
        ) {
            Text(
                text = "Pick from Gallery",
                fontSize = 18.sp,
                color = Color.White,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

@Composable
fun ImageQualityCheckScreen(
    isImageHighQuality: Boolean,
    onRetakeImage: () -> Unit,
    onProceedToClassification: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = Color(0xFF98CCF5)
            ) // Gradient replaces solid color
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ){

        Spacer(modifier = Modifier.height(250.dp))

        Text(
            text = if (isImageHighQuality) "Image is of Good Quality" else "Image is not of sufficient quality for InSight Screening.",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            color = Color.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRetakeImage,
            enabled = !isImageHighQuality,
            modifier = Modifier.fillMaxWidth(0.6f),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12334D))
        ) {
            Text("Retake Image", fontSize = 18.sp, color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))



        Button(
            onClick = onProceedToClassification,
            enabled = isImageHighQuality,
            modifier = Modifier.fillMaxWidth(0.6f),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12334D))
        ) {
            Text("Proceed to Screening", fontSize = 18.sp, color = Color.White)
        }
    }
}


@Composable
fun AnimatedPieChart(score: Float, label: String) {
    val animatedScore by animateFloatAsState(
        targetValue = score,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(modifier = Modifier.size(250.dp)) { // Enlarged pie chart size
            val canvasSize = size.minDimension

            drawArc(
                color = Color(0xFF5248E0),
                startAngle = -90f,
                sweepAngle = animatedScore * 360f,
                useCenter = true,
                topLeft = Offset(0f, 0f),
                size = Size(canvasSize, canvasSize)
            )

            drawArc(
                color = Color.Gray,
                startAngle = -90f + (animatedScore * 360f),
                sweepAngle = (1.0f - animatedScore) * 360f,
                useCenter = true,
                topLeft = Offset(0f, 0f),
                size = Size(canvasSize, canvasSize)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = String.format("Probability: %.2f%%", score * 100),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            color = Color.Black
        )
    }
}

@Composable
fun ResultScreen(
    classificationResult: List<List<Float>>?,
    onBackToHome: () -> Unit,
    classificationDRResult: Float?
) {
    val probabilities = classificationResult
    var currentScreenIndex by remember { mutableStateOf(0) }

    if (probabilities != null) {
        val diseases = listOf(
            "Diabetic Retinopathy" to probabilities[0][1],
            "Macular Edema" to probabilities[1][1],
            "Myopic Fundus" to probabilities[2][1],
            "Glaucoma" to probabilities[3][1],
            "AMD" to probabilities[4][1],

            )

        if (currentScreenIndex < diseases.size) {
            val (diseaseName, score) = diseases[currentScreenIndex]

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = Color(0xFF98CCF5)
                    ) // Gradient replaces solid color
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(150.dp))

                Text(
                    text = diseaseName,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    color = Color.Black
                )

                AnimatedPieChart(score = score, label = "$diseaseName Probability")

                // Insert textbox
                if (currentScreenIndex == 0) {
                    if (score > 0.5) {
                        if (classificationDRResult != null) {
                            if (classificationDRResult > 0.5) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                        .background(Color(0xFFCA9ADB), shape = RoundedCornerShape(8.dp))
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = "Classification Result: Severe or Proliferative DR",
                                        color = Color.Black,
                                        fontSize = 20.sp // Increased font size

                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                        .background(Color(0xFFCA9ADB), shape = RoundedCornerShape(8.dp))
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = "Classification Result: Mild to moderate non-proliferative diabetic retinopathy.",
                                        color = Color.Black,
                                        fontSize = 20.sp // Increased font size

                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (currentScreenIndex < diseases.size - 1) {
                            currentScreenIndex++
                        } else {
                            onBackToHome()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12334D)),
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = if (currentScreenIndex < diseases.size - 1) "Next" else "Finish",
                        fontSize = 20.sp,
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }
    }
}

@Composable
fun DeliverResultScreen(
    onSaveAsPDF: () -> Unit,
    onEmailPDF: (String) -> Unit,
    onBackToHome: () -> Unit,
    email: String
) {
    var isEmailValid by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = Color(0xFF98CCF5)
            ) // Gradient replaces solid color
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(250.dp))

        Text(
            text = "Save or Email PDF Report",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            color = Color.Black,
            textAlign = TextAlign.Center
        )


        Button(
            onClick = onSaveAsPDF,
            modifier = Modifier.fillMaxWidth(0.6f),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12334D))
        ) {
            Text("Save to PDF", fontSize = 18.sp, color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))


        isEmailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()


        Button(
            onClick = {
                if (isEmailValid && email.isNotEmpty()) {
                    onEmailPDF(email)
                }
            },
            modifier = Modifier.fillMaxWidth(0.6f),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12334D))
        ) {
            Text("Email PDF", fontSize = 18.sp, color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onBackToHome,
            modifier = Modifier.fillMaxWidth(0.6f),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF12334D))
        ) {
            Text("Back to Home", fontSize = 18.sp, color = Color.White)
        }
    }
}

fun sendPDFByEmail(context: Context, email: String) {
    val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
    val file = File(documentsDir, "ScreeningResults.pdf")

    if (!file.exists()) {
        Toast.makeText(context, "PDF file not found!", Toast.LENGTH_SHORT).show()
        return
    }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
        putExtra(Intent.EXTRA_SUBJECT, "Your Insight Screening Report")
        putExtra(Intent.EXTRA_TEXT, "Attached is your medical screening report.")
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Required for modern Android versions
    }

    try {
        context.startActivity(Intent.createChooser(intent, "Send email via"))
    } catch (e: Exception) {
        Toast.makeText(context, "No email client installed", Toast.LENGTH_SHORT).show()
    }
}

fun generatePDF(
    context: Context,
    classificationResult: List<List<Float>>?,
    classificationDRResult: Float?,
    image: Bitmap?,
    patientName: String,
    patientEmail: String
) {
    val document = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val margin = 50f
    val maxY = pageHeight - 50f  // Prevents content from getting cut off

    var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
    var page = document.startPage(pageInfo)
    var canvas = page.canvas
    var paint = Paint()

    // Function to create a new page when needed
    fun newPage() {
        document.finishPage(page)
        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.pages.size + 1).create()
        page = document.startPage(pageInfo)
        canvas = page.canvas
        paint = Paint().apply { typeface = Typeface.SERIF; textSize = 12f }
    }

    var x = margin

    // **Title: "INSIGHT PRELIMINARY EYE SCREENING" (Bold & Centered)**
    paint.textSize = 12f
    paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    val title = "INSIGHT PRELIMINARY EYE SCREENING"
    val textWidth = paint.measureText(title)
    val xPos = (pageInfo.pageWidth - textWidth) / 2
    canvas.drawText(title, xPos, 50f, paint)

    // **Load and Draw Higher-Resolution Logo**
    val logoBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.icon)
    val resizedLogo = Bitmap.createScaledBitmap(logoBitmap, 120, 100, true)
    canvas.drawBitmap(resizedLogo, 450f, 20f, null)

    // **Patient Information**
    paint.textSize = 12f
    canvas.drawText("Patient Information:", 50f, 100f, paint)

    paint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)

    canvas.drawText("Name: $patientName", 50f, 125f, paint)

    canvas.drawText("Email: $patientEmail", 50f, 150f, paint)

    var y = 190f
    val maxWidth = 500f

    // **Screening Results Header**
    paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    canvas.drawText("Screening Results:", 50f, y, paint)
    y += 25f
    paint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)

    // **Format Screening Results into Table Layout**
    val diseases = listOf("Diabetic Retinopathy", "Glaucoma", "Macular Edema", "Myopic Fundus", "AMD")
    val probabilities = classificationResult?.mapIndexed { index, result ->
        String.format("%.2f", result[1] * 100) + "%"
    } ?: listOf("No Data", "No Data", "No Data", "No Data", "No Data")

    val colLeftX = 50f   // Left column starting X position
    val colRightX = 300f // Right column starting X position

    for (i in diseases.indices step 2) {
        // Print left column (Always exists)
        canvas.drawText("${diseases[i]}: ${probabilities[i]}", colLeftX, y, paint)

        // Print right column (Check if index exists to avoid out of bounds)
        if (i + 1 < diseases.size) {
            canvas.drawText("${diseases[i + 1]}: ${probabilities[i + 1]}", colRightX, y, paint)
        }

        y += 22f // Move to the next row
    }

    y += 5f // Extra spacing after results table
    // **Draw Fundus Image**
    image?.let {
        val aspectRatio = it.width.toFloat() / it.height.toFloat()
        val imageWidth = 150f
        val imageHeight = imageWidth / aspectRatio
        canvas.drawBitmap(Bitmap.createScaledBitmap(it, imageWidth.toInt(), imageHeight.toInt(), true), 190f, y + 20f, null)
        y += imageHeight + 40f
    }

    // **Process All High-Risk Diseases**
    val highRiskDiseases = classificationResult?.mapIndexedNotNull { index, result ->
        if (result[1] > 0.50f) diseases[index] else null
    } ?: emptyList()

    for (disease in highRiskDiseases) {
        y += 25f
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        canvas.drawText("You have a high risk for $disease.", 50f, y, paint)
        y += 22f
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)

        // **Disease Descriptions (Restored)**
        val description = when (disease) {
            "Diabetic Retinopathy" -> "Diabetic Retinopathy is an eye condition caused by damage to blood vessels in the retina due to diabetes."
            "Glaucoma" -> "Glaucoma is a group of eye diseases that damage the optic nerve, often caused by increased pressure in the eye."
            "Macular Edema" -> "Macular Edema is the swelling of the macula, the central part of the retina, leading to blurry or distorted vision."
            "Myopic Fundus" -> "Myopic Fundus refers to degenerative changes in the retina due to severe nearsightedness, increasing the risk of retinal detachment."
            "AMD" -> "Age-related Macular Degeneration (AMD) affects the macula, leading to central vision loss, often in older adults."
            else -> null
        }

        description?.let {
            for (line in wrapText("• $it", paint, maxWidth)) {
                if (y > maxY) {
                    newPage()
                    y = margin
                }
                canvas.drawText(line, margin + 20f, y, paint)
                y += 22f
            }

        }

        // **Diabetic Retinopathy Stage Classification**
        if (disease == "Diabetic Retinopathy" && classificationDRResult != null) {
            paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            canvas.drawText("Stage Classification:", 50f, y, paint)
            y += 22f
            paint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)

            val severityMessage: String
            val nextStepsDR: List<String>

            if (classificationDRResult > 0.5) {
                severityMessage = "You likely have severe or proliferative diabetic retinopathy."
                nextStepsDR = listOf(
                    "Immediate consultation with an ophthalmologist is recommended.",
                    "Treatment may include laser therapy, anti-VEGF injections, or vitrectomy.",
                    "Maintain strict blood sugar and blood pressure control."
                )
            } else {
                severityMessage = "You likely have mild to moderate non-proliferative diabetic retinopathy."
                nextStepsDR = listOf(
                    "Regular eye exams (every 6-12 months) to monitor progression.",
                    "Maintain good blood sugar control to slow progression.",
                    "Exercise regularly and manage blood pressure."
                )
            }
            paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            for (line in wrapText(severityMessage, paint, maxWidth)) {
                if (y > maxY) {
                    newPage()
                    y = margin
                }
                canvas.drawText(line, margin+ 20f, y, paint)
                y += 22f
            }
            paint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            for (step in nextStepsDR) {
                for (line in wrapText("• $step", paint, maxWidth)) {
                    if (y > maxY) {
                        newPage()
                        y = margin
                    }
                    canvas.drawText(line, margin + 20f, y, paint)
                    y += 22f
                }
            }

        }


        // **Next Steps for Each Disease**
        val nextSteps = when (disease) {


            "Glaucoma" -> listOf("Use prescribed eye drops regularly and attend follow-up appointments. Try to visit an ophthalmologist at your earliest convenience. ")
            "Macular Edema" -> listOf("Control blood sugar and cholesterol levels, exercise regularly. Try to visit an ophthalmologist at your earliest convenience.")
            "Myopic Fundus" -> listOf("Regular eye exams and use corrective lenses. Try to visit an ophthalmologist at your earliest convenience.")
            "AMD" -> listOf("Eat a diet rich in leafy greens and antioxidants, avoid smoking. Try to visit an ophthalmologist at your earliest convenience.")
            else -> emptyList()
        }

        for (step in nextSteps) {
            for (line in wrapText("• $step", paint, maxWidth)) {
                if (y > maxY) {
                    newPage()
                    y = margin
                }
                canvas.drawText(line, margin + 20f, y, paint)
                y += 22f
            }
        }
    }
    if (highRiskDiseases.isEmpty()){
        // **General Eye Health Tips**
        val eyeHealthTips = listOf(
            "Follow a balanced diet to maintain healthy blood sugar and cholesterol levels.",
            "Protect your eyes from UV light and wear protective sunglasses.",
            "Exercise regularly to improve overall eye and vascular health.",
            "Avoid eye strain and dryness (use lubricating eye drops if necessary)."
        )

        y += 25f // Add space before the tips section

        // **Bold Heading for General Eye Health Tips**
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        canvas.drawText("General Eye Health Tips:", 50f, y, paint)
        y += 22f

        // **Set normal font for bullet points**
        paint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)

        for (tip in eyeHealthTips) {
            val wrappedTip = wrapText("• $tip", paint, maxWidth)
            for (line in wrappedTip) {
                canvas.drawText(line, 70f, y, paint) // Indented for bullets
                y += 22f
            }
        }
    }



    document.finishPage(page)
    val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "ScreeningResults.pdf")
    document.writeTo(FileOutputStream(file))
    document.close()

    Toast.makeText(context, "PDF saved in Documents: ${file.absolutePath}", Toast.LENGTH_LONG).show()
}







// Function to wrap text so it does not go off-page width
fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
    val words = text.split(" ")
    val wrappedLines = mutableListOf<String>()
    var currentLine = ""
    for (word in words) {
        val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
        if (paint.measureText(testLine) > maxWidth) {
            wrappedLines.add(currentLine)
            currentLine = word
        } else {
            currentLine = testLine
        }
    }
    if (currentLine.isNotEmpty()) {
        wrappedLines.add(currentLine)
    }
    return wrappedLines
}

fun createResultsMessage(classificationResult: List<List<Float>>?): String {
    val diseases = listOf(
        "Diabetic Retinopathy", "Glaucoma", "Macular Edema", "Myopic Fundus", "AMD"
    )

    val diseaseRecommendations = mapOf(
        "Diabetic Retinopathy" to "Diabetic Retinopathy is an eye condition caused by damage to the blood vessels in the retina due to diabetes. It can lead to vision impairment or blindness if left untreated. Next Steps: Control blood sugar levels and attend regular eye check-ups.",
        "Glaucoma" to "Glaucoma is a group of eye diseases that damage the optic nerve, often caused by increased pressure in the eye. It can lead to gradual vision loss. Next Steps: Use eye drops prescribed by your provider.",
        "Macular Edema" to "Macular Edema is the swelling of the macula, the central part of the retina, often caused by diabetes or other underlying conditions, leading to blurry or distorted vision. Next Steps: Control blood sugar and cholesterol levels, exercise regularly.",
        "Myopic Fundus" to "Myopic Fundus refers to degenerative changes in the retina due to severe nearsightedness (myopia), which can increase the risk of retinal detachment. Next Steps: Use corrective lenses and attend regular eye checkups.",
        "AMD" to "Age-related Macular Degeneration (AMD) is a condition that affects the macula, leading to vision loss in the center of the visual field, often in older adults. Next Steps: Eat a diet rich in leafy greens and antioxidants, avoid smoking."
    )


    val resultsText = classificationResult?.mapIndexed { index, result ->
        "${diseases[index]}: ${String.format("%.2f", result[1] * 100)}%"
    }?.joinToString("\n") ?: "No results available."

    val highRiskDiseases = classificationResult?.mapIndexedNotNull { index, result ->
        if (result[1] > 0.50f) diseases[index] else null
    } ?: emptyList()

    val recommendations = highRiskDiseases.joinToString("\n") { disease ->
        "You have a high risk for $disease. ${diseaseRecommendations[disease]}"
    }

    val warningText = if (highRiskDiseases.isNotEmpty())
        "\n\nAccording to Insight Screening, you are at high risk for the following diseases:\n$recommendations"
    else ""

    return "Here are your screening results:\n\n$resultsText$warningText"
}



fun sendEmailWithResults(email: String, classificationResult: List<List<Float>>?, image: Bitmap?, context: Context) {
    val subject = "Your Medical Screening Results"


    val message = createResultsMessage(classificationResult)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, message)
    }

    image?.let {
        val imageUri = Uri.parse(MediaStore.Images.Media.insertImage(context.contentResolver, it, "Screening Image", null))
        intent.putExtra(Intent.EXTRA_STREAM, imageUri)
    }

    try {
        context.startActivity(Intent.createChooser(intent, "Send email via"))
    } catch (e: Exception) {
        Toast.makeText(context, "No email client installed", Toast.LENGTH_SHORT).show()
    }
}