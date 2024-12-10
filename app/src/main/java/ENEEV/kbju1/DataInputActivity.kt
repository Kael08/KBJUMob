package ENEEV.kbju1

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.math.BigDecimal

class DataInputActivity : AppCompatActivity() {
    private lateinit var nameInput: EditText
    private lateinit var heightInput: EditText
    private lateinit var weightInput: EditText
    private lateinit var ageInput: EditText
    private lateinit var genderRadioGroup: RadioGroup
    private lateinit var purposeRadioGroup: RadioGroup
    private lateinit var activityCoefficientRadioGroup: RadioGroup
    private lateinit var submitButton: Button

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var popupWindow: PopupWindow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_input)

        // Инициализация полей
        nameInput = findViewById(R.id.nameInput)
        heightInput = findViewById(R.id.heightInput)
        weightInput = findViewById(R.id.weightInput)
        ageInput = findViewById(R.id.ageInput)
        genderRadioGroup = findViewById(R.id.genderRadioGroup)
        purposeRadioGroup = findViewById(R.id.purposeRadioGroup)
        activityCoefficientRadioGroup = findViewById(R.id.activity_coefficientRadioGroup)
        submitButton = findViewById(R.id.submitButton)

        val ivHelpIc: ImageView = findViewById(R.id.helpIcon)

        ivHelpIc.setOnClickListener {
            showHelpPopup()
        }

        submitButton.setOnClickListener {
            if (validateFields()) {
                val name = nameInput.text.toString().trim()
                val height = heightInput.text.toString().toDouble()
                val weight = weightInput.text.toString().toDouble()
                val age = ageInput.text.toString().toInt()
                val gender = when (genderRadioGroup.checkedRadioButtonId) {
                    R.id.maleRadioButton -> 1
                    else -> 2
                }
                val purpose = when (purposeRadioGroup.checkedRadioButtonId) {
                    R.id.muscle_growthRadioButton -> 2
                    else -> 1
                }

                val activity_coef_id = when(activityCoefficientRadioGroup.checkedRadioButtonId){
                    R.id.minimumRadioButton->1
                    R.id.easyRadioButton->2
                    R.id.normalRadioButton->3
                    R.id.hardRadioButton->4
                    else -> 5
                }

                // Получение данных из BufferValues
                val phoneNumber = BufferValues.phoneNumber.orEmpty()
                val firebaseUID = BufferValues.firebaseUID.orEmpty()


                sendPostRequest(
                    phoneNumber = phoneNumber,
                    name = name,
                    height = height,
                    weight = weight,
                    age = age,
                    firebaseUID = firebaseUID,
                    goalID = purpose,
                    genderID = gender,
                    activity_coef_id=activity_coef_id
                ) { success ->
                    if (success) {
                        val intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        System.exit(0)
                    }
                }
            }
        }
    }
    private fun showHelpPopup() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView: View = inflater.inflate(R.layout.popup_help, null)

        popupWindow = PopupWindow(
            popupView,
            resources.getDimensionPixelSize(R.dimen.popup_width),
            resources.getDimensionPixelSize(R.dimen.popup_height),
            true
        )

        popupWindow.isOutsideTouchable = true
        popupWindow.isFocusable = true

        popupView.setOnClickListener {
            popupWindow.dismiss()
        }

        popupWindow.showAsDropDown(findViewById(R.id.helpIcon))
    }
    private fun sendPostRequest(
        phoneNumber: String,
        name: String,
        height: Double,
        weight: Double,
        age: Int,
        firebaseUID: String,
        goalID: Int,
        genderID: Int,
        activity_coef_id: Int,
        callback: (Boolean) -> Unit // Добавляем callback с параметром типа Boolean
    ) {
        val url = BufferValues.url+"/auth/registration"
        val client = OkHttpClient()

        // Формирование JSON
        val json = JSONObject().apply {
            put("phoneNumber", phoneNumber)
            put("name", name)
            put("height", height.toString())
            put("weight", weight.toString())
            put("age", age)
            put("firebaseUID", firebaseUID)
            put("goalID", goalID)
            put("genderID", genderID)
            put("activity_coef_id", activity_coef_id)
        }

        // Логирование JSON
        Log.d("Request JSON", json.toString())

        // Тело запроса
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)

        // Формирование запроса
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        // Отправка запроса
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Response", "Ошибка: ${e.message}", e)
                handler.post {
                    Toast.makeText(this@DataInputActivity, "Ошибка сети: ${e.message}", Toast.LENGTH_LONG).show()
                }
                callback(false) // Отправляем false в callback при ошибке
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("Response", "Code: ${response.code}, Body: $responseBody")

                if (response.isSuccessful) {
                    handler.post {
                        Toast.makeText(this@DataInputActivity, "Пользователь успешно добавлен", Toast.LENGTH_SHORT).show()
                        val gson = Gson()
                        val user = gson.fromJson(responseBody, User::class.java)
                        BufferValues.currentUser=user

                        val file = File(applicationContext.filesDir,"current_user.json")
                        file.writeText(gson.toJson(user)?: "")
                    }
                    callback(true) // Отправляем true в callback при успешном ответе
                } else {
                    handler.post {
                        Toast.makeText(
                            this@DataInputActivity,
                            "Ошибка: ${response.code} - ${response.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    callback(false) // Отправляем false в callback при ошибке
                }
            }
        })
    }



    private fun validateFields(): Boolean {
        val name = nameInput.text.toString().trim()
        val height = heightInput.text.toString().trim()
        val weight = weightInput.text.toString().trim()
        val age = ageInput.text.toString().trim()
        val gender = genderRadioGroup.checkedRadioButtonId
        val purpose = purposeRadioGroup.checkedRadioButtonId
        val activity_coef = activityCoefficientRadioGroup.checkedRadioButtonId

        return when {
            name.isEmpty() -> {
                showToast("Поле 'Имя' пустое")
                false
            }
            height.isEmpty() || !isPositiveNumber(height) -> {
                showToast("Поле 'Рост' некорректно")
                false
            }
            weight.isEmpty() || !isPositiveNumber(weight) -> {
                showToast("Поле 'Вес' некорректно")
                false
            }
            age.isEmpty() || !isPositiveNumber(age) -> {
                showToast("Поле 'Возраст' некорректно")
                false
            }
            gender == -1 -> {
                showToast("Пол не выбран")
                false
            }
            purpose == -1 -> {
                showToast("Цель не выбрана")
                false
            }
            activity_coef==-1->{
                showToast("Уровень активности не выбран")
                false
            }
            else -> true
        }
    }

    private fun isPositiveNumber(value: String): Boolean {
        return value.toDoubleOrNull()?.let { it > 0 } ?: false
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
