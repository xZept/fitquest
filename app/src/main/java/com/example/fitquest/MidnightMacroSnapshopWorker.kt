import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.fitquest.database.AppDatabase
import com.example.fitquest.database.MacroDiary
import com.example.fitquest.datastore.DataStoreManager
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import java.time.*

class MidnightMacroSnapshotWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val db = AppDatabase.getInstance(context)
        val userId = runCatching { DataStoreManager.getUserId(context).first() }.getOrNull()
            ?: return Result.success() // no user yet

        val zone = ZoneId.of("Asia/Manila")

        // Determine the diary day. If we fired shortly after midnight due to inexact alarms,
        // log for "yesterday" to preserve the 23:59 intention.
        val now = ZonedDateTime.now(zone)
        val targetDate = if (now.toLocalTime().isBefore(java.time.LocalTime.of(1, 0)))
            now.minusDays(1).toLocalDate()
        else
            now.toLocalDate()

        val dayKey = targetDate.year * 10000 + targetDate.monthValue * 100 + targetDate.dayOfMonth

        // Avoid duplicates
        db.macroDiaryDao().get(userId, dayKey)?.let { return Result.success() }

        // Get totals for that day
        val totals = db.foodLogDao().totalsForDay(userId, dayKey)
        val plan = db.macroPlanDao().getLatestForUser(userId)

        val row = MacroDiary(
            userId = userId,
            dayKey = dayKey,
            calories = totals.calories.roundToInt(),
            protein  = totals.protein.roundToInt(),
            carbs    = totals.carbohydrate.roundToInt(),
            fat      = totals.fat.roundToInt(),
            planCalories = plan?.calories ?: 0,
            planProtein  = plan?.protein  ?: 0,
            planCarbs    = plan?.carbs    ?: 0,
            planFat      = plan?.fat      ?: 0
        )

        db.macroDiaryDao().upsert(row)
        return Result.success()
    }
}


