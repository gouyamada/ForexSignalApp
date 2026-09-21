package com.gymd.forex.domain

enum class CurrencyPair(val symbol: String, val displayName: String, val category: String) {
    USD_JPY("USD/JPY", "米ドル / 日本円", "Major"),
    EUR_USD("EUR/USD", "ユーロ / 米ドル", "Major"),
    GBP_JPY("GBP/JPY", "ポンド / 日本円", "Cross Yen"),
    EUR_JPY("EUR/JPY", "ユーロ / 日本円", "Cross Yen"),
    AUD_USD("AUD/USD", "豪ドル / 米ドル", "Major"),
    GBP_USD("GBP/USD", "ポンド / 米ドル", "Major")
}
