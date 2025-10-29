package com.zero.zegion

import android.Manifest
import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Outline
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader
import org.json.JSONObject
import org.json.JSONArray
import java.util.regex.Pattern

val appMap = mapOf(
    "google" to "com.google.android.googlequicksearchbox",
    "maps" to "com.google.android.apps.maps",
    "gmail" to "com.google.android.gm",
    "whatsapp" to "com.whatsapp",
    "facebook" to "com.facebook.katana",
    "instagram" to "com.instagram.android",
    "twitter" to "com.twitter.android",
    "snapchat" to "com.snapchat.android",
    "spotify" to "com.spotify.music",
    "netflix" to "com.netflix.mediaclient",
    "messenger" to "com.facebook.orca",
    "linkedin" to "com.linkedin.android",
    "slack" to "com.Slack",
    "reddit" to "com.reddit.frontpage",
    "chrome" to "com.android.chrome",
    "tiktok" to "com.zhiliaoapp.musically",
    "zoom" to "us.zoom.videomeetings",
    "telegram" to "org.telegram.messenger",
    "calculator" to "com.android.calculator2",
    "calendar" to "com.google.android.calendar",
    "drive" to "com.google.android.apps.docs",
    "lens" to "com.google.ar.lens",
    "meet" to "com.google.android.apps.meetings",
    "ola" to "com.olacabs.customer",
    "prime video" to "com.amazon.avod.thirdpartyclient",
    "sheets" to "com.google.android.apps.docs.editors.sheets",
    "youtube music" to "com.google.android.apps.youtube.music",
    "youtube" to "com.google.android.youtube"
)

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var textView: TextView
    private lateinit var handlerAnimation: Handler
    private lateinit var mainButton: ImageButton
    private lateinit var rippleButton: View
    private lateinit var animationContainer: View
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var audioButton: Button
    private var string = ""
    private var isSpeaking = false
    private var lastPrompt = ""
    private var prompt = ""
    private var input = ""


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the TextView and other views
        textView = findViewById(R.id.text_view)
        animationContainer = findViewById(R.id.animation_container)
        mainButton = findViewById(R.id.main_button)
        rippleButton = findViewById(R.id.ripple_button)
        textToSpeech = TextToSpeech(this, this)

        // Initialize the SpeechRecognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        // Initialize the handlerAnimation
        @Suppress("DEPRECATION")
        handlerAnimation = Handler()

        val result = textToSpeech.setLanguage(Locale.US)

        //if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
        if (result == TextToSpeech.LANG_MISSING_DATA) {
            Toast.makeText(this, "Language not supported", Toast.LENGTH_SHORT).show()
        }

        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val apiKey = sharedPreferences.getString("GeminiApiKey", "")

        val generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = "AIzaSyDM5TNMV437_9MNB75yVfiL1DHcqEfoVhE" // Use an empty string if API key is not found
        )

        // Set up the RecognitionListener
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Toast.makeText(this@MainActivity, "Listening", Toast.LENGTH_SHORT).show()
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
                stopPulse()
            }

            override fun onError(error: Int) {
                textView.text = getString(R.string.speech_error)
                Toast.makeText(this@MainActivity, getString(R.string.speech_error), Toast.LENGTH_SHORT).show()
                stopPulse()
            }

            @RequiresApi(Build.VERSION_CODES.O)
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val current = LocalTime.now()
                val availableApps = appMap.keys.joinToString(", ")

                // Generate the prompt with available apps
                prompt = "You are an AI assistant, so whenever a command is given to you, check if the command is within a set of given parameters. " +
                        "Make the answer short but make it feel like it is a natural response. The timezone is Asia/ Kolkata, so calculate for new timezones if needed." +
                        "The available functions right now are camera, time, and apps like $availableApps. The time is $current. " +
                        "Do your best to answer general commands or logical ones." +
                        "Available note function are add, show/ read and remove. Dont ask for any followup questions." +
                        "The user command is: "

                if (!matches.isNullOrEmpty()) {
                    prompt += matches[0].toTitleCase()
                    val str = matches[0].toTitleCase()

                    // Launch a coroutine to handle the API call
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            // Call generateContent in a coroutine
                            val response = generativeModel.generateContent(prompt)
                            input = str

                            // Update the UI on the main thread
                            withContext(Dispatchers.Main) {
                                string = response.text.toString()
                                prompt = "$str - $string"
                                typingAnimation(textView, string) {}
                                toggleSpeech(string)
                            }
                        } catch (e: Exception) {
                            // Handle errors
                            withContext(Dispatchers.Main) {
                                textView.text = getString(R.string.speech_error)
                                Toast.makeText(this@MainActivity, getString(R.string.speech_error), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                stopPulse()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Partial recognition results
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Events related to the speech recognition
            }
        })

        // Set up animations for views
        animationContainer.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val diameter = view.width.coerceAtMost(view.height)
                outline.setOval(0, 0, diameter, diameter)
            }
        }
        animationContainer.clipToOutline = true

        mainButton.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val diameter = view.width.coerceAtMost(view.height)
                outline.setOval(0, 0, diameter, diameter)
            }
        }
        mainButton.clipToOutline = true

        // Set click listener for the button
        mainButton.setOnClickListener {
            isSpeaking = true
            toggleSpeech("")
            startListening()
            ripplerunner.run()
        }
    }

    private var ripplerunner = object : Runnable {
        override fun run() {
            rippleButton.animate().scaleX(4f).scaleY(4f).alpha(0f).setDuration(1000).withEndAction {
                rippleButton.scaleX = 1f
                rippleButton.scaleY = 1f
                rippleButton.alpha = 1f
            }
            handlerAnimation.postDelayed(this, 1500)
        }
    }


    private fun stopPulse() {
        handlerAnimation.removeCallbacks(ripplerunner)
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

    private fun typingAnimation(textView: TextView, string: String, onComplete: () -> Unit) {
        val n = string.length
        val time = 1250 / n
        val stringBuilder = StringBuilder()

        Thread {
            for (i in string) {
                stringBuilder.append(i)
                Thread.sleep(time.toLong())
                runOnUiThread {
                    textView.text = stringBuilder.toString()
                }
            }
            // Call the onComplete function after the loop ends
            runOnUiThread {
                onComplete()
            }
        }.start()
    }


    private fun toggleSpeech(string: String) {

        if (isSpeaking) {
            // Stop speaking
            textToSpeech.stop()
            isSpeaking = false
        } else {
            if (string.isNotEmpty()) {
                val params = Bundle()
                textToSpeech.speak(string, TextToSpeech.QUEUE_FLUSH, params, "UniqueID")
                isSpeaking = true
            } else {
                Toast.makeText(this, "Text is empty", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Set language for TTS

            textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    // Called when the TTS starts speaking
                    isSpeaking = true
                }

                // App names list (optional, in case you need just the names later)
                val appNames = appMap.keys.toList()

                // Function to launch an app based on user prompt
                fun launchAppFromPrompt(pm: String) {
                    val lowerCasePrompt = pm.lowercase()

                    if ("camera" in lowerCasePrompt) {

                        val intent = Intent(this@MainActivity, CameraActivity::class.java)
                        startActivity(intent)

                    } else if ("read note" in lowerCasePrompt || "show note" in lowerCasePrompt) {

                        val notes = getNotesFromAPI().toString()
                        isSpeaking = false
                        toggleSpeech(notes.toString())
                        typingAnimation(textView, notes) {}
                        prompt = ""

                    } else if ("take note" in lowerCasePrompt || "add note" in lowerCasePrompt) {

                        input = extractTextAfterNote(input).toString()
                        sendDataToMongo(input)
                        val notes = "Successfully Added"
                        isSpeaking = false
                        toggleSpeech(notes.toString())
                        typingAnimation(textView, notes) {}
                        prompt = ""

                    } else if ("delete note" in lowerCasePrompt || "remove note" in lowerCasePrompt) {

                        input = extractTextAfterNote(input).toString()
                        removeDataFromAPI(input)
                        Log.e("Main","$input")
                        val notes = "Successfully Removed"
                        isSpeaking = false
                        toggleSpeech(notes.toString())
                        typingAnimation(textView, notes) {}
                        prompt = ""

                    } else if (prompt != "") {

                        val matchedApp = appMap.keys.firstOrNull { lowerCasePrompt.contains(it) }
                        if (matchedApp != null) {
                            launchApp(appMap[matchedApp]!!)
                        } else {
                            Log.e("AppLauncher", "App not recognized or installed")
                        }
                    }
                }

                // Function to launch the app based on the package name
                fun launchApp(packageName: String) {
                    val intent = packageManager.getLaunchIntentForPackage(packageName)
                    if (intent != null) {
                        startActivity(intent)
                    } else {
                        Log.e("AppLauncher", "App not installed: $packageName")
                    }
                }

                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    val packageManager = packageManager

                    launchAppFromPrompt(prompt)

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

}

fun sendDataToMongo(item_id: String) {
    val url = URL("http://13.53.69.75/adddata/${item_id.replace(" ", "_")}")
    val connection = url.openConnection() as HttpURLConnection

    try {
        connection.requestMethod = "GET" // Set request method to GET
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            println("Data sent successfully to MongoDB!")
        } else {
            println("Failed to send data. Response Code: $responseCode")
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        connection.disconnect()
    }
}

fun removeDataFromAPI(itemId: String): String {
    // Replace spaces with underscores in the item_id to match the API format
    val formattedItemId = itemId.replace(" ", "_")

    // Define the URL for the API endpoint
    val url = URL("http://13.53.69.75/remove/$formattedItemId")

    // Establish an HTTP connection
    with(url.openConnection() as HttpURLConnection) {
        requestMethod = "GET" // or "POST" if you are using POST method on the server

        // Get the response code
        val responseCode = responseCode

        return if (responseCode == HttpURLConnection.HTTP_OK) {
            // Read the response from the input stream
            inputStream.bufferedReader().use { it.readText() }
        } else {
            "Error: $responseCode"
        }
    }
}


fun extractTextAfterNote(input: String): String? {
    // Regex pattern to match "note" or "notes" followed by any text
    val pattern = Pattern.compile("(?i)\\bnotes?\\b\\s*(.*)")
    val matcher = pattern.matcher(input)

    return if (matcher.find()) {
        matcher.group(1)?.trim() // Get the text after "note" or "notes"
    } else {
        null // Return null if "note" or "notes" is not found
    }
}


fun getNotesFromAPI(): List<String> {
    val url = URL("http://13.53.69.75/getdata")
    val connection = url.openConnection() as HttpURLConnection
    val notesList = mutableListOf<String>()

    try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        val responseCode = connection.responseCode

        if (responseCode == HttpURLConnection.HTTP_OK) {
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
            reader.close()

            // Parse the JSON response
            val jsonResponse = JSONObject(response.toString())
            val dataArray: JSONArray = jsonResponse.getJSONArray("m")

            // Append notes into the list from last to first
            for (i in dataArray.length() - 1 downTo 0) {
                val noteObject = dataArray.getJSONObject(i)
                val note = noteObject.getString("note")
                notesList.add(note)
            }
        } else {
            println("GET request failed with response code: $responseCode")
        }
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        connection.disconnect()
    }

    return notesList
}

fun String.toTitleCase(): String {
    return split(" ").joinToString(" ") { it ->
        it.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(
            Locale.ROOT
        ) else it.toString()
    } }
}
