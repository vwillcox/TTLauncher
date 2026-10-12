package app.ttlauncher.health

import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Today's figures. Any of them is null where it isn't allowed or nothing has recorded it. */
data class Vitals(
    val steps: Long? = null,
    /** The latest heart rate of the last day, in beats a minute, then today's lowest and highest. */
    val pulse: Long? = null,
    val pulseLow: Long? = null,
    val pulseHigh: Long? = null,
    /** Last night's sleep. */
    val sleepMinutes: Long? = null,
    /** Kilocalories burned by moving about, and then all of them, resting ones included. */
    val active: Double? = null,
    val burned: Double? = null,
    val metres: Double? = null,
    val floors: Double? = null,
    /** Time spent in recorded workouts today. */
    val exerciseMinutes: Long? = null,
    /** Today's average heart rate, and the resting rate of the last couple of days. */
    val pulseAverage: Long? = null,
    val pulseResting: Long? = null,
    /** The latest blood oxygen reading of the last couple of days, as a percentage. */
    val oxygen: Double? = null,
    /** Water drunk today, in litres. */
    val water: Double? = null,
    /** The latest weight of the last month, in kilograms. */
    val kilograms: Double? = null,
)

enum class HealthAccess { UNKNOWN, UNAVAILABLE, NOT_ALLOWED, ALLOWED }

/**
 * Reads from Health Connect, Android's own store of health data. Samsung Health puts a Galaxy Watch's
 * figures there, and Google Fit and Fitbit put theirs, so the one source covers all of them. The
 * figures are read while a widget showing them is on screen, kept only in memory, and sent nowhere.
 */
class HealthRepo(private val context: Context) {
    /** What the widget asks to read. Whatever part of it is allowed is what it shows. */
    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(FloorsClimbedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(HydrationRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
    )

    private val client by lazy { HealthConnectClient.getOrCreate(context) }
    private var readAt = 0L

    private val _vitals = MutableStateFlow<Vitals?>(null)
    val vitals: StateFlow<Vitals?> = _vitals.asStateFlow()
    private val _access = MutableStateFlow(HealthAccess.UNKNOWN)
    val access: StateFlow<HealthAccess> = _access.asStateFlow()
    private val _partial = MutableStateFlow(false)

    /** True while some of what the widget can show hasn't been allowed, so there is more to be had for the asking. */
    val partial: StateFlow<Boolean> = _partial.asStateFlow()

    /** Opens Health Connect's own page for what this app may read. */
    fun manageIntent(): Intent = HealthConnectClient.getHealthConnectManageDataIntent(context)

    suspend fun refresh(force: Boolean = false) {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            _access.value = HealthAccess.UNAVAILABLE
            return
        }
        val granted = runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
        fun allowed(record: kotlin.reflect.KClass<out androidx.health.connect.client.records.Record>) = HealthPermission.getReadPermission(record) in granted
        if (granted.none { it in permissions }) {
            _access.value = HealthAccess.NOT_ALLOWED
            _vitals.value = null
            return
        }
        _access.value = HealthAccess.ALLOWED
        _partial.value = !granted.containsAll(permissions)
        if (!force && System.currentTimeMillis() - readAt < STALE_AFTER_MS) return
        readAt = System.currentTimeMillis()

        val now = Instant.now()
        val midnight = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        val metrics = buildSet {
            if (allowed(StepsRecord::class)) add(StepsRecord.COUNT_TOTAL)
            if (allowed(DistanceRecord::class)) add(DistanceRecord.DISTANCE_TOTAL)
            if (allowed(ActiveCaloriesBurnedRecord::class)) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (allowed(TotalCaloriesBurnedRecord::class)) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
            if (allowed(HeartRateRecord::class)) {
                add(HeartRateRecord.BPM_MIN)
                add(HeartRateRecord.BPM_MAX)
                add(HeartRateRecord.BPM_AVG)
            }
            if (allowed(FloorsClimbedRecord::class)) add(FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL)
            if (allowed(ExerciseSessionRecord::class)) add(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)
            if (allowed(HydrationRecord::class)) add(HydrationRecord.VOLUME_TOTAL)
        }
        // Each is asked for on its own account, so one that fails leaves the rest standing.
        val today = if (metrics.isEmpty()) null else runCatching { client.aggregate(AggregateRequest(metrics, TimeRangeFilter.between(midnight, now))) }.getOrNull()
        // Last night began yesterday evening.
        val sleep = if (!allowed(SleepSessionRecord::class)) null else runCatching {
            client.aggregate(AggregateRequest(setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL), TimeRangeFilter.between(midnight.minus(Duration.ofHours(6)), now)))[SleepSessionRecord.SLEEP_DURATION_TOTAL]
        }.getOrNull()
        val pulse = if (!allowed(HeartRateRecord::class)) null else runCatching {
            client.readRecords(ReadRecordsRequest(HeartRateRecord::class, TimeRangeFilter.between(now.minus(Duration.ofDays(1)), now), ascendingOrder = false, pageSize = 1))
                .records.firstOrNull()?.samples?.maxByOrNull { it.time }?.beatsPerMinute
        }.getOrNull()
        val recently = TimeRangeFilter.between(now.minus(Duration.ofDays(2)), now)
        val resting = if (!allowed(RestingHeartRateRecord::class)) null else runCatching {
            client.aggregate(AggregateRequest(setOf(RestingHeartRateRecord.BPM_AVG), recently))[RestingHeartRateRecord.BPM_AVG]
        }.getOrNull()
        val oxygen = if (!allowed(OxygenSaturationRecord::class)) null else runCatching {
            client.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, recently, ascendingOrder = false, pageSize = 1)).records.firstOrNull()?.percentage?.value
        }.getOrNull()
        val weight = if (!allowed(WeightRecord::class)) null else runCatching {
            client.readRecords(ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.between(now.minus(Duration.ofDays(30)), now), ascendingOrder = false, pageSize = 1))
                .records.firstOrNull()?.weight?.inKilograms
        }.getOrNull()
        _vitals.value = Vitals(
            steps = today?.get(StepsRecord.COUNT_TOTAL),
            pulse = pulse,
            pulseLow = today?.get(HeartRateRecord.BPM_MIN),
            pulseHigh = today?.get(HeartRateRecord.BPM_MAX),
            sleepMinutes = sleep?.toMinutes()?.takeIf { it > 0 },
            active = today?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories,
            burned = today?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories,
            metres = today?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters,
            floors = today?.get(FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL)?.takeIf { it > 0 },
            exerciseMinutes = today?.get(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)?.toMinutes()?.takeIf { it > 0 },
            pulseAverage = today?.get(HeartRateRecord.BPM_AVG),
            pulseResting = resting,
            oxygen = oxygen,
            water = today?.get(HydrationRecord.VOLUME_TOTAL)?.inLiters?.takeIf { it > 0 },
            kilograms = weight,
        )
    }

    private companion object {
        const val STALE_AFTER_MS = 2 * 60 * 1000L
    }
}
