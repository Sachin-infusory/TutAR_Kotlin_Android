// ModelBrowserDrawer.kt - Bottom drawer for model browsing with Breadcrumb Navigation
package com.infusory.tutarapp.ui.models

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.recyclerview.widget.GridLayoutManager
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
    private lateinit var breadcrumbContainer: LinearLayout
    private lateinit var searchEditText: EditText
    private lateinit var dialogView: View

    private var classesData: List<ClassData> = emptyList()
    private val navigationStack = mutableListOf<NavigationLevel>()
    private var isSearchMode = false
    private var currentSearchQuery = ""

    // Dismiss listener
    private var onDismissListener: (() -> Unit)? = null

    fun show() {
        loadModelData()
        if (classesData.isNotEmpty()) {
            createAndShowDialog()
        } else {
            Toast.makeText(context, "class_data.json file missing", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadModelData() {
        try {
            val gson = Gson()
            val listType = object : TypeToken<List<ClassData>>() {}.type

            try {
                val inputStream = context.assets.open("class_data.json")
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
        bottomSheetDialog = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_model_browser, null)

        dialogView = view

        setupViews(view)
        setupRecyclerView()

        bottomSheetDialog?.setContentView(view)

        // Configure bottom sheet behavior
        bottomSheetDialog?.setOnShowListener { dialog ->
            val bottomSheet = (dialog as BottomSheetDialog).findViewById<android.widget.FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)
                val displayMetrics = context.resources.displayMetrics
                val height = (displayMetrics.heightPixels * 0.85).toInt()

                val layoutParams = it.layoutParams
                layoutParams.height = height
                it.layoutParams = layoutParams

                behavior.peekHeight = height
                behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                behavior.isDraggable = true
                behavior.isHideable = true
            }
        }

        // Set dismiss listener to notify when dialog is dismissed
        bottomSheetDialog?.setOnDismissListener {
            onDismissListener?.invoke()
        }

        bottomSheetDialog?.show()
        showClasses()
        updateSearchUI()
    }

    private fun setupViews(view: View) {
        breadcrumbContainer = view.findViewById(R.id.breadcrumb_container)
        recyclerView = view.findViewById(R.id.rv_content)
        searchEditText = view.findViewById(R.id.et_search)

        // Close button
        view.findViewById<View>(R.id.btn_close).setOnClickListener {
            bottomSheetDialog?.dismiss()
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
        recyclerView.layoutManager = GridLayoutManager(context, 2) // 2-column grid
        recyclerView.adapter = adapter
    }

    private fun handleItemClick(item: BrowserItem) {
        when (item.type) {
            BrowserItemType.CLASS -> {
                val classData = item.data as ClassData
                // Check if this class is already in the navigation stack
                val existingIndex = navigationStack.indexOfFirst {
                    it is NavigationLevel.CLASS && it.data.Class == classData.Class
                }
                if (existingIndex != -1) {
                    // Class already exists in stack, navigate to that position
                    while (navigationStack.size > existingIndex + 1) {
                        navigationStack.removeLast()
                    }
                    showSubjects(classData)
                } else {
                    // New class, add to stack
                    showSubjects(classData)
                }
            }
            BrowserItemType.SUBJECT -> {
                val subjectData = item.data as SubjectData
                // Check if this subject is already in the navigation stack
                val existingIndex = navigationStack.indexOfFirst {
                    it is NavigationLevel.SUBJECT && it.data.name == subjectData.name
                }
                if (existingIndex != -1) {
                    // Subject already exists in stack, navigate to that position
                    while (navigationStack.size > existingIndex + 1) {
                        navigationStack.removeLast()
                    }
                    showTopics(subjectData)
                } else {
                    // New subject, add to stack
                    showTopics(subjectData)
                }
            }
            BrowserItemType.TOPIC -> {
                val topicData = item.data as TopicData
                // Check if this topic is already in the navigation stack
                val existingIndex = navigationStack.indexOfFirst {
                    it is NavigationLevel.TOPIC && it.data.name == topicData.name
                }
                if (existingIndex != -1) {
                    // Topic already exists in stack, navigate to that position
                    while (navigationStack.size > existingIndex + 1) {
                        navigationStack.removeLast()
                    }
                    showModels(topicData)
                } else {
                    // New topic, add to stack
                    showModels(topicData)
                }
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
        updateBreadcrumbs()

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

        adapter.updateItems(items)
    }

    private fun showSubjects(classData: ClassData) {
        // Only add to navigation stack if it's not already there
        val classLevel = NavigationLevel.CLASS(classData)
        if (navigationStack.isEmpty() || navigationStack.last() != classLevel) {
            navigationStack.add(classLevel)
        }
        updateBreadcrumbs()

        val items = mutableListOf<BrowserItem>()

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

        adapter.updateItems(items)
    }

    private fun showTopics(subjectData: SubjectData) {
        // Only add to navigation stack if it's not already there
        val subjectLevel = NavigationLevel.SUBJECT(subjectData)
        if (navigationStack.isEmpty() || navigationStack.last() != subjectLevel) {
            navigationStack.add(subjectLevel)
        }
        updateBreadcrumbs()

        val items = mutableListOf<BrowserItem>()

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

        adapter.updateItems(items)
    }

    private fun showModels(topicData: TopicData) {
        // Only add to navigation stack if it's not already there
        val topicLevel = NavigationLevel.TOPIC(topicData)
        if (navigationStack.isEmpty() || navigationStack.last() != topicLevel) {
            navigationStack.add(topicLevel)
        }
        updateBreadcrumbs()

        val items = mutableListOf<BrowserItem>()

        val models = topicData.models ?: emptyList()
        items.addAll(models.map { modelData ->
            BrowserItem(
                type = BrowserItemType.MODEL,
                title = modelData.name,
                subtitle = "",
                modelPath = modelData.filename,
                thumbnailPath = modelData.thumbnail ?: "",
                data = modelData
            )
        })

        adapter.updateItems(items)
    }

    private fun updateBreadcrumbs() {
        breadcrumbContainer.removeAllViews()

        // Always add "Home" as first breadcrumb
        addBreadcrumb("Home", isLast = navigationStack.isEmpty()) {
            if (navigationStack.isNotEmpty()) {
                navigationStack.clear()
                showClasses()
            }
        }

        // Add navigation stack items
        navigationStack.forEachIndexed { index, item ->
            addBreadcrumbSeparator()

            val name = when (item) {
                is NavigationLevel.CLASS -> item.data.Class
                is NavigationLevel.SUBJECT -> item.data.name
                is NavigationLevel.TOPIC -> item.data.name
            }

            val isLast = index == navigationStack.size - 1
            addBreadcrumb(name, isLast) {
                // Navigate to this level by removing subsequent items
                while (navigationStack.size > index + 1) {
                    navigationStack.removeLast()
                }
                loadCurrentLevelData()
                updateBreadcrumbs()
            }
        }
    }

    private fun addBreadcrumb(text: String, isLast: Boolean = false, onClick: (() -> Unit)? = null) {
        val textView = TextView(context).apply {
            this.text = text
            setTextColor(
                if (isLast) context.getColor(android.R.color.darker_gray)
                else context.getColor(R.color.colorPrimary)
            )
            textSize = 14f
            if (!isLast) {
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            setPadding(16, 8, 16, 8)

            if (onClick != null && !isLast) {
                setOnClickListener { onClick() }
                background = context.getDrawable(android.R.drawable.list_selector_background)
            }
        }

        breadcrumbContainer.addView(textView)
    }

    private fun addBreadcrumbSeparator() {
        val separator = TextView(context).apply {
            text = " > "
            setTextColor(context.getColor(android.R.color.darker_gray))
            textSize = 14f
            setPadding(4, 8, 4, 8)
        }

        breadcrumbContainer.addView(separator)
    }

    private fun loadCurrentLevelData() {
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
        val inputManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
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
                thumbnailPath = result.model.thumbnail ?: "",
                data = result.model
            )
        })

        adapter.updateItems(items)

        // Update breadcrumbs for search
        breadcrumbContainer.removeAllViews()
        addBreadcrumb("Search Results for: \"$currentSearchQuery\"", true)
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
            dialogView.findViewById<View>(R.id.btn_clear_search).visibility = View.VISIBLE
        } else {
            dialogView.findViewById<View>(R.id.btn_clear_search).visibility = View.GONE
        }
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


    fun dismiss() {
        bottomSheetDialog?.dismiss()
    }

    /**
     * Set a listener to be called when the drawer is dismissed
     */
    fun setOnDismissListener(listener: () -> Unit) {
        onDismissListener = listener
    }
}

// Add equals and hashCode methods to NavigationLevel classes for proper comparison
sealed class NavigationLevel {
    data class CLASS(val data: ClassData) : NavigationLevel() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as CLASS
            return data.Class == other.data.Class
        }

        override fun hashCode(): Int {
            return data.Class.hashCode()
        }
    }

    data class SUBJECT(val data: SubjectData) : NavigationLevel() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as SUBJECT
            return data.name == other.data.name
        }

        override fun hashCode(): Int {
            return data.name.hashCode()
        }
    }

    data class TOPIC(val data: TopicData) : NavigationLevel() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TOPIC
            return data.name == other.data.name
        }

        override fun hashCode(): Int {
            return data.name.hashCode()
        }
    }
}

data class SearchResult(
    val model: ModelData,
    val classData: ClassData,
    val subjectData: SubjectData,
    val topicData: TopicData,
    val path: String
)