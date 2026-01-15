package com.smartmotionrecorder.coin

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CoinRepository private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val _coins = MutableStateFlow(prefs.getInt(KEY_COINS, 0))
    val coins: StateFlow<Int> = _coins.asStateFlow()
    private val lock = Any()

    fun getCoins(): Int = _coins.value

    fun addCoins(amount: Int) {
        if (amount <= 0) return
        synchronized(lock) {
            val newValue = prefs.getInt(KEY_COINS, 0) + amount
            if (prefs.edit().putInt(KEY_COINS, newValue).commit()) {
                _coins.value = newValue
            }
        }
    }

    fun spendCoin(amount: Int = 1): Boolean {
        if (amount <= 0) return false
        synchronized(lock) {
            val current = prefs.getInt(KEY_COINS, 0)
            if (current < amount) return false
            val newValue = current - amount
            val ok = prefs.edit().putInt(KEY_COINS, newValue).commit()
            if (ok) _coins.value = newValue
            return ok
        }
    }

    fun refund(amount: Int) {
        if (amount <= 0) return
        addCoins(amount)
    }

    companion object {
        private const val PREF_NAME = "coin_prefs"
        private const val KEY_COINS = "coins"

        @Volatile
        private var INSTANCE: CoinRepository? = null

        fun get(context: Context): CoinRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CoinRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
