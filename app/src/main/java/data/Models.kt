package SmartSecurity.gatetrackapplication.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

enum class Role { GUARD, RESIDENT, ADMIN }
enum class EntryType { VISITOR, VEHICLE, RESIDENT }
enum class Direction { ENTRY, EXIT }
enum class EntryStatus { PENDING, APPROVED, DENIED }

data class UserProfile(
    @DocumentId val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = Role.RESIDENT.name,
    val unitNumber: String = "",
    val active: Boolean = true,
    val presence: String = "OUT"
)

data class Visitor(
    @DocumentId val visitorId: String = "",
    val name: String = "",
    val idNumber: String = "",
    val phone: String = ""
)

data class Vehicle(
    @DocumentId val plateNumber: String = "",
    val vehicleType: String = "",
    val ownerName: String = ""
)

data class EntryLog(
    @DocumentId val logId: String = "",
    val entryType: String = EntryType.VISITOR.name,
    val direction: String = Direction.ENTRY.name,
    val visitorName: String = "",
    val idNumber: String = "",
    val plateNumber: String = "",
    val vehicleType: String = "",
    val purpose: String = "",
    val residentId: String = "",
    val unitNumber: String = "",
    val guardId: String = "",
    val guardName: String = "",
    val status: String = EntryStatus.PENDING.name,
    val preApproved: Boolean = false,
    val presence: String = "",
    @ServerTimestamp val timestamp: Date? = null,
    val exitTime: Date? = null
)

data class PreApproval(
    @DocumentId val id: String = "",
    val residentId: String = "",
    val unitNumber: String = "",
    val visitorName: String = "",
    val purpose: String = "",
    val expectedDate: String = "",
    val used: Boolean = false,
    @ServerTimestamp val createdAt: Date? = null
)