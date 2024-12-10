package ENEEV.kbju1

data class User(
    val id: Long,
    val name: String,
    val phoneNumber: String,
    val height: Double,
    val weight: Double,
    val age: Int,
    val firebaseUID: String,
    val goalID: Int,
    val genderID: Int,
    val activity_coef_id: Int
)