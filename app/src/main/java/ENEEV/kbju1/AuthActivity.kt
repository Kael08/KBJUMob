package ENEEV.kbju1

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit
import com.google.firebase.FirebaseApp
import com.google.gson.Gson
import okhttp3.*
import java.io.File
import java.io.IOException

class AuthActivity : AppCompatActivity() {
    private lateinit var phoneInput: EditText
    private lateinit var sendCodeButton: Button
    private lateinit var codeInput: EditText
    private lateinit var verifyCodeButton: Button

    private lateinit var auth: FirebaseAuth
    private var verificationId: String? = null

    private val handler = Handler(Looper.getMainLooper())


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContentView(R.layout.activity_auth)

        if(checkCurrentUser())
        {
            val intent = Intent(this,MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        phoneInput = findViewById(R.id.phoneNumberInput)
        sendCodeButton = findViewById(R.id.sendCodeButton)
        codeInput = findViewById(R.id.codeInput)
        verifyCodeButton = findViewById(R.id.verifyCodeButton)

        auth = FirebaseAuth.getInstance()

        sendCodeButton.setOnClickListener {
            val phoneNumber = phoneInput.text.toString().trim()
            BufferValues.phoneNumber=phoneNumber
            if (phoneNumber.isNotEmpty()) {
                sendVerificationCode(phoneNumber)
            } else {
                Toast.makeText(this, "Введите номер телефона", Toast.LENGTH_SHORT).show()
            }
        }

        verifyCodeButton.setOnClickListener {
            val code = codeInput.text.toString().trim()
            if (code.isNotEmpty() && verificationId != null) {
                verifyCode(code)
            } else {
                Toast.makeText(this, "Введите код подтверждения", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendVerificationCode(phoneNumber: String) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    signInWithCredential(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Log.e("FirebaseAuth", "Verification failed", e)
                    Toast.makeText(this@AuthActivity, "Ошибка верификации", Toast.LENGTH_SHORT).show()
                }

                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    this@AuthActivity.verificationId = verificationId
                    codeInput.visibility = View.VISIBLE
                    verifyCodeButton.visibility = View.VISIBLE
                    Toast.makeText(this@AuthActivity, "Код отправлен", Toast.LENGTH_SHORT).show()
                }
            })
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun verifyCode(code: String) {
        val credential = PhoneAuthProvider.getCredential(verificationId!!, code)
        signInWithCredential(credential)
    }

    private fun signInWithCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    auth.currentUser?.getIdToken(true)?.addOnSuccessListener { result ->
                        BufferValues.idToken = result.token
                        BufferValues.firebaseUID=auth.uid
                        Log.d("FirebaseAuth", "ID Token: ${BufferValues.idToken}")

                        sendLoginRequest { success ->
                            val intent: Intent
                            if (success) {
                                intent = Intent(this, MainActivity::class.java)
                            } else {
                                intent = Intent(this, DataInputActivity::class.java)
                            }
                            startActivity(intent)
                            finish()
                        }
                    }
                } else {
                    Toast.makeText(this, "Ошибка входа", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // Проверка на то, что ранее пользователь уже входил
    private fun checkCurrentUser(): Boolean {
        val file = File(applicationContext.filesDir,"current_user.json")

        if(!file.exists())
            return false

        if(file.readText().isEmpty())
            return false
        else {
            val gson = Gson()
            val jsonString = file.readText()
            val user=gson.fromJson(jsonString,User::class.java)
            BufferValues.currentUser=user
            return true
        }
    }

    private fun sendLoginRequest(callback: (Boolean) -> Unit) {
        val url = BufferValues.url+"/auth/login"
        val client = OkHttpClient()

        val idToken = BufferValues.idToken
        if (idToken.isNullOrEmpty()) {
            Log.e("AuthActivity", "Token is null or empty")
            Toast.makeText(this, "Ошибка авторизации: токен отсутствует", Toast.LENGTH_SHORT).show()
            System.exit(0)
            return
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", idToken)
            .post(RequestBody.create(null, ""))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post {
                    Toast.makeText(this@AuthActivity, "Ошибка авторизации", Toast.LENGTH_SHORT).show()
                    Log.e("AuthActivity", "Request failed", e)
                    System.exit(0)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
                    val gson = Gson()
                    val user = gson.fromJson(responseBody, User::class.java)
                    BufferValues.currentUser = user
                    val file = File(applicationContext.filesDir, "current_user.json")
                    file.writeText(gson.toJson(user) ?: "")

                    handler.post {
                        Toast.makeText(this@AuthActivity, "Авторизация успешна", Toast.LENGTH_SHORT).show()
                        callback(true)
                    }
                } else {
                    handler.post {
                        if (responseBody.isNullOrEmpty()) {
                            Toast.makeText(this@AuthActivity, "Требуется регистрация", Toast.LENGTH_SHORT).show()
                            callback(false)
                        } else {
                            Toast.makeText(this@AuthActivity, "Неверный токен. Завершение программы", Toast.LENGTH_SHORT).show()
                            Log.e("AuthActivity", "Request failed with code: ${response.code}")
                            System.exit(0)
                        }
                    }
                }
            }
        })
    }
}
