package com.example.detoxapp

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class AdViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val adRequest = AdRequest.Builder().build()

    private var _homeInterstitialAd: InterstitialAd? = null
    private var _editProfileInterstitialAd: InterstitialAd? = null
    private var _messageSaveInterstitialAd: InterstitialAd? = null
    private var _joinOrCreateGroupInterstitialAd: InterstitialAd? = null

    val homeInterstitialAd: InterstitialAd?
        get() = _homeInterstitialAd

    val editProfileInterstitialAd: InterstitialAd?
        get() = _editProfileInterstitialAd

    val messageSaveInterstitialAd: InterstitialAd?
        get() = _messageSaveInterstitialAd

    val joinOrCreateGroupInterstitialAd: InterstitialAd?
        get() = _joinOrCreateGroupInterstitialAd

    init {
        loadHomeAd()
        loadEditProfileAd()
        loadMessageSaveAd()
        loadCreateOrJoinGroupAd()
    }

    fun loadHomeAd() {
        InterstitialAd.load(
            context,
            "ca-app-pub-7055736346592282/7121992255", // AdUnitID para HomeScreen
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    _homeInterstitialAd = ad
                    Log.d("AdViewModel", "Home Interstitial Ad loaded")
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    _homeInterstitialAd = null
                    Log.e("AdViewModel", "Home Interstitial Ad failed to load: ${adError.message}")
                }
            }
        )
    }

    fun loadCreateOrJoinGroupAd(){
        InterstitialAd.load(
            context,
            "ca-app-pub-7055736346592282/6573357608",
            adRequest,
            object : InterstitialAdLoadCallback(){
                override fun onAdLoaded(ad: InterstitialAd){
                    _joinOrCreateGroupInterstitialAd = ad
                }

                override fun onAdFailedToLoad(p0: LoadAdError) {
                    _joinOrCreateGroupInterstitialAd = null
                }
            }
        )
    }

    fun loadEditProfileAd() {
        InterstitialAd.load(
            context,
            "ca-app-pub-7055736346592282/6138468670", // AdUnitID para EditProfile
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    _editProfileInterstitialAd = ad
                    Log.d("AdViewModel", "EditProfile Interstitial Ad loaded")
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    _editProfileInterstitialAd = null
                    Log.e("AdViewModel", "EditProfile Interstitial Ad failed to load: ${adError.message}")
                }
            }
        )
    }

    fun loadMessageSaveAd() {
        InterstitialAd.load(
            context,
            "ca-app-pub-7055736346592282/6789205369", // AdUnitID para guardar mensaje
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    _messageSaveInterstitialAd = ad
                    Log.d("AdViewModel", "MessageSave Interstitial Ad loaded")
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    _messageSaveInterstitialAd = null
                    Log.e("AdViewModel", "MessageSave Interstitial Ad failed to load: ${adError.message}")
                }
            }
        )
    }

    fun clearHomeAd() {
        _homeInterstitialAd = null
    }

    fun clearEditProfileAd() {
        _editProfileInterstitialAd = null
    }

    fun clearMessageSaveAd() {
        _messageSaveInterstitialAd = null
    }
    fun clearCreateOrJoinAd(){
        _joinOrCreateGroupInterstitialAd = null
    }
}