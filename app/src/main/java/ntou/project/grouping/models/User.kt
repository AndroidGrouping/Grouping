package ntou.project.grouping.models

data class User(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val bio: String = "",
    val friends: List<String> = emptyList(),
    val incomingRequests: List<String> = emptyList(), // 收到的好友請求 (UIDs)
    val outgoingRequests: List<String> = emptyList()  // 送出的好友請求 (UIDs)
)
