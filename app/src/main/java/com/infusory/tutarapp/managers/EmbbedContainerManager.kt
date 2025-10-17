package com.infusory.tutarapp.managers

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.*

data class EmbedContainerState(
    val leftMargin: Int = 0,
    val topMargin: Int = 0,
    val width: Int = 600,
    val height: Int = 400,
    val searchQuery: String = "",
    val urls: List<String> = emptyList(),
    val isInteractMode: Boolean = false,
    val containerId: String = "",
    val containerType: String = "", // "youtube" or "website"
    val isFullScreen: Boolean = false,
    val aspectRatio: Float = 16f/9f,
    val currentContentType: String = "NONE", // "NONE", "YOUTUBE", "WEBSITE"
    val currentUrl: String = ""
)


class EmbedContainerManager(private val context: Context, private val id: String) {
    private val fileName = "embed_container_state_$id.json"
    private var state = EmbedContainerState(containerId = id)
    private val urls = mutableListOf<String>()

    companion object {
        private val allEmbedContainers = mutableMapOf<String, EmbedContainerState>()
        private const val TAG = "EmbedContainerManager"

        // Container creation and management functions
        fun createNewEmbedContainerState(context: Context, containerType: String = ""): EmbedContainerState {
            val containerId = generateEmbedContainerId()
            val staggeredOffset = allEmbedContainers.size * 50

            Log.d(TAG, "Creating new embed container state: type=$containerType, id=$containerId, offset=$staggeredOffset")

            return EmbedContainerState(
                leftMargin = 100 + staggeredOffset,
                topMargin = 150 + staggeredOffset,
                width = 700,
                height = 500,
                containerId = containerId,
                containerType = containerType,
                currentContentType = when (containerType) {
                    "youtube" -> "YOUTUBE"
                    "website" -> "WEBSITE"
                    else -> "NONE"
                }
            )
        }

        fun createEmbedContainerStateFromEmbedConfig(
            embedConfig: JSONObject,
            embedId: String,
            embedType: String,
            videoId: String,
            inputValue: String
        ): EmbedContainerState {
            Log.d(TAG, "Creating container from embed config: id=$embedId, type=$embedType, videoId=$videoId")

            val x = embedConfig.getInt("x")
            val y = embedConfig.getInt("y")
            val width = embedConfig.getInt("width")
            val height = embedConfig.getInt("height")

            val containerType = when (embedType) {
                "youtube" -> "youtube"
                "web" -> "website"
                else -> "website"
            }

            val currentContentType = when (embedType) {
                "youtube" -> "YOUTUBE"
                "web" -> "WEBSITE"
                else -> "WEBSITE"
            }

            val currentUrl = when (embedType) {
                "youtube" -> if (videoId.startsWith("http")) videoId else "https://youtube.com/watch?v=$videoId"
                "web" -> if (videoId.startsWith("http")) videoId else "https://$videoId"
                else -> videoId
            }

            Log.d(TAG, "Parsed embed config: x=$x, y=$y, width=$width, height=$height, url=$currentUrl")

            return EmbedContainerState(
                leftMargin = x,
                topMargin = y,
                width = width,
                height = height,
                searchQuery = inputValue,
                containerId = embedId,
                containerType = containerType,
                currentContentType = currentContentType,
                currentUrl = currentUrl,
                urls = listOf(currentUrl)
            )
        }

        private fun generateEmbedContainerId(): String {
            return "embed_container_${UUID.randomUUID().toString().substring(0, 8)}"
        }

        fun addEmbedContainerState(context: Context, state: EmbedContainerState) {
            Log.d(TAG, "Adding embed container state: id=${state.containerId}, type=${state.containerType}")
            Log.d(TAG, "Container details: position=(${state.leftMargin}, ${state.topMargin}), size=${state.width}x${state.height}")
            Log.d(TAG, "Content: url=${state.currentUrl}, search=${state.searchQuery}, fullscreen=${state.isFullScreen}")

            allEmbedContainers[state.containerId] = state
            saveIndividualEmbedContainerState(context, state)
            saveGlobalState(context)
        }

        fun removeEmbedContainerState(context: Context, containerId: String) {
            Log.d(TAG, "Removing embed container state: id=$containerId")
            allEmbedContainers.remove(containerId)
            // Remove individual container file
            val fileName = "embed_container_state_$containerId.json"
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Deleted individual container file: $fileName")
            }
            saveGlobalState(context)
        }

        fun updateEmbedContainerState(context: Context, state: EmbedContainerState) {
            Log.d(TAG, "Updating embed container state: id=${state.containerId}")
            Log.d(TAG, "Updated details: position=(${state.leftMargin}, ${state.topMargin}), size=${state.width}x${state.height}")

            allEmbedContainers[state.containerId] = state
            saveIndividualEmbedContainerState(context, state)
            saveGlobalState(context)
        }

        fun getEmbedContainerState(context: Context, containerId: String): EmbedContainerState? {
            Log.d(TAG, "Getting embed container state: id=$containerId")
            return allEmbedContainers[containerId] ?: loadIndividualEmbedContainerState(context, containerId)
        }

        fun clearAllEmbedContainers(context: Context) {
            Log.d(TAG, "Clearing all embed containers. Count: ${allEmbedContainers.size}")
            allEmbedContainers.clear()
            // Clear all individual embed container files
            val filesDir = context.filesDir
            filesDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("embed_container_state_") || file.name == "all_embed_containers_state.json") {
                    file.delete()
                    Log.d(TAG, "Deleted container file: ${file.name}")
                }
            }
        }

        fun getAllEmbedContainers(context: Context): Map<String, List<EmbedContainerState>> {
            Log.d(TAG, "Getting all embed containers")
            // Load from global state first to ensure we have all containers
            loadGlobalState(context)

            val youtubeContainers = mutableListOf<EmbedContainerState>()
            val websiteContainers = mutableListOf<EmbedContainerState>()

            allEmbedContainers.values.forEach { state ->
                when (state.containerType) {
                    "youtube" -> youtubeContainers.add(state)
                    "website" -> websiteContainers.add(state)
                }
            }

            Log.d(TAG, "Found ${youtubeContainers.size} YouTube containers and ${websiteContainers.size} website containers")
            return mapOf(
                "youtube" to youtubeContainers,
                "website" to websiteContainers
            )
        }

        private fun saveIndividualEmbedContainerState(context: Context, state: EmbedContainerState) {
            try {
                val fileName = "embed_container_state_${state.containerId}.json"
                val json = JSONObject().apply {
                    put("leftMargin", state.leftMargin)
                    put("topMargin", state.topMargin)
                    put("width", state.width)
                    put("height", state.height)
                    put("searchQuery", state.searchQuery)
                    put("isInteractMode", state.isInteractMode)
                    put("containerId", state.containerId)
                    put("containerType", state.containerType)
                    put("isFullScreen", state.isFullScreen)
                    put("aspectRatio", state.aspectRatio)
                    put("currentContentType", state.currentContentType)
                    put("currentUrl", state.currentUrl)
                    put("urls", JSONArray().apply {
                        state.urls.forEach { put(it) }
                    })
                }

                Log.d(TAG, "Saving individual container state to: $fileName")
                Log.d(TAG, "JSON content: ${json.toString(2)}") // Pretty print for debugging

                context.openFileOutput(fileName, Context.MODE_PRIVATE).use {
                    it.write(json.toString().toByteArray())
                }
                Log.d(TAG, "Successfully saved individual container state")
            } catch (e: IOException) {
                Log.e(TAG, "Error saving individual container state", e)
            }
        }

        private fun loadIndividualEmbedContainerState(context: Context, containerId: String): EmbedContainerState? {
            try {
                val fileName = "embed_container_state_$containerId.json"
                val file = File(context.filesDir, fileName)
                if (!file.exists()) {
                    Log.d(TAG, "Individual container file not found: $fileName")
                    return null
                }

                Log.d(TAG, "Loading individual container state from: $fileName")
                val jsonString = context.openFileInput(fileName).bufferedReader().use { it.readText() }
                val json = JSONObject(jsonString)

                Log.d(TAG, "Loaded JSON: ${json.toString(2)}")

                val urlsList = mutableListOf<String>()
                val jsonUrls = json.getJSONArray("urls")
                for (i in 0 until jsonUrls.length()) {
                    urlsList.add(jsonUrls.getString(i))
                }

                val state = EmbedContainerState(
                    leftMargin = json.getInt("leftMargin"),
                    topMargin = json.getInt("topMargin"),
                    width = json.getInt("width"),
                    height = json.getInt("height"),
                    searchQuery = json.getString("searchQuery"),
                    urls = urlsList,
                    isInteractMode = json.getBoolean("isInteractMode"),
                    containerId = json.getString("containerId"),
                    containerType = json.getString("containerType"),
                    isFullScreen = json.getBoolean("isFullScreen"),
                    aspectRatio = json.getDouble("aspectRatio").toFloat(),
                    currentContentType = json.getString("currentContentType"),
                    currentUrl = json.getString("currentUrl")
                )

                Log.d(TAG, "Successfully loaded container state: $state")
                return state
            } catch (e: IOException) {
                Log.e(TAG, "Error loading individual container state", e)
                return null
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error loading container state", e)
                return null
            }
        }

        private fun saveGlobalState(context: Context) {
            try {
                val jsonArray = JSONArray()
                allEmbedContainers.values.forEach { state ->
                    jsonArray.put(JSONObject().apply {
                        put("leftMargin", state.leftMargin)
                        put("topMargin", state.topMargin)
                        put("width", state.width)
                        put("height", state.height)
                        put("searchQuery", state.searchQuery)
                        put("isInteractMode", state.isInteractMode)
                        put("containerId", state.containerId)
                        put("containerType", state.containerType)
                        put("isFullScreen", state.isFullScreen)
                        put("aspectRatio", state.aspectRatio)
                        put("currentContentType", state.currentContentType)
                        put("currentUrl", state.currentUrl)
                        put("urls", JSONArray().apply {
                            state.urls.forEach { put(it) }
                        })
                    })
                }

                val globalJson = JSONObject().apply {
                    put("allEmbedContainers", jsonArray)
                }

                Log.d(TAG, "Saving global state with ${allEmbedContainers.size} containers")
                Log.d(TAG, "Global state JSON: ${globalJson.toString(2)}")

                context.openFileOutput("all_embed_containers_state.json", Context.MODE_PRIVATE).use {
                    it.write(globalJson.toString().toByteArray())
                }
                Log.d(TAG, "Successfully saved global state")
            } catch (e: IOException) {
                Log.e(TAG, "Error saving global state", e)
            }
        }

        private fun loadGlobalState(context: Context) {
            try {
                val file = File(context.filesDir, "all_embed_containers_state.json")
                if (!file.exists()) {
                    Log.d(TAG, "Global state file not found")
                    return
                }

                Log.d(TAG, "Loading global state")
                val jsonString = context.openFileInput("all_embed_containers_state.json").bufferedReader().use { it.readText() }
                val json = JSONObject(jsonString)
                val containersArray = json.getJSONArray("allEmbedContainers")

                Log.d(TAG, "Found ${containersArray.length()} containers in global state")

                for (i in 0 until containersArray.length()) {
                    val containerJson = containersArray.getJSONObject(i)
                    val containerId = containerJson.getString("containerId")

                    val urlsList = mutableListOf<String>()
                    val jsonUrls = containerJson.getJSONArray("urls")
                    for (j in 0 until jsonUrls.length()) {
                        urlsList.add(jsonUrls.getString(j))
                    }

                    val embedContainerState = EmbedContainerState(
                        leftMargin = containerJson.getInt("leftMargin"),
                        topMargin = containerJson.getInt("topMargin"),
                        width = containerJson.getInt("width"),
                        height = containerJson.getInt("height"),
                        searchQuery = containerJson.getString("searchQuery"),
                        urls = urlsList,
                        isInteractMode = containerJson.getBoolean("isInteractMode"),
                        containerId = containerId,
                        containerType = containerJson.getString("containerType"),
                        isFullScreen = containerJson.getBoolean("isFullScreen"),
                        aspectRatio = containerJson.getDouble("aspectRatio").toFloat(),
                        currentContentType = containerJson.getString("currentContentType"),
                        currentUrl = containerJson.getString("currentUrl")
                    )

                    allEmbedContainers[containerId] = embedContainerState
                    Log.d(TAG, "Loaded container: id=$containerId, type=${embedContainerState.containerType}")
                }
                Log.d(TAG, "Successfully loaded ${allEmbedContainers.size} containers from global state")
            } catch (e: IOException) {
                Log.e(TAG, "Error loading global state", e)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error loading global state", e)
            }
        }
    }

    init {
        allEmbedContainers[id] = state
        Log.d(TAG, "Initialized EmbedContainerManager for container: $id")
    }

    fun setState(newState: EmbedContainerState) {
        Log.d(TAG, "Setting state for container: $id")
        state = newState.copy(urls = urls, containerId = id)
        allEmbedContainers[id] = state
        saveState()
        saveGlobalState()
    }

    fun getState(): EmbedContainerState {
        Log.d(TAG, "Getting state for container: $id")
        return state
    }

    fun addUrl(url: String) {
        Log.d(TAG, "Adding URL to container $id: $url")
        urls.add(url)
        // Determine container type based on URL
        state = state.copy(
            containerType = if (isYouTubeUrl(url)) "youtube" else "website",
            currentContentType = if (isYouTubeUrl(url)) "YOUTUBE" else "WEBSITE",
            currentUrl = url
        )
        allEmbedContainers[id] = state
        saveState()
        saveGlobalState()
    }

    fun getUrls(): List<String> {
        Log.d(TAG, "Getting URLs for container: $id, count: ${urls.size}")
        return urls.toList()
    }

    private fun isYouTubeUrl(url: String): Boolean {
        val patterns = arrayOf(
            "^(https?://)?(www\\.)?(youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/embed/|youtube\\.com/shorts/).+",
            "^(https?://)?(www\\.)?youtube\\.com/live/.+"
        )
        val isYouTube = patterns.any { pattern ->
            java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(url).matches()
        }
        Log.d(TAG, "URL $url is YouTube: $isYouTube")
        return isYouTube
    }

    private fun saveState() {
        try {
            val json = JSONObject().apply {
                put("leftMargin", state.leftMargin)
                put("topMargin", state.topMargin)
                put("width", state.width)
                put("height", state.height)
                put("searchQuery", state.searchQuery)
                put("isInteractMode", state.isInteractMode)
                put("containerId", state.containerId)
                put("containerType", state.containerType)
                put("isFullScreen", state.isFullScreen)
                put("aspectRatio", state.aspectRatio)
                put("currentContentType", state.currentContentType)
                put("currentUrl", state.currentUrl)
                put("urls", JSONArray().apply {
                    urls.forEach { put(it) }
                })
            }
            Log.d(TAG, "Saving instance state to: $fileName")
            Log.d(TAG, "Instance state JSON: ${json.toString(2)}")

            context.openFileOutput(fileName, Context.MODE_PRIVATE).use {
                it.write(json.toString().toByteArray())
            }
            Log.d(TAG, "Successfully saved instance state")
        } catch (e: IOException) {
            Log.e(TAG, "Error saving instance state", e)
        }
    }

    private fun saveGlobalState() {
        Companion.saveGlobalState(context)
    }

    fun loadState(): EmbedContainerState? {
        try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) {
                Log.d(TAG, "Instance state file not found: $fileName")
                return null
            }

            Log.d(TAG, "Loading instance state from: $fileName")
            val jsonString = context.openFileInput(fileName).bufferedReader().use { it.readText() }
            val json = JSONObject(jsonString)
            urls.clear()
            val jsonUrls = json.getJSONArray("urls")
            for (i in 0 until jsonUrls.length()) {
                urls.add(jsonUrls.getString(i))
            }
            state = EmbedContainerState(
                leftMargin = json.getInt("leftMargin"),
                topMargin = json.getInt("topMargin"),
                width = json.getInt("width"),
                height = json.getInt("height"),
                searchQuery = json.getString("searchQuery"),
                urls = urls.toList(),
                isInteractMode = json.getBoolean("isInteractMode"),
                containerId = json.getString("containerId"),
                containerType = json.getString("containerType"),
                isFullScreen = json.getBoolean("isFullScreen"),
                aspectRatio = json.getDouble("aspectRatio").toFloat(),
                currentContentType = json.getString("currentContentType"),
                currentUrl = json.getString("currentUrl")
            )
            allEmbedContainers[id] = state
            Log.d(TAG, "Successfully loaded instance state: $state")
            return state
        } catch (e: IOException) {
            Log.e(TAG, "Error loading instance state", e)
            return null
        }
    }

    fun loadGlobalState() {
        Companion.loadGlobalState(context)
    }

    fun clearState() {
        Log.d(TAG, "Clearing state for container: $id")
        allEmbedContainers.remove(id)
        val file = File(context.filesDir, fileName)
        if (file.exists()) {
            file.delete()
            Log.d(TAG, "Deleted instance state file: $fileName")
        }
        saveGlobalState()
    }

    fun removeEmbedContainer() {
        clearState()
    }
}