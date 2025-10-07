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
        val yesterday = LocalDate.now(zone).minusDays(1)
        val dayKey = yesterday.year * 10000 + yesterday.monthValue * 100 + yesterday.dayOfMonth

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


