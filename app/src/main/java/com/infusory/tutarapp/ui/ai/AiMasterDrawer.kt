package com.infusory.tutarapp.ui.ai

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.*
import com.infusory.tutarapp.R
import com.infusory.tutarapp.ui.data.ClassData
import com.infusory.tutarapp.ui.data.SubjectData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import android.util.Base64
import android.graphics.BitmapFactory
import com.bumptech.glide.Glide
import com.infusory.tutarapp.ui.whiteboard.WhiteboardActivity

class AiMasterDrawer(
    private val activity: WhiteboardActivity,
    private val onResultReceived: (String) -> Unit
) {

    private val context: Context get() = activity
    private var dialog: Dialog? = null
    private lateinit var dialogView: View

    // UI Components
    private lateinit var classSpinner: Spinner
    private lateinit var subjectSpinner: Spinner
    private lateinit var optionSpinner: Spinner
    private lateinit var queryEditText: EditText
    private lateinit var searchButton: ImageButton
    private lateinit var resultsScrollView: ScrollView
    private lateinit var resultsContainer: LinearLayout

    // Data
    private var classesData: List<ClassData> = emptyList()
    private var selectedClass: ClassData? = null
    private var selectedSubject: SubjectData? = null
    private var selectedOption: String = ""

    private val options = arrayOf("Lesson Plan", "Description")

    // properties to store the last search results
    private var lastSearchData: JSONObject? = null
    private var hasSearchResults: Boolean = false

    private var capturedImageBase64: String? = null

    // Network
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun show() {
        capturedImageBase64 = null // Clear any previous image
        loadClassData()
        if (classesData.isNotEmpty()) {
            createAndShowDialog()
        } else {
            Toast.makeText(context, "Unable to load class data", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadClassData() {
        try {
            val gson = Gson()
            val listType = object : TypeToken<List<ClassData>>() {}.type

            try {
                val inputStream = context.assets.open("class_list_demo.json")
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                inputStream.close()

                classesData = gson.fromJson(jsonString, listType)
                return
            } catch (e: Exception) {
                Log.d("AiMasterDrawer", "Failed to load from assets, trying other locations...")
            }

            val internalFile = java.io.File(context.filesDir, "class_data.json")
            if (internalFile.exists()) {
                val fileReader = java.io.FileReader(internalFile)
                classesData = gson.fromJson(fileReader, listType)
                fileReader.close()
                return
            }

            Log.e("AiMasterDrawer", "class_data.json not found in any location")
            classesData = emptyList()

        } catch (e: Exception) {
            Log.e("AiMasterDrawer", "Error loading class data: ${e.message}", e)
            classesData = emptyList()
        }
    }

//    private fun setupViews(view: View) {
//        view.findViewById<View>(R.id.btn_close).setOnClickListener { dialog?.dismiss() }
//        classSpinner = view.findViewById(R.id.spinner_class)
//        subjectSpinner = view.findViewById(R.id.spinner_subject)
//        optionSpinner = view.findViewById(R.id.spinner_option)
//        queryEditText = view.findViewById(R.id.et_query)
//        searchButton = view.findViewById(R.id.btn_search) // ✅ Now correctly gets ImageButton
//        searchButton.setOnClickListener { performSearch() }
//        resultsScrollView = view.findViewById(R.id.scroll_results)
//        resultsContainer = view.findViewById(R.id.ll_results_container)
//
//        // ✅ ADD THIS: Handle image preview if available
//        if (!capturedImageBase64.isNullOrEmpty()) {
//            // Hide the query input when using image search
//            queryEditText.visibility = View.GONE
//            queryEditText.setText("Image search active")
//
//            Toast.makeText(context, "Using captured image for search", Toast.LENGTH_SHORT).show()
//        } else {
//            // Show query input for text search
//            queryEditText.visibility = View.VISIBLE
//        }
//    }

    private fun setupViews(view: View) {
        view.findViewById<View>(R.id.btn_close).setOnClickListener { dialog?.dismiss() }
        classSpinner = view.findViewById(R.id.spinner_class)
        subjectSpinner = view.findViewById(R.id.spinner_subject)
        optionSpinner = view.findViewById(R.id.spinner_option)
        queryEditText = view.findViewById(R.id.et_query)
        searchButton = view.findViewById(R.id.btn_search)
        searchButton.setOnClickListener { performSearch() }
        resultsScrollView = view.findViewById(R.id.scroll_results)
        resultsContainer = view.findViewById(R.id.ll_results_container)

        // ✅ Handle image vs text search mode
        if (!capturedImageBase64.isNullOrEmpty()) {
            // Image search mode - hide query input
            queryEditText.visibility = View.GONE

            Log.d("AiMasterDrawer", "🖼️ IMAGE SEARCH MODE")
            Log.d(
                "AiMasterDrawer",
                "Base64 image length: ${capturedImageBase64!!.length} characters"
            )

            Toast.makeText(
                context,
                "🖼️ Image captured! Select class, subject & option, then search.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            // Text search mode - show query input
            queryEditText.visibility = View.VISIBLE
            queryEditText.setText("") // Clear any previous text

            Log.d("AiMasterDrawer", "📝 TEXT SEARCH MODE")
        }
    }
//    private fun setupSpinners() {
//        val classNames = mutableListOf("Select Class")
//        classNames.addAll(classesData.map { it.Class })
//        val classAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, classNames)
//        classAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
//        classSpinner.adapter = classAdapter
//        classSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
//            override fun onItemSelected(
//                parent: AdapterView<*>?,
//                view: View?,
//                position: Int,
//                id: Long
//            ) {
//                selectedClass = if (position > 0) classesData[position - 1] else null
//                setupSubjectSpinner()
//            }
//
//            override fun onNothingSelected(parent: AdapterView<*>?) {
//                selectedClass = null; setupSubjectSpinner()
//            }
//        }
//
//        val optionsList = mutableListOf("Select Option")
//        optionsList.addAll(options)
//        val optionAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, optionsList)
//        optionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
//        optionSpinner.adapter = optionAdapter
//        optionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
//            override fun onItemSelected(
//                parent: AdapterView<*>?,
//                view: View?,
//                position: Int,
//                id: Long
//            ) {
//                selectedOption = if (position > 0) options[position - 1] else ""
//            }
//
//            override fun onNothingSelected(parent: AdapterView<*>?) {
//                selectedOption = ""
//            }
//        }
//        setupSubjectSpinner()
//    }

    private fun setupSpinners() {
        val classNames = mutableListOf("Select Class")
        classNames.addAll(classesData.map { it.Class })
        val classAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, classNames)
        classAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        classSpinner.adapter = classAdapter

        // ✅ FORCE DEFAULT CLASS TO "Demo"
        val demoClassIndex = classesData.indexOfFirst { it.Class.equals("Demo", ignoreCase = true) }
        if (demoClassIndex != -1) {
            // Set selection without triggering listener initially
            classSpinner.post {
                classSpinner.setSelection(demoClassIndex + 1)
            }
            selectedClass = classesData[demoClassIndex]
            Log.d("AiMasterDrawer", "Default class set to: ${selectedClass?.Class}")
        } else {
            Log.w("AiMasterDrawer", "Demo class not found in data")
        }

        classSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                selectedClass = if (position > 0) classesData[position - 1] else null
                setupSubjectSpinner()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedClass = null
                setupSubjectSpinner()
            }
        }

        val optionsList = mutableListOf("Select Option")
        optionsList.addAll(options)
        val optionAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, optionsList)
        optionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        optionSpinner.adapter = optionAdapter

        // ✅ FORCE DEFAULT OPTION TO "Lesson Plan"
        val lessonPlanIndex = options.indexOfFirst { it.equals("Lesson Plan", ignoreCase = true) }
        if (lessonPlanIndex != -1) {
            optionSpinner.post {
                optionSpinner.setSelection(lessonPlanIndex + 1)
            }
            selectedOption = options[lessonPlanIndex]
            Log.d("AiMasterDrawer", "Default option set to: $selectedOption")
        } else {
            Log.w("AiMasterDrawer", "Lesson Plan option not found")
        }

        optionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                selectedOption = if (position > 0) options[position - 1] else ""
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedOption = ""
            }
        }

        // ✅ Setup subject spinner with default selection
        setupSubjectSpinner()
    }
//    private fun setupSubjectSpinner() {
//        val subjectNames = mutableListOf("Select Subject")
//        selectedClass?.subjects?.let { subjectNames.addAll(it.map { it.name }) }
//        val subjectAdapter =
//            ArrayAdapter(context, android.R.layout.simple_spinner_item, subjectNames)
//        subjectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
//        subjectSpinner.adapter = subjectAdapter
//        subjectSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
//            override fun onItemSelected(
//                parent: AdapterView<*>?,
//                view: View?,
//                position: Int,
//                id: Long
//            ) {
//                selectedSubject =
//                    if (position > 0 && selectedClass?.subjects != null) selectedClass!!.subjects!![position - 1] else null
//            }
//
//            override fun onNothingSelected(parent: AdapterView<*>?) {
//                selectedSubject = null
//            }
//        }
//    }

    private fun setupSubjectSpinner() {
        val subjectNames = mutableListOf("Select Subject")
        selectedClass?.subjects?.let { subjectNames.addAll(it.map { it.name }) }
        val subjectAdapter =
            ArrayAdapter(context, android.R.layout.simple_spinner_item, subjectNames)
        subjectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        subjectSpinner.adapter = subjectAdapter

        // ✅ FORCE DEFAULT SUBJECT TO "General"
        if (selectedClass?.subjects != null) {
            val generalSubjectIndex = selectedClass!!.subjects!!.indexOfFirst {
                it.name.equals("General", ignoreCase = true)
            }
            if (generalSubjectIndex != -1) {
                subjectSpinner.post {
                    subjectSpinner.setSelection(generalSubjectIndex + 1)
                }
                selectedSubject = selectedClass!!.subjects!![generalSubjectIndex]
                Log.d("AiMasterDrawer", "Default subject set to: ${selectedSubject?.name}")
            } else {
                Log.w(
                    "AiMasterDrawer",
                    "General subject not found for class: ${selectedClass?.Class}"
                )
            }
        }

        subjectSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                selectedSubject = if (position > 0 && selectedClass?.subjects != null) {
                    selectedClass!!.subjects!![position - 1]
                } else {
                    null
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedSubject = null
            }
        }
    }
//    private fun performSearch() {
//        // Check for selections
//        if (selectedClass == null || selectedSubject == null || selectedOption.isEmpty()) {
//            Toast.makeText(context, "Please select class, subject, and option", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        // Check if we have either a query or an image
//        val queryText = queryEditText.text.toString().trim()
//        val hasQuery = queryText.isNotEmpty() && queryText != "Image search active"
//        val hasImage = !capturedImageBase64.isNullOrEmpty()
//
//        if (!hasQuery && !hasImage) {
//            Toast.makeText(context, "Please enter a query or use image search", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        searchButton.isEnabled = false
//        // ImageButton doesn't have text property, so we can't change the button text
//        // Optionally, you could change the button's appearance or show a progress indicator
//
//        val requestData = JSONObject().apply {
//            put("className", selectedClass!!.Class)
//            put("subjectName", selectedSubject!!.name)
//            put("contentType", selectedOption)
//
//            // ✅ FIXED: Include image if available, otherwise use text query
//            if (hasImage) {
//                // Remove the "data:image/png;base64," prefix if present
//                val cleanBase64 = if (capturedImageBase64!!.contains(",")) {
//                    capturedImageBase64!!.split(",")[1]
//                } else {
//                    capturedImageBase64!!
//                }
//
//                put("image", cleanBase64)
//                put("imageType", "image/png")
//                // Don't include query field when using image, or use a placeholder
//                // Check with your API documentation which is correct
//            } else {
//                put("query", queryText)
//            }
//        }
//
//        Log.d("AiMasterDrawer", "Search request: $requestData")
//        makeApiRequest(requestData.toString())
//    }

    // ✅ NEW METHOD: Directly perform image search without showing dialog first
    fun performImageSearchDirectly(imageBase64: String) {
        Log.d("AiMasterDrawer", "=== performImageSearchDirectly() called ===")
        Log.d("AiMasterDrawer", "Base64 image length: ${imageBase64.length}")

        capturedImageBase64 = imageBase64

        // Clear previous results
        lastSearchData = null
        hasSearchResults = false

        // Load class data if not already loaded
        if (classesData.isEmpty()) {
            loadClassData()
        }

        if (classesData.isEmpty()) {
            Toast.makeText(context, "Unable to load class data", Toast.LENGTH_LONG).show()
            return
        }

        // Find Demo class
        val demoClass = classesData.find { it.Class.equals("Demo", ignoreCase = true) }
        if (demoClass == null) {
            Log.e("AiMasterDrawer", "Demo class not found")
            Toast.makeText(context, "Demo class not found", Toast.LENGTH_SHORT).show()
            return
        }

        // Find General subject
        val generalSubject =
            demoClass.subjects?.find { it.name.equals("General", ignoreCase = true) }
        if (generalSubject == null) {
            Log.e("AiMasterDrawer", "General subject not found")
            Toast.makeText(context, "General subject not found", Toast.LENGTH_SHORT).show()
            return
        }

        // Set default values
        selectedClass = demoClass
        selectedSubject = generalSubject
        selectedOption = "Lesson Plan"

        Log.d(
            "AiMasterDrawer",
            "Auto-selected: Class=${selectedClass?.Class}, Subject=${selectedSubject?.name}, Option=$selectedOption"
        )

        // ✅ Prepare request with correct key names for your API
        val requestData = JSONObject().apply {
            put("className", selectedClass!!.Class)
            put("subjectName", selectedSubject!!.name)
            put("contentType", selectedOption)

            // ✅ IMPORTANT: Use the correct key name your API expects
            // Based on your description, it should be "base64Image"
            put("base64Image", imageBase64)
            put("imageType", "image/png") // or "mimeType": "image/png" if your API uses that

            // Don't include query field for image search
        }

        Log.d("AiMasterDrawer", "Request prepared with image, making API call...")
        Log.d("AiMasterDrawer", "Request keys: ${requestData.keys().asSequence().toList()}")

        // Make API request
        makeApiRequest(requestData.toString())
    }

    private fun performSearch() {
        Log.d("AiMasterDrawer", "=== performSearch() called ===")

        // Check for selections
        if (selectedClass == null || selectedSubject == null || selectedOption.isEmpty()) {
            Log.w(
                "AiMasterDrawer",
                "Missing selections - Class: $selectedClass, Subject: $selectedSubject, Option: $selectedOption"
            )
            Toast.makeText(context, "Please select class, subject, and option", Toast.LENGTH_SHORT)
                .show()
            return
        }

        Log.d(
            "AiMasterDrawer",
            "Selections OK - Class: ${selectedClass?.Class}, Subject: ${selectedSubject?.name}, Option: $selectedOption"
        )

        // Check if we have either a query or an image
        val queryText = queryEditText.text.toString().trim()
        val hasQuery = queryText.isNotEmpty() && queryText != "Image search active"
        val hasImage = !capturedImageBase64.isNullOrEmpty()

        Log.d("AiMasterDrawer", "Query text: '$queryText'")
        Log.d("AiMasterDrawer", "Has valid query: $hasQuery")
        Log.d("AiMasterDrawer", "Has image: $hasImage")
        Log.d("AiMasterDrawer", "Image Base64 length: ${capturedImageBase64?.length ?: 0}")

        if (!hasQuery && !hasImage) {
            Log.w("AiMasterDrawer", "Neither query nor image available")
            Toast.makeText(context, "Please enter a query or use image search", Toast.LENGTH_SHORT)
                .show()
            return
        }

        searchButton.isEnabled = false
        Log.d("AiMasterDrawer", "Search button disabled, preparing request...")

        val requestData = JSONObject().apply {
            put("className", selectedClass!!.Class)
            put("subjectName", selectedSubject!!.name)
            put("contentType", selectedOption)

            // ✅ Send ONLY image OR query, not both
            if (hasImage) {
                Log.d("AiMasterDrawer", "Preparing IMAGE search request")

                // Clean the base64 string (remove data URI prefix if present)
                val cleanBase64 = if (capturedImageBase64!!.contains(",")) {
                    val parts = capturedImageBase64!!.split(",")
                    Log.d("AiMasterDrawer", "Removing data URI prefix: ${parts[0]}")
                    parts[1]
                } else {
                    capturedImageBase64!!
                }

                Log.d("AiMasterDrawer", "Clean Base64 length: ${cleanBase64.length}")
                Log.d("AiMasterDrawer", "First 50 chars: ${cleanBase64.take(50)}")

                put("base64Image", cleanBase64)
                put("mimeType", "image/png")

                Log.d("AiMasterDrawer", "✅ Image added to request (NOT including query field)")
            } else {
                Log.d("AiMasterDrawer", "Preparing TEXT search request")
                put("query", queryText)
                Log.d("AiMasterDrawer", "✅ Query added to request: $queryText")
            }
        }

        Log.d("AiMasterDrawer", "=== Request Summary ===")
        Log.d("AiMasterDrawer", "Using image search: $hasImage")
        Log.d("AiMasterDrawer", "Using text search: $hasQuery")
        Log.d("AiMasterDrawer", "Request keys: ${requestData.keys().asSequence().toList()}")
        Log.d("AiMasterDrawer", "Request size: ${requestData.toString().length} characters")

        makeApiRequest(requestData.toString())
    }

//    private fun makeApiRequest(jsonData: String) {
//        val mediaType = "application/json; charset=utf-8".toMediaType()
//        val requestBody = jsonData.toRequestBody(mediaType)
//        val request = Request.Builder()
//            .url("https://dev-api.tutarverse.com/gemini/searcher")
//            .post(requestBody)
//            .addHeader("Content-Type", "application/json")
//            .build()
//
//        coroutineScope.launch(Dispatchers.IO) {
//            try {
//                val response = client.newCall(request).execute()
//                val responseBody = response.body?.string() ?: ""
//                withContext(Dispatchers.Main) {
//                    searchButton.isEnabled = true
//                    // ✅ Removed searchButton.text since ImageButton doesn't have text
//                    if (response.isSuccessful) {
//                        Log.e("AiMasterDrawer", "API Response: $responseBody")
//                        handleApiResponse(responseBody)
//                    } else {
//                        Toast.makeText(
//                            context,
//                            "Search failed: ${response.code}",
//                            Toast.LENGTH_LONG
//                        ).show()
//                    }
//                }
//            } catch (e: IOException) {
//                withContext(Dispatchers.Main) {
//                    searchButton.isEnabled = true
//                    // ✅ Removed searchButton.text
//                    Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
//                }
//            } catch (e: Exception) {
//                withContext(Dispatchers.Main) {
//                    searchButton.isEnabled = true
//                    // ✅ Removed searchButton.text
//                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
//                }
//            }
//        }
//    }

    private fun makeApiRequest(jsonData: String) {
        Log.e("AiMasterDrawer", "API parameter Response: $jsonData")
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonData.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://dev-api.tutarverse.com/gemini/search")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                withContext(Dispatchers.Main) {
                    // ✅ Only enable button if it's initialized (dialog is shown)
                    if (::searchButton.isInitialized) {
                        searchButton.isEnabled = true
                    }

                    if (response.isSuccessful) {
                        // Log the full successful response
                        Log.e("AiMasterDrawer", "API Response: $responseBody")
                        handleApiResponse(responseBody)
                    } else {
                        Log.e("AiMasterDrawer", "API Error [${response.code}]: $responseBody")
                        Toast.makeText(
                            context,
                            "Search failed: ${response.code}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: IOException) {
                Log.e("AiMasterDrawer", "Network error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    // ✅ Only enable button if it's initialized
                    if (::searchButton.isInitialized) {
                        searchButton.isEnabled = true
                    }
                    Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("AiMasterDrawer", "Unexpected error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    // ✅ Only enable button if it's initialized
                    if (::searchButton.isInitialized) {
                        searchButton.isEnabled = true
                    }
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun handleApiResponse(responseBody: String) {
        try {
            val jsonResponse = JSONObject(responseBody)
            if (jsonResponse.optBoolean("status", false)) {
                val data = jsonResponse.getJSONObject("data")
                // Store the data before displaying
                lastSearchData = data
                hasSearchResults = true

                // ✅ Show dialog with results if not already shown
                if (dialog == null || dialog?.isShowing == false) {
                    Log.d("AiMasterDrawer", "Creating and showing dialog with results")
                    createAndShowDialog()
                } else {
                    Log.d("AiMasterDrawer", "Updating existing dialog with results")
                    displayResults(data)
                    resultsScrollView.visibility = View.VISIBLE
                }

                Toast.makeText(context, "Search completed successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Search failed: No data received", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("AiMasterDrawer", "Error handling API response", e)
            Toast.makeText(context, "Error processing response: ${e.message}", Toast.LENGTH_LONG)
                .show()
        }
    }

//    private fun createAndShowDialog() {
//        dialog = Dialog(context, R.style.CustomCenteredDialog)
//        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
//
//        val view = LayoutInflater.from(context).inflate(R.layout.dialog_ai_master, null)
//        dialogView = view
//
//        setupViews(view)
//        setupSpinners()
//
//        // Restore previous search results if they exist
//        if (hasSearchResults && lastSearchData != null) {
//            resultsScrollView.visibility = View.VISIBLE
//            displayResults(lastSearchData!!)
//        }
//
//        dialog?.setContentView(view)
//
//        dialog?.window?.let { window ->
//            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
//
//            val displayMetrics = context.resources.displayMetrics
//            val configuration = context.resources.configuration
//            val isLandscape =
//                configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
//
//            val dialogWidth = (550 * displayMetrics.density).toInt()
//            val dialogHeight = (displayMetrics.heightPixels * 0.90).toInt()
//
//            val layoutParams = window.attributes
//            layoutParams.width = dialogWidth
//            layoutParams.height = dialogHeight
//            layoutParams.gravity =
//                if (isLandscape) Gravity.CENTER else Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
//            layoutParams.dimAmount = 0.6f
//            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
//            window.attributes = layoutParams
//        }
//
//        dialog?.setCancelable(true)
//        dialog?.setCanceledOnTouchOutside(true)
//        dialog?.show()
//    }

    private fun createAndShowDialog() {
        dialog = Dialog(context, R.style.CustomCenteredDialog)
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_ai_master, null)
        dialogView = view

        setupViews(view)
        setupSpinners()

        // ✅ ONLY restore previous search results if NOT doing an image search
        if (hasSearchResults && lastSearchData != null && capturedImageBase64.isNullOrEmpty()) {
            resultsScrollView.visibility = View.VISIBLE
            displayResults(lastSearchData!!)
            Log.d("AiMasterDrawer", "Restored previous search results")
        } else {
            resultsScrollView.visibility = View.GONE
            Log.d("AiMasterDrawer", "Starting fresh - no previous results shown")
        }

        dialog?.setContentView(view)

        dialog?.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            val displayMetrics = context.resources.displayMetrics
            val configuration = context.resources.configuration
            val isLandscape =
                configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

            val dialogWidth = (550 * displayMetrics.density).toInt()
            val dialogHeight = (displayMetrics.heightPixels * 0.90).toInt()

            val layoutParams = window.attributes
            layoutParams.width = dialogWidth
            layoutParams.height = dialogHeight
            layoutParams.gravity =
                if (isLandscape) Gravity.CENTER else Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            layoutParams.dimAmount = 0.6f
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes = layoutParams
        }

        dialog?.setCancelable(true)
        dialog?.setCanceledOnTouchOutside(true)
        dialog?.show()
    }

    private fun displayResults(data: JSONObject) {
        // Clear immediately
        resultsContainer.removeAllViews()
        resultsScrollView.visibility = View.VISIBLE

        Log.d("AiMasterDrawer", "Data keys: ${data.keys().asSequence().toList()}")

        // Show loading indicator
        val loadingView = TextView(context).apply {
            text = "Loading content..."
            textSize = 14f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(dpToPx(20), dpToPx(40), dpToPx(20), dpToPx(40))
        }
        resultsContainer.addView(loadingView)

        // Process views with delays to prevent frame drops
        coroutineScope.launch {
            delay(50) // Let the loading view render first

            withContext(Dispatchers.Main) {
                resultsContainer.removeView(loadingView)
            }

            // Handle content first (lesson plan or description)
            if (data.has("content")) {
                val content = data.getJSONObject("content")

                if (content.has("lessonPlan")) {
                    withContext(Dispatchers.Main) {
                        displayLessonPlan(content.getJSONObject("lessonPlan"))
                    }
                    delay(100) // Give UI time to render
                }

                if (content.has("description")) {
                    val descriptionArray = content.getJSONArray("description")
                    if (descriptionArray.length() > 0) {
                        withContext(Dispatchers.Main) {
                            displayDescription(descriptionArray)
                        }
                        delay(100)
                    }
                }
            }

            // Media sections with progressive loading
            if (data.has("names")) {
                val names = data.getJSONArray("names")
                if (names.length() > 0) {
                    withContext(Dispatchers.Main) {
                        display3DModels(names)
                    }
                    delay(100)
                }
            }

            if (data.has("images")) {
                val images = data.getJSONArray("images")
                if (images.length() > 0) {
                    withContext(Dispatchers.Main) {
                        displayImages(images)
                    }
                    delay(100)
                }
            }

            if (data.has("videos")) {
                val videos = data.getJSONArray("videos")
                if (videos.length() > 0) {
                    withContext(Dispatchers.Main) {
                        displayVideos(videos)
                    }
                }
            }
        }
    }

    // ===== LESSON PLAN DISPLAY =====

    private fun displayLessonPlan(lessonPlan: JSONObject) {
        resultsContainer.addView(createLessonPlanContainer(lessonPlan))
    }

    private fun createLessonPlanContainer(lessonPlan: JSONObject): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(4), dpToPx(12), dpToPx(4), dpToPx(12))

            layoutParams = createMarginLayoutParams(0, dpToPx(20), 0, dpToPx(8))
            elevation = 3f
            isClickable = true
            setOnClickListener {
                Log.d("AiMasterDrawer", "Lesson Plan clicked")
                Toast.makeText(context, "Lesson Plan", Toast.LENGTH_SHORT).show()
            }

            // Add title
            addView(createSectionHeader("Lesson Plan"))

            // Add content sections
            if (lessonPlan.has("topic")) {
                addView(createLessonPlanCard("TOPIC", lessonPlan.getString("topic"), true))
            }
            if (lessonPlan.has("explanation")) {
                addView(
                    createLessonPlanCard(
                        "Explanation",
                        lessonPlan.getString("explanation"),
                        false
                    )
                )
            }
            if (lessonPlan.has("conclusion")) {
                addView(
                    createLessonPlanCard(
                        "Conclusion",
                        lessonPlan.getString("conclusion"),
                        false
                    )
                )
            }
            if (lessonPlan.has("notes")) {
                addView(createLessonPlanCard("Notes", lessonPlan.getString("notes"), false))
            }
        }
    }

//    private fun createLessonPlanCard(heading: String, content: String, isTopic: Boolean): LinearLayout {
//        return LinearLayout(context).apply {
//            orientation = LinearLayout.VERTICAL
//            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
//
//            background = createGradientBackground()
//            layoutParams = createMarginLayoutParams(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
//            elevation = 2f
//            isClickable = true
//            setOnClickListener {
//                Log.d("AiMasterDrawer", "$heading clicked")
//                Toast.makeText(context, heading, Toast.LENGTH_SHORT).show()
//            }
//
//            // Heading
//            addView(TextView(context).apply {
//                text = heading
//                textSize = if (isTopic) 14f else 16f
//                setTypeface(null, android.graphics.Typeface.BOLD)
//                setTextColor(Color.WHITE)
//                letterSpacing = if (isTopic) 0.1f else 0.05f
//                gravity = if (isTopic) Gravity.CENTER else Gravity.START
//            })
//
//            // Content
//            addView(TextView(context).apply {
//                text = content
//                textSize = if (isTopic) 18f else 15f
//                setTextColor(Color.WHITE)
//                setPadding(0, dpToPx(8), 0, 0)
//                setTextIsSelectable(true)
//                setLineSpacing(1.4f, 1.0f)
//                gravity = if (isTopic) Gravity.CENTER else Gravity.START
//            })
//        }
//    }

    private fun addTextContainerToWhiteboard(text: String, title: String = "") {
        try {
            // Call the activity method to add text container
            activity.addTextContainerWithContent(text, title)

            // Dismiss the drawer after adding
            dismiss()

            Toast.makeText(context, "Text added to canvas", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("AiMasterDrawer", "Error adding text container", e)
            Toast.makeText(context, "Error adding text: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createLessonPlanCard(
        heading: String,
        content: String,
        isTopic: Boolean
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))

            background = createGradientBackground()
            layoutParams = createMarginLayoutParams(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
            elevation = 2f
            isClickable = true
            setOnClickListener {
                Log.d("AiMasterDrawer", "$heading clicked")
                // Add text container instead of just showing toast
                addTextContainerToWhiteboard(content, heading)
            }

            // Heading
            addView(TextView(context).apply {
                text = heading
                textSize = if (isTopic) 14f else 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.WHITE)
                letterSpacing = if (isTopic) 0.1f else 0.05f
                gravity = if (isTopic) Gravity.CENTER else Gravity.START
            })

            // Content
            addView(TextView(context).apply {
                text = content
                textSize = if (isTopic) 18f else 15f
                setTextColor(Color.WHITE)
                setPadding(0, dpToPx(8), 0, 0)
                setTextIsSelectable(true)
                setLineSpacing(1.4f, 1.0f)
                gravity = if (isTopic) Gravity.CENTER else Gravity.START
            })
        }
    }
    // ===== DESCRIPTION DISPLAY =====

    private fun displayDescription(descriptionArray: JSONArray) {
        resultsContainer.addView(createDescriptionContainer(descriptionArray))
        resultsContainer.addView(createSpacer(5))
    }

    private fun createDescriptionContainer(descriptionArray: JSONArray): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(4), dpToPx(12), dpToPx(4), dpToPx(12))
            layoutParams = createMarginLayoutParams(0, dpToPx(20), 0, dpToPx(8))
            elevation = 3f
            isClickable = true
            setOnClickListener {
                Log.d("AiMasterDrawer", "Description clicked")
                Toast.makeText(context, "Description", Toast.LENGTH_SHORT).show()
            }

            // Add title
            addView(createSectionHeader("Description"))

            // Add description points (limit to prevent performance issues)
            val maxPoints = minOf(descriptionArray.length(), 15)
            for (i in 0 until maxPoints) {
                addView(createDescriptionPoint(descriptionArray.getString(i), i))
            }

            // Show "more" indicator if needed
            if (descriptionArray.length() > maxPoints) {
                addView(TextView(context).apply {
                    text = "... ${descriptionArray.length() - maxPoints} more points"
                    textSize = 12f
                    setTextColor(Color.LTGRAY)
                    setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
                    gravity = Gravity.CENTER
                })
            }
        }
    }

    //    private fun createDescriptionPoint(point: String, index: Int): LinearLayout {
//        return LinearLayout(context).apply {
//            orientation = LinearLayout.HORIZONTAL
//            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
//
//            background = createDashedBorder()
//            layoutParams = createMarginLayoutParams(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4))
//            isClickable = true
//            setOnClickListener {
//                Log.d("AiMasterDrawer", "Point ${index + 1} clicked")
//                Toast.makeText(context, "Point ${index + 1}", Toast.LENGTH_SHORT).show()
//            }
//
//            // Bullet
//            addView(View(context).apply {
//                background = createCircleBullet()
//                layoutParams = LinearLayout.LayoutParams(dpToPx(8), dpToPx(8)).apply {
//                    setMargins(dpToPx(4), dpToPx(8), dpToPx(12), 0)
//                }
//            })
//
//            // Text
//            addView(TextView(context).apply {
//                text = point
//                textSize = 14f
//                setTextColor(Color.WHITE)
//                setLineSpacing(1.4f, 1.0f)
//                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
//            })
//        }
//    }
    private fun createDescriptionPoint(point: String, index: Int): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))

            background = createDashedBorder()
            layoutParams = createMarginLayoutParams(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4))
            isClickable = true
            setOnClickListener {
                Log.d("AiMasterDrawer", "Point ${index + 1} clicked")
                // Add text container instead of just showing toast
                addTextContainerToWhiteboard(point, "Point ${index + 1}")
            }

            // Bullet
            addView(View(context).apply {
                background = createCircleBullet()
                layoutParams = LinearLayout.LayoutParams(dpToPx(8), dpToPx(8)).apply {
                    setMargins(dpToPx(4), dpToPx(8), dpToPx(12), 0)
                }
            })

            // Text
            addView(TextView(context).apply {
                text = point
                textSize = 14f
                setTextColor(Color.WHITE)
                setLineSpacing(1.4f, 1.0f)
                layoutParams =
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
    }
    // ===== ADD THESE HELPER METHODS TO YOUR AiMasterDrawer CLASS =====

    private fun createSectionHeader(title: String): TextView {
        return TextView(context).apply {
            text = title
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(12))
        }
    }

    private fun createSpacer(heightDp: Int): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(heightDp)
            )
        }
    }

    private fun createMarginLayoutParams(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(left, top, right, bottom)
        }
    }

    private fun createRoundedBackground(
        colorHex: String,
        cornerRadius: Float
    ): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor(colorHex))
            this.cornerRadius = cornerRadius
        }
    }

    private fun createGradientBackground(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.parseColor("#2C5F5D"),
                Color.parseColor("#1E4645")
            )
        ).apply {
            cornerRadius = dpToPx(8).toFloat()
        }
    }

    private fun createDashedBorder(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(
                dpToPx(1),
                Color.parseColor("#666666"),
                dpToPx(8).toFloat(),
                dpToPx(4).toFloat()
            )
            cornerRadius = dpToPx(8).toFloat()
        }
    }

    private fun createCircleBullet(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(Color.parseColor("#2196F3"))
        }
    }


//    private fun displayMCQs(mcqs: JSONArray) {
//        val sectionTitle = createSectionTitle("Multiple Choice Questions")
//        resultsContainer.addView(sectionTitle)
//        for (i in 0 until mcqs.length()) {
//            val mcq = mcqs.getJSONObject(i)
//            resultsContainer.addView(createMCQCard(mcq, i + 1))
//        }
//        addSpacer(5)
//    }

    private fun displayVideos(videos: JSONArray) {
        val sectionTitle = createSectionTitle("Videos")
        resultsContainer.addView(sectionTitle)

        val horizontalContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val scrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(horizontalContainer)
        }
        resultsContainer.addView(scrollView)

        for (i in 0 until videos.length()) {
            val video = videos.getJSONObject(i)
            val videoId = video.getString("videoId")
            val thumbnail = video.optString("thumbnail", "")
            val title = video.optString("title", "Video ${i + 1}")

            val videoCard = createVideoCard(videoId, thumbnail, title)
            horizontalContainer.addView(videoCard)
        }

        addSpacer(5)
    }

    private fun display3DModels(names: JSONArray) {
        val sectionTitle = createSectionTitle("3D Models")
        resultsContainer.addView(sectionTitle)

        val horizontalContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val scrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(horizontalContainer)
        }
        resultsContainer.addView(scrollView)

        for (i in 0 until names.length()) {
            val name = names.getString(i)
            val modelCard = create3DModelCard(name)
            horizontalContainer.addView(modelCard)
        }

        addSpacer(5)
    }

    private fun addYouTubeContainerToWhiteboard(videoId: String) {
        try {
            // Create the YouTube URL
            val youtubeUrl = "https://www.youtube.com/watch?v=$videoId"

            // Call the activity method to add YouTube container
            activity.addYouTubeContainerWithUrl(youtubeUrl)

            // Dismiss the drawer after adding
            dismiss()

            Toast.makeText(context, "YouTube video added to canvas", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("AiMasterDrawer", "Error adding YouTube container", e)
            Toast.makeText(context, "Error adding video: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createVideoCard(videoId: String, thumbnail: String, title: String): LinearLayout {
        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setBackgroundResource(R.drawable.modern_video_card_background)
            layoutParams =
                LinearLayout.LayoutParams(dpToPx(200), LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply {
                        setMargins(dpToPx(8), 0, dpToPx(8), 0)
                    }
            isClickable = true
            elevation = 4f
            // ✅ Change click listener to add YouTube container
            setOnClickListener {
                addYouTubeContainerToWhiteboard(videoId)
            }
        }

        // ✅ Create a FrameLayout to overlay the play icon on the thumbnail
        val thumbnailContainer = FrameLayout(context).apply {
            layoutParams =
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(80))
        }

        val thumbnailView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            // Use Glide to load the image
            if (thumbnail.isNotEmpty()) {
                Glide.with(context)
                    .load(thumbnail)
                    .placeholder(R.drawable.section_icon_video)
                    .error(R.drawable.section_icon_video)
                    .into(this)
            } else {
                setImageResource(R.drawable.section_icon_video)
            }
        }

        // ✅ Create the play icon overlay
        val playIcon = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_media_play)
            scaleType = ImageView.ScaleType.CENTER
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(48),
                dpToPx(48)
            ).apply {
                gravity = Gravity.CENTER
            }

            // Create rounded background programmatically
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#80000000")) // Semi-transparent black
            }
            background = drawable

            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
        }

        // Add thumbnail and play icon to the container
        thumbnailContainer.addView(thumbnailView)
        thumbnailContainer.addView(playIcon)

        cardLayout.addView(thumbnailContainer)
        return cardLayout
    }

    private fun create3DModelCard(name: String): LinearLayout {
        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setBackgroundResource(R.drawable.modern_video_card_background)
            layoutParams =
                LinearLayout.LayoutParams(dpToPx(120), LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply {
                        setMargins(dpToPx(8), 0, dpToPx(8), 0)
                    }
            isClickable = true
            elevation = 4f
            setOnClickListener { open3DModel(name) }
        }

        val thumbnailView = ImageView(context).apply {
            setImageResource(R.drawable.ic_ar) // Ensure this drawable exists
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams =
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(80))
        }

        val titleView = TextView(context).apply {
            text = name
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dpToPx(4), 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        cardLayout.addView(thumbnailView)
        cardLayout.addView(titleView)
        return cardLayout
    }

    private fun createSectionTitle(title: String): LinearLayout {
        val sectionLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dpToPx(20), 0, dpToPx(12))
            }
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8))
        }

        val iconRes = when (title.lowercase()) {
            "lesson plan" -> R.drawable.section_icon_lesson
            "videos" -> R.drawable.section_icon_video
            "multiple choice questions" -> R.drawable.section_icon_quiz
            "3d models" -> R.drawable.section_icon_lesson // Add this drawable
            "images" -> R.drawable.section_icon_lesson // Add this drawable
            else -> R.drawable.section_icon_lesson
        }

        val iconView = ImageView(context).apply {
            setImageResource(iconRes)
            layoutParams = LinearLayout.LayoutParams(dpToPx(28), dpToPx(28)).apply {
                setMargins(0, 0, dpToPx(12), 0)
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }

        val titleView = TextView(context).apply {
            text = title
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#1F2937"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        sectionLayout.addView(iconView)
        sectionLayout.addView(titleView)
        return sectionLayout
    }

    private fun createInfoCard(label: String, content: String): LinearLayout {
        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            setBackgroundResource(R.drawable.lesson_plan_card_background)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dpToPx(6), 0, dpToPx(6))
            }
            elevation = 2f
        }

        val labelView = TextView(context).apply {
            text = label
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#667eea"))
            letterSpacing = 0.02f
        }

        val contentView = TextView(context).apply {
            text = content
            textSize = 14f
            setTextColor(Color.parseColor("#374151"))
            setPadding(0, dpToPx(8), 0, 0)
            setTextIsSelectable(true)
            setLineSpacing(1.3f, 1.0f)
        }

        cardLayout.addView(labelView)
        cardLayout.addView(contentView)
        return cardLayout
    }

    private fun createMCQCard(mcq: JSONObject, index: Int): LinearLayout {
        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            setBackgroundResource(R.drawable.modern_mcq_card_background)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dpToPx(6), 0, dpToPx(6))
            }
            elevation = 2f
        }

        val questionHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
        }

        val questionBadge = TextView(context).apply {
            text = "Q$index"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            setBackgroundColor(Color.parseColor("#667eea"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, dpToPx(12), 0)
            }
        }

        val questionView = TextView(context).apply {
            text = mcq.getString("question")
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#1F2937"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setLineSpacing(1.3f, 1.0f)
        }

        questionHeader.addView(questionBadge)
        questionHeader.addView(questionView)
        cardLayout.addView(questionHeader)

        val spacer = View(context).apply {
            layoutParams =
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(8))
        }
        cardLayout.addView(spacer)

        val options = mcq.getJSONArray("options")
        val answer = mcq.getString("answer")

        for (i in 0 until options.length()) {
            val option = options.getString(i)
            val isCorrect = option == answer

            val optionLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dpToPx(4), 0, dpToPx(4))
                }
                setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
                setBackgroundColor(
                    if (isCorrect) Color.parseColor("#DCFCE7") else Color.parseColor(
                        "#F9FAFB"
                    )
                )
            }

            val optionLabel = TextView(context).apply {
                val prefix = when (i) {
                    0 -> "A"
                    1 -> "B"
                    2 -> "C"
                    3 -> "D"
                    else -> "${('A' + i)}"
                }
                text = prefix
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2))
                setBackgroundColor(
                    if (isCorrect) Color.parseColor("#16A34A") else Color.parseColor(
                        "#6B7280"
                    )
                )
                layoutParams =
                    LinearLayout.LayoutParams(dpToPx(24), LinearLayout.LayoutParams.WRAP_CONTENT)
                        .apply {
                            setMargins(0, 0, dpToPx(12), 0)
                        }
                gravity = Gravity.CENTER
            }

            val optionText = TextView(context).apply {
                text = option
                textSize = 13f
                setTextColor(if (isCorrect) Color.parseColor("#166534") else Color.parseColor("#374151"))
                if (isCorrect) setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams =
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setLineSpacing(1.3f, 1.0f)
            }

            optionLayout.addView(optionLabel)
            optionLayout.addView(optionText)
            cardLayout.addView(optionLayout)
        }

        return cardLayout
    }

    private fun open3DModel(name: String) {
        Toast.makeText(context, "Opening 3D Model: $name", Toast.LENGTH_SHORT).show() // Placeholder
    }

    private fun addSpacer(height: Int = 20) {
        val spacer = View(context).apply {
            layoutParams =
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(height))
        }
        resultsContainer.addView(spacer)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private fun createImageCard(imageBase64: String, index: Int): LinearLayout {
        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setBackgroundResource(R.drawable.modern_video_card_background)
            layoutParams = LinearLayout.LayoutParams(dpToPx(120), dpToPx(120)).apply {
                setMargins(dpToPx(8), 0, dpToPx(8), 0)
            }
            isClickable = true
            elevation = 4f
            setOnClickListener {
                try {
                    activity.addImageContainerFromBase64(imageBase64)
                    dismiss() // Close the drawer after adding the image
                } catch (e: Exception) {
                    Log.e("AiMasterDrawer", "Error adding image", e)
                    Toast.makeText(context, "Error adding image: ${e.message}", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }

        val imageView = ImageView(context).apply {
            try {
                val decodedString = if (imageBase64.contains(",")) {
                    imageBase64.split(",")[1] // Remove data URI prefix if present
                } else {
                    imageBase64
                }
                val decodedByte =
                    android.util.Base64.decode(decodedString, android.util.Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedByte, 0, decodedByte.size)
                setImageBitmap(bitmap)
                scaleType = ImageView.ScaleType.CENTER_CROP
            } catch (e: Exception) {
                Log.e("AiMasterDrawer", "Error decoding image", e)
                setBackgroundColor(Color.LTGRAY)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        cardLayout.addView(imageView)
        return cardLayout
    }

    private fun displayImages(images: JSONArray) {
        val sectionTitle = createSectionTitle("Images")
        resultsContainer.addView(sectionTitle)

        val horizontalContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val scrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(horizontalContainer)
        }
        resultsContainer.addView(scrollView)

        for (i in 0 until images.length()) {
            val imageBase64 = images.getString(i)
            val imageCard = createImageCard(imageBase64, i + 1) // No activity parameter
            horizontalContainer.addView(imageCard)
        }

        addSpacer()
    }

    // if we need we can clear the data using this function
    fun clearResults() {
        lastSearchData = null
        hasSearchResults = false
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
        // Data is preserved! All your search results, selections, etc. remain intact
    }
}