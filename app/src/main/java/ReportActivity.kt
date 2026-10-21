package SmartSecurity.gatetrackapplication.ui.admin

import SmartSecurity.gatetrackapplication.data.model.EntryLog
import SmartSecurity.gatetrackapplication.databinding.ActivityReportBinding
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportBinding
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.etDate.setText(dayFmt.format(Date()))
        binding.btnGenerate.setOnClickListener { generate() }
        binding.btnShare.setOnClickListener { share() }
        generate()
    }

    private fun generate() {
        val dateText = binding.etDate.text.toString().trim()
        val day = try {
            dayFmt.parse(dateText)
        } catch (e: Exception) {
            null
        }
        if (day == null) {
            Toast.makeText(this, "Use the format yyyy-MM-dd", Toast.LENGTH_LONG).show()
            return
        }

        val cal = Calendar.getInstance().apply { time = day }
        val start = cal.time
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val end = cal.time

        binding.tvReport.text = "Loading..."
        FirebaseFirestore.getInstance().collection("entryLogs")
            .whereGreaterThanOrEqualTo("timestamp", start)
            .whereLessThan("timestamp", end)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.toObjects(EntryLog::class.java)
                    .sortedBy { it.timestamp?.time ?: 0L }
                binding.tvReport.text = build(dateText, list)
            }
            .addOnFailureListener {
                binding.tvReport.text = "Could not load: ${it.message}"
            }
    }

    private fun build(date: String, list: List<EntryLog>): String {
        val sb = StringBuilder()
        sb.append("GATETRACK DAILY REPORT\n")
        sb.append("Date: $date\n\n")
        sb.append("Total entries: ${list.size}\n")
        sb.append("Visitors: ${list.count { it.entryType == "VISITOR" }}\n")
        sb.append("Vehicles: ${list.count { it.entryType == "VEHICLE" }}\n")
        sb.append("Residents: ${list.count { it.entryType == "RESIDENT" }}\n")
        sb.append("Approved: ${list.count { it.status == "APPROVED" }}\n")
        sb.append("Denied: ${list.count { it.status == "DENIED" }}\n")
        sb.append("Pending: ${list.count { it.status == "PENDING" }}\n")
        sb.append("Pre-approved: ${list.count { it.preApproved }}\n\n")
        sb.append("DETAILS\n")
        if (list.isEmpty()) {
            sb.append("No gate activity on this date.\n")
        }
        list.forEach { e ->
            val who = e.visitorName.ifBlank { e.plateNumber.ifBlank { "Unknown" } }
            val time = e.timestamp?.let { timeFmt.format(it) } ?: "--:--"
            sb.append("$time  $who  ${e.entryType}/${e.direction}  Unit ${e.unitNumber}  ${e.status}\n")
        }
        return sb.toString()
    }

    private fun share() {
        val text = binding.tvReport.text.toString()
        if (text.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "GateTrack daily report")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Share report"))
    }
}