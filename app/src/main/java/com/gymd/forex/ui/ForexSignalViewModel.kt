package com.gymd.forex.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gymd.forex.data.CacheTtlConfig
import com.gymd.forex.data.ForexRepository
import com.gymd.forex.domain.CascadeMTFEvaluator
import com.gymd.forex.domain.MacroEvaluator
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ForexSignalViewModel(
    private val repository: ForexRepository,
    private val mtfEvaluator: CascadeMTFEvaluator = CascadeMTFEvaluator(),
    private val macroEvaluator: MacroEvaluator = MacroEvaluator(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForexDashboardUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refreshSignals()
    }

    /**
     * 最新のテクニカルデータおよびマクロ経済指標を取得し、統合判定を実行
     */
    fun refreshSignals(symbol: String = _uiState.value.symbol) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            try {
                supervisorScope {
                    // 1. 為替各時間足とマクロ指標（米2年・10年債・DXY）を完全並列フェッチ
                    val d1Deferred = async { repository.getDailyIndicators(symbol) }
                    val h8Deferred = async { repository.getH8Indicators(symbol) }
                    val h4Deferred = async { repository.getH4Indicators(symbol) }
                    val m15Deferred = async { repository.getM15Indicators(symbol) }
                    val macroDeferred = async { repository.getMacroIndicators() }

                    val d1 = d1Deferred.await()
                    val h8 = h8Deferred.await()
                    val h4 = h4Deferred.await()
                    val m15 = m15Deferred.await()
                    val macroData = macroDeferred.await()

                    // 2. マクロ指標の評価（米金利動向・ドルインデックス）
                    val macroAnalysis = macroEvaluator.evaluate(macroData)

                    // 3. テクニカル分析（日足・8H・4H・15M）の多層判定
                    val baseMtfResult = mtfEvaluator.evaluate(d1, h8, h4, m15)

                    // 4. テクニカル結果にマクロフィルターを適用（ダイバージェンス防止＆確信度補正）
                    val finalMtfResult = mtfEvaluator.applyMacroFilter(baseMtfResult, macroAnalysis)

                    // 5. 各時間足のキャッシュ状態を取得
                    val d1Cache = repository.getCacheInfo(symbol, "1day", CacheTtlConfig.TTL_DAILY_MS)
                    val h8Cache = repository.getCacheInfo(symbol, "8h", CacheTtlConfig.TTL_8H_MS)
                    val h4Cache = repository.getCacheInfo(symbol, "4h", CacheTtlConfig.TTL_4H_MS)
                    val m15Cache = repository.getCacheInfo(symbol, "15min", CacheTtlConfig.TTL_15M_MS)

                    val statuses = listOf(
                        TimeFrameStatus("日足 (D1)", finalMtfResult.dailyBias, d1Cache.isCached, d1Cache.ttlRemainingText),
                        TimeFrameStatus("8時間足 (8H)", finalMtfResult.h8Bias, h8Cache.isCached, h8Cache.ttlRemainingText),
                        TimeFrameStatus("4時間足 (4H)", finalMtfResult.h4Bias, h4Cache.isCached, h4Cache.ttlRemainingText),
                        TimeFrameStatus("15分足 (15M)", finalMtfResult.m15Decision.toBias(), m15Cache.isCached, m15Cache.ttlRemainingText),
                    )

                    // 6. 更新完了時刻の文字列生成
                    val currentTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

                    // 7. State の一括更新
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            symbol = symbol,
                            currentPrice = m15.price,
                            mtfResult = finalMtfResult,
                            macroResult = macroAnalysis,
                            timeFrameStatuses = statuses,
                            lastUpdatedTime = currentTimeStr,
                        )
                    }
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "シグナル解析に失敗いたしました: ${e.localizedMessage ?: "通信エラー"}",
                    )
                }
            }
        }
    }
}

/**
 * ViewModel 生成用 Factory
 */
class ForexViewModelFactory(
    private val repository: ForexRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ForexSignalViewModel::class.java)) {
            return ForexSignalViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
