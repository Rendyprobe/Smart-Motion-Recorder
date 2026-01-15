package com.smartmotionrecorder.ads

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd

class RewardedAdManager(private val context: Context, private val listener: Listener) {
    interface Listener {
        fun onRewardEarned(amount: Int)
        fun onAdFailed(message: String)
        fun onAdClosed()
        fun onAdLoaded()
    }

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    fun initialize() {
        MobileAds.initialize(context) {}
        loadAd()
    }

    fun isReady(): Boolean = rewardedAd != null && !isLoading

    fun loadAd() {
        if (isLoading) return
        isLoading = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            TEST_AD_UNIT,
            adRequest,
            object : com.google.android.gms.ads.rewarded.RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isLoading = false
                    listener.onAdFailed("Iklan gagal dimuat: ${error.message}")
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isLoading = false
                    listener.onAdLoaded()
                }
            }
        )
    }

    fun show(activity: Activity) {
        val ad = rewardedAd
        if (ad == null) {
            listener.onAdFailed("Iklan belum siap")
            loadAd()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                listener.onAdClosed()
                loadAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                listener.onAdFailed("Iklan gagal ditampilkan: ${adError.message}")
                loadAd()
            }
        }
        ad.show(activity) { rewardItem: RewardItem ->
            listener.onRewardEarned(rewardItem.amount)
        }
    }

    companion object {
        const val TEST_AD_UNIT = "ca-app-pub-1053766309291403/5817632918"
    }
}
