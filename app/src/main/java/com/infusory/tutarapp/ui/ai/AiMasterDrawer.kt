package com.infusory.tutarapp.ui.ai

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
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
import android.widget.ImageButton
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
import com.infusory.tutarapp.ui.data.ModelData
import com.infusory.tutarapp.ui.data.SubjectData
import com.infusory.tutarapp.ui.data.TopicData
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
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.concurrent.TimeUnit

class AiMasterDrawer(
    private val activity: WhiteboardActivity,
    private val mainLayoutBackgroundColor: Int = Color.WHITE,
    private val onResultReceived: (String) -> Unit
) {
    init {
        Log.e("AiMasterDrawer", "Initialized with background color: #${String.format("%08X", mainLayoutBackgroundColor)}")
    }

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
    private lateinit var animatedBorderView: AnimatedGradientBorderView

    // Data
    private var classesData: List<ClassData> = emptyList()
    private var selectedClass: ClassData? = null
    private var selectedSubject: SubjectData? = null
    private var selectedOption: String = ""

    private val options = arrayOf("Lesson Plan", "Description")

    // properties to store the last search results
    private var lastSearchData: JSONObject? = null
    private var hasSearchResults: Boolean = false

    private var currentCapturedImage: String? = null

    // Network
    private val client = OkHttpClient.Builder()
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Streaming state
    private var requestStartTime: Long = 0
    private var isStreaming = false

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
            try {
                val inputStream = context.assets.open("class_data.json")
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                inputStream.close()

                classesData = normalizeClassData(jsonString)
                if (classesData.isNotEmpty()) {
                    Log.d("AiMasterDrawer", "Loaded production data: ${classesData.size} classes")
                    return
                }
            } catch (e: Exception) {
                Log.e("AiMasterDrawer", "Production data not available: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("AiMasterDrawer", "Critical error loading class data: ${e.message}", e)
            classesData = emptyList()
        }
    }

    private fun normalizeClassData(jsonString: String): List<ClassData> {
        return try {
            val gson = Gson()
            val listType = object : TypeToken<List<ClassData>>() {}.type
            val rawData = gson.fromJson<List<ClassData>>(jsonString, listType)

            rawData.filter { it.Class != null }.map { classData ->
                val normalizedSubjects = classData.subjects?.map { subject ->
                    when {
                        subject.topics != null -> subject // Production structure
//                        subject.models != null -> SubjectData( // Demo structure
//                            name = subject.name,
//                            topics = listOf(TopicData(name = subject.name, models = subject.models))
//                        )
                        else -> SubjectData(name = subject.name, topics = emptyList())
                    }
                }
                classData.copy(subjects = normalizedSubjects)
            }
        } catch (e: Exception) {
            Log.e("AiMasterDrawer", "Normalization failed: ${e.message}")
            emptyList()
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
                selectedClass = null; setupSubjectSpinner()
            }
        }

        val optionsList = mutableListOf("Select Option")
        optionsList.addAll(options)
        val optionAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, optionsList)
        optionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        optionSpinner.adapter = optionAdapter
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
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                selectedSubject =
                    if (position > 0 && selectedClass?.subjects != null) selectedClass!!.subjects!![position - 1] else null
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedSubject = null
            }
        }
    }

    private fun performSearch() {
        if (selectedClass == null || selectedSubject == null || selectedOption.isEmpty() || queryEditText.text.toString()
                .trim().isEmpty()
        ) {
            Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        searchButton.isEnabled = false
        searchButton.alpha = 0.5f

        // Start the animated border
        animatedBorderView.startAnimation()

        showLoadingSkeleton()

        val requestData = JSONObject().apply {
            put("className", selectedClass!!.Class)
            put("subjectName", selectedSubject!!.name)
            put("contentType", selectedOption)
            put("query", queryEditText.text.toString().trim())
        }

        Log.d("AiMasterDrawer", "Search request: $requestData")
        makeStreamingRequest(requestData.toString())
    }

    private fun makeStreamingRequest(jsonData: String) {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonData.toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://ndkg0hst-5001.inc1.devtunnels.ms/gemini/search")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        requestStartTime = System.currentTimeMillis()
        isStreaming = true

        coroutineScope.launch(Dispatchers.IO) {
            try {
                Log.d("AiMasterDrawer", "🚀 Starting streaming request...")

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        handleStreamingError("Request failed: ${response.code}")
                    }
                    return@launch
                }

                // Process streaming response
                processStreamingResponse(response)

            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    handleStreamingError("Network error: ${e.message}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    handleStreamingError("Error: ${e.message}")
                }
            } finally {
                isStreaming = false
                withContext(Dispatchers.Main) {
                    // ✅ Re-enable search button after completion
                    if (::searchButton.isInitialized) {
                        searchButton.isEnabled = true
                        searchButton.alpha = 1.0f
                    }
                }
            }
        }
    }

    private suspend fun processStreamingResponse(response: Response) {
        val reader = response.body?.byteStream()?.bufferedReader() ?: return
        var buffer = StringBuilder()

        try {
            reader.use { bufferedReader ->
                var line: String?

                while (bufferedReader.readLine().also { line = it } != null && isStreaming) {
                    if (line.isNullOrEmpty()) {
                        // Empty line signals end of SSE message
                        if (buffer.isNotEmpty()) {
                            processSSEMessage(buffer.toString())
                            buffer = StringBuilder()
                        }
                    } else {
                        buffer.append(line).append("\n")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AiMasterDrawer", "Error reading stream", e)
            withContext(Dispatchers.Main) {
                handleStreamingError("Stream reading error: ${e.message}")
            }
        } finally {
            withContext(Dispatchers.Main) {
                searchButton.isEnabled = true
                searchButton.alpha = 1.0f

                // Stop the animated border
                animatedBorderView.stopAnimation()

                val totalTime = System.currentTimeMillis() - requestStartTime
                Log.d("AiMasterDrawer", "🏁 Streaming completed in ${totalTime}ms")
            }
        }
    }

    private suspend fun processSSEMessage(message: String) {
        val lines = message.split("\n")

        for (line in lines) {
            if (line.startsWith("data: ")) {
                val jsonData = line.substring(6).trim()

                if (jsonData.isEmpty() || jsonData == "[DONE]") continue

                try {
                    val responseTime = System.currentTimeMillis() - requestStartTime
                    val parsed = JSONObject(jsonData)

                    withContext(Dispatchers.Main) {
                        handleStreamingData(parsed, responseTime)
                    }
                } catch (e: Exception) {
                    Log.e("AiMasterDrawer", "Error parsing SSE data: $jsonData", e)
                }
            }
        }
    }

    private fun handleStreamingData(parsed: JSONObject, responseTime: Long) {
        val type = parsed.optString("type")
        val data = parsed.optJSONObject("data")
        val message = parsed.optString("message")

        // Clear skeleton on first real data - check for the container tag
        if (resultsContainer.childCount > 0) {
            val firstChild = resultsContainer.getChildAt(0)
            if (firstChild.tag == "skeleton_container") {
                resultsContainer.removeAllViews()
            }
        }

        when (type) {

            "gemini" -> {
                data?.let {
                    Log.d("AiMasterDrawer", "Gemini response received")

                    // ✅ 1. Display 3D Models first
                    if (it.has("names")) {
                        val names = it.getJSONArray("names")
                        display3DModels(names)
                    }

                    // ✅ 2. Display Images next
                    if (it.has("images")) {
                        val images = it.getJSONArray("images")
                        displayImages(images)
                    }

                    // ✅ 3. Display Videos next
                    if (it.has("videos")) {
                        val videos = it.getJSONArray("videos")
                        displayVideos(videos)
                    }

                    // ✅ 4. Finally display content (lesson plan + description)
                    if (it.has("content")) {
                        val content = it.getJSONObject("content")

                        // Lesson plan first within content
                        if (content.has("lessonPlan")) {
                            displayLessonPlan(content.getJSONObject("lessonPlan"))
                        }

                        // Then description
                        if (content.has("description")) {
                            val descriptionArray = content.getJSONArray("description")
                            if (descriptionArray.length() > 0) {
                                displayDescription(descriptionArray)
                            }
                        }
                    }
                }
            }

            "images" -> {
                data?.let {
                    if (it.has("images")) {
                        val images = it.getJSONArray("images")
                        displayImages(images)
                    }
                }
            }

            "videos" -> {
                data?.let {
                    if (it.has("videos")) {
                        val videos = it.getJSONArray("videos")
                        displayVideos(videos)
                    }
                }
            }

            "description" -> {
                data?.let {
                    if (it.has("description")) {
                        val descriptionArray = it.getJSONArray("description")
                        if (descriptionArray.length() > 0) {
                            displayDescription(descriptionArray)
                        }
                    }
                }
            }

            "lessonPlan" -> {
                data?.let {
                    if (it.has("lessonPlan")) {
                        displayLessonPlan(it.getJSONObject("lessonPlan"))
                    }
                }
            }

            "mcqs" -> {
                data?.let {
                    if (it.has("mcqs")) {
                        Log.d("AiMasterDrawer", "MCQs received")
                    }
                }
            }

            "error" -> {
                displayErrorMessage("Error: $message", responseTime)
            }
        }

    }

    private fun displayErrorMessage(message: String, responseTime: Long) {
        val errorView = TextView(context).apply {
            text = "❌ $message - ${responseTime}ms"
            textSize = 14f
            setTextColor(Color.parseColor("#721C24"))
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            setBackgroundColor(Color.parseColor("#F8D7DA"))
        }
        resultsContainer.addView(errorView)
    }

    private fun createShimmerAnimation(): android.animation.ObjectAnimator {
        return android.animation.ObjectAnimator.ofFloat(null, "alpha", 0.3f, 1f, 0.3f).apply {
            duration = 1500
            repeatCount = android.animation.ObjectAnimator.INFINITE
            repeatMode = android.animation.ObjectAnimator.REVERSE
        }
    }

    private fun showLoadingSkeleton() {
        resultsContainer.removeAllViews()
        resultsScrollView.visibility = View.VISIBLE

        val skeletonContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(4), dpToPx(12), dpToPx(4), dpToPx(12))
            tag = "skeleton_container"
        }

        skeletonContainer.addView(createSkeletonSectionHeader())

        for (i in 0 until 3) {
            skeletonContainer.addView(createSkeletonCard(if (i == 0) 120 else 180))
        }

        skeletonContainer.addView(createSpacer(20))
        skeletonContainer.addView(createSkeletonSectionHeader())
        skeletonContainer.addView(createSkeletonHorizontalMedia())

        resultsContainer.addView(skeletonContainer)
        startSkeletonAnimations(skeletonContainer)
    }

    private fun createSkeletonSectionHeader(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dpToPx(20), 0, dpToPx(12))
            }
            setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8))
            gravity = Gravity.CENTER_VERTICAL

            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(28), dpToPx(28)).apply {
                    setMargins(0, 0, dpToPx(12), 0)
                }
                background = createSkeletonBackground()
                tag = "skeleton"
            })

            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(150), dpToPx(24))
                background = createSkeletonBackground()
                tag = "skeleton"
            })
        }
    }

    private fun createSkeletonCard(heightDp: Int): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(14))
            background = createSkeletonCardBackground()
            layoutParams = createMarginLayoutParams(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
            elevation = 2f
            tag = "skeleton"

            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(100), dpToPx(16))
                background = createSkeletonBackground()
                tag = "skeleton"
            })

            val numLines = if (heightDp > 150) 4 else 2
            for (i in 0 until numLines) {
                addView(View(context).apply {
                    val width =
                        if (i == numLines - 1) dpToPx(180) else LinearLayout.LayoutParams.MATCH_PARENT
                    layoutParams = LinearLayout.LayoutParams(width, dpToPx(12)).apply {
                        setMargins(0, dpToPx(8), 0, 0)
                    }
                    background = createSkeletonBackground()
                    tag = "skeleton"
                })
            }
        }
    }

    private fun createSkeletonHorizontalMedia(): HorizontalScrollView {
        val horizontalContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
        }

        for (i in 0 until 4) {
            horizontalContainer.addView(createSkeletonMediaCard())
        }

        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(horizontalContainer)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun createSkeletonMediaCard(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            background = createSkeletonCardBackground()
            layoutParams = LinearLayout.LayoutParams(dpToPx(150), dpToPx(120)).apply {
                setMargins(dpToPx(8), 0, dpToPx(8), 0)
            }
            elevation = 4f
            tag = "skeleton"

            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(80)
                )
                background = createSkeletonBackground()
                tag = "skeleton"
            })

            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(100), dpToPx(12)).apply {
                    setMargins(0, dpToPx(8), 0, 0)
                }
                background = createSkeletonBackground()
                tag = "skeleton"
            })
        }
    }

    private fun createSkeletonBackground(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor("#212121"));
            cornerRadius = dpToPx(4).toFloat()
        }
    }

    private fun createSkeletonCardBackground(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor("#121212"));
            cornerRadius = dpToPx(8).toFloat()
        }
    }

    private fun startSkeletonAnimations(container: LinearLayout) {
        fun animateSkeletonViews(view: View) {
            if (view.tag == "skeleton") {
                val animator = createShimmerAnimation()
                animator.target = view
                animator.start()
            }

            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) {
                    animateSkeletonViews(view.getChildAt(i))
                }
            }
        }

        animateSkeletonViews(container)
    }

    private fun handleStreamingError(errorMessage: String) {
        searchButton.isEnabled = true
        searchButton.alpha = 1.0f

        // Stop the animated border on error
        animatedBorderView.stopAnimation()

        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
        displayErrorMessage(errorMessage, System.currentTimeMillis() - requestStartTime)
    }

    private fun createAndShowDialog() {
        dialog = Dialog(context, R.style.CustomCenteredDialog)
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_ai_master, null)
        dialogView = view

        // Create and add the animated border view as an overlay
        animatedBorderView = AnimatedGradientBorderView(context)
        val borderContainer = FrameLayout(context).apply {
            addView(view)
            addView(
                animatedBorderView, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        setupViews(view)
        setupSpinners()

        if (hasSearchResults && lastSearchData != null) {
            resultsScrollView.visibility = View.VISIBLE
            displayResults(lastSearchData!!)
        }

        dialog?.setContentView(borderContainer)

        dialog?.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            val displayMetrics = context.resources.displayMetrics
            val configuration = context.resources.configuration
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            val dialogWidth = (550 * displayMetrics.density).toInt()   // your current width
            val dialogHeight = (displayMetrics.heightPixels * 0.80).toInt()  // your current height

            val layoutParams = window.attributes
            layoutParams.width = dialogWidth
            layoutParams.height = dialogHeight

            // Bottom vertically, centered horizontally
            layoutParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL

            // Optional: small margin from bottom (0 = flush)
            layoutParams.y = 0

            layoutParams.dimAmount = 0.6f
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes = layoutParams
        }


        dialog?.setCancelable(true)
        dialog?.setCanceledOnTouchOutside(true)
        dialog?.show()
    }

    private fun displayResults(data: JSONObject) {
        resultsContainer.removeAllViews()
        resultsScrollView.visibility = View.VISIBLE

        Log.d("AiMasterDrawer", "Data keys: ${data.keys().asSequence().toList()}")

        val loadingView = TextView(context).apply {
            text = "Loading content..."
            textSize = 14f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(dpToPx(20), dpToPx(40), dpToPx(20), dpToPx(40))
        }
        resultsContainer.addView(loadingView)

        coroutineScope.launch {
            delay(50)

            withContext(Dispatchers.Main) {
                resultsContainer.removeView(loadingView)
            }

            if (data.has("content")) {
                val content = data.getJSONObject("content")

                if (content.has("lessonPlan")) {
                    withContext(Dispatchers.Main) {
                        displayLessonPlan(content.getJSONObject("lessonPlan"))
                    }
                    delay(100)
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

            if (data.has("names")) {
                val names = data.getJSONArray("names")
                if (names.length() > 0) {
                    withContext(Dispatchers.Main) {
                        display3DModels(names)
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
            if (data.has("images")) {
                val images = data.getJSONArray("images")
                if (images.length() > 0) {
                    withContext(Dispatchers.Main) {
                        displayImages(images)
                    }
                    delay(100)
                }
            }


        }
    }

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

            addView(createSectionHeader("Lesson Plan"))

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

    private fun addTextContainerToWhiteboard(text: String, title: String = "") {
        try {
            activity.addTextContainerWithContent(text, title)
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
                addTextContainerToWhiteboard(content, heading)
            }

            addView(TextView(context).apply {
                text = heading
                textSize = if (isTopic) 14f else 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                letterSpacing = if (isTopic) 0.1f else 0.05f
                gravity = if (isTopic) Gravity.CENTER else Gravity.START
            })

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

            addView(createSectionHeader("Description"))

            val maxPoints = minOf(descriptionArray.length(), 15)
            for (i in 0 until maxPoints) {
                addView(createDescriptionPoint(descriptionArray.getString(i), i))
            }

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

    private fun createDescriptionPoint(point: String, index: Int): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))

            background = createDashedBorder()
            layoutParams = createMarginLayoutParams(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4))
            isClickable = true
            setOnClickListener {
                Log.d("AiMasterDrawer", "Point ${index + 1} clicked")
                addTextContainerToWhiteboard(point, "Point ${index + 1}")
            }

            addView(View(context).apply {
                background = createCircleBullet()
                layoutParams = LinearLayout.LayoutParams(dpToPx(8), dpToPx(8)).apply {
                    setMargins(dpToPx(4), dpToPx(8), dpToPx(12), 0)
                }
            })

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
            setStroke(
                dpToPx(1),
                Color.parseColor("#666666"),
                dpToPx(8).toFloat(),
                dpToPx(4).toFloat()
            )
            cornerRadius = dpToPx(8).toFloat()
        }
    }

    private fun createCircleBullet(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#2196F3"))
        }
    }

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

    private fun getModelsForSubject(subject: SubjectData?): List<ModelData> {
        return subject?.topics?.flatMap { it.models ?: emptyList() } ?: emptyList()
    }

    private fun display3DModels(names: JSONArray) {
        val sectionTitle = createSectionTitle("3D Models")
        resultsContainer.addView(sectionTitle)
        val models = getModelsForSubject(selectedSubject)
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
            setOnClickListener {
                // Use the manager's method to add YouTube container with URL and search
                val youtubeUrl = if (videoId.startsWith("http")) {
                    videoId
                } else {
                    "https://www.youtube.com/watch?v=$videoId"
                }
                activity.addYouTubeContainerWithUrl(youtubeUrl)
            }
        }

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

        val playIcon = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_media_play)
            scaleType = ImageView.ScaleType.CENTER
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(48),
                dpToPx(48)
            ).apply {
                gravity = Gravity.CENTER
            }

            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#80000000"))
            }
            background = drawable

            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
        }

        thumbnailContainer.addView(thumbnailView)
        thumbnailContainer.addView(playIcon)

        // Add title if available
//        if (title.isNotEmpty() && title != "Video ${videos.length()}") {
//            val titleView = TextView(context).apply {
//                text = title
//                textSize = 12f
//                setTextColor(Color.WHITE)
//                maxLines = 2
//                ellipsize = TextUtils.TruncateAt.END
//                setPadding(0, dpToPx(4), 0, 0)
//                layoutParams = LinearLayout.LayoutParams(
//                    LinearLayout.LayoutParams.MATCH_PARENT,
//                    LinearLayout.LayoutParams.WRAP_CONTENT
//                )
//            }
//            cardLayout.addView(titleView)
//        }

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
            setImageResource(R.drawable.ic_ar)
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams =
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(80))
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
            "3d models" -> R.drawable.section_icon_lesson
            "images" -> R.drawable.section_icon_lesson
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

    private fun open3DModel(name: String) {
        Toast.makeText(context, "Opening 3D Model: $name", Toast.LENGTH_SHORT).show()
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
                    dismiss()
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
                    imageBase64.split(",")[1]
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
            val imageCard = createImageCard(imageBase64, i + 1)
            horizontalContainer.addView(imageCard)
        }

        addSpacer()
    }

    fun clearResults() {
        lastSearchData = null
        hasSearchResults = false
        isStreaming = false
        currentCapturedImage = null // ✅ Clear the captured image
        Log.d("AiMasterDrawer", "Cleared results and captured image")
    }

    fun dismiss() {
        isStreaming = false
        animatedBorderView.stopAnimation()
        currentCapturedImage = null // ✅ Clear the captured image on dismiss
        dialog?.dismiss()
        dialog = null
        Log.d("AiMasterDrawer", "Dismissed and cleared captured image")
    }

    // Custom View for Animated Gradient Border
    inner class AnimatedGradientBorderView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val borderWidth = dpToPx(4).toFloat()
        private val cornerRadius = dpToPx(16).toFloat()
        private var gradientOffset = 0f
        private var animator: ValueAnimator? = null

        // Gemini-inspired gradient colors
        private val gradientColors = intArrayOf(
            Color.parseColor("#4285F4"), // Blue
            Color.parseColor("#EA4335"), // Red
            Color.parseColor("#FBBC04"), // Yellow
            Color.parseColor("#34A853"), // Green
            Color.parseColor("#4285F4")  // Blue (repeat for seamless loop)
        )

        init {
            setWillNotDraw(false)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = borderWidth
            visibility = View.GONE // Hidden by default
        }

        fun startAnimation() {
            visibility = View.VISIBLE

            animator?.cancel()
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 3000
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART

                addUpdateListener { animation ->
                    gradientOffset = animation.animatedValue as Float
                    invalidate()
                }

                start()
            }
        }

        fun stopAnimation() {
            animator?.cancel()
            animator = null
            visibility = View.GONE
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            if (visibility != View.VISIBLE) return

            val rectF = RectF(
                borderWidth / 2,
                borderWidth / 2,
                width - borderWidth / 2,
                height - borderWidth / 2
            )

            // Create animated linear gradient
            val gradientWidth = width * 2f
            val offset = -gradientWidth * gradientOffset

            paint.shader = LinearGradient(
                offset,
                0f,
                offset + gradientWidth,
                0f,
                gradientColors,
                null,
                Shader.TileMode.REPEAT
            )

            // Draw rounded rectangle border
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            stopAnimation()
        }
    }

    /*
    * Perform image search directly without showing dialog first
    * Auto-selects Demo class, General subject, and Lesson Plan content type
    */
    fun performImageSearchDirectly(imageBase64: String) {
        currentCapturedImage = imageBase64
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

        // ✅ Find Miscellaneous class
        val miscClass = classesData.find { it.Class.equals("Miscellaneous", ignoreCase = true) }
        if (miscClass == null) {
            Toast.makeText(context, "Miscellaneous class not found", Toast.LENGTH_SHORT).show()
            return
        }

        // ✅ Find General subject - check if subjects exist first
        val generalSubject = miscClass.subjects?.find { it.name.equals("General", ignoreCase = true) }
        if (generalSubject == null) {
            // If General not found, use the first available subject
            val firstSubject = miscClass.subjects?.firstOrNull()
            if (firstSubject == null) {
                Toast.makeText(context, "No subjects found in Miscellaneous class", Toast.LENGTH_SHORT).show()
                return
            }

            // ✅ Set default values
            selectedClass = miscClass
            selectedSubject = firstSubject
            selectedOption = "Lesson Plan"

            // ✅ Use actual class and subject names in the request
            val requestData = JSONObject().apply {
                put("className", miscClass.Class) // Use actual class name
                put("subjectName", firstSubject.name) // Use actual subject name
                put("contentType", selectedOption)
                put("base64Image", currentCapturedImage!!)
                put("mimeType", "image/png")
            }

            val requestJson = requestData.toString()
            Log.d("AiMasterDrawer", "Using first available subject: ${firstSubject.name}")

            // ✅ Show dialog with loading state
            createAndShowDialogWithImageSearch()

            // ✅ Make streaming API request
            makeStreamingRequest(requestJson)
        } else {
            // ✅ Set default values
            selectedClass = miscClass
            selectedSubject = generalSubject
            selectedOption = "Lesson Plan"

            // ✅ Use actual class and subject names in the request
            val requestData = JSONObject().apply {
                put("className", miscClass.Class) // Use actual class name
                put("subjectName", generalSubject.name) // Use actual subject name
                put("contentType", selectedOption)
                put("base64Image", currentCapturedImage!!)
                put("mimeType", "image/png")
            }

            val requestJson = requestData.toString()

            // Log a sample of the request
            try {
                val sampleRequest = JSONObject().apply {
                    put("className", requestData.getString("className"))
                    put("subjectName", requestData.getString("subjectName"))
                    put("contentType", requestData.getString("contentType"))
                    put("mimeType", requestData.getString("mimeType"))
                    put(
                        "base64Image",
                        "${currentCapturedImage!!.take(100)}... (${currentCapturedImage!!.length} chars)"
                    )
                }
                Log.d("AiMasterDrawer", "Request preview: $sampleRequest")
            } catch (e: Exception) {
                Log.e("AiMasterDrawer", "Error creating request preview", e)
            }

            // ✅ Show dialog with loading state
            createAndShowDialogWithImageSearch()

            // ✅ Make streaming API request
            makeStreamingRequest(requestJson)
        }
    }

    /*
    * Create and show dialog specifically for image search
    * Pre-fills selections and starts with loading skeleton
    */
    private fun createAndShowDialogWithImageSearch() {
        dialog = Dialog(context, R.style.CustomCenteredDialog)
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_ai_master, null)
        dialogView = view

        // Create and add the animated border view as an overlay
        animatedBorderView = AnimatedGradientBorderView(context)
        val borderContainer = FrameLayout(context).apply {
            addView(view)
            addView(
                animatedBorderView, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        setupViews(view)
        setupSpinners()

        // Use coroutine to ensure proper sequencing
        coroutineScope.launch {
            // Small delay to ensure UI is ready
            delay(50)

            withContext(Dispatchers.Main) {
                // ✅ Force select Miscellaneous class
                val miscIndex = classesData.indexOfFirst { it.Class.equals("Miscellaneous", ignoreCase = true) }
                if (miscIndex != -1) {
                    classSpinner.setSelection(miscIndex + 1)

                    // Manually trigger the class selection listener
                    selectedClass = classesData[miscIndex]
                    setupSubjectSpinner()

                    // Small delay for subject spinner to update
                    delay(50)

                    // ✅ Force select the subject
                    val targetSubject = selectedClass?.subjects?.find { it.name.equals("General", ignoreCase = true) }
                        ?: selectedClass?.subjects?.firstOrNull()

                    if (targetSubject != null) {
                        // Get the current subject names from the spinner
                        val subjectAdapter = subjectSpinner.adapter
                        for (i in 0 until subjectAdapter.count) {
                            val item = subjectAdapter.getItem(i) as? String
                            if (item != null && item.equals(targetSubject.name, ignoreCase = true)) {
                                subjectSpinner.setSelection(i)
                                selectedSubject = targetSubject
                                Log.d("AiMasterDrawer", "Auto-selected subject: ${targetSubject.name}")
                                break
                            }
                        }
                    }
                }

                // ✅ Force select Lesson Plan option
                val lessonPlanIndex = options.indexOfFirst { it.equals("Lesson Plan", ignoreCase = true) }
                if (lessonPlanIndex != -1) {
                    optionSpinner.setSelection(lessonPlanIndex + 1)
                }

                showLoadingSkeleton()
                animatedBorderView.startAnimation()
            }
        }

        dialog?.setContentView(borderContainer)

        dialog?.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            val displayMetrics = context.resources.displayMetrics
            val configuration = context.resources.configuration
            val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            val dialogWidth = (550 * displayMetrics.density).toInt()
            val dialogHeight = (displayMetrics.heightPixels * 0.80).toInt()

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

        // ✅ Add dismiss listener to clear current image
        dialog?.setOnDismissListener {
            Log.d("AiMasterDrawer", "Dialog dismissed, clearing current captured image")
            currentCapturedImage = null
        }

        dialog?.show()

        Log.d("AiMasterDrawer", "Dialog shown with image search configuration")
    }}