package ntou.project.grouping.models

import com.google.firebase.Timestamp

data class Post(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatarUrl: String = "", // 發布者大頭貼 URL
    val tags: List<String> = emptyList(),
    val timestamp: Timestamp = Timestamp.now(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val locationName: String = "",
    val eventTime: String = "",
    val eventEndTime: String = "", // 新增：活動結束時間
    val participants: List<String> = emptyList(),
    val maxParticipants: Int = 0 // 人數上限 (0 代表不限人數)
)
