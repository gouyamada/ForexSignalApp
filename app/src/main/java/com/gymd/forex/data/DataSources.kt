package com.gymd.forex.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.gymd.forex.domain.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query as RetrofitQuery
import kotlin.math.sqrt

// ==========================================
// 1. Room Entity, DAO & Database
// ==========================================

@Entity(tableName = "forex_cache")
data class ForexCacheEntity(
    @PrimaryKey val cacheKey: String,
    val symbol: String,
    val timeframe: String,
    val price: Double,
    val ema20: Double,
    val ema50: Double,
    val ema200: Double,
    val rsi: Double,
    val bbUpper: Double = 0.0,
    val bbLower: Double = 0.0,
    val bbMiddle: Double = 0.0,
    val macdLine: Double = 0.0,
    val macdSignal: Double = 0.0,
    val macdHist: Double = 0.0,
    val prevMacdHist: Double = 0.0,
    val lastUpdatedTimestamp: Long
)

@Dao
interface ForexCacheDao {
    @Query("SELECT * FROM forex_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun getCacheByKey(key: String): ForexCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(entity: ForexCacheEntity)

    @Query("DELETE FROM forex_cache WHERE symbol = :symbol")
    suspend fun clearCacheForSymbol(symbol: String)
}

@Database(entities = [ForexCacheEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun forexCacheDao(): ForexCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "forex_db"
            ).build().also { INSTANCE = it }
        }
    }
}

// ==========================================
// 2. Twelve Data API Interface
// ==========================================

interface TwelveDataApiService {
    @GET("time_series")
    suspend fun getTimeSeries(
        @RetrofitQuery("symbol") symbol: String,
        @RetrofitQuery("interval") interval: String,
        @RetrofitQuery("outputsize") outputSize: Int = 50,
        @RetrofitQuery("apikey") apiKey: String
    ): TwelveDataTimeSeriesResponse

    companion object {
        fun create(): TwelveDataApiService {
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl("https://api.twelvedata.com/")
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(TwelveDataApiService::class.java)
        }
    }
}

// ==========================================
// 3. Cache TTL Configuration
// ==========================================

object CacheTtlConfig {
    const val TTL_DAILY_MS = 6 * 60 * 60 * 1000L   // 日足: 6時間有効
    const val TTL_8H_MS    = 2 * 60 * 60 * 1000L   // 8時間足: 2時間有効
    const val TTL_4H_MS    = 30 * 60 * 1000L       // 4時間足: 30分有効
    const val TTL_15M_MS   = 3 * 60 * 1000L        // 15分足: 3分有効
}

data class CacheInfo(val isCached: Boolean, val ttlRemainingText: String)

// ==========================================
// 4. Repository Implementation
// ==========================================

class ForexRepository(
    private val apiService: TwelveDataApiService,
    private val cacheDao: ForexCacheDao,
    // ※ここにTwelve Dataで発行したAPIキーを記載してください（テスト時は demo でも可）
    private val apiKey: String = "demo"
) {

    suspend fun getCacheInfo(symbol: String, tf: String, ttlMs: Long): CacheInfo {
        val cached = cacheDao.getCacheByKey("${symbol}_$tf") ?: return CacheInfo(false, "未取得")
        val remain = ttlMs - (System.currentTimeMillis() - cached.lastUpdatedTimestamp)
        return if (remain > 0) {
            val mins = (remain / 60000).coerceAtLeast(1)
            CacheInfo(true, "残り ${mins}分")
        } else {
            CacheInfo(false, "期限切れ (再取得)")
        }
    }

    suspend fun getDailyIndicators(symbol: String): DailyIndicators = withContext(Dispatchers.IO) {
        val cached = getValidCache(symbol, "1day", CacheTtlConfig.TTL_DAILY_MS)
        if (cached != null) {
            return@withContext DailyIndicators(cached.price, cached.ema20, cached.ema200)
        }
        val fetched = fetchAndCalculate(symbol, "1day")
        saveCache(symbol, "1day", fetched)
        DailyIndicators(fetched.price, fetched.ema20, fetched.ema200)
    }

    suspend fun getH8Indicators(symbol: String): H8Indicators = withContext(Dispatchers.IO) {
        val cached = getValidCache(symbol, "8h", CacheTtlConfig.TTL_8H_MS)
        if (cached != null) {
            return@withContext H8Indicators(cached.price, cached.ema20, cached.ema50)
        }
        val fetched = fetchAndCalculate(symbol, "8h")
        saveCache(symbol, "8h", fetched)
        H8Indicators(fetched.price, fetched.ema20, fetched.ema50)
    }

    suspend fun getH4Indicators(symbol: String): H4Indicators = withContext(Dispatchers.IO) {
        val cached = getValidCache(symbol, "4h", CacheTtlConfig.TTL_4H_MS)
        if (cached != null) {
            return@withContext H4Indicators(cached.price, cached.ema20, cached.ema50, cached.rsi)
        }
        val fetched = fetchAndCalculate(symbol, "4h")
        saveCache(symbol, "4h", fetched)
        H4Indicators(fetched.price, fetched.ema20, fetched.ema50, fetched.rsi)
    }

    suspend fun getM15Indicators(symbol: String): TechnicalIndicators = withContext(Dispatchers.IO) {
        val cached = getValidCache(symbol, "15min", CacheTtlConfig.TTL_15M_MS)
        if (cached != null) {
            return@withContext cached.toTechnicalIndicators()
        }
        val fetched = fetchAndCalculate(symbol, "15min")
        saveCache(symbol, "15min", fetched)
        fetched
    }

    // --- Private Fetch & Technical Calculation ---

    private suspend fun fetchAndCalculate(symbol: String, interval: String): TechnicalIndicators {
        val response = apiService.getTimeSeries(
            symbol = symbol,
            interval = interval,
            outputSize = 50,
            apiKey = apiKey
        )

        val values = response.values
        if (values.isNullOrEmpty()) {
            val errorMsg = response.message ?: "Twelve Dataからの応答データが空です"
            throw IllegalStateException(errorMsg)
        }

        // 時系列は通常 [最新, 1本前, 2本前, ...] の順で返却されます
        val closes = values.map { it.close.toDoubleOrNull() ?: 0.0 }
        val currentPrice = closes.firstOrNull() ?: 0.0

        val ema20 = calculateEma(closes, 20)
        val ema50 = calculateEma(closes, 50.coerceAtMost(closes.size))
        val ema200 = calculateEma(closes, closes.size) // データ数に応じた近似長期線
        val rsi = calculateRsi(closes, 14)

        // ボリンジャーバンド (20期間, ±2σ)
        val bbPeriod = 20.coerceAtMost(closes.size)
        val subCloses = closes.take(bbPeriod)
        val bbMiddle = subCloses.average()
        val variance = subCloses.map { (it - bbMiddle) * (it - bbMiddle) }.average()
        val stdDev = sqrt(variance)
        val bbUpper = bbMiddle + (2 * stdDev)
        val bbLower = bbMiddle - (2 * stdDev)

        // MACD簡易計算 (EMA12 - EMA26)
        val ema12 = calculateEma(closes, 12.coerceAtMost(closes.size))
        val ema26 = calculateEma(closes, 26.coerceAtMost(closes.size))
        val macdLine = ema12 - ema26
        val macdSignal = macdLine * 0.8 // 簡易シグナル近似
        val macdHist = macdLine - macdSignal

        return TechnicalIndicators(
            price = currentPrice,
            ema20 = ema20,
            ema50 = ema50,
            ema200 = ema200,
            macdLine = macdLine,
            macdSignal = macdSignal,
            macdHist = macdHist,
            prevMacdHist = macdHist * 0.9,
            rsi = rsi,
            bbUpper2Sigma = bbUpper,
            bbLower2Sigma = bbLower,
            bbMiddle = bbMiddle
        )
    }

    private fun calculateEma(prices: List<Double>, period: Int): Double {
        if (prices.isEmpty()) return 0.0
        val actualPeriod = period.coerceAtMost(prices.size)
        val reversed = prices.take(actualPeriod).reversed() // 過去から現在順に計算
        val multiplier = 2.0 / (actualPeriod + 1.0)
        var ema = reversed.first()
        for (i in 1 until reversed.size) {
            ema += (reversed[i] - ema) * multiplier
        }
        return ema
    }

    private fun calculateRsi(prices: List<Double>, period: Int): Double {
        if (prices.size < period + 1) return 50.0
        var gainSum = 0.0
        var lossSum = 0.0

        for (i in 0 until period) {
            val change = prices[i] - prices[i + 1]
            if (change > 0) gainSum += change else lossSum += -change
        }

        val avgGain = gainSum / period
        val avgLoss = lossSum / period

        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    private suspend fun getValidCache(symbol: String, tf: String, ttlMs: Long): ForexCacheEntity? {
        val cached = cacheDao.getCacheByKey("${symbol}_$tf") ?: return null
        val age = System.currentTimeMillis() - cached.lastUpdatedTimestamp
        return if (age < ttlMs) cached else null
    }

    private suspend fun saveCache(symbol: String, tf: String, d: TechnicalIndicators) {
        val entity = ForexCacheEntity(
            cacheKey = "${symbol}_$tf",
            symbol = symbol,
            timeframe = tf,
            price = d.price,
            ema20 = d.ema20,
            ema50 = d.ema50,
            ema200 = d.ema200,
            rsi = d.rsi,
            bbUpper = d.bbUpper2Sigma,
            bbLower = d.bbLower2Sigma,
            bbMiddle = d.bbMiddle,
            macdLine = d.macdLine,
            macdSignal = d.macdSignal,
            macdHist = d.macdHist,
            prevMacdHist = d.prevMacdHist,
            lastUpdatedTimestamp = System.currentTimeMillis()
        )
        cacheDao.insertCache(entity)
    }

    private fun ForexCacheEntity.toTechnicalIndicators() = TechnicalIndicators(
        price = price, ema20 = ema20, ema50 = ema50, ema200 = ema200,
        macdLine = macdLine, macdSignal = macdSignal, macdHist = macdHist,
        prevMacdHist = prevMacdHist, rsi = rsi, bbUpper2Sigma = bbUpper,
        bbLower2Sigma = bbLower, bbMiddle = bbMiddle
    )

    suspend fun getMacroIndicators(): MacroIndicators = withContext(Dispatchers.IO) {
        val ttl = 60 * 60 * 1000L // 1時間キャッシュ

        // 2年債、10年債、DXYの各キャッシュ確認＆取得
        val us02y = getMacroSeriesCached("US02Y", ttl)
        val us10y = getMacroSeriesCached("US10Y", ttl)
        val dxy   = getMacroSeriesCached("DXY", ttl)

        MacroIndicators(
            us02yCurrent = us02y.first,
            us02yEma20   = us02y.second,
            us10yCurrent = us10y.first,
            us10yEma20   = us10y.second,
            dxyCurrent   = dxy.first,
            dxyEma20     = dxy.second
        )
    }

    private suspend fun getMacroSeriesCached(symbol: String, ttlMs: Long): Pair<Double, Double> {
        val cached = getValidCache(symbol, "1h", ttlMs)
        if (cached != null) {
            return Pair(cached.price, cached.ema20)
        }

        // Twelve Dataから取得
        val response = apiService.getTimeSeries(
            symbol = symbol,
            interval = "1h",
            outputSize = 30,
            apiKey = apiKey
        )
        val closes = response.values?.mapNotNull { it.close.toDoubleOrNull() } ?: listOf(0.0)
        val current = closes.firstOrNull() ?: 0.0
        val ema20 = calculateEma(closes, 20)

        // DBに保存
        saveCache(symbol, "1h", TechnicalIndicators(
            price = current, ema20 = ema20, ema50 = 0.0, ema200 = 0.0,
            macdLine = 0.0, macdSignal = 0.0, macdHist = 0.0, prevMacdHist = 0.0,
            rsi = 50.0, bbUpper2Sigma = 0.0, bbLower2Sigma = 0.0, bbMiddle = 0.0
        ))

        return Pair(current, ema20)
    }
}
