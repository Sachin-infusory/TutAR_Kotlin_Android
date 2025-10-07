// ModelBrowserDrawer.kt - Bottom drawer for model browsing (Fixed)
package com.infusory.tutarapp.ui.models

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.infusory.tutarapp.R
import com.infusory.tutarapp.ui.data.ClassData
import com.infusory.tutarapp.ui.data.ModelData
import com.infusory.tutarapp.ui.data.SubjectData
import com.infusory.tutarapp.ui.data.TopicData
import java.io.File
import java.io.FileReader

class ModelBrowserDrawer(
    private val context: Context,
    private val onModelSelected: (ModelData, String) -> Unit
) {

    private var bottomSheetDialog: BottomSheetDialog? = null
    private lateinit var adapter: ModelBrowserAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var pathTextView: TextView
    private lateinit var titleTextView: TextView
    private lateinit var searchEditText: android.widget.EditText
    private lateinit var dialogView: View

    private var classesData: List<ClassData> = emptyList()
    private val navigationStack = mutableListOf<NavigationLevel>()
    private var isSearchMode = false
    private var currentSearchQuery = ""

    fun show() {
        loadModelData()
        if (classesData.isNotEmpty()) {
            createAndShowDialog()
        } else {
            Toast.makeText(context, "class_list_demo.json file missing in models folder", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadModelData() {
        try {
            val gson = Gson()
            val listType = object : TypeToken<List<ClassData>>() {}.type

            try {
                val inputStream = context.assets.open("class_list_demo.json")
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                inputStream.close()

                classesData = gson.fromJson(jsonString, listType)
                Log.d("ModelBrowser", "Successfully loaded ${classesData.size} classes from assets")
                return
            } catch (e: java.io.FileNotFoundException) {
                Log.d("ModelBrowser", "class_data.json not found in assets, trying storage...")
            }

            // Try internal storage
            val internalFile = File(context.filesDir, "class_data.json")
            if (internalFile.exists()) {
                val fileReader = FileReader(internalFile)
                classesData = gson.fromJson(fileReader, listType)
                fileReader.close()
                Log.d("ModelBrowser", "Successfully loaded ${classesData.size} classes from internal storage")
                return
            }

            // Try external storage
            val externalFile = File(context.getExternalFilesDir(null), "models/class_data.json")
            if (externalFile.exists()) {
                val fileReader = FileReader(externalFile)
                classesData = gson.fromJson(fileReader, listType)
                fileReader.close()
                Log.d("ModelBrowser", "Successfully loaded ${classesData.size} classes from external storage")
                return
            }

            // No file found anywhere
            Log.e("ModelBrowser", "class_data.json not found in any location")
            classesData = emptyList()

        } catch (e: Exception) {
            Log.e("ModelBrowser", "Error loading model data: ${e.message}", e)
            classesData = emptyList()
            Toast.makeText(context, "Error reading class_data.json: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createAndShowDialog() {
        // Use standard BottomSheetDialog without any custom theme
        bottomSheetDialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_model_browser, null)

        dialogView = view

        setupViews(view)
        setupRecyclerView()

        bottomSheetDialog?.setContentView(view)

        // Configure bottom sheet behavior AFTER setting content
        bottomSheetDialog?.setOnShowListener { dialog ->
            val bottomSheet = (dialog as BottomSheetDialog).findViewById<android.widget.FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)

                // Set the height first
                val displayMetrics = context.resources.displayMetrics
                val height = (displayMetrics.heightPixels * 0.85).toInt() // 85% of screen height

                val layoutParams = it.layoutParams
                layoutParams.height = height
                it.layoutParams = layoutParams

                // Configure behavior after setting height
                behavior.peekHeight = height // This ensures it opens at the set height
                behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                behavior.isDraggable = true // Allow dragging to close
                behavior.isHideable = true // Allow dismissing by dragging down
            }
        }

        bottomSheetDialog?.show()
        showClasses()
        updateSearchUI()
    }

    private fun setupViews(view: View) {
        pathTextView = view.findViewById(R.id.tv_path)
        titleTextView = view.findViewById(R.id.tv_title)
        recyclerView = view.findViewById(R.id.rv_models)
        searchEditText = view.findViewById(R.id.et_search)

        // Close button
        view.findViewById<View>(R.id.btn_close).setOnClickListener {
            bottomSheetDialog?.dismiss()
        }

        // Back button
        view.findViewById<View>(R.id.btn_back).setOnClickListener {
            if (isSearchMode) {
                clearSearch()
            } else {
                navigateBack()
            }
        }

        // Search button
        view.findViewById<View>(R.id.btn_search).setOnClickListener {
            performSearch()
        }

        // Clear search button
        view.findViewById<View>(R.id.btn_clear_search).setOnClickListener {
            clearSearch()
        }

        // Search on enter key
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }

        // Real-time search as user types
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isEmpty() && isSearchMode) {
                    clearSearch()
                }
            }
        })
    }

    private fun setupRecyclerView() {
        adapter = ModelBrowserAdapter { item ->
            handleItemClick(item)
        }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
    }

    private fun handleItemClick(item: BrowserItem) {
        when (item.type) {
            BrowserItemType.CLASS -> {
                val classData = item.data as ClassData
                showSubjects(classData)
            }
            BrowserItemType.SUBJECT -> {
                val subjectData = item.data as SubjectData
                showTopics(subjectData)
            }
            BrowserItemType.TOPIC -> {
                val topicData = item.data as TopicData
                showModels(topicData)
            }
            BrowserItemType.MODEL -> {
                val modelData = item.data as ModelData
                val fullPath = getCurrentPath() + " > " + modelData.name
                onModelSelected(modelData, fullPath)
                bottomSheetDialog?.dismiss()
            }
            BrowserItemType.BACK -> {
                navigateBack()
            }
        }
    }

    private fun showClasses() {
        navigationStack.clear()

        val items = classesData.mapNotNull { classData ->
            val subjects = classData.subjects ?: return@mapNotNull null
            if (subjects.isEmpty()) return@mapNotNull null

            val subjectCount = subjects.size
            val totalModels = subjects.sumOf { subject ->
                subject.topics?.sumOf { topic -> topic.models?.size ?: 0 } ?: 0
            }

            BrowserItem(
                type = BrowserItemType.CLASS,
                title = classData.Class,
                subtitle = "$subjectCount subjects, $totalModels models",
                data = classData
            )
        }

        adapter.updateItems(items, "Classes")
        updateUI("Model Library", "Classes")
    }

    private fun showSubjects(classData: ClassData) {
        navigationStack.add(NavigationLevel.CLASS(classData))

        val items = mutableListOf<BrowserItem>()
        items.add(createBackItem())

        val subjects = classData.subjects ?: emptyList()
        items.addAll(subjects.mapNotNull { subjectData ->
            val topics = subjectData.topics ?: return@mapNotNull null
            val topicCount = topics.size
            val modelCount = topics.sumOf { topic -> topic.models?.size ?: 0 }

            BrowserItem(
                type = BrowserItemType.SUBJECT,
                title = subjectData.name,
                subtitle = "$topicCount topics, $modelCount models",
                data = subjectData
            )
        })

        adapter.updateItems(items, getCurrentPath())
        updateUI(classData.Class, "Subjects")
    }

    private fun showTopics(subjectData: SubjectData) {
        navigationStack.add(NavigationLevel.SUBJECT(subjectData))

        val items = mutableListOf<BrowserItem>()
        items.add(createBackItem())

        val topics = subjectData.topics ?: emptyList()
        items.addAll(topics.mapNotNull { topicData ->
            val models = topicData.models ?: return@mapNotNull null
            val modelCount = models.size

            BrowserItem(
                type = BrowserItemType.TOPIC,
                title = topicData.name,
                subtitle = "$modelCount models",
                data = topicData
            )
        })

        adapter.updateItems(items, getCurrentPath())
        updateUI(subjectData.name, "Topics")
    }

    private fun showModels(topicData: TopicData) {
        navigationStack.add(NavigationLevel.TOPIC(topicData))

        val items = mutableListOf<BrowserItem>()
        items.add(createBackItem())

        val models = topicData.models ?: emptyList()
        items.addAll(models.map { modelData ->
            BrowserItem(
                type = BrowserItemType.MODEL,
                title = modelData.name,
                subtitle = "",
                modelPath = modelData.filename,
                thumbnailPath = modelData.thumbnail,
                data = modelData
            )
        })

        adapter.updateItems(items, getCurrentPath())
        updateUI(topicData.name, "Models")
    }

    private fun performSearch() {
        val query = searchEditText.text.toString().trim()
        if (query.isEmpty()) {
            clearSearch()
            return
        }

        currentSearchQuery = query
        isSearchMode = true

        val searchResults = searchModels(query)
        showSearchResults(searchResults)
        updateSearchUI()

        // Hide keyboard
        val inputManager = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        inputManager.hideSoftInputFromWindow(searchEditText.windowToken, 0)
    }

    private fun searchModels(query: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        classesData.forEach { classData ->
            classData.subjects?.forEach { subjectData ->
                subjectData.topics?.forEach { topicData ->
                    topicData.models?.forEach { modelData ->
                        if (modelData.name.contains(query, ignoreCase = true) ||
                            topicData.name.contains(query, ignoreCase = true) ||
                            subjectData.name.contains(query, ignoreCase = true) ||
                            classData.Class.contains(query, ignoreCase = true)) {

                            results.add(
                                SearchResult(
                                    model = modelData,
                                    classData = classData,
                                    subjectData = subjectData,
                                    topicData = topicData,
                                    path = "${classData.Class} > ${subjectData.name} > ${topicData.name}"
                                )
                            )
                        }
                    }
                }
            }
        }

        return results.sortedBy { it.model.name }
    }

    private fun showSearchResults(results: List<SearchResult>) {
        val items = mutableListOf<BrowserItem>()

        items.add(BrowserItem(
            type = BrowserItemType.BACK,
            title = "← Clear Search",
            subtitle = "Back to browse mode"
        ))

        items.addAll(results.map { result ->
            BrowserItem(
                type = BrowserItemType.MODEL,
                title = result.model.name,
                subtitle = result.path,
                modelPath = result.model.filename,
                thumbnailPath = result.model.thumbnail,
                data = result.model
            )
        })

        adapter.updateItems(items, "Search Results")
        updateUI("Search Results", "Found ${results.size} models for \"$currentSearchQuery\"")
    }

    private fun clearSearch() {
        isSearchMode = false
        currentSearchQuery = ""
        searchEditText.setText("")
        navigationStack.clear()
        showClasses()
        updateSearchUI()
    }

    private fun updateSearchUI() {
        if (isSearchMode) {
            dialogView.findViewById<View>(R.id.btn_clear_search).visibility = android.view.View.VISIBLE
            pathTextView.text = "Search Mode"
        } else {
            dialogView.findViewById<View>(R.id.btn_clear_search).visibility = android.view.View.GONE
        }
    }

    private fun createBackItem(): BrowserItem {
        return BrowserItem(
            type = BrowserItemType.BACK,
            title = "← Back",
            subtitle = ""
        )
    }

    private fun navigateBack() {
        if (navigationStack.isEmpty()) {
            bottomSheetDialog?.dismiss()
            return
        }

        navigationStack.removeLastOrNull()

        when {
            navigationStack.isEmpty() -> showClasses()
            navigationStack.size == 1 -> {
                val classLevel = navigationStack.last() as NavigationLevel.CLASS
                showSubjects(classLevel.data)
            }
            navigationStack.size == 2 -> {
                val subjectLevel = navigationStack.last() as NavigationLevel.SUBJECT
                showTopics(subjectLevel.data)
            }
            navigationStack.size == 3 -> {
                val topicLevel = navigationStack.last() as NavigationLevel.TOPIC
                showModels(topicLevel.data)
            }
        }
    }

    private fun getCurrentPath(): String {
        if (navigationStack.isEmpty()) return "Classes"

        return navigationStack.joinToString(" > ") { level ->
            when (level) {
                is NavigationLevel.CLASS -> level.data.Class
                is NavigationLevel.SUBJECT -> level.data.name
                is NavigationLevel.TOPIC -> level.data.name
            }
        }
    }

    private fun updateUI(title: String, pathSuffix: String) {
        titleTextView.text = title
        pathTextView.text = getCurrentPath()
    }

    fun dismiss() {
        bottomSheetDialog?.dismiss()
    }
}

sealed class NavigationLevel {
    data class CLASS(val data: ClassData) : NavigationLevel()
    data class SUBJECT(val data: SubjectData) : NavigationLevel()
    data class TOPIC(val data: TopicData) : NavigationLevel()
}

data class SearchResult(
    val model: ModelData,
    val classData: ClassData,
    val subjectData: SubjectData,
    val topicData: TopicData,
    val path: String
)