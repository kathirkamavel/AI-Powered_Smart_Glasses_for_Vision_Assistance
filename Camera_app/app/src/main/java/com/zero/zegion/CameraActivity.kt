package com.zero.zegion

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Outline
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

class CameraActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var textureView: TextureView
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraDevice: CameraDevice
    private lateinit var captureRequestBuilder: CaptureRequest.Builder
    private lateinit var handler: Handler
    private lateinit var handlerThread: HandlerThread
    private lateinit var imageReader: ImageReader
    private lateinit var resultView: TextView
    private lateinit var imageButton: ImageButton
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var audioButton: ImageButton
    private lateinit var speechRecognizer: SpeechRecognizer
    private var promptText = ""
    private var isSpeaking = false
    private var lastPrompt = ""

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Set language for TTS
            val result = textToSpeech.setLanguage(Locale.US)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Language not supported", Toast.LENGTH_SHORT).show()
            }

            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    // Called when the TTS starts speaking
                }

                override fun onDone(utteranceId: String?) {
                    // Called when the TTS finishes speaking
                    Log.d("CameraActivity", "TTS has finished speaking")
                    isSpeaking = false
                    mainfn()
                }

                @Deprecated("Deprecated in Java", ReplaceWith("TODO(\"Not yet implemented\")"))
                override fun onError(p0: String?) {
                    TODO("Not yet implemented")
                }
            })
        } else {
            Toast.makeText(this, "Initialization failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.developer_options, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here.
        return when (item.itemId) {
            R.id.Gemini_API_Key -> {
                showApiKeyInputDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_secondary)

        textureView = findViewById(R.id.textureview)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        handlerThread = HandlerThread("CameraHandlerThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                // Handle texture size changes if needed
            }

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                return true
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
                // Handle texture updates if needed
            }
        }

        checkPermissions()

        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val apiKey = sharedPreferences.getString("GeminiApiKey", "")

        if (apiKey == "") {

            Toast.makeText(this, "Please create API Key", Toast.LENGTH_LONG).show()

        }

        println(apiKey)

        val generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey ?: "AIzaSyDM5TNMV437_9MNB75yVfiL1DHcqEfoVhE" // Use an empty string if API key is not found
        )

        val prompt = """
            
            Prompt:
            
                Imagine you're wearing smart glasses, and I'm your AI assistant providing you with visual information. You're currently in a room and ask me, "What is in front of me?" Your question prompts me to describe the scene ahead, focusing on providing new information while avoiding repetition.
            
                Your task is to respond to this question as if you were the smart glasses, offering a concise yet informative description of the objects and layout directly in front of the user.
            
                Instructions:
                
                Prioritize anything that could prove harmful to the user.
                Ensure the response contains only new information and essential details about the immediate surroundings.
                Maintain clarity and coherence in the description to assist the user effectively.
                Aim to provide relevant information that aids the user's understanding of their environment.
                Dont include the word Response in the response.
                The may require answer to a specific question. The question if available will be below.
                Add the keyword OBJ at the end of response if it seems like a object is dangerously close to the camera like less than inches away. Do not do add he word OBJ at the end of the response if it is not very much close.
                
                Question:
            
        """.trimIndent()

        promptText = ""

        resultView = findViewById(R.id.text_view)

        imageReader = ImageReader.newInstance(1080, 1920, ImageFormat.JPEG, 1)
        imageReader.setOnImageAvailableListener({ p0 ->
            val image = p0?.acquireLatestImage()
            val buffer = image!!.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "img.jpeg")
            val opStream = FileOutputStream(file)
            opStream.write(bytes)

            opStream.close()
            image.close()
            Toast.makeText(this@CameraActivity, "Processing", Toast.LENGTH_SHORT).show()
            sendDataAPI(generativeModel, prompt, resultView)
        }, handler)

        findViewById<Button>(R.id.capture_button).apply{
            setOnClickListener{
                mainfn()
            }
        }

        imageButton = findViewById(R.id.convert_audio_button)
        imageButton.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val diameter = view.width.coerceAtMost(view.height)
                outline.setOval(0, 0, diameter, diameter)
            }
        }
        imageButton.clipToOutline = true

        audioButton = findViewById(R.id.add_prompt_text)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        audioButton.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val diameter = view.width.coerceAtMost(view.height)
                outline.setOval(0, 0, diameter, diameter)
            }
        }
        audioButton.clipToOutline = true

        textToSpeech = TextToSpeech(this, this)

        imageButton.setOnClickListener {

            toggleSpeech()

        }

        audioButton.setOnClickListener {

            startListening()
            isSpeaking = true
            toggleSpeech()

        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Toast.makeText(this@CameraActivity, "Listening", Toast.LENGTH_SHORT).show()
            }

            override fun onBeginningOfSpeech() {
                // Speech input started
            }

            override fun onRmsChanged(rmsdB: Float) {
                // The sound level in the audio stream has changed
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // More sound has been received
            }

            override fun onEndOfSpeech() {
            }

            override fun onError(error: Int) {
                Toast.makeText(this@CameraActivity, getString(R.string.speech_error), Toast.LENGTH_SHORT).show()
                promptText = ""
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val string = matches[0].toTitleCase()
                    promptText = string
                    isSpeaking = false
                    mainfn()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Partial recognition results
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Events related to the speech recognition
            }
        })

    }

    private fun showApiKeyInputDialog() {
        // Create an EditText view to input the API key
        val editText = EditText(this)
        editText.hint = "Enter Gemini API Key"

        // Create a LinearLayout to hold the EditText and set margins
        val layout = LinearLayout(this)
        layout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER

        // Convert DP to PX for margins
        val marginInDp = 20
        val marginInPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            marginInDp.toFloat(),
            resources.displayMetrics
        ).toInt()

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(marginInPx, 0, marginInPx, 0)
        editText.layoutParams = params

        layout.addView(editText)

        // Create an AlertDialog
        val dialog = AlertDialog.Builder(this)
            .setTitle("Developer Settings")
            .setMessage("Please enter your Gemini API key:")
            .setView(layout)
            .setPositiveButton("OK") { _, _ ->
                val apiKey = editText.text.toString().trim()
                if (apiKey.isNotEmpty()) {
                    // Save the API key or handle it as needed
                    handleApiKey(apiKey)
                } else {
                    Toast.makeText(this, "API key cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun handleApiKey(apiKey: String) {
        // Save the API key to shared preferences or use it directly
        // Example: Saving to shared preferences
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("GeminiApiKey", apiKey)
        editor.apply()

        // Inform the user
        Toast.makeText(this, "API key saved successfully", Toast.LENGTH_SHORT).show()

        val intent = Intent(this@CameraActivity, CameraActivity::class.java)
        startActivity(intent)
    }

    private fun mainfn() {

        val capReq = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        capReq.addTarget(imageReader.surface)
        cameraCaptureSession.capture(capReq.build(), null, null)

    }

    private fun startListening() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())

        speechRecognizer.startListening(intent)
    }

    private fun toggleSpeech() {
        val text = resultView.text.toString()

        if (isSpeaking) {
            // Stop speaking
            textToSpeech.stop()
            isSpeaking = false
        } else {
            if (text.isNotEmpty()) {
                val params = Bundle()
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, "UniqueID")
                isSpeaking = true
            } else {
                Toast.makeText(this, "Text is empty", Toast.LENGTH_SHORT).show()
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun sendDataAPI(generativeModel: GenerativeModel, prompt: String, resultView: TextView) {

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val imagePath = getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.absolutePath + "/img.jpeg"
                        val imageFile = File(imagePath)

                        if (imageFile.exists()) {
                            val bitmap = BitmapFactory.decodeStream(FileInputStream(imageFile))

                            if (promptText == lastPrompt) promptText = ""
                            val inputContent = content {

                                image(bitmap)
                                text(prompt + promptText)

                            }
                            lastPrompt = promptText

                            // Send the image to the API with the prompt
                            val response = generativeModel.generateContent(inputContent)

                            withContext(Dispatchers.Main) {
                                // Update UI or show response
                                // Toast.makeText(this@CameraActivity, response.text, Toast.LENGTH_LONG).show()
                                resultView.text = response.text.toString().replace("OBJ", "").trim()
                            }

                            checkAndVibrate(response.text.toString())

                            isSpeaking = false
                            toggleSpeech()

                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@CameraActivity, "Image file not found", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CameraActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun checkAndVibrate(response: String) {
        if ("OBJ" in response) {
            // Get the Vibrator service
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

            // Check if the device has a vibrator
            if (vibrator.hasVibrator()) {
                // Vibrate for 500 milliseconds
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator.vibrate(500)
                }
            }
        }
    }


    @SuppressLint("MissingPermission")
    private fun openCamera() {
        try {
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(cameraDevice: CameraDevice) {
                    this@CameraActivity.cameraDevice = cameraDevice


                    captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    val surface = Surface(textureView.surfaceTexture)
                    captureRequestBuilder.addTarget(surface)

                    cameraDevice.createCaptureSession(listOf(surface, imageReader.surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            cameraCaptureSession = session
                            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, handler)
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Toast.makeText(this@CameraActivity, "Camera configuration failed", Toast.LENGTH_SHORT).show()
                        }
                    }, handler)
                }

                override fun onDisconnected(cameraDevice: CameraDevice) {
                    cameraDevice.close()
                }

                override fun onError(cameraDevice: CameraDevice, error: Int) {
                    cameraDevice.close()
                    Toast.makeText(this@CameraActivity, "Camera error: $error", Toast.LENGTH_SHORT).show()
                }
            }, handler)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to open camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openCamera()
                } else {
                    Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        if (::cameraDevice.isInitialized) {
            cameraDevice.close()
        }
        handler.removeCallbacksAndMessages(null)
        handlerThread.quitSafely()
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    }
}
