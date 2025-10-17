// ModelData.kt - Data classes for JSON structure
package com.infusory.tutarapp.ui.data

data class ClassData(
    val Class: String,
    val subjects: List<SubjectData>? = null,
    val version: Int? = null // For production structure
)

data class SubjectData(
    val name: String,
    val topics: List<TopicData>? = null,
    val models: List<ModelData>? = null // For demo structure
)

data class TopicData(
    val name: String,
    val thumbnail: String? = null,
    val models: List<ModelData>? = null
)

data class ModelData(
    val name: String,
    val filename: String,
    val thumbnail: String? = null
)