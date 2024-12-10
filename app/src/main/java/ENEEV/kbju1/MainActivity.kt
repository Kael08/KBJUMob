package ENEEV.kbju1

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import java.io.File
import java.io.IOException
import java.nio.Buffer
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var sign_out_button: Button
    private lateinit var add_product_button: ImageView
    private lateinit var popupWindow: PopupWindow
    private lateinit var product_name_textView: TextView
    private lateinit var product_kbju_textView: TextView
    private lateinit var kbju_result_textView: TextView
    private var kbju_max = 0.0

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val user = BufferValues.currentUser
        sign_out_button = findViewById(R.id.sign_out_button)
        add_product_button = findViewById(R.id.productsIcon)
        product_name_textView=findViewById(R.id.productNameTextView)
        product_kbju_textView=findViewById(R.id.productKBJUTextView)
        kbju_result_textView=findViewById(R.id.kbjuResult)

        sign_out_button.setOnClickListener {
            val file = File(applicationContext.filesDir, "current_user.json")
            file.writeText("")
            BufferValues.currentUser = null
            val intent = Intent(this, AuthActivity::class.java)
            startActivity(intent)
            finish()
        }

        add_product_button.setOnClickListener {
            showProductsPopup()
        }

        val activity_coef = user?.activity_coef_id?.let {
            when (it) {
                1 -> 1.2
                2 -> 1.3
                3 -> 1.5
                4 -> 1.7
                else -> 1.9
            }
        } ?: 1.2

        if (user != null) {
            kbju_max = if (user.genderID == 1) {
                ((9.99 * user.weight) + (6.25 * user.height) - (4.92 * user.age) + 5) * activity_coef
            } else {
                ((9.99 * user.weight) + (6.25 * user.height) - (4.92 * user.age) - 161) * activity_coef
            }
        }

        val currentDateTextView: TextView = findViewById(R.id.currentDate)
        currentDateTextView.text = currentDateTextView.text.toString() + getCurrentDate()

        val helloUserTextView: TextView = findViewById(R.id.helloUser)
        helloUserTextView.text = "${user?.name ?: "Пользователь"}" + helloUserTextView.text.toString()
    }

    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val date = Date()
        return dateFormat.format(date)
    }

    private fun parseProducts(json: String): List<Product> {
        val gson = Gson()
        val type = object : TypeToken<List<Product>>() {}.type
        return gson.fromJson(json, type)
    }

    private fun showProductsPopup() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView: View = inflater.inflate(R.layout.popup_products, null)

        popupWindow = PopupWindow(
            popupView,
            resources.getDimensionPixelSize(R.dimen.popup_width),
            resources.getDimensionPixelSize(R.dimen.popup_height),
            true
        )

        popupWindow.isOutsideTouchable = true
        popupWindow.isFocusable = true

        // Проверяем, есть ли уже данные в BufferValues.products
        if (BufferValues.products != null) {
            // Если данные есть, отображаем их
            displayProducts(popupView, BufferValues.products!!)
        } else {
            // Если данных нет, отправляем запрос на сервер
            sendGetRequest { success ->
                if (success) {
                    // Если запрос успешен, отображаем данные
                    displayProducts(popupView, BufferValues.products!!)
                } else {
                    // Если запрос не удался, показываем сообщение об ошибке
                    Handler(Looper.getMainLooper()).post {
                        popupWindow.dismiss()
                        // Показываем сообщение об ошибке (например, через Toast)
                        showToast("Ошибка загрузки продуктов")
                    }
                }
            }
        }
    }

    private fun displayProducts(popupView: View, products: List<Product>) {
        runOnUiThread {
            val productsRecyclerView: RecyclerView = popupView.findViewById(R.id.productsRecyclerView)
            productsRecyclerView.layoutManager = LinearLayoutManager(this)
            productsRecyclerView.adapter = ProductAdapter(products) { product ->
                popupWindow.dismiss()
                handleSelectedProduct(product)
            }
            popupWindow.showAsDropDown(findViewById(R.id.productsIcon))
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun handleSelectedProduct(product: Product) {
        // Добавление продукта в активность
        product_name_textView.text = product.name
        product_kbju_textView.text = "Ккал:"+product.calories+" Б:"+product.proteins+" Ж:"+product.fats+" У:"+product.carbs

        // Суммирование кбжу выбранного продукта с кбжу пользователя
        BufferValues.calories+=product.calories
        BufferValues.proteins+=product.proteins
        BufferValues.fats+=product.fats
        BufferValues.carbs+=product.carbs

        // Обновление результата кбжу за день
        kbju_result_textView.text="Каллории: "+(BufferValues.calories-kbju_max)+" Белки:"+BufferValues.proteins+" Жиры:"+BufferValues.fats+" Углеводы:"+BufferValues.carbs
    }

    private fun sendGetRequest(callback: (Boolean) -> Unit) {
        val url = BufferValues.url+"/products"
        val client = OkHttpClient()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    BufferValues.products = parseProducts(response.body?.string().toString())
                    callback(true)
                } else {
                    callback(false)
                }

            }
        })
    }


}