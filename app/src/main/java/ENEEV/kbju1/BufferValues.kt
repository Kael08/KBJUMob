package ENEEV.kbju1

import com.google.gson.Gson

object BufferValues {
    var idToken: String? = null
    var phoneNumber: String? = null
    var firebaseUID: String? = null
    var currentUser: User? = null

    var products: List<Product>?=null

    var url = "http://192.168.0.174:8080"

    var proteins =0.0
    var fats= 0.0
    var carbs =0.0
    var calories =0
}