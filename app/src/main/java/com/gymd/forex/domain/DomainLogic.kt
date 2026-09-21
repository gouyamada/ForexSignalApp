package com.gymd.forex.domain

// --- Models ---
data class TechnicalIndicators(
    val price: Double, val ema20: Double, val ema50: Double, val ema200: Double,
    val macdLine: Double, val macdSignal: Double, val macdHist: Double, val prevMacdHist: Double,
    val rsi: Double, val bbUpper2Sigma: Double, val bbLower2Sigma: Double, val bbMiddle: Double
)
data class DailyIndicators(val price: Double, val ema20: Double, val ema200: Double)
data class H8Indicators(val price: Double, val ema20: Double, val ema50: Double)
data class H4Indicators(val price: Double, val ema20: Double, val ema50: Double, val rsi: Double)

enum class SignalDecision { STRONG_LONG, LONG, STRONG_SHORT, SHORT, WAIT; fun toBias() = when(this) { STRONG_LONG, LONG -> TrendBias.BULLISH; STRONG_SHORT, SHORT -> TrendBias.BEARISH; WAIT -> TrendBias.NEUTRAL } }
enum class TrendBias { BULLISH, BEARISH, NEUTRAL }

data class CascadeMtfResult(
    val finalDecision: SignalDecision, val dailyBias: TrendBias, val h8Bias: TrendBias,
    val h4Bias: TrendBias, val m15Decision: SignalDecision, val filterPassed: Boolean, val explanation: String
)

// 米国債利回り・DXYの指標データ
data class MacroIndicators(
    val us02yCurrent: Double,
    val us02yEma20: Double,
    val us10yCurrent: Double,
    val us10yEma20: Double,
    val dxyCurrent: Double,
    val dxyEma20: Double
)

enum class MacroBias {
    BULLISH_USD, // 米金利・DXY高（ドル高追従）
    BEARISH_USD, // 米金利・DXY安（ドル安警戒）
    NEUTRAL      // 綱引き状態
}

data class MacroAnalysisResult(
    val bias: MacroBias,
    val score: Int,
    val us02yRising: Boolean,
    val us10yRising: Boolean,
    val dxyRising: Boolean,
    val explanation: String
)

class MacroEvaluator {
    fun evaluate(m: MacroIndicators): MacroAnalysisResult {
        val us02yUp = m.us02yCurrent >= m.us02yEma20
        val us10yUp = m.us10yCurrent >= m.us10yEma20
        val dxyUp   = m.dxyCurrent >= m.dxyEma20

        var score = 0
        if (us02yUp) score++ else score--
        if (us10yUp) score++ else score--
        if (dxyUp)   score++ else score--

        val bias = when {
            score >= 2 -> MacroBias.BULLISH_USD
            score <= -2 -> MacroBias.BEARISH_USD
            else -> MacroBias.NEUTRAL
        }

        val explanation = when (bias) {
            MacroBias.BULLISH_USD -> "米2年・10年債利回りおよびDXYが上昇軌道。強力なドル高支援環境です。"
            MacroBias.BEARISH_USD -> "米金利低下およびDXY失速により、ドル買いに極めて不利な環境です。"
            MacroBias.NEUTRAL -> "金利動向とドル指数の歩調が合っておらず、方向感が拮抗しています。"
        }

        return MacroAnalysisResult(
            bias = bias,
            score = score,
            us02yRising = us02yUp,
            us10yRising = us10yUp,
            dxyRising = dxyUp,
            explanation = explanation
        )
    }
}

// --- Evaluators ---
class AdvancedSignalEngine {
    fun evaluate(i: TechnicalIndicators): SignalDecision {
        val bandWidth = (i.bbUpper2Sigma - i.bbLower2Sigma) / (i.bbMiddle.takeIf { it > 0 } ?: 1.0)
        if (bandWidth < 0.003) return SignalDecision.WAIT

        var longScore = 0
        var shortScore = 0

        if (i.price > i.ema20 && i.ema20 > i.ema50 && i.ema50 > i.ema200) longScore += 2
        else if (i.price > i.ema20 && i.ema20 > i.ema50) longScore += 1

        if (i.price < i.ema20 && i.ema20 < i.ema50 && i.ema50 < i.ema200) shortScore += 2
        else if (i.price < i.ema20 && i.ema20 < i.ema50) shortScore += 1

        if (i.macdLine > i.macdSignal && i.macdHist > i.prevMacdHist) longScore += 1
        if (i.macdLine < i.macdSignal && i.macdHist < i.prevMacdHist) shortScore += 1

        if (i.price > i.bbMiddle && i.price < i.bbUpper2Sigma) longScore += 1
        if (i.price < i.bbMiddle && i.price > i.bbLower2Sigma) shortScore += 1

        if (i.rsi > 70.0 || i.price >= i.bbUpper2Sigma) longScore -= 2
        if (i.rsi < 30.0 || i.price <= i.bbLower2Sigma) shortScore -= 2

        return when {
            longScore >= 4 -> SignalDecision.STRONG_LONG
            longScore >= 3 -> SignalDecision.LONG
            shortScore >= 4 -> SignalDecision.STRONG_SHORT
            shortScore >= 3 -> SignalDecision.SHORT
            else -> SignalDecision.WAIT
        }
    }
}

class CascadeMTFEvaluator(private val m15Engine: AdvancedSignalEngine = AdvancedSignalEngine()) {
    private fun evalDaily(d1: DailyIndicators) = when {
        d1.price > d1.ema200 && d1.price > d1.ema20 -> TrendBias.BULLISH
        d1.price < d1.ema200 && d1.price < d1.ema20 -> TrendBias.BEARISH
        else -> TrendBias.NEUTRAL
    }
    private fun evalH8(h8: H8Indicators) = when {
        h8.price > h8.ema20 && h8.ema20 > h8.ema50 -> TrendBias.BULLISH
        h8.price < h8.ema20 && h8.ema20 < h8.ema50 -> TrendBias.BEARISH
        else -> TrendBias.NEUTRAL
    }
    private fun evalH4(h4: H4Indicators) = when {
        h4.price > h4.ema20 && h4.ema20 > h4.ema50 && h4.rsi in 40.0..68.0 -> TrendBias.BULLISH
        h4.price < h4.ema20 && h4.ema20 < h4.ema50 && h4.rsi in 32.0..60.0 -> TrendBias.BEARISH
        else -> TrendBias.NEUTRAL
    }

    fun evaluate(d1: DailyIndicators, h8: H8Indicators, h4: H4Indicators, m15: TechnicalIndicators): CascadeMtfResult {
        val dailyBias = evalDaily(d1)
        val h8Bias = evalH8(h8)
        val h4Bias = evalH4(h4)
        val m15Signal = m15Engine.evaluate(m15)

        val isAllBull = dailyBias == TrendBias.BULLISH && h8Bias == TrendBias.BULLISH && h4Bias == TrendBias.BULLISH
        val isAllBear = dailyBias == TrendBias.BEARISH && h8Bias == TrendBias.BEARISH && h4Bias == TrendBias.BEARISH

        var finalDecision = SignalDecision.WAIT
        val explanation: String

        if (isAllBull) {
            if (m15Signal == SignalDecision.STRONG_LONG || m15Signal == SignalDecision.LONG) {
                finalDecision = m15Signal
                explanation = "日足・8H・4Hがすべて上昇トレンドで一致。15分足の買いシグナルを承認。"
            } else explanation = "上位足は買い優勢ですが、15分足で適切な押し目・トリガーが形成されていません。"
        } else if (isAllBear) {
            if (m15Signal == SignalDecision.STRONG_SHORT || m15Signal == SignalDecision.SHORT) {
                finalDecision = m15Signal
                explanation = "日足・8H・4Hがすべて下降トレンドで一致。15分足の売りシグナルを承認。"
            } else explanation = "上位足は売り優勢ですが、15分足で適切な戻り目・トリガーが形成されていません。"
        } else {
            explanation = "上位足のトレンド方向が不揃い（日足:$dailyBias, 8H:$h8Bias, 4H:$h4Bias）のため見送り。"
        }

        return CascadeMtfResult(finalDecision, dailyBias, h8Bias, h4Bias, m15Signal, finalDecision != SignalDecision.WAIT, explanation)
    }

    fun applyMacroFilter(
        baseResult: CascadeMtfResult,
        macro: MacroAnalysisResult
    ): CascadeMtfResult {
        // 上位足と下位足がLONG一致していても、マクロがドル安（金利急落）なら強制WAIT
        if (baseResult.finalDecision in listOf(SignalDecision.LONG, SignalDecision.STRONG_LONG)) {
            if (macro.bias == MacroBias.BEARISH_USD) {
                return baseResult.copy(
                    finalDecision = SignalDecision.WAIT,
                    filterPassed = false,
                    explanation = "【マクロ警告】テクニカルは買いですが、米債利回り・DXYが下落中のためエントリーを見送ります。"
                )
            }
            // マクロもドル高なら最強判定へ
            if (macro.bias == MacroBias.BULLISH_USD) {
                return baseResult.copy(
                    finalDecision = SignalDecision.STRONG_LONG,
                    explanation = "${baseResult.explanation} 加えて米金利・DXY上昇のファンダメンタルズ支援が一致。"
                )
            }
        }

        // SHORTの場合も同様に逆行を検知
        if (baseResult.finalDecision in listOf(SignalDecision.SHORT, SignalDecision.STRONG_SHORT)) {
            if (macro.bias == MacroBias.BULLISH_USD) {
                return baseResult.copy(
                    finalDecision = SignalDecision.WAIT,
                    filterPassed = false,
                    explanation = "【マクロ警告】テクニカルは売りですが、米債利回り・DXYが上昇中のため逆張り売りを禁止します。"
                )
            }
        }

        return baseResult
    }
}