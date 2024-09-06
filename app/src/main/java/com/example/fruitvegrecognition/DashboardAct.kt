package com.example.fruitvegrecognition

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.ByteOrder



class DashboardAct : AppCompatActivity() {

    private lateinit var cameraButton: ImageButton
    private lateinit var uploadButton: ImageButton
    private lateinit var imageView: ImageView
    private lateinit var searchView: SearchView
    private lateinit var nameValue: TextView
    private lateinit var caloriesValue: TextView
    private lateinit var fatValue: TextView
    private lateinit var carbsValue: TextView
    private lateinit var proteinValue: TextView
    private lateinit var resultTextView: TextView

    private lateinit var interpreter: Interpreter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // Initialize views
        cameraButton = findViewById(R.id.cameraButton)
        uploadButton = findViewById(R.id.uploadButton)
        imageView = findViewById(R.id.imageView)
        searchView = findViewById(R.id.searchView)
        nameValue = findViewById(R.id.nameValue)
        caloriesValue = findViewById(R.id.caloriesValue)
        fatValue = findViewById(R.id.fatValue)
        carbsValue = findViewById(R.id.carbsValue)
        proteinValue = findViewById(R.id.proteinValue)
        resultTextView = findViewById(R.id.resultTextView)

        // Load TFLite model
        interpreter = Interpreter(loadModelFile())

        // Camera button functionality
        cameraButton.setOnClickListener {
            val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(cameraIntent, 100)
        }

        // Upload button functionality
        uploadButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, 101)
        }

        // Search functionality
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { fetchNutritionData(it) }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })
    }

    // Handle image capture or upload
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK && data != null) {
            when (requestCode) {
                100 -> { // Camera image capture
                    val imageBitmap = data.extras?.get("data") as Bitmap
                    imageView.setImageBitmap(imageBitmap)
                    predictImage(imageBitmap)
                }
                101 -> { // Image upload from gallery
                    val selectedImageUri = data.data
                    imageView.setImageURI(selectedImageUri)
                    selectedImageUri?.let {
                        val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, it)
                        predictImage(bitmap)
                    }
                }
            }
        }
    }

    // Fetch nutrition data from FatSecret using Jsoup
    private fun fetchNutritionData(query: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://www.fatsecret.com/calories-nutrition/usda/$query"
                val doc = Jsoup.connect(url).get()
                val name = doc.select("h1[itemprop=name]").text()
                val calories = doc.select("span[itemprop=calories]").text()
                val fat = doc.select("span[itemprop=fatContent]").text()
                val carbs = doc.select("span[itemprop=carbohydrateContent]").text()
                val protein = doc.select("span[itemprop=proteinContent]").text()

                // Update UI with the fetched data
                withContext(Dispatchers.Main) {
                    nameValue.text = name
                    caloriesValue.text = calories
                    fatValue.text = fat
                    carbsValue.text = carbs
                    proteinValue.text = protein
                }
            } catch (e: IOException) {
                e.printStackTrace()
                // Handle the error
            }
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun predictImage(bitmap: Bitmap) {
        // Resize the image to 150x150
        val resizedBitmap = resizeBitmap(bitmap, 150, 150)

        // Convert the Bitmap to TensorImage
        val tensorImage = TensorImage.fromBitmap(resizedBitmap)
        val imageProcessor = ImageProcessor.Builder().build()

        val processedImage = imageProcessor.process(tensorImage)

        // Create the TensorBuffer for input
        val inputFeature = TensorBuffer.createFixedSize(intArrayOf(1, 150, 150, 3), DataType.FLOAT32)

        // Allocate ByteBuffer for input feature
        val byteBuffer = ByteBuffer.allocateDirect(270000 * 4) // 150*150*3*4 bytes
        byteBuffer.order(ByteOrder.nativeOrder())

        // Load the processed image buffer into the input feature
        processedImage.buffer.rewind()
        byteBuffer.put(processedImage.buffer)
        byteBuffer.rewind()
        inputFeature.loadBuffer(byteBuffer)

        // Run model inference
        val outputFeature = TensorBuffer.createFixedSize(intArrayOf(1, 36), DataType.FLOAT32)
        interpreter.run(inputFeature.buffer, outputFeature.buffer)

        // Get the result
        val predictions = outputFeature.floatArray
        val predictedClass = predictions.indices.maxByOrNull { predictions[it] } ?: 0

        // Use your labels to display the result
        val labels = arrayOf(
            "apple", "banana", "beetroot", "bell pepper", "cabbage", "capsicum",
            "carrot", "cauliflower", "chilli pepper", "corn", "cucumber", "eggplant",
            "garlic", "ginger", "grapes", "jalepeno", "kiwi", "lemon", "lettuce",
            "mango", "onion", "orange", "paprika", "pear", "peas", "pineapple",
            "pomegranate", "potato", "raddish", "soy beans", "spinach", "sweetcorn",
            "sweetpotato", "tomato", "turnip", "watermelon"
        )

        val resultLabel = labels[predictedClass]
        // Update UI with the predicted label
        CoroutineScope(Dispatchers.Main).launch {
            resultTextView.text = resultLabel
        }
    }
    // Function to load model file from assets
    private fun loadModelFile(): ByteBuffer {
        val assetManager = assets
        val fileDescriptor = assetManager.openFd("model.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
}
