package SmartSecurity.gatetrackapplication.ui.auth

import SmartSecurity.gatetrackapplication.databinding.ActivityLoginBinding
import SmartSecurity.gatetrackapplication.ui.admin.AdminDashboardActivity
import SmartSecurity.gatetrackapplication.ui.guard.GuardDashboardActivity
import SmartSecurity.gatetrackapplication.ui.resident.ResidentDashboardActivity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Already logged in? Skip straight to the right dashboard.
        val current = auth.currentUser
        if (current != null) {
            setLoading(true)
            routeByRole(current.uid)
            return
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                toast("Enter email and password")
                return@setOnClickListener
            }

            setLoading(true)
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    routeByRole(result.user!!.uid)
                }
                .addOnFailureListener {
                    setLoading(false)
                    toast("Login failed: ${it.message}")
                }
        }
    }

    private fun routeByRole(uid: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    failAndSignOut("No profile found for this account")
                    return@addOnSuccessListener
                }
                if (doc.getBoolean("active") == false) {
                    failAndSignOut("This account is disabled")
                    return@addOnSuccessListener
                }
                val role = doc.getString("role")?.trim()?.uppercase()
                if (role == "ADMIN") {
                    startActivity(Intent(this, AdminDashboardActivity::class.java))
                    finish()
                } else if (role == "GUARD") {
                    startActivity(Intent(this, GuardDashboardActivity::class.java))
                    finish()
                } else if (role == "RESIDENT") {
                    startActivity(Intent(this, ResidentDashboardActivity::class.java))
                    finish()
                } else {
                    failAndSignOut("Unknown role")
                }
            }
            .addOnFailureListener {
                failAndSignOut("Could not load profile: ${it.message}")
            }
    }

    private fun failAndSignOut(message: String) {
        auth.signOut()
        setLoading(false)
        toast(message)
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}