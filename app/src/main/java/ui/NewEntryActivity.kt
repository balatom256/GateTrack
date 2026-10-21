package SmartSecurity.gatetrackapplication.ui.guard

import SmartSecurity.gatetrackapplication.data.model.EntryLog
import SmartSecurity.gatetrackapplication.databinding.ActivityNewEntryBinding
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NewEntryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewEntryBinding
    private val db = FirebaseFirestore.getInstance()

    private val types = listOf("VISITOR", "RESIDENT")
    private val directions = listOf("ENTRY", "EXIT")
    private val vehicleTypes = listOf("CAR", "MOTORCYCLE", "OTHER")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewEntryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.spType.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)
        binding.spDirection.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, directions)
        binding.spVehicleType.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, vehicleTypes)

        binding.cbVehicle.setOnCheckedChangeListener { _, checked ->
            binding.plateBox.visibility = if (checked) View.VISIBLE else View.GONE
            if (!checked) binding.etPlate.setText("")
        }
        binding.btnSave.setOnClickListener { save() }
    }

    private fun save() {
        val type = types[binding.spType.selectedItemPosition]
        val direction = directions[binding.spDirection.selectedItemPosition]
        val name = binding.etName.text.toString().trim()
        val idNumber = binding.etIdNumber.text.toString().trim()
        val hasVehicle = binding.cbVehicle.isChecked
        val plate = if (hasVehicle)
            binding.etPlate.text.toString().uppercase().replace(Regex("[^A-Z0-9]"), "")
        else ""
        val vehicleType =
            if (hasVehicle) vehicleTypes[binding.spVehicleType.selectedItemPosition] else ""
        val purpose = binding.etPurpose.text.toString().trim()
        val unit = binding.etUnit.text.toString().trim().uppercase()

        if (type == "VISITOR" && direction == "ENTRY" && name.isEmpty()) {
            toast("Enter the visitor's name")
            return
        }
        if (type == "VISITOR" && direction == "EXIT" && name.isEmpty() && plate.isEmpty()) {
            toast("Enter the visitor's name or plate to mark them as left")
            return
        }
        if (hasVehicle && plate.isEmpty()) {
            toast("Enter the plate number of the vehicle")
            return
        }

        // Visitor leaving: close their open record.
        if (direction == "EXIT" && type == "VISITOR") {
            setLoading(true)
            handleVisitorExit(name, plate)
            return
        }

        if (unit.isEmpty()) {
            toast("Enter the unit")
            return
        }

        setLoading(true)
        val guard = FirebaseAuth.getInstance().currentUser
        val base = EntryLog(
            entryType = type,
            direction = direction,
            visitorName = name,
            idNumber = idNumber,
            plateNumber = plate,
            vehicleType = vehicleType,
            purpose = purpose,
            unitNumber = unit,
            guardId = guard?.uid ?: "",
            guardName = guard?.email ?: ""
        )

        db.collection("users")
            .whereEqualTo("unitNumber", unit)
            .whereEqualTo("role", "RESIDENT")
            .get()
            .addOnSuccessListener { users ->
                val resident = users.documents.firstOrNull()
                if (resident == null) {
                    setLoading(false)
                    toast("No resident found for unit $unit")
                    return@addOnSuccessListener
                }
                val residentId = resident.id

                if (type == "RESIDENT") {
                    val presence = if (direction == "ENTRY") "INSIDE" else "OUT"
                    val rName = name.ifEmpty { resident.getString("fullName").orEmpty() }
                    resident.reference.update("presence", presence)
                        .addOnSuccessListener {
                            store(
                                base.copy(
                                    residentId = residentId,
                                    visitorName = rName,
                                    status = "APPROVED"
                                )
                            )
                        }
                        .addOnFailureListener {
                            setLoading(false)
                            toast("Error: ${it.message}")
                        }
                } else {
                    checkPreApproval(base.copy(residentId = residentId), residentId, name)
                }
            }
            .addOnFailureListener {
                setLoading(false)
                toast("Error: ${it.message}")
            }
    }

    private fun handleVisitorExit(name: String, plate: String) {
        db.collection("entryLogs")
            .whereEqualTo("direction", "ENTRY")
            .get()
            .addOnSuccessListener { snap ->
                val open = snap.documents.filter { d ->
                    d.getString("presence") != "LEFT" &&
                            d.getString("entryType") != "RESIDENT" &&
                            (
                                    (name.isNotEmpty() &&
                                            d.getString("visitorName").orEmpty().equals(name, true)) ||
                                            (plate.isNotEmpty() &&
                                                    d.getString("plateNumber").orEmpty()
                                                        .replace(Regex("[^A-Za-z0-9]"), "")
                                                        .equals(plate, true))
                                    )
                }
                val match = open.firstOrNull { it.getString("status") == "APPROVED" }
                if (match == null) {
                    setLoading(false)
                    toast(
                        if (open.isNotEmpty()) "That visitor is still waiting for approval"
                        else "Nobody inside matches that name or plate"
                    )
                    return@addOnSuccessListener
                }
                match.reference.update(
                    "presence", "LEFT",
                    "exitTime", FieldValue.serverTimestamp()
                )
                    .addOnSuccessListener {
                        toast("Marked as LEFT")
                        finish()
                    }
                    .addOnFailureListener {
                        setLoading(false)
                        toast("Could not update: ${it.message}")
                    }
            }
            .addOnFailureListener {
                setLoading(false)
                toast("Error: ${it.message}")
            }
    }

    private fun checkPreApproval(log: EntryLog, residentId: String, name: String) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        db.collection("preApprovals")
            .whereEqualTo("residentId", residentId)
            .whereEqualTo("used", false)
            .get()
            .addOnSuccessListener { result ->
                val match = result.documents.firstOrNull {
                    it.getString("visitorName").orEmpty().equals(name, ignoreCase = true) &&
                            it.getString("expectedDate").orEmpty().let { d -> d.isBlank() || d == today }
                }
                if (match != null) {
                    match.reference.update("used", true)
                    store(
                        log.copy(
                            status = "APPROVED",
                            preApproved = true,
                            presence = "INSIDE",
                            purpose = log.purpose.ifBlank { match.getString("purpose").orEmpty() }
                        )
                    )
                } else {
                    store(log.copy(status = "PENDING", preApproved = false))
                }
            }
            .addOnFailureListener {
                setLoading(false)
                toast("Error: ${it.message}")
            }
    }

    private fun store(log: EntryLog) {
        db.collection("entryLogs").add(log)
            .addOnSuccessListener {
                toast(
                    if (log.status == "PENDING") "Saved. Waiting for resident approval"
                    else "Entry recorded"
                )
                finish()
            }
            .addOnFailureListener {
                setLoading(false)
                toast("Could not save: ${it.message}")
            }
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !loading
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}