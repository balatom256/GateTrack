package SmartSecurity.gatetrackapplication.ui.admin

import SmartSecurity.gatetrackapplication.databinding.ActivityAddUserBinding
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AddUserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddUserBinding
    private val roles = listOf("RESIDENT", "GUARD")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddUserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.spRole.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)
        binding.btnCreate.setOnClickListener { create() }
    }

    // A second Firebase app instance lets us create accounts
    // without signing the admin out.
    private fun secondaryAuth(): FirebaseAuth {
        val name = "secondary"
        val app = try {
            FirebaseApp.getInstance(name)
        } catch (e: IllegalStateException) {
            FirebaseApp.initializeApp(this, FirebaseOptions.fromResource(this)!!, name)
        }
        return FirebaseAuth.getInstance(app)
    }

    private fun norm(s: String) = s.uppercase().replace(Regex("[^A-Z0-9]"), "")

    private fun create() {
        val role = roles[binding.spRole.selectedItemPosition]
        val fullName = binding.etFullName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val phone = binding.etPhone.text.toString().trim()
        val unit = binding.etUnit.text.toString().trim()

        if (fullName.isEmpty() || email.isEmpty() || password.length < 6) {
            toast("Enter name, email and a password of at least 6 characters")
            return
        }
        if (role == "RESIDENT" && unit.isEmpty()) {
            toast("Enter the address for a resident")
            return
        }

        setLoading(true)
        val db = FirebaseFirestore.getInstance()

        val createAccount = {
            val auth = secondaryAuth()
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val uid = result.user!!.uid
                    val profile = hashMapOf(
                        "fullName" to fullName,
                        "email" to email,
                        "phone" to phone,
                        "role" to role,
                        "unitNumber" to (if (role == "RESIDENT") unit else "-"),
                        "active" to true
                    )
                    db.collection("users").document(uid).set(profile)
                        .addOnSuccessListener {
                            auth.signOut()
                            toast("$role account created")
                            finish()
                        }
                        .addOnFailureListener {
                            setLoading(false)
                            toast("Profile not saved: ${it.message}")
                        }
                }
                .addOnFailureListener {
                    setLoading(false)
                    toast("Could not create account: ${it.message}")
                }
        }

        if (role == "RESIDENT") {
            // Block duplicate addresses (ignores capitals, spaces and commas).
            db.collection("users").whereEqualTo("role", "RESIDENT").get()
                .addOnSuccessListener { snap ->
                    val taken = snap.documents.any {
                        norm(it.getString("unitNumber").orEmpty()) == norm(unit)
                    }
                    if (taken) {
                        setLoading(false)
                        toast("$unit already has a resident")
                    } else {
                        createAccount()
                    }
                }
                .addOnFailureListener {
                    setLoading(false)
                    toast("Error: ${it.message}")
                }
        } else {
            createAccount()
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnCreate.isEnabled = !loading
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}