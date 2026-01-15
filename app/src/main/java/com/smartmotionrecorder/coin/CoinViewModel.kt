package com.smartmotionrecorder.coin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CoinViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = CoinRepository.get(app)
    val coins: StateFlow<Int> = repo.coins

    fun onWatchAdReward() {
        repo.addCoins(5)
    }

    /**
     * @return true jika start berhasil (coin cukup dan onStart sukses). Refund otomatis jika start gagal.
     */
    fun tryStartMonitoring(onStart: () -> Boolean): Boolean {
        val spent = repo.spendCoin(1)
        if (!spent) return false
        val success = try {
            onStart()
        } catch (_: Exception) {
            false
        }
        if (!success) {
            repo.refund(1)
        }
        return success
    }
}
