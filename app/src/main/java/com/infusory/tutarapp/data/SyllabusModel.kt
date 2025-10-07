// ModelData.kt - Data classes for JSON structure
package com.infusory.tutarapp.ui.data

data class ClassData(
    val Class: String,
    val subjects: List<SubjectData>
)

data class SubjectData(
    val name: String,
    val topics: List<TopicData>
)

data class TopicData(
    val name: String,
    val models: List<ModelData>
)

data class ModelData(
    val name: String,
    val filename: String,
    val thumbnail: String
)