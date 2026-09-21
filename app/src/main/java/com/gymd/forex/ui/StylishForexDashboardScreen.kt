package com.gymd.forex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gymd.forex.domain.MacroAnalysisResult
import com.gymd.forex.domain.MacroBias
import com.gymd.forex.domain.SignalDecision
import com.gymd.forex.domain.TrendBias
import kotlinx.coroutines.launch

// スタイリッシュ・ダークテーマ専用カラーパレット
private val DarkBg = Color(0xFF0B0E14)
private val CardBg = Color(0xFF151B26)
private val CardBorder = Color(0xFF232B3B)
private val TextPrimary = Color(0xFFF0F4F8)
private val TextSecondary = Color(0xFF8B949E)

private val SignalLongGreen = Color(0xFF00E676)
private val SignalShortRed = Color(0xFFFF3D57)
private val SignalWaitGray = Color(0xFF78909C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StylishForexDashboardScreen(viewModel: ForexSignalViewModel) {
    val state by viewModel.uiState.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()
    val haptic = LocalHapticFeedback.current

    // ボトムシート状態の管理
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val coroutineScope = rememberCoroutineScope()

    // ... (触覚フィードバック制御ロジックはそのまま) ...

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                StylishTopBar(
                    lastUpdatedTime = state.lastUpdatedTime,
                    onRefresh = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.refreshSignals()
                    },
                    isLoading = state.isLoading
                )
            }
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = state.isLoading,
                onRefresh = { viewModel.refreshSignals() },
                state = pullToRefreshState,
                // ... (indicator設定) ...
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                // ... (エラー表示) ...
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(4.dp)) }

                    // 1. スポットレート表示（タップでボトムシート展開）
                    item {
                        RateHeaderCard(
                            symbol = state.symbol,
                            price = state.currentPrice,
                            onSymbolClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showBottomSheet = true
                            }
                        )
                    }

                    // 2. マクロ金利・DXYレーダー
                    item {
                        state.macroResult?.let { macro ->
                            MacroMarketRadarCard(macro = macro)
                        }
                    }

                    // 3. メイン統合判定カード
                    item {
                        state.mtfResult?.let { result ->
                            HeroDecisionCard(
                                decision = result.finalDecision,
                                explanation = result.explanation
                            )
                        }
                    }

                    // 4. 各時間足ステータス一覧
                    items(state.timeFrameStatuses) { status ->
                        TimeFrameStatusRow(status)
                    }

                    item { Spacer(modifier = Modifier.height(20.dp)) }
                }
            }
        }

        // ボトムシートの描画
        if (showBottomSheet) {
            CurrencySelectorBottomSheet(
                selectedSymbol = state.symbol,
                onSelect = { selectedPair ->
                    coroutineScope.launch {
                        sheetState.hide()
                        showBottomSheet = false
                        // 通貨ペア切り替え＆再取得
                        viewModel.refreshSignals(selectedPair.symbol)
                    }
                },
                onDismiss = { showBottomSheet = false },
                sheetState = sheetState
            )
        }
    }
}

@Composable
private fun StylishTopBar(
    lastUpdatedTime: String?,
    onRefresh: () -> Unit,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "QUANT SIGNAL",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Automated MTF Filter",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                if (!lastUpdatedTime.isNullOrEmpty()) {
                    Text(
                        text = " • ",
                        color = Color(0xFF384457),
                        fontSize = 11.sp
                    )
                    Text(
                        text = "最終更新 $lastUpdatedTime",
                        color = Color(0xFF6B7A90),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        IconButton(
            onClick = onRefresh,
            enabled = !isLoading,
            modifier = Modifier
                .size(40.dp)
                .background(CardBg, shape = CircleShape)
                .border(1.dp, CardBorder, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "更新",
                tint = if (isLoading) TextSecondary else TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun RateHeaderCard(
    symbol: String,
    price: Double,
    onSymbolClick: () -> Unit // ➔ クリックイベントを追加
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 通貨ペア選択トリガー領域
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSymbolClick() }
                    .padding(4.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = symbol,
                            color = TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "通貨変更",
                            tint = SignalLongGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "TAP TO SWITCH",
                        color = SignalLongGreen.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // 価格表示
            Text(
                text = if (price > 0) String.format("%.3f", price) else "--.---",
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun MacroMarketRadarCard(macro: MacroAnalysisResult) {
    val (statusLabel, statusColor) = when (macro.bias) {
        MacroBias.BULLISH_USD -> "USD SUPPORTIVE" to SignalLongGreen
        MacroBias.BEARISH_USD -> "USD HEADWIND" to SignalShortRed
        MacroBias.NEUTRAL -> "MIXED / NEUTRAL" to SignalWaitGray
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MACRO YIELD & DXY RADAR",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = statusLabel,
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MacroPill(name = "US 2Y Yield", isBullish = macro.us02yRising, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                MacroPill(name = "US 10Y Yield", isBullish = macro.us10yRising, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                MacroPill(name = "DXY Index", isBullish = macro.dxyRising, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = macro.explanation,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun MacroPill(name: String, isBullish: Boolean, modifier: Modifier = Modifier) {
    val color = if (isBullish) SignalLongGreen else SignalShortRed
    val text = if (isBullish) "▲ 上昇" else "▼ 下降"

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = name,
            fontSize = 11.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
            color = color.copy(alpha = 0.12f),
            shape = RoundedCornerShape(6.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
        ) {
            Text(
                text = text,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun HeroDecisionCard(decision: SignalDecision, explanation: String) {
    val (label, accentColor, glowBrush) = when (decision) {
        SignalDecision.STRONG_LONG, SignalDecision.LONG -> Triple(
            "LONG (買い)",
            SignalLongGreen,
            Brush.verticalGradient(listOf(Color(0xFF0F2B1E), CardBg))
        )
        SignalDecision.STRONG_SHORT, SignalDecision.SHORT -> Triple(
            "SHORT (売り)",
            SignalShortRed,
            Brush.verticalGradient(listOf(Color(0xFF33141A), CardBg))
        )
        SignalDecision.WAIT -> Triple(
            "WAIT (様子見)",
            SignalWaitGray,
            Brush.verticalGradient(listOf(Color(0xFF1E242F), CardBg))
        )
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(glowBrush)
                .padding(24.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(accentColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "EXECUTION SIGNAL",
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = label,
                    color = TextPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black
                )

                Spacer(modifier = Modifier.height(14.dp))

                HorizontalDivider(color = CardBorder)

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = explanation,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

@Composable
private fun TimeFrameStatusRow(status: TimeFrameStatus) {
    val (biasText, biasColor) = when (status.bias) {
        TrendBias.BULLISH -> "BULLISH" to SignalLongGreen
        TrendBias.BEARISH -> "BEARISH" to SignalShortRed
        TrendBias.NEUTRAL -> "NEUTRAL" to SignalWaitGray
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = status.label,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (status.isFromCache) "📦 ${status.remainingTtlText}" else "🌐 API最新取得",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(biasColor.copy(alpha = 0.12f))
                    .border(1.dp, biasColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = biasText,
                    color = biasColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}