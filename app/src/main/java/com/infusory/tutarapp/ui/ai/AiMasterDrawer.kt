package com.infusory.tutarapp.ui.ai

import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infusory.tutarapp.R
import com.infusory.tutarapp.ui.data.ClassData
import com.infusory.tutarapp.ui.data.SubjectData
import com.infusory.tutarapp.ui.whiteboard.WhiteboardActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.concurrent.TimeUnit

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
    private lateinit var searchButton: Button
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

    // Network
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun show() {
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
                Log.d("AiMasterDrawer", "Successfully loaded ${classesData.size} classes from assets")
                return
            } catch (e: Exception) {
                Log.d("AiMasterDrawer", "Failed to load from assets, trying other locations...")
            }

            val internalFile = File(context.filesDir, "class_data.json")
            if (internalFile.exists()) {
                val fileReader = FileReader(internalFile)
                classesData = gson.fromJson(fileReader, listType)
                fileReader.close()
                Log.d("AiMasterDrawer", "Successfully loaded ${classesData.size} classes from internal storage")
                return
            }

            Log.e("AiMasterDrawer", "class_data.json not found in any location")
            classesData = emptyList()

        } catch (e: Exception) {
            Log.e("AiMasterDrawer", "Error loading class data: ${e.message}", e)
            classesData = emptyList()
        }
    }

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
    }

    private fun setupSpinners() {
        val classNames = mutableListOf("Select Class")
        classNames.addAll(classesData.map { it.Class })
        val classAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, classNames)
        classAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        classSpinner.adapter = classAdapter
        classSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedClass = if (position > 0) classesData[position - 1] else null
                setupSubjectSpinner()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { selectedClass = null; setupSubjectSpinner() }
        }

        val optionsList = mutableListOf("Select Option")
        optionsList.addAll(options)
        val optionAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, optionsList)
        optionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        optionSpinner.adapter = optionAdapter
        optionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedOption = if (position > 0) options[position - 1] else ""
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { selectedOption = "" }
        }
        setupSubjectSpinner()
    }

    private fun setupSubjectSpinner() {
        val subjectNames = mutableListOf("Select Subject")
        selectedClass?.subjects?.let { subjectNames.addAll(it.map { it.name }) }
        val subjectAdapter =
            ArrayAdapter(context, android.R.layout.simple_spinner_item, subjectNames)
        subjectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        subjectSpinner.adapter = subjectAdapter
        subjectSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedSubject = if (position > 0 && selectedClass?.subjects != null) selectedClass!!.subjects!![position - 1] else null
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { selectedSubject = null }
        }
    }

    private fun performSearch() {
        if (selectedClass == null || selectedSubject == null || selectedOption.isEmpty() || queryEditText.text.toString().trim().isEmpty()) {
            Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        searchButton.isEnabled = false
        searchButton.text = "Searching..."
        val requestData = JSONObject().apply {
            put("className", selectedClass!!.Class)
            put("subjectName", selectedSubject!!.name)
            put("contentType", selectedOption)
            put("query", queryEditText.text.toString().trim())
        }
        Log.d("AiMasterDrawer", "Search request: $requestData")
        makeApiRequest(requestData.toString())
    }

    private fun makeApiRequest(jsonData: String) {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonData.toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://dev-api.tutarverse.com/gemini/searcher")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                withContext(Dispatchers.Main) {
                    searchButton.isEnabled = true
                    searchButton.text = "Search"
                    if (response.isSuccessful) {
                        Log.e("AiMasterDrawer", "API Response: $responseBody")
                        handleApiResponse(responseBody)
                    } else {
                        Toast.makeText(
                            context,
                            "Search failed: ${response.code}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    searchButton.isEnabled = true
                    searchButton.text = "Search"
                    Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    searchButton.isEnabled = true
                    searchButton.text = "Search"
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
                displayResults(data)
                resultsScrollView.visibility = View.VISIBLE
                Toast.makeText(context, "Search completed successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Search failed: No data received", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("AiMasterDrawer", "Error handling API response", e)
            Toast.makeText(context, "Error processing response: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createAndShowDialog() {
        dialog = Dialog(context, R.style.CustomCenteredDialog)
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_ai_master, null)
        dialogView = view

        setupViews(view)
        setupSpinners()

        // Restore previous search results if they exist
        if (hasSearchResults && lastSearchData != null) {
            resultsScrollView.visibility = View.VISIBLE
            displayResults(lastSearchData!!)
        }

        dialog?.setContentView(view)

        dialog?.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            val displayMetrics = context.resources.displayMetrics
            val configuration = context.resources.configuration
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            val dialogWidth = (550 * displayMetrics.density).toInt()
            val dialogHeight = (displayMetrics.heightPixels * 0.90).toInt()

            val layoutParams = window.attributes
            layoutParams.width = dialogWidth
            layoutParams.height = dialogHeight
            layoutParams.gravity = if (isLandscape) Gravity.CENTER else Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
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
                addView(createLessonPlanCard("Explanation", lessonPlan.getString("explanation"), false))
            }
            if (lessonPlan.has("conclusion")) {
                addView(createLessonPlanCard("Conclusion", lessonPlan.getString("conclusion"), false))
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

private fun createLessonPlanCard(heading: String, content: String, isTopic: Boolean): LinearLayout {
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
            setTypeface(null, Typeface.BOLD)
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
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
    }
}
    // ===== ADD THESE HELPER METHODS TO YOUR AiMasterDrawer CLASS =====

// Add these methods right after the displayImages() method and before clearResults()

    private fun createSectionHeader(title: String): TextView {
        return TextView(context).apply {
            text = title
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
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

    private fun createMarginLayoutParams(left: Int, top: Int, right: Int, bottom: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(left, top, right, bottom)
        }
    }

    private fun createRoundedBackground(colorHex: String, cornerRadius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(colorHex))
            this.cornerRadius = cornerRadius
        }
    }

    private fun createGradientBackground(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.parseColor("#2C5F5D"),
                Color.parseColor("#1E4645")
            )
        ).apply {
            cornerRadius = dpToPx(8).toFloat()
        }
    }

    private fun createDashedBorder(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(dpToPx(1), Color.parseColor("#666666"), dpToPx(8).toFloat(), dpToPx(4).toFloat())
            cornerRadius = dpToPx(8).toFloat()
        }
    }

    private fun createCircleBullet(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
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
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
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
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
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
            layoutParams = LinearLayout.LayoutParams(dpToPx(200), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
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
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(80))
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
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
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
            layoutParams = LinearLayout.LayoutParams(dpToPx(120), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dpToPx(8), 0, dpToPx(8), 0)
            }
            isClickable = true
            elevation = 4f
            setOnClickListener { open3DModel(name) }
        }

        val thumbnailView = ImageView(context).apply {
            setImageResource(R.drawable.ic_ar) // Ensure this drawable exists
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(80))
        }

        val titleView = TextView(context).apply {
            text = name
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dpToPx(4), 0, 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        cardLayout.addView(thumbnailView)
        cardLayout.addView(titleView)
        return cardLayout
    }

    private fun createSectionTitle(title: String): LinearLayout {
        val sectionLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
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
            setTypeface(null, Typeface.BOLD)
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
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dpToPx(6), 0, dpToPx(6))
            }
            elevation = 2f
        }

        val labelView = TextView(context).apply {
            text = label
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
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
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dpToPx(6), 0, dpToPx(6))
            }
            elevation = 2f
        }

        val questionHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            gravity = Gravity.CENTER_VERTICAL
        }

        val questionBadge = TextView(context).apply {
            text = "Q$index"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            setBackgroundColor(Color.parseColor("#667eea"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, dpToPx(12), 0)
            }
        }

        val questionView = TextView(context).apply {
            text = mcq.getString("question")
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1F2937"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setLineSpacing(1.3f, 1.0f)
        }

        questionHeader.addView(questionBadge)
        questionHeader.addView(questionView)
        cardLayout.addView(questionHeader)

        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(8))
        }
        cardLayout.addView(spacer)

        val options = mcq.getJSONArray("options")
        val answer = mcq.getString("answer")

        for (i in 0 until options.length()) {
            val option = options.getString(i)
            val isCorrect = option == answer

            val optionLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, dpToPx(4), 0, dpToPx(4))
                }
                setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
                setBackgroundColor(if (isCorrect) Color.parseColor("#DCFCE7") else Color.parseColor("#F9FAFB"))
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
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2))
                setBackgroundColor(if (isCorrect) Color.parseColor("#16A34A") else Color.parseColor("#6B7280"))
                layoutParams = LinearLayout.LayoutParams(dpToPx(24), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, dpToPx(12), 0)
                }
                gravity = Gravity.CENTER
            }

            val optionText = TextView(context).apply {
                text = option
                textSize = 13f
                setTextColor(if (isCorrect) Color.parseColor("#166534") else Color.parseColor("#374151"))
                if (isCorrect) setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
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
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(height))
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
                    Toast.makeText(context, "Error adding image: ${e.message}", Toast.LENGTH_SHORT).show()
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
                val decodedByte = Base64.decode(decodedString, Base64.DEFAULT)
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
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
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



//
//package com.infusory.tutarapp.ui.ai
//
//import android.app.Dialog
//import android.content.Context
//import android.content.res.Configuration
//import android.graphics.BitmapFactory
//import android.graphics.Color
//import android.graphics.Typeface
//import android.graphics.drawable.ColorDrawable
//import android.graphics.drawable.GradientDrawable
//import android.text.TextUtils
//import android.util.Base64
//import android.util.Log
//import android.view.Gravity
//import android.view.LayoutInflater
//import android.view.View
//import android.view.Window
//import android.view.WindowManager
//import android.widget.AdapterView
//import android.widget.ArrayAdapter
//import android.widget.Button
//import android.widget.EditText
//import android.widget.FrameLayout
//import android.widget.HorizontalScrollView
//import android.widget.ImageView
//import android.widget.LinearLayout
//import android.widget.ScrollView
//import android.widget.Spinner
//import android.widget.TextView
//import android.widget.Toast
//import com.bumptech.glide.Glide
//import com.google.gson.Gson
//import com.google.gson.reflect.TypeToken
//import com.infusory.tutarapp.R
//import com.infusory.tutarapp.ui.data.ClassData
//import com.infusory.tutarapp.ui.data.SubjectData
//import com.infusory.tutarapp.ui.whiteboard.WhiteboardActivity
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.SupervisorJob
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import okhttp3.MediaType.Companion.toMediaType
//import okhttp3.OkHttpClient
//import okhttp3.Request
//import okhttp3.RequestBody.Companion.toRequestBody
//import okhttp3.Response
//import org.json.JSONArray
//import org.json.JSONObject
//import java.io.BufferedReader
//import java.io.File
//import java.io.FileReader
//import java.io.IOException
//import java.util.concurrent.TimeUnit
//
//class AiMasterDrawer(
//    private val activity: WhiteboardActivity,
//    private val onResultReceived: (String) -> Unit
//) {
//
//    private val context: Context get() = activity
//    private var dialog: Dialog? = null
//    private lateinit var dialogView: View
//
//    // UI Components
//    private lateinit var classSpinner: Spinner
//    private lateinit var subjectSpinner: Spinner
//    private lateinit var optionSpinner: Spinner
//    private lateinit var queryEditText: EditText
//    private lateinit var searchButton: Button
//    private lateinit var resultsScrollView: ScrollView
//    private lateinit var resultsContainer: LinearLayout
//
//    // Data
//    private var classesData: List<ClassData> = emptyList()
//    private var selectedClass: ClassData? = null
//    private var selectedSubject: SubjectData? = null
//    private var selectedOption: String = ""
//
//    private val options = arrayOf("Lesson Plan", "Description")
//
//    // properties to store the last search results
//    private var lastSearchData: JSONObject? = null
//    private var hasSearchResults: Boolean = false
//
//    // Network
//    private val client = OkHttpClient.Builder()
//        .connectTimeout(120, TimeUnit.SECONDS)
//        .readTimeout(120, TimeUnit.SECONDS)
//        .writeTimeout(120, TimeUnit.SECONDS)
//        .callTimeout(120, TimeUnit.SECONDS)
//        .build()
//    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
//
//    // Streaming state
//    private var requestStartTime: Long = 0
//    private var isStreaming = false
//
//    fun show() {
//        loadClassData()
//        if (classesData.isNotEmpty()) {
//            createAndShowDialog()
//        } else {
//            Toast.makeText(context, "Unable to load class data", Toast.LENGTH_LONG).show()
//        }
//    }
//
//    private fun loadClassData() {
//        try {
//            val gson = Gson()
//            val listType = object : TypeToken<List<ClassData>>() {}.type
//
//            try {
//                val inputStream = context.assets.open("class_list_demo.json")
//                val jsonString = inputStream.bufferedReader().use { it.readText() }
//                inputStream.close()
//
//                classesData = gson.fromJson(jsonString, listType)
//                Log.d("AiMasterDrawer", "Successfully loaded ${classesData.size} classes from assets")
//                return
//            } catch (e: Exception) {
//                Log.d("AiMasterDrawer", "Failed to load from assets, trying other locations...")
//            }
//
//            val internalFile = File(context.filesDir, "class_data.json")
//            if (internalFile.exists()) {
//                val fileReader = FileReader(internalFile)
//                classesData = gson.fromJson(fileReader, listType)
//                fileReader.close()
//                Log.d("AiMasterDrawer", "Successfully loaded ${classesData.size} classes from internal storage")
//                return
//            }
//
//            Log.e("AiMasterDrawer", "class_data.json not found in any location")
//            classesData = emptyList()
//
//        } catch (e: Exception) {
//            Log.e("AiMasterDrawer", "Error loading class data: ${e.message}", e)
//            classesData = emptyList()
//        }
//    }
//
//    private fun setupViews(view: View) {
//        view.findViewById<View>(R.id.btn_close).setOnClickListener { dialog?.dismiss() }
//        classSpinner = view.findViewById(R.id.spinner_class)
//        subjectSpinner = view.findViewById(R.id.spinner_subject)
//        optionSpinner = view.findViewById(R.id.spinner_option)
//        queryEditText = view.findViewById(R.id.et_query)
//        searchButton = view.findViewById(R.id.btn_search)
//        searchButton.setOnClickListener { performSearch() }
//        resultsScrollView = view.findViewById(R.id.scroll_results)
//        resultsContainer = view.findViewById(R.id.ll_results_container)
//    }
//
//    private fun setupSpinners() {
//        val classNames = mutableListOf("Select Class")
//        classNames.addAll(classesData.map { it.Class })
//        val classAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, classNames)
//        classAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
//        classSpinner.adapter = classAdapter
//        classSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
//            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
//                selectedClass = if (position > 0) classesData[position - 1] else null
//                setupSubjectSpinner()
//            }
//            override fun onNothingSelected(parent: AdapterView<*>?) { selectedClass = null; setupSubjectSpinner() }
//        }
//
//        val optionsList = mutableListOf("Select Option")
//        optionsList.addAll(options)
//        val optionAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, optionsList)
//        optionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
//        optionSpinner.adapter = optionAdapter
//        optionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
//            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
//                selectedOption = if (position > 0) options[position - 1] else ""
//            }
//            override fun onNothingSelected(parent: AdapterView<*>?) { selectedOption = "" }
//        }
//        setupSubjectSpinner()
//    }
//
//    private fun setupSubjectSpinner() {
//        val subjectNames = mutableListOf("Select Subject")
//        selectedClass?.subjects?.let { subjectNames.addAll(it.map { it.name }) }
//        val subjectAdapter =
//            ArrayAdapter(context, android.R.layout.simple_spinner_item, subjectNames)
//        subjectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
//        subjectSpinner.adapter = subjectAdapter
//        subjectSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
//            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
//                selectedSubject = if (position > 0 && selectedClass?.subjects != null) selectedClass!!.subjects!![position - 1] else null
//            }
//            override fun onNothingSelected(parent: AdapterView<*>?) { selectedSubject = null }
//        }
//    }
//
//    private fun performSearch() {
//        if (selectedClass == null || selectedSubject == null || selectedOption.isEmpty() || queryEditText.text.toString().trim().isEmpty()) {
//            Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        searchButton.isEnabled = false
//        searchButton.text = "Streaming..."
//
//        // Clear previous results
//        resultsContainer.removeAllViews()
//        resultsScrollView.visibility = View.VISIBLE
//
//        val requestData = JSONObject().apply {
//            put("className", selectedClass!!.Class)
//            put("subjectName", selectedSubject!!.name)
//            put("contentType", selectedOption)
//            put("query", queryEditText.text.toString().trim())
//        }
//
//        Log.d("AiMasterDrawer", "Search request: $requestData")
//        makeStreamingRequest(requestData.toString())
//    }
//
//    private fun makeStreamingRequest(jsonData: String) {
//        val mediaType = "application/json; charset=utf-8".toMediaType()
//        val requestBody = jsonData.toRequestBody(mediaType)
//        val request = Request.Builder()
//            .url("https://ndkg0hst-5001.inc1.devtunnels.ms/gemini/searcher")
//            .post(requestBody)
//            .addHeader("Content-Type", "application/json")
//            .build()
//
//        requestStartTime = System.currentTimeMillis()
//        isStreaming = true
//
//        coroutineScope.launch(Dispatchers.IO) {
//            try {
//                Log.d("AiMasterDrawer", "🚀 Starting streaming request...")
//
//                val response = client.newCall(request).execute()
//
//                if (!response.isSuccessful) {
//                    withContext(Dispatchers.Main) {
//                        handleStreamingError("Request failed: ${response.code}")
//                    }
//                    return@launch
//                }
//
//                // Process streaming response
//                processStreamingResponse(response)
//
//            } catch (e: IOException) {
//                withContext(Dispatchers.Main) {
//                    handleStreamingError("Network error: ${e.message}")
//                }
//            } catch (e: Exception) {
//                withContext(Dispatchers.Main) {
//                    handleStreamingError("Error: ${e.message}")
//                }
//            } finally {
//                isStreaming = false
//            }
//        }
//    }
//
//    private suspend fun processStreamingResponse(response: Response) {
//        val reader = response.body?.byteStream()?.bufferedReader() ?: return
//        var buffer = StringBuilder()
//
//        try {
//            reader.use { bufferedReader ->
//                var line: String?
//
//                while (bufferedReader.readLine().also { line = it } != null && isStreaming) {
//                    if (line.isNullOrEmpty()) {
//                        // Empty line signals end of SSE message
//                        if (buffer.isNotEmpty()) {
//                            processSSEMessage(buffer.toString())
//                            buffer = StringBuilder()
//                        }
//                    } else {
//                        buffer.append(line).append("\n")
//                    }
//                }
//            }
//        } catch (e: Exception) {
//            Log.e("AiMasterDrawer", "Error reading stream", e)
//            withContext(Dispatchers.Main) {
//                handleStreamingError("Stream reading error: ${e.message}")
//            }
//        } finally {
//            withContext(Dispatchers.Main) {
//                searchButton.isEnabled = true
//                searchButton.text = "Search"
//
//                val totalTime = System.currentTimeMillis() - requestStartTime
//                Log.d("AiMasterDrawer", "🏁 Streaming completed in ${totalTime}ms")
//            }
//        }
//    }
//
//    private suspend fun processSSEMessage(message: String) {
//        val lines = message.split("\n")
//
//        for (line in lines) {
//            if (line.startsWith("data: ")) {
//                val jsonData = line.substring(6).trim()
//
//                if (jsonData.isEmpty() || jsonData == "[DONE]") continue
//
//                try {
//                    val responseTime = System.currentTimeMillis() - requestStartTime
//                    val parsed = JSONObject(jsonData)
//
//                    Log.d("AiMasterDrawer", "📨 Stream Response: type=${parsed.optString("type")} at ${responseTime}ms")
//
//                    withContext(Dispatchers.Main) {
//                        handleStreamingData(parsed, responseTime)
//                    }
//                } catch (e: Exception) {
//                    Log.e("AiMasterDrawer", "Error parsing SSE data: $jsonData", e)
//                }
//            }
//        }
//    }
//
//    private fun handleStreamingData(parsed: JSONObject, responseTime: Long) {
//        val type = parsed.optString("type")
//        val data = parsed.optJSONObject("data")
//        val message = parsed.optString("message")
//
//        when (type) {
//            "status" -> {
//                data?.let {
//                    val statusMessage = it.optString("message", "Processing...")
//                    val step = it.optInt("step", 0)
//                    val total = it.optInt("total", 0)
//                    displayStatusUpdate(statusMessage, step, total, responseTime)
//                }
//            }
//
//            "gemini" -> {
//                data?.let {
//                    Log.d("AiMasterDrawer", "Gemini response received")
//                    // The gemini response contains the names array
//                    if (it.has("names")) {
//                        val names = it.getJSONArray("names")
//                        display3DModels(names)
//                    }
//                }
//            }
//
//            "images" -> {
//                data?.let {
//                    if (it.has("images")) {
//                        val images = it.getJSONArray("images")
//                        displayImages(images)
//                    }
//                }
//            }
//
//            "videos" -> {
//                data?.let {
//                    if (it.has("videos")) {
//                        val videos = it.getJSONArray("videos")
//                        displayVideos(videos)
//                    }
//                }
//            }
//
//            "mcqs" -> {
//                data?.let {
//                    if (it.has("mcqs")) {
//                        // Handle MCQs if needed
//                        Log.d("AiMasterDrawer", "MCQs received")
//                    }
//                }
//            }
//
//            "complete" -> {
//                data?.let {
//                    val completeMessage = it.optString("message", "Processing complete")
//                    displayCompletionMessage(completeMessage, responseTime)
//                }
//            }
//
//            "timing" -> {
//                data?.let {
//                    val step = it.optString("step", "")
//                    val duration = it.optString("stepDuration", "")
//                    Log.d("AiMasterDrawer", "⏱️ Timing: $step - $duration")
//                }
//            }
//
//            "error" -> {
//                displayErrorMessage("Error: $message", responseTime)
//            }
//        }
//    }
//
//    private fun displayStatusUpdate(message: String, step: Int, total: Int, responseTime: Long) {
//        val statusView = TextView(context).apply {
//            text = "⏳ $message (Step $step/$total) - ${responseTime}ms"
//            textSize = 13f
//            setTextColor(Color.parseColor("#666666"))
//            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
//            setBackgroundColor(Color.parseColor("#FFF3CD"))
//        }
//        resultsContainer.addView(statusView)
//        resultsScrollView.post { resultsScrollView.fullScroll(View.FOCUS_DOWN) }
//    }
//
//    private fun displayCompletionMessage(message: String, responseTime: Long) {
//        val completeView = TextView(context).apply {
//            text = "✅ $message - Total: ${responseTime}ms"
//            textSize = 14f
//            setTextColor(Color.parseColor("#155724"))
//            setTypeface(null, Typeface.BOLD)
//            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
//            setBackgroundColor(Color.parseColor("#D4EDDA"))
//        }
//        resultsContainer.addView(completeView)
//        resultsScrollView.post { resultsScrollView.fullScroll(View.FOCUS_DOWN) }
//    }
//
//    private fun displayErrorMessage(message: String, responseTime: Long) {
//        val errorView = TextView(context).apply {
//            text = "❌ $message - ${responseTime}ms"
//            textSize = 14f
//            setTextColor(Color.parseColor("#721C24"))
//            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
//            setBackgroundColor(Color.parseColor("#F8D7DA"))
//        }
//        resultsContainer.addView(errorView)
//    }
//
//    private fun handleStreamingError(errorMessage: String) {
//        searchButton.isEnabled = true
//        searchButton.text = "Search"
//        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
//        displayErrorMessage(errorMessage, System.currentTimeMillis() - requestStartTime)
//    }
//
//    private fun handleApiResponse(responseBody: String) {
//        try {
//            val jsonResponse = JSONObject(responseBody)
//            if (jsonResponse.optBoolean("status", false)) {
//                val data = jsonResponse.getJSONObject("data")
//                lastSearchData = data
//                hasSearchResults = true
//                displayResults(data)
//                resultsScrollView.visibility = View.VISIBLE
//                Toast.makeText(context, "Search completed successfully!", Toast.LENGTH_SHORT).show()
//            } else {
//                Toast.makeText(context, "Search failed: No data received", Toast.LENGTH_LONG).show()
//            }
//        } catch (e: Exception) {
//            Log.e("AiMasterDrawer", "Error handling API response", e)
//            Toast.makeText(context, "Error processing response: ${e.message}", Toast.LENGTH_LONG).show()
//        }
//    }
//
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
//            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
//
//            val dialogWidth = (550 * displayMetrics.density).toInt()
//            val dialogHeight = (displayMetrics.heightPixels * 0.90).toInt()
//
//            val layoutParams = window.attributes
//            layoutParams.width = dialogWidth
//            layoutParams.height = dialogHeight
//            layoutParams.gravity = if (isLandscape) Gravity.CENTER else Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
//            layoutParams.dimAmount = 0.6f
//            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
//            window.attributes = layoutParams
//        }
//
//        dialog?.setCancelable(true)
//        dialog?.setCanceledOnTouchOutside(true)
//        dialog?.show()
//    }
//
//    private fun displayResults(data: JSONObject) {
//        resultsContainer.removeAllViews()
//        resultsScrollView.visibility = View.VISIBLE
//
//        Log.d("AiMasterDrawer", "Data keys: ${data.keys().asSequence().toList()}")
//
//        val loadingView = TextView(context).apply {
//            text = "Loading content..."
//            textSize = 14f
//            setTextColor(Color.GRAY)
//            gravity = Gravity.CENTER
//            setPadding(dpToPx(20), dpToPx(40), dpToPx(20), dpToPx(40))
//        }
//        resultsContainer.addView(loadingView)
//
//        coroutineScope.launch {
//            delay(50)
//
//            withContext(Dispatchers.Main) {
//                resultsContainer.removeView(loadingView)
//            }
//
//            if (data.has("content")) {
//                val content = data.getJSONObject("content")
//
//                if (content.has("lessonPlan")) {
//                    withContext(Dispatchers.Main) {
//                        displayLessonPlan(content.getJSONObject("lessonPlan"))
//                    }
//                    delay(100)
//                }
//
//                if (content.has("description")) {
//                    val descriptionArray = content.getJSONArray("description")
//                    if (descriptionArray.length() > 0) {
//                        withContext(Dispatchers.Main) {
//                            displayDescription(descriptionArray)
//                        }
//                        delay(100)
//                    }
//                }
//            }
//
//            if (data.has("names")) {
//                val names = data.getJSONArray("names")
//                if (names.length() > 0) {
//                    withContext(Dispatchers.Main) {
//                        display3DModels(names)
//                    }
//                    delay(100)
//                }
//            }
//
//            if (data.has("images")) {
//                val images = data.getJSONArray("images")
//                if (images.length() > 0) {
//                    withContext(Dispatchers.Main) {
//                        displayImages(images)
//                    }
//                    delay(100)
//                }
//            }
//
//            if (data.has("videos")) {
//                val videos = data.getJSONArray("videos")
//                if (videos.length() > 0) {
//                    withContext(Dispatchers.Main) {
//                        displayVideos(videos)
//                    }
//                }
//            }
//        }
//    }
//
//    private fun displayLessonPlan(lessonPlan: JSONObject) {
//        resultsContainer.addView(createLessonPlanContainer(lessonPlan))
//    }
//
//    private fun createLessonPlanContainer(lessonPlan: JSONObject): LinearLayout {
//        return LinearLayout(context).apply {
//            orientation = LinearLayout.VERTICAL
//            setPadding(dpToPx(4), dpToPx(12), dpToPx(4), dpToPx(12))
//
//            layoutParams = createMarginLayoutParams(0, dpToPx(20), 0, dpToPx(8))
//            elevation = 3f
//            isClickable = true
//            setOnClickListener {
//                Log.d("AiMasterDrawer", "Lesson Plan clicked")
//                Toast.makeText(context, "Lesson Plan", Toast.LENGTH_SHORT).show()
//            }
//
//            addView(createSectionHeader("Lesson Plan"))
//
//            if (lessonPlan.has("topic")) {
//                addView(createLessonPlanCard("TOPIC", lessonPlan.getString("topic"), true))
//            }
//            if (lessonPlan.has("explanation")) {
//                addView(createLessonPlanCard("Explanation", lessonPlan.getString("explanation"), false))
//            }
//            if (lessonPlan.has("conclusion")) {
//                addView(createLessonPlanCard("Conclusion", lessonPlan.getString("conclusion"), false))
//            }
//            if (lessonPlan.has("notes")) {
//                addView(createLessonPlanCard("Notes", lessonPlan.getString("notes"), false))
//            }
//        }
//    }
//
//    private fun addTextContainerToWhiteboard(text: String, title: String = "") {
//        try {
//            activity.addTextContainerWithContent(text, title)
//            dismiss()
//            Toast.makeText(context, "Text added to canvas", Toast.LENGTH_SHORT).show()
//        } catch (e: Exception) {
//            Log.e("AiMasterDrawer", "Error adding text container", e)
//            Toast.makeText(context, "Error adding text: ${e.message}", Toast.LENGTH_SHORT).show()
//        }
//    }
//
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
//                addTextContainerToWhiteboard(content, heading)
//            }
//
//            addView(TextView(context).apply {
//                text = heading
//                textSize = if (isTopic) 14f else 16f
//                setTypeface(null, Typeface.BOLD)
//                setTextColor(Color.WHITE)
//                letterSpacing = if (isTopic) 0.1f else 0.05f
//                gravity = if (isTopic) Gravity.CENTER else Gravity.START
//            })
//
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
//
//    private fun displayDescription(descriptionArray: JSONArray) {
//        resultsContainer.addView(createDescriptionContainer(descriptionArray))
//        resultsContainer.addView(createSpacer(5))
//    }
//
//    private fun createDescriptionContainer(descriptionArray: JSONArray): LinearLayout {
//        return LinearLayout(context).apply {
//            orientation = LinearLayout.VERTICAL
//            setPadding(dpToPx(4), dpToPx(12), dpToPx(4), dpToPx(12))
//            layoutParams = createMarginLayoutParams(0, dpToPx(20), 0, dpToPx(8))
//            elevation = 3f
//            isClickable = true
//            setOnClickListener {
//                Log.d("AiMasterDrawer", "Description clicked")
//                Toast.makeText(context, "Description", Toast.LENGTH_SHORT).show()
//            }
//
//            addView(createSectionHeader("Description"))
//
//            val maxPoints = minOf(descriptionArray.length(), 15)
//            for (i in 0 until maxPoints) {
//                addView(createDescriptionPoint(descriptionArray.getString(i), i))
//            }
//
//            if (descriptionArray.length() > maxPoints) {
//                addView(TextView(context).apply {
//                    text = "... ${descriptionArray.length() - maxPoints} more points"
//                    textSize = 12f
//                    setTextColor(Color.LTGRAY)
//                    setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
//                    gravity = Gravity.CENTER
//                })
//            }
//        }
//    }
//
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
//                addTextContainerToWhiteboard(point, "Point ${index + 1}")
//            }
//
//            addView(View(context).apply {
//                background = createCircleBullet()
//                layoutParams = LinearLayout.LayoutParams(dpToPx(8), dpToPx(8)).apply {
//                    setMargins(dpToPx(4), dpToPx(8), dpToPx(12), 0)
//                }
//            })
//
//            addView(TextView(context).apply {
//                text = point
//                textSize = 14f
//                setTextColor(Color.WHITE)
//                setLineSpacing(1.4f, 1.0f)
//                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
//            })
//        }
//    }
//
//    private fun createSectionHeader(title: String): TextView {
//        return TextView(context).apply {
//            text = title
//            textSize = 18f
//            setTypeface(null, Typeface.BOLD)
//            setTextColor(Color.WHITE)
//            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(12))
//        }
//    }
//
//    private fun createSpacer(heightDp: Int): View {
//        return View(context).apply {
//            layoutParams = LinearLayout.LayoutParams(
//                LinearLayout.LayoutParams.MATCH_PARENT,
//                dpToPx(heightDp)
//            )
//        }
//    }
//
//    private fun createMarginLayoutParams(left: Int, top: Int, right: Int, bottom: Int): LinearLayout.LayoutParams {
//        return LinearLayout.LayoutParams(
//            LinearLayout.LayoutParams.MATCH_PARENT,
//            LinearLayout.LayoutParams.WRAP_CONTENT
//        ).apply {
//            setMargins(left, top, right, bottom)
//        }
//    }
//
//    private fun createRoundedBackground(colorHex: String, cornerRadius: Float): GradientDrawable {
//        return GradientDrawable().apply {
//            setColor(Color.parseColor(colorHex))
//            this.cornerRadius = cornerRadius
//        }
//    }
//
//    private fun createGradientBackground(): GradientDrawable {
//        return GradientDrawable(
//            GradientDrawable.Orientation.TL_BR,
//            intArrayOf(
//                Color.parseColor("#2C5F5D"),
//                Color.parseColor("#1E4645")
//            )
//        ).apply {
//            cornerRadius = dpToPx(8).toFloat()
//        }
//    }
//
//    private fun createDashedBorder(): GradientDrawable {
//        return GradientDrawable().apply {
//            setColor(Color.TRANSPARENT)
//            setStroke(dpToPx(1), Color.parseColor("#666666"), dpToPx(8).toFloat(), dpToPx(4).toFloat())
//            cornerRadius = dpToPx(8).toFloat()
//        }
//    }
//
//    private fun createCircleBullet(): GradientDrawable {
//        return GradientDrawable().apply {
//            shape = GradientDrawable.OVAL
//            setColor(Color.parseColor("#2196F3"))
//        }
//    }
//
//    private fun displayVideos(videos: JSONArray) {
//        val sectionTitle = createSectionTitle("Videos")
//        resultsContainer.addView(sectionTitle)
//
//        val horizontalContainer = LinearLayout(context).apply {
//            orientation = LinearLayout.HORIZONTAL
//            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
//        }
//
//        val scrollView = HorizontalScrollView(context).apply {
//            isHorizontalScrollBarEnabled = false
//            addView(horizontalContainer)
//        }
//        resultsContainer.addView(scrollView)
//
//        for (i in 0 until videos.length()) {
//            val video = videos.getJSONObject(i)
//            val videoId = video.getString("videoId")
//            val thumbnail = video.optString("thumbnail", "")
//            val title = video.optString("title", "Video ${i + 1}")
//
//            val videoCard = createVideoCard(videoId, thumbnail, title)
//            horizontalContainer.addView(videoCard)
//        }
//
//        addSpacer(5)
//    }
//
//    private fun display3DModels(names: JSONArray) {
//        val sectionTitle = createSectionTitle("3D Models")
//        resultsContainer.addView(sectionTitle)
//
//        val horizontalContainer = LinearLayout(context).apply {
//            orientation = LinearLayout.HORIZONTAL
//            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
//        }
//
//        val scrollView = HorizontalScrollView(context).apply {
//            isHorizontalScrollBarEnabled = false
//            addView(horizontalContainer)
//        }
//        resultsContainer.addView(scrollView)
//
//        for (i in 0 until names.length()) {
//            val name = names.getString(i)
//            val modelCard = create3DModelCard(name)
//            horizontalContainer.addView(modelCard)
//        }
//
//        addSpacer(5)
//    }
//
//    private fun addYouTubeContainerToWhiteboard(videoId: String) {
//        try {
//            val youtubeUrl = "https://www.youtube.com/watch?v=$videoId"
//            activity.addYouTubeContainerWithUrl(youtubeUrl)
//            dismiss()
//            Toast.makeText(context, "YouTube video added to canvas", Toast.LENGTH_SHORT).show()
//        } catch (e: Exception) {
//            Log.e("AiMasterDrawer", "Error adding YouTube container", e)
//            Toast.makeText(context, "Error adding video: ${e.message}", Toast.LENGTH_SHORT).show()
//        }
//    }
//
//    private fun createVideoCard(videoId: String, thumbnail: String, title: String): LinearLayout {
//        val cardLayout = LinearLayout(context).apply {
//            orientation = LinearLayout.VERTICAL
//            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
//            setBackgroundResource(R.drawable.modern_video_card_background)
//            layoutParams = LinearLayout.LayoutParams(dpToPx(200), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
//                setMargins(dpToPx(8), 0, dpToPx(8), 0)
//            }
//            isClickable = true
//            elevation = 4f
//            setOnClickListener {
//                addYouTubeContainerToWhiteboard(videoId)
//            }
//        }
//
//        val thumbnailContainer = FrameLayout(context).apply {
//            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(80))
//        }
//
//        val thumbnailView = ImageView(context).apply {
//            scaleType = ImageView.ScaleType.CENTER_CROP
//            layoutParams = FrameLayout.LayoutParams(
//                FrameLayout.LayoutParams.MATCH_PARENT,
//                FrameLayout.LayoutParams.MATCH_PARENT
//            )
//
//            if (thumbnail.isNotEmpty()) {
//                Glide.with(context)
//                    .load(thumbnail)
//                    .placeholder(R.drawable.section_icon_video)
//                    .error(R.drawable.section_icon_video)
//                    .into(this)
//            } else {
//                setImageResource(R.drawable.section_icon_video)
//            }
//        }
//
//        val playIcon = ImageView(context).apply {
//            setImageResource(android.R.drawable.ic_media_play)
//            scaleType = ImageView.ScaleType.CENTER
//            layoutParams = FrameLayout.LayoutParams(
//                dpToPx(48),
//                dpToPx(48)
//            ).apply {
//                gravity = Gravity.CENTER
//            }
//
//            val drawable = GradientDrawable().apply {
//                shape = GradientDrawable.OVAL
//                setColor(Color.parseColor("#80000000"))
//            }
//            background = drawable
//
//            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
//        }
//
//        thumbnailContainer.addView(thumbnailView)
//        thumbnailContainer.addView(playIcon)
//
//        cardLayout.addView(thumbnailContainer)
//        return cardLayout
//    }
//
//    private fun create3DModelCard(name: String): LinearLayout {
//        val cardLayout = LinearLayout(context).apply {
//            orientation = LinearLayout.VERTICAL
//            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
//            setBackgroundResource(R.drawable.modern_video_card_background)
//            layoutParams = LinearLayout.LayoutParams(dpToPx(120), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
//                setMargins(dpToPx(8), 0, dpToPx(8), 0)
//            }
//            isClickable = true
//            elevation = 4f
//            setOnClickListener { open3DModel(name) }
//        }
//
//        val thumbnailView = ImageView(context).apply {
//            setImageResource(R.drawable.ic_ar)
//            scaleType = ImageView.ScaleType.CENTER_CROP
//            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(80))
//        }
//
//        val titleView = TextView(context).apply {
//            text = name
//            textSize = 12f
//            setTypeface(null, Typeface.BOLD)
//            setTextColor(Color.WHITE)
//            maxLines = 2
//            ellipsize = TextUtils.TruncateAt.END
//            gravity = Gravity.CENTER_HORIZONTAL
//            setPadding(0, dpToPx(4), 0, 0)
//            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
//        }
//
//        cardLayout.addView(thumbnailView)
//        cardLayout.addView(titleView)
//        return cardLayout
//    }
//
//    private fun createSectionTitle(title: String): LinearLayout {
//        val sectionLayout = LinearLayout(context).apply {
//            orientation = LinearLayout.HORIZONTAL
//            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
//                setMargins(0, dpToPx(20), 0, dpToPx(12))
//            }
//            gravity = Gravity.CENTER_VERTICAL
//            setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8))
//        }
//
//        val iconRes = when (title.lowercase()) {
//            "lesson plan" -> R.drawable.section_icon_lesson
//            "videos" -> R.drawable.section_icon_video
//            "multiple choice questions" -> R.drawable.section_icon_quiz
//            "3d models" -> R.drawable.section_icon_lesson
//            "images" -> R.drawable.section_icon_lesson
//            else -> R.drawable.section_icon_lesson
//        }
//
//        val iconView = ImageView(context).apply {
//            setImageResource(iconRes)
//            layoutParams = LinearLayout.LayoutParams(dpToPx(28), dpToPx(28)).apply {
//                setMargins(0, 0, dpToPx(12), 0)
//            }
//            scaleType = ImageView.ScaleType.CENTER_INSIDE
//        }
//
//        val titleView = TextView(context).apply {
//            text = title
//            textSize = 20f
//            setTypeface(null, Typeface.BOLD)
//            setTextColor(Color.parseColor("#1F2937"))
//            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
//        }
//
//        sectionLayout.addView(iconView)
//        sectionLayout.addView(titleView)
//        return sectionLayout
//    }
//
//    private fun open3DModel(name: String) {
//        Toast.makeText(context, "Opening 3D Model: $name", Toast.LENGTH_SHORT).show()
//    }
//
//    private fun addSpacer(height: Int = 20) {
//        val spacer = View(context).apply {
//            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(height))
//        }
//        resultsContainer.addView(spacer)
//    }
//
//    private fun dpToPx(dp: Int): Int {
//        return (dp * context.resources.displayMetrics.density).toInt()
//    }
//
//    private fun createImageCard(imageBase64: String, index: Int): LinearLayout {
//        val cardLayout = LinearLayout(context).apply {
//            orientation = LinearLayout.VERTICAL
//            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
//            setBackgroundResource(R.drawable.modern_video_card_background)
//            layoutParams = LinearLayout.LayoutParams(dpToPx(120), dpToPx(120)).apply {
//                setMargins(dpToPx(8), 0, dpToPx(8), 0)
//            }
//            isClickable = true
//            elevation = 4f
//            setOnClickListener {
//                try {
//                    activity.addImageContainerFromBase64(imageBase64)
//                    dismiss()
//                } catch (e: Exception) {
//                    Log.e("AiMasterDrawer", "Error adding image", e)
//                    Toast.makeText(context, "Error adding image: ${e.message}", Toast.LENGTH_SHORT).show()
//                }
//            }
//        }
//
//        val imageView = ImageView(context).apply {
//            try {
//                val decodedString = if (imageBase64.contains(",")) {
//                    imageBase64.split(",")[1]
//                } else {
//                    imageBase64
//                }
//                val decodedByte = Base64.decode(decodedString, Base64.DEFAULT)
//                val bitmap = BitmapFactory.decodeByteArray(decodedByte, 0, decodedByte.size)
//                setImageBitmap(bitmap)
//                scaleType = ImageView.ScaleType.CENTER_CROP
//            } catch (e: Exception) {
//                Log.e("AiMasterDrawer", "Error decoding image", e)
//                setBackgroundColor(Color.LTGRAY)
//            }
//            layoutParams = LinearLayout.LayoutParams(
//                LinearLayout.LayoutParams.MATCH_PARENT,
//                LinearLayout.LayoutParams.MATCH_PARENT
//            )
//        }
//
//        cardLayout.addView(imageView)
//        return cardLayout
//    }
//
//    private fun displayImages(images: JSONArray) {
//        val sectionTitle = createSectionTitle("Images")
//        resultsContainer.addView(sectionTitle)
//
//        val horizontalContainer = LinearLayout(context).apply {
//            orientation = LinearLayout.HORIZONTAL
//            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
//        }
//
//        val scrollView = HorizontalScrollView(context).apply {
//            isHorizontalScrollBarEnabled = false
//            addView(horizontalContainer)
//        }
//        resultsContainer.addView(scrollView)
//
//        for (i in 0 until images.length()) {
//            val imageBase64 = images.getString(i)
//            val imageCard = createImageCard(imageBase64, i + 1)
//            horizontalContainer.addView(imageCard)
//        }
//
//        addSpacer()
//    }
//
//    fun clearResults() {
//        lastSearchData = null
//        hasSearchResults = false
//        isStreaming = false
//    }
//
//    fun dismiss() {
//        isStreaming = false
//        dialog?.dismiss()
//        dialog = null
//    }
//}