package com.gymd.forex.ui

import com.gymd.forex.domain.CascadeMtfResult
import com.gymd.forex.domain.MacroAnalysisResult
import com.gymd.forex.domain.TrendBias

/**
 * 各時間足のカード表示用データモデル
 */
data class TimeFrameStatus(
    val label: String,
    val bias: TrendBias,
    val isFromCache: Boolean,
    val remainingTtlText: String
)

/**
 * ダッシュボード画面全体のUI状態
 */
data class ForexDashboardUiState(
    val isLoading: Boolean = false,
    val symbol: String = "USD/JPY",
    val currentPrice: Double = 0.0,
    val mtfResult: CascadeMtfResult? = null,
    val macroResult: MacroAnalysisResult? = null,
    val timeFrameStatuses: List<TimeFrameStatus> = emptyList(),
    val errorMessage: String? = null,
    val lastUpdatedTime: String? = null
)
