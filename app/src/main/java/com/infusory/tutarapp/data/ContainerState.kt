// ContainerStateData.kt
package com.infusory.tutarapp.ui.components

import com.infusory.tutarapp.ui.components.containers.ContainerBase

/**
 * Data class for saving and loading the complete state of all containers
 * Used for persistence and state management
 */
data class ContainerStateData(
    val containers: List<ContainerState>,
    val version: Int = CURRENT_VERSION,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any> = emptyMap()
) {
    companion object {
        const val CURRENT_VERSION = 1

        /**
         * Create an empty state
         */
        fun empty(): ContainerStateData = ContainerStateData(emptyList())

        /**
         * Create state with metadata
         */
        fun withMetadata(
            containers: List<ContainerState>,
            metadata: Map<String, Any>
        ): ContainerStateData = ContainerStateData(
            containers = containers,
            metadata = metadata
        )
    }

    /**
     * Get containers of a specific type
     */
    fun getContainersByType(type: ContainerBase.ContainerType): List<ContainerState> {
        return containers.filter { it.type == type }
    }

    /**
     * Get total number of containers
     */
    fun getContainerCount(): Int = containers.size

    /**
     * Check if state is empty
     */
    fun isEmpty(): Boolean = containers.isEmpty()

    /**
     * Check if state is valid (basic validation)
     */
    fun isValid(): Boolean {
        return version > 0 && containers.all { it.isValid() }
    }

    /**
     * Get summary information about the state
     */
    fun getSummary(): String {
        val typeCount = mutableMapOf<ContainerBase.ContainerType, Int>()
        containers.forEach { container ->
            typeCount[container.type] = typeCount.getOrDefault(container.type, 0) + 1
        }

        return buildString {
            append("Containers: ${containers.size}")
            if (typeCount.isNotEmpty()) {
                append(" (")
                typeCount.entries.joinToString(", ") { "${it.key.name}: ${it.value}" }
                append(")")
            }
        }
    }
}

/**
 * Data class representing the state of a single container
 * Contains all information needed to recreate the container
 */
data class ContainerState(
    val type: ContainerBase.ContainerType,
    val position: Pair<Float, Float>,
    val scale: Float,
    val size: Pair<Int, Int>,
    val customData: Map<String, Any> = emptyMap(),
    val id: String = generateId(),
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        private fun generateId(): String = "container_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}"

        /**
         * Create a basic container state with minimal data
         */
        fun basic(
            type: ContainerBase.ContainerType,
            x: Float,
            y: Float,
            width: Int,
            height: Int
        ): ContainerState = ContainerState(
            type = type,
            position = Pair(x, y),
            scale = 1.0f,
            size = Pair(width, height)
        )

        /**
         * Create container state with custom data
         */
        fun withCustomData(
            type: ContainerBase.ContainerType,
            x: Float,
            y: Float,
            width: Int,
            height: Int,
            scale: Float,
            customData: Map<String, Any>
        ): ContainerState = ContainerState(
            type = type,
            position = Pair(x, y),
            scale = scale,
            size = Pair(width, height),
            customData = customData
        )
    }

    /**
     * Validate the container state
     */
    fun isValid(): Boolean {
        return size.first > 0 &&
                size.second > 0 &&
                scale > 0.0f &&
                id.isNotEmpty()
    }

    /**
     * Get position as separate X and Y values
     */
    fun getX(): Float = position.first
    fun getY(): Float = position.second

    /**
     * Get size as separate width and height values
     */
    fun getWidth(): Int = size.first
    fun getHeight(): Int = size.second

    /**
     * Create a copy with updated position
     */
    fun withPosition(x: Float, y: Float): ContainerState = copy(position = Pair(x, y))

    /**
     * Create a copy with updated scale
     */
    fun withScale(newScale: Float): ContainerState = copy(scale = newScale)

    /**
     * Create a copy with updated size
     */
    fun withSize(width: Int, height: Int): ContainerState = copy(size = Pair(width, height))

    /**
     * Create a copy with additional custom data
     */
    fun withCustomData(additionalData: Map<String, Any>): ContainerState {
        val mergedData = customData.toMutableMap().apply { putAll(additionalData) }
        return copy(customData = mergedData)
    }

    /**
     * Get a specific custom data value
     */
    fun getCustomData(key: String): Any? = customData[key]

    /**
     * Get a specific custom data value with type casting
     */
    inline fun <reified T> getCustomData(key: String, defaultValue: T): T {
        return try {
            customData[key] as? T ?: defaultValue
        } catch (e: Exception) {
            defaultValue
        }
    }

    /**
     * Check if custom data contains a specific key
     */
    fun hasCustomData(key: String): Boolean = customData.containsKey(key)

    /**
     * Get display information about the container
     */
    fun getDisplayInfo(): String {
        return "${type.name} at (${position.first.toInt()}, ${position.second.toInt()}) " +
                "size ${size.first}x${size.second} scale ${String.format("%.1f", scale)}x"
    }
}

/**
 * Builder class for creating ContainerStateData with fluent API
 */
class ContainerStateBuilder {
    private val containers = mutableListOf<ContainerState>()
    private val metadata = mutableMapOf<String, Any>()

    fun addContainer(state: ContainerState): ContainerStateBuilder {
        containers.add(state)
        return this
    }

    fun addContainer(
        type: ContainerBase.ContainerType,
        x: Float,
        y: Float,
        width: Int,
        height: Int,
        scale: Float = 1.0f,
        customData: Map<String, Any> = emptyMap()
    ): ContainerStateBuilder {
        containers.add(ContainerState.withCustomData(type, x, y, width, height, scale, customData))
        return this
    }

    fun addMetadata(key: String, value: Any): ContainerStateBuilder {
        metadata[key] = value
        return this
    }

    fun addMetadata(data: Map<String, Any>): ContainerStateBuilder {
        metadata.putAll(data)
        return this
    }

    fun build(): ContainerStateData {
        return ContainerStateData.withMetadata(containers.toList(), metadata.toMap())
    }
}

/**
 * Extension functions for easier state management
 */

/**
 * Create a ContainerStateBuilder
 */
fun containerState(init: ContainerStateBuilder.() -> Unit): ContainerStateData {
    return ContainerStateBuilder().apply(init).build()
}

/**
 * Filter containers by type
 */
fun ContainerStateData.filterByType(type: ContainerBase.ContainerType): ContainerStateData {
    return copy(containers = containers.filter { it.type == type })
}

/**
 * Filter containers by position area
 */
fun ContainerStateData.filterByArea(
    minX: Float, minY: Float,
    maxX: Float, maxY: Float
): ContainerStateData {
    return copy(containers = containers.filter { container ->
        container.getX() >= minX && container.getX() <= maxX &&
                container.getY() >= minY && container.getY() <= maxY
    })
}

/**
 * Sort containers by creation time
 */
fun ContainerStateData.sortByTimestamp(): ContainerStateData {
    return copy(containers = containers.sortedBy { it.timestamp })
}

/**
 * Sort containers by position (top-left to bottom-right)
 */
fun ContainerStateData.sortByPosition(): ContainerStateData {
    return copy(containers = containers.sortedWith(compareBy<ContainerState> { it.getY() }.thenBy { it.getX() }))
}