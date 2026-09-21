package com.gymd.forex.data

import com.squareup.moshi.Json

// 最新価格取得用 (/price)
data class TwelveDataPriceResponse(
    @field:Json(name = "price") val price: String?
)

// ローソク足データ用 (/time_series)
data class TwelveDataTimeSeriesResponse(
    @field:Json(name = "values") val values: List<TimeSeriesValue>?,
    @field:Json(name = "status") val status: String?,
    @field:Json(name = "message") val message: String?
)

data class TimeSeriesValue(
    @field:Json(name = "datetime") val datetime: String,
    @field:Json(name = "open") val open: String,
    @field:Json(name = "high") val high: String,
    @field:Json(name = "low") val low: String,
    @field:Json(name = "close") val close: String
)