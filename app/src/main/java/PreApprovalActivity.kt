package SmartSecurity.gatetrackapplication.ui.resident

import SmartSecurity.gatetrackapplication.data.model.PreApproval
import SmartSecurity.gatetrackapplication.databinding.ActivityPreApprovalBinding
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PreApprovalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreApprovalBinding
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPreApprovalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.etDate.setText(
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        )
        binding.btnSave.setOnClickListener { save() }
    }

    private fun save() {
        val name = binding.etName.text.toString().trim()
        val purpose = binding.etPurpose.text.toString().trim()
        val date = binding.etDate.text.toString().trim()
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (name.isEmpty()) {
            toast("Enter the visitor's name")
            return
        }
        if (purpose.isEmpty()) {
            toast("Enter the purpose of the visit")
            return
        }
        if (uid == null) {
            toast("Not signed in")
            return
        }

        setLoading(true)
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val pre = PreApproval(
                    residentId = uid,
                    unitNumber = doc.getString("unitNumber").orEmpty(),
                    visitorName = name,
                    purpose = purpose,
                    expectedDate = date,
                    used = false
                )
                db.collection("preApprovals").add(pre)
                    .addOnSuccessListener {
                        toast("Pre-approval saved")
                        finish()
                    }
                    .addOnFailureListener {
                        setLoading(false)
                        toast("Could not save: ${it.message}")
                    }
            }
            .addOnFailureListener {
                setLoading(false)
                toast("Error: ${it.message}")
            }
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !loading
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}