package ntou.project.grouping.models

import com.google.firebase.Timestamp

data class Post(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val tags: List<String> = emptyList(),
    val timestamp: Timestamp = Timestamp.now(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val locationName: String = "",
    val eventTime: String = "",
    val participants: List<String> = emptyList(),
    val maxParticipants: Int = 0 // 新增：人數上限 (0 代表不限人數)
)
