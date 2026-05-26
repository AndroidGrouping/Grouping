package ntou.project.grouping.models

data class User(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val bio: String = "", // 新增：自我介紹
    val friends: List<String> = emptyList() // List of friend UIDs
)
