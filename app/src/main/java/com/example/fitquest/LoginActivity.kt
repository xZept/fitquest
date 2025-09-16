package com.example.fitquest

import androidx.core.view.WindowInsetsCompat.Type
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import com.example.fitquest.repository.FitquestRepository
import kotlinx.coroutines.launch


class LoginActivity : AppCompatActivity() {

    private lateinit var repository: FitquestRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize repository
        repository = FitquestRepository(this)

        // hides the system navigation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.apply {
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsets.Type.navigationBars())
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE

            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
        }


        val edtUsername = findViewById<EditText>(R.id.editUsername)
        val edtPassword = findViewById<EditText>(R.id.editPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        val mainLayout = findViewById<View>(R.id.loginLayout)

        ViewCompat.setOnApplyWindowInsetsListener(mainLayout) { view, insets ->
            val systemInsets = insets.getInsets(Type.systemBars())
            view.setPadding(0, 0, 0, systemInsets.bottom)
            insets
        }

        btnLogin.setOnClickListener {
            val username = edtUsername.text.toString().trim()
            val password = edtPassword.text.toString().trim()

            Log.d("FitquestDB", "Attempting login with username=$username, password=$password")

            // User authentication
            lifecycleScope.launch {
                var userId = repository.authenticateUser(username, password)
                Log.d("FitquestDB", "authenticateUser() returned userId=$userId")

                if (userId != null) {
                    Toast.makeText(
                        this@LoginActivity,
                        "Login Successful!, User ID = $userId", // For debugging
                        Toast.LENGTH_SHORT
                    ).show()

                    val intent = Intent(this@LoginActivity, DashboardActivity::class.java)
                    intent.putExtra("userId", userId)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(
                        this@LoginActivity,
                        "User not found!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        val tvSignUpLink = findViewById<TextView>(R.id.tvSignUpLink)
        tvSignUpLink.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java) // replace with your actual SignUp activity
            startActivity(intent)
        }

    }
}
