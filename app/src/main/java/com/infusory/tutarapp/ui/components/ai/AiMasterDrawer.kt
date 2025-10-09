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

class AiMasterDrawer(
    private val context: Context,
    private val onResultReceived: (String) -> Unit
) {

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
                val inputStream = context.assets.open("class_list_demo.json")
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                inputStream.close()

                classesData = gson.fromJson(jsonString, listType)
                Log.d("AiMasterDrawer", "Successfully loaded ${classesData.size} classes from assets")
                return
            } catch (e: Exception) {
                Log.d("AiMasterDrawer", "Failed to load from assets, trying other locations...")
            }

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
        dialog = Dialog(context, R.style.CustomCenteredDialog)
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_ai_master, null)
        dialogView = view

        setupViews(view)
        setupSpinners()

        dialog?.setContentView(view)

        dialog?.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            val displayMetrics = context.resources.displayMetrics
            val configuration = context.resources.configuration
            val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

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
        val subjectAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, subjectNames)
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
                        Toast.makeText(context, "Search failed: ${response.code}", Toast.LENGTH_LONG).show()
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

    private fun displayResults(data: JSONObject) {
        resultsContainer.removeAllViews()

        if (data.has("content")) {
            val content = data.getJSONObject("content")
            if (content.has("lessonPlan")) {
                val lessonPlan = content.getJSONObject("lessonPlan")
                displayLessonPlan(lessonPlan)
            }
        }

        if (data.has("names")) {
            val names = data.getJSONArray("names")
            if (names.length() > 0) {
                display3DModels(names)
            }
        }

        if (data.has("images")) {
            val images = data.getJSONArray("images")
            if (images.length() > 0) {
                displayImages(images)
            }
        }

        if (data.has("videos")) {
            val videos = data.getJSONArray("videos")
            if (videos.length() > 0) {
                displayVideos(videos)
            }
        }

        if (data.has("mcqs")) {
            val mcqs = data.getJSONArray("mcqs")
            if (mcqs.length() > 0) {
                displayMCQs(mcqs)
            }
        }
    }

    private fun displayLessonPlan(lessonPlan: JSONObject) {
        val sectionTitle = createSectionTitle("Lesson Plan")
        resultsContainer.addView(sectionTitle)
        if (lessonPlan.has("topic")) resultsContainer.addView(createInfoCard("Topic", lessonPlan.getString("topic")))
        if (lessonPlan.has("explanation")) resultsContainer.addView(createInfoCard("Explanation", lessonPlan.getString("explanation")))
        if (lessonPlan.has("conclusion")) resultsContainer.addView(createInfoCard("Conclusion", lessonPlan.getString("conclusion")))
        if (lessonPlan.has("notes")) resultsContainer.addView(createInfoCard("Notes", lessonPlan.getString("notes")))
        addSpacer()
    }

    private fun displayMCQs(mcqs: JSONArray) {
        val sectionTitle = createSectionTitle("Multiple Choice Questions")
        resultsContainer.addView(sectionTitle)
        for (i in 0 until mcqs.length()) {
            val mcq = mcqs.getJSONObject(i)
            resultsContainer.addView(createMCQCard(mcq, i + 1))
        }
        addSpacer()
    }

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
            val thumbnail = video.getString("thumbnail")
            val title = video.optString("title", "Video ${i + 1}")
            val videoCard = createVideoCard(videoId, thumbnail, title)
            horizontalContainer.addView(videoCard)
        }

        addSpacer()
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

        addSpacer()
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
            val imageCard = createImageCard(imageBase64, i + 1)
            horizontalContainer.addView(imageCard)
        }

        addSpacer()
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
            setOnClickListener { openYouTubeVideo(videoId) }
        }

        val thumbnailView = ImageView(context).apply {
            setImageURI(Uri.parse(thumbnail))
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(80))
        }

        val titleView = TextView(context).apply {
            text = title
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
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
            setTypeface(null, android.graphics.Typeface.BOLD)
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

    private fun createImageCard(imageBase64: String, index: Int): LinearLayout {
        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setBackgroundResource(R.drawable.modern_video_card_background)
            layoutParams = LinearLayout.LayoutParams(dpToPx(120), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dpToPx(8), 0, dpToPx(8), 0)
            }
            isClickable = true
            elevation = 4f
//            setOnClickListener { showImageFullScreen(imageBase64) }
        }

        val imageView = ImageView(context).apply {
            val decodedString = imageBase64.split(",")[1] // Remove data URI prefix if present
            val decodedByte = Base64.decode(decodedString, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedByte, 0, decodedByte.size)
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(80))
        }

        val titleView = TextView(context).apply {
            text = "Image $index"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dpToPx(4), 0, 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        cardLayout.addView(imageView)
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
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
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
            setTypeface(null, android.graphics.Typeface.BOLD)
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
            setTypeface(null, android.graphics.Typeface.BOLD)
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
                setTypeface(null, android.graphics.Typeface.BOLD)
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
                if (isCorrect) setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
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

    private fun open3DModel(name: String) {
        Toast.makeText(context, "Opening 3D Model: $name", Toast.LENGTH_SHORT).show() // Placeholder
    }

//    private fun showImageFullScreen(imageBase64: String) {
//        val dialog = Dialog(context)
//        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
//        dialog.setContentView(R.layout.dialog_fullscreen_image)
//        val imageView = dialog.findViewById<ImageView>(R.id.iv_fullscreen_image)
//        val decodedString = imageBase64.split(",")[1]
//        val decodedByte = Base64.decode(decodedString, Base64.DEFAULT)
//        val bitmap = BitmapFactory.decodeByteArray(decodedByte, 0, decodedByte.size)
//        imageView.setImageBitmap(bitmap)
//        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
//        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
//        dialog.show()
//    }

    private fun addSpacer() {
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(20))
        }
        resultsContainer.addView(spacer)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    fun dismiss() {
        dialog?.dismiss()
        coroutineScope.cancel()
    }
}