package ntou.project.grouping.models

data class User(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val friends: List<String> = emptyList() // List of friend UIDs
)
