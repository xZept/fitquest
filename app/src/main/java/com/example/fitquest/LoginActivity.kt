package com.example.fitquest

import android.content.Context // <-- ADDED: Needed for SharedPreferences
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
import androidx.core.view.WindowInsetsCompat.Type
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

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter username and password.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Log.d("FitquestDB", "Attempting login with username=$username, password=$password")

            lifecycleScope.launch {
                val userId = repository.authenticateUser(username, password)
                Log.d("FitquestDB", "authenticateUser() returned userId=$userId")

                if (userId != null) {
                    // --- START OF FIX ---

                    // 1. Save the logged-in user's ID to SharedPreferences.
                    //    This makes it available to all other activities, like ProfileActivity.
                    val sharedPref = getSharedPreferences("FitQuestPrefs", Context.MODE_PRIVATE)
                    with(sharedPref.edit()) {
                        putLong("LOGGED_IN_USER_ID", userId.toLong()) // Save as Long
                        apply()
                    }
                    Log.d("FitquestDB", "Login successful. Saved user ID $userId to SharedPreferences.")

                    // 2. Show success message and navigate to the dashboard.
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "Login Successful!", Toast.LENGTH_SHORT).show()
                    }

                    val intent = Intent(this@LoginActivity, DashboardActivity::class.java)
                    startActivity(intent)
                    finish()

                    // --- END OF FIX ---

                } else {
                    runOnUiThread {
                        Toast.makeText(this@LoginActivity, "Invalid username or password.", Toast.LENGTH_SHORT).show()
                    }
                    Log.d("FitquestDB", "Login failed for username: $username")
                }
            }
        }

        val tvSignUpLink = findViewById<TextView>(R.id.tvSignUpLink)
        tvSignUpLink.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }
}
