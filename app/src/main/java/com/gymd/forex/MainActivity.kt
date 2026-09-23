package com.gymd.forex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gymd.forex.data.AppDatabase
import com.gymd.forex.data.ForexRepository
import com.gymd.forex.data.TwelveDataApiService
import com.gymd.forex.ui.ForexSignalViewModel
import com.gymd.forex.ui.ForexViewModelFactory
import com.gymd.forex.ui.StylishForexDashboardScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. ローカルキャッシュDB（Room）の初期化
        val database = AppDatabase.getDatabase(applicationContext)

        // 2. Twelve Data API サービスの生成
        val apiService = TwelveDataApiService.create()

        // 3. リポジトリの初期化
        // BuildConfig 経由で安全に渡す
        val repository = ForexRepository(
            apiService = apiService,
            cacheDao = database.forexCacheDao(),
            apiKey = BuildConfig.TWELVE_DATA_API_KEY,
        )

        // 4. UIの描画
        setContent {
            MaterialTheme {
                // 背景をスタイリッシュダッシュボードに合わせたダークカラーで塗りつぶし
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0B0E14)
                ) {
                    val viewModel: ForexSignalViewModel = viewModel(
                        factory = ForexViewModelFactory(repository)
                    )
                    StylishForexDashboardScreen(viewModel = viewModel)
                }
            }
        }
    }
}