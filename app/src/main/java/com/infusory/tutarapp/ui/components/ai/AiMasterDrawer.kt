// AiMasterDrawer.kt - AI Search Interface Drawer
package com.infusory.tutarapp.ui.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import com.google.android.material.bottomsheet.BottomSheetDialog
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

class AiMasterDrawer(
    private val context: Context,
    private val onResultReceived: (String) -> Unit
) {

    private var bottomSheetDialog: BottomSheetDialog? = null
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

    private val options = arrayOf("lesson plan", "description")

    // Network
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
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
                // Try loading from assets first
                val inputStream = context.assets.open("class_list_demo.json")
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                inputStream.close()

                classesData = gson.fromJson(jsonString, listType)
                Log.d("AiMasterDrawer", "Successfully loaded ${classesData.size} classes from assets")
                return
            } catch (e: Exception) {
                Log.d("AiMasterDrawer", "Failed to load from assets, trying other locations...")
            }

            // Try other locations as fallback
            val internalFile = java.io.File(context.filesDir, "class_data.json")
            if (internalFile.exists()) {
                val fileReader = java.io.FileReader(internalFile)
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

    private fun createAndShowDialog() {
        bottomSheetDialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_ai_master, null)

        dialogView = view
        setupViews(view)
        setupSpinners()

        bottomSheetDialog?.setContentView(view)

        // Configure bottom sheet behavior
        bottomSheetDialog?.setOnShowListener { dialog ->
            val bottomSheet = (dialog as BottomSheetDialog).findViewById<android.widget.FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)

                val displayMetrics = context.resources.displayMetrics
                val height = (displayMetrics.heightPixels * 0.90).toInt() // 90% of screen height for more content

                val layoutParams = it.layoutParams
                layoutParams.height = height
                it.layoutParams = layoutParams

                behavior.peekHeight = height
                behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                behavior.isDraggable = true
                behavior.isHideable = true
            }
        }

        bottomSheetDialog?.show()
    }

    private fun setupViews(view: View) {
        // Title and close button
        view.findViewById<TextView>(R.id.tv_title).text = "AI Master Search"
        view.findViewById<View>(R.id.btn_close).setOnClickListener {
            bottomSheetDialog?.dismiss()
        }

        // Spinners
        classSpinner = view.findViewById(R.id.spinner_class)
        subjectSpinner = view.findViewById(R.id.spinner_subject)
        optionSpinner = view.findViewById(R.id.spinner_option)

        // Query input
        queryEditText = view.findViewById(R.id.et_query)

        // Search button
        searchButton = view.findViewById(R.id.btn_search)
        searchButton.setOnClickListener {
            performSearch()
        }

        // Results components
        resultsScrollView = view.findViewById(R.id.scroll_results)
        resultsContainer = view.findViewById(R.id.ll_results_container)
    }

    private fun setupSpinners() {
        // Setup Class Spinner
        val classNames = mutableListOf("Select Class")
        classNames.addAll(classesData.map { it.Class })

        val classAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, classNames)
        classAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        classSpinner.adapter = classAdapter

        classSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) {
                    selectedClass = classesData[position - 1]
                    setupSubjectSpinner()
                } else {
                    selectedClass = null
                    setupSubjectSpinner() // Clear subjects
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedClass = null
                setupSubjectSpinner()
            }
        }

        // Setup Option Spinner
        val optionsList = mutableListOf("Select Option")
        optionsList.addAll(options)

        val optionAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, optionsList)
        optionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        optionSpinner.adapter = optionAdapter

        optionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedOption = if (position > 0) options[position - 1] else ""
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedOption = ""
            }
        }

        // Initialize subject spinner
        setupSubjectSpinner()
    }

    private fun setupSubjectSpinner() {
        val subjectNames = mutableListOf("Select Subject")

        selectedClass?.subjects?.let { subjects ->
            subjectNames.addAll(subjects.map { it.name })
        }

        val subjectAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, subjectNames)
        subjectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        subjectSpinner.adapter = subjectAdapter

        subjectSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
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

    private fun performSearch() {
        // Validate inputs
        if (selectedClass == null) {
            Toast.makeText(context, "Please select a class", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedSubject == null) {
            Toast.makeText(context, "Please select a subject", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedOption.isEmpty()) {
            Toast.makeText(context, "Please select an option", Toast.LENGTH_SHORT).show()
            return
        }

        val query = queryEditText.text.toString().trim()
        if (query.isEmpty()) {
            Toast.makeText(context, "Please enter a search query", Toast.LENGTH_SHORT).show()
            return
        }

        // Disable search button and show loading
        searchButton.isEnabled = false
        searchButton.text = "Searching..."

        // Prepare request data
        val requestData = JSONObject().apply {
            put("className", selectedClass!!.Class)
            put("subjectName", selectedSubject!!.name)
            put("contentType", selectedOption)
            put("query", query)
        }

        Log.d("AiMasterDrawer", "Search request: $requestData")

        // Make API call
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
                    // Re-enable search button
                    searchButton.isEnabled = true
                    searchButton.text = "Search"

                    if (response.isSuccessful) {
                        Log.d("AiMasterDrawer", "API Response: $responseBody")
                        handleApiResponse(responseBody)
                    } else {
                        Log.e("AiMasterDrawer", "API Error: ${response.code} - $responseBody")
                        Toast.makeText(context, "Search failed: ${response.code}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    // Re-enable search button
                    searchButton.isEnabled = true
                    searchButton.text = "Search"

                    Log.e("AiMasterDrawer", "Network error", e)
                    Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // Re-enable search button
                    searchButton.isEnabled = true
                    searchButton.text = "Search"

                    Log.e("AiMasterDrawer", "Unexpected error", e)
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
                displayResults(data)

                // Show results section
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

    private fun displayResults(data: JSONObject) {
        resultsContainer.removeAllViews()

        // Display lesson plan content
        if (data.has("content")) {
            val content = data.getJSONObject("content")
            if (content.has("lessonPlan")) {
                val lessonPlan = content.getJSONObject("lessonPlan")
                displayLessonPlan(lessonPlan)
            }
        }

        // Display videos
        if (data.has("videos")) {
            val videos = data.getJSONArray("videos")
            if (videos.length() > 0) {
                displayVideos(videos)
            }
        }

        // Display MCQs
        if (data.has("mcqs")) {
            val mcqs = data.getJSONArray("mcqs")
            if (mcqs.length() > 0) {
                displayMCQs(mcqs)
            }
        }

        // Display related names if available
        if (data.has("names")) {
            val names = data.getJSONArray("names")
            if (names.length() > 0) {
                displayRelatedNames(names)
            }
        }
    }

    private fun displayLessonPlan(lessonPlan: JSONObject) {
        // Create lesson plan section
        val sectionTitle = createSectionTitle("Lesson Plan")
        resultsContainer.addView(sectionTitle)

        // Topic
        if (lessonPlan.has("topic")) {
            val topicCard = createInfoCard("Topic", lessonPlan.getString("topic"))
            resultsContainer.addView(topicCard)
        }

        // Explanation
        if (lessonPlan.has("explanation")) {
            val explanationCard = createInfoCard("Explanation", lessonPlan.getString("explanation"))
            resultsContainer.addView(explanationCard)
        }

        // Conclusion
        if (lessonPlan.has("conclusion")) {
            val conclusionCard = createInfoCard("Conclusion", lessonPlan.getString("conclusion"))
            resultsContainer.addView(conclusionCard)
        }

        // Notes
        if (lessonPlan.has("notes")) {
            val notesCard = createInfoCard("Notes", lessonPlan.getString("notes"))
            resultsContainer.addView(notesCard)
        }

        addSpacer()
    }

    private fun displayVideos(videos: JSONArray) {
        val sectionTitle = createSectionTitle("Related Videos")
        resultsContainer.addView(sectionTitle)

        for (i in 0 until videos.length()) {
            val video = videos.getJSONObject(i)
            val videoId = video.getString("videoId")
            val thumbnail = video.getString("thumbnail")

            val videoCard = createVideoCard(videoId, thumbnail, i + 1)
            resultsContainer.addView(videoCard)
        }

        addSpacer()
    }

    private fun displayMCQs(mcqs: JSONArray) {
        val sectionTitle = createSectionTitle("Multiple Choice Questions")
        resultsContainer.addView(sectionTitle)

        for (i in 0 until mcqs.length()) {
            val mcq = mcqs.getJSONObject(i)
            val mcqCard = createMCQCard(mcq, i + 1)
            resultsContainer.addView(mcqCard)
        }

        addSpacer()
    }

    private fun displayRelatedNames(names: JSONArray) {
        val sectionTitle = createSectionTitle("Related Topics")
        resultsContainer.addView(sectionTitle)

        val namesText = StringBuilder()
        for (i in 0 until names.length()) {
            if (i > 0) namesText.append(", ")
            namesText.append(names.getString(i))
        }

        val namesCard = createInfoCard("Related", namesText.toString())
        resultsContainer.addView(namesCard)

        addSpacer()
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
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8))
        }

        // Icon based on section type
        val iconRes = when (title.lowercase()) {
            "lesson plan" -> R.drawable.section_icon_lesson
            "related videos" -> R.drawable.section_icon_video
            "multiple choice questions" -> R.drawable.section_icon_quiz
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
            setTextColor(android.graphics.Color.parseColor("#1F2937"))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
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

        // Label with accent color
        val labelView = TextView(context).apply {
            text = label
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#667eea"))
            letterSpacing = 0.02f
        }

        // Content with better typography
        val contentView = TextView(context).apply {
            text = content
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#374151"))
            setPadding(0, dpToPx(8), 0, 0)
            setTextIsSelectable(true)
            setLineSpacing(1.3f, 1.0f)
        }

        cardLayout.addView(labelView)
        cardLayout.addView(contentView)

        return cardLayout
    }

    private fun createVideoCard(videoId: String, thumbnail: String, index: Int): LinearLayout {
        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            setBackgroundResource(R.drawable.modern_video_card_background)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dpToPx(6), 0, dpToPx(6))
            }
            isClickable = true
            elevation = 2f
            setOnClickListener {
                openYouTubeVideo(videoId)
            }
        }

        // Video thumbnail placeholder
        val thumbnailView = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_media_play)
            layoutParams = LinearLayout.LayoutParams(dpToPx(50), dpToPx(36)).apply {
                setMargins(0, 0, dpToPx(12), 0)
            }
            setBackgroundResource(R.drawable.modern_card_background)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        }

        // Video info
        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val titleView = TextView(context).apply {
            text = "Video $index"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.parseColor("#1F2937"))
        }

        val idView = TextView(context).apply {
            text = "Tap to watch on YouTube"
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#6B7280"))
            setPadding(0, dpToPx(2), 0, 0)
        }

        textLayout.addView(titleView)
        textLayout.addView(idView)

        // Play indicator
        val playIndicator = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_media_play)
            layoutParams = LinearLayout.LayoutParams(dpToPx(24), dpToPx(24))
            setColorFilter(android.graphics.Color.parseColor("#E53E3E"))
        }

        cardLayout.addView(thumbnailView)
        cardLayout.addView(textLayout)
        cardLayout.addView(playIndicator)

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

        // Question header with number badge
        val questionHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val questionBadge = TextView(context).apply {
            text = "Q$index"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            setBackgroundColor(android.graphics.Color.parseColor("#667eea"))
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
            setTextColor(android.graphics.Color.parseColor("#1F2937"))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            setLineSpacing(1.3f, 1.0f)
        }

        questionHeader.addView(questionBadge)
        questionHeader.addView(questionView)
        cardLayout.addView(questionHeader)

        // Add some spacing
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(8)
            )
        }
        cardLayout.addView(spacer)

        // Options with better styling
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
                if (isCorrect) {
                    setBackgroundColor(android.graphics.Color.parseColor("#DCFCE7"))
                } else {
                    setBackgroundColor(android.graphics.Color.parseColor("#F9FAFB"))
                }
            }

            val optionLabel = TextView(context).apply {
                val prefix = when(i) {
                    0 -> "A"
                    1 -> "B"
                    2 -> "C"
                    3 -> "D"
                    else -> "${('A' + i)}"
                }
                text = prefix
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.WHITE)
                setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2))
                setBackgroundColor(if (isCorrect)
                    android.graphics.Color.parseColor("#16A34A")
                else
                    android.graphics.Color.parseColor("#6B7280"))
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(24),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, dpToPx(12), 0)
                }
                gravity = android.view.Gravity.CENTER
            }

            val optionText = TextView(context).apply {
                text = option
                textSize = 13f
                setTextColor(if (isCorrect)
                    android.graphics.Color.parseColor("#166534")
                else
                    android.graphics.Color.parseColor("#374151"))
                if (isCorrect) {
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                setLineSpacing(1.3f, 1.0f)
            }

            optionLayout.addView(optionLabel)
            optionLayout.addView(optionText)
            cardLayout.addView(optionLayout)
        }

        return cardLayout
    }

    private fun openYouTubeVideo(videoId: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId"))
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open video: $videoId", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addSpacer() {
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(20)
            )
        }
        resultsContainer.addView(spacer)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    fun dismiss() {
        bottomSheetDialog?.dismiss()
        coroutineScope.cancel()
    }
}