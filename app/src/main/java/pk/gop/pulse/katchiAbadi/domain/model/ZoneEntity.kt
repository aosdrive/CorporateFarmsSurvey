package pk.gop.pulse.katchiAbadi.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

// ============================================
// ZONE ENTITY
// ============================================
@Entity(tableName = "zones")
data class ZoneEntity(
    @PrimaryKey
    val zoneId: Long,
    val zoneName: String,
    val isActive: Boolean = true,
    val lastSynced: Long = System.currentTimeMillis()
)