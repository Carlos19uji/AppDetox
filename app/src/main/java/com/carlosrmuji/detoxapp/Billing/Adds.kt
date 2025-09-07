package com.carlosrmuji.detoxapp.Billing

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AdViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val adRequest = AdRequest.Builder().build()
    private val firestore = Firebase.firestore
    private val auth = FirebaseAuth.getInstance()

    private var _homeInterstitialAd: InterstitialAd? = null
    private var _editProfileInterstitialAd: InterstitialAd? = null
    private var _messageSaveInterstitialAd: InterstitialAd? = null
    private var _joinOrCreateGroupInterstitialAd: InterstitialAd? = null
    private var _aichatInterstitialAd: InterstitialAd? = null
    private var _restrictAppAd: InterstitialAd? = null
    private var _restrictedPhoneAdd: InterstitialAd? = null

    val homeInterstitialAd: InterstitialAd? get() = _homeInterstitialAd
    val editProfileInterstitialAd: InterstitialAd? get() = _editProfileInterstitialAd
    val messageSaveInterstitialAd: InterstitialAd? get() = _messageSaveInterstitialAd
    val joinOrCreateGroupInterstitialAd: InterstitialAd? get() = _joinOrCreateGroupInterstitialAd
    val aichatInterstitialAd: InterstitialAd? get() = _aichatInterstitialAd
    val restrictAppAd: InterstitialAd? get() = _restrictAppAd
    val restrictedPhoneAd: InterstitialAd? get() = _restrictedPhoneAdd

    init {
        viewModelScope.launch {
            checkUserPlanAndLoadAds()
        }
    }

    private suspend fun checkUserPlanAndLoadAds() {
        val userId = auth.currentUser?.uid ?: return
        try {
            val planSnapshot = firestore
                .collection("users")
                .document(userId)
                .collection("plan")
                .document("plan")
                .get()
                .await()

            val userPlan = planSnapshot.getString("plan") ?: "base_plan"

            if (userPlan == "base_plan") {
                loadAllAds()
            } else {
                clearAllAds()
            }

        } catch (e: Exception) {
            Log.e("AdViewModel", "Error fetching user plan: ${e.message}")
            // Si falla, mejor no cargar anuncios por precaución
            clearAllAds()
        }
    }

    fun loadAllAds() {
        loadHomeAd()
        loadEditProfileAd()
        loadMessageSaveAd()
        loadCreateOrJoinGroupAd()
        loadAiChatAd()
        loadRestrictAppsAd()
        loadRestrictedPhoneAd()
    }

    fun clearAllAds() {
        _homeInterstitialAd = null
        _editProfileInterstitialAd = null
        _messageSaveInterstitialAd = null
        _joinOrCreateGroupInterstitialAd = null
        _aichatInterstitialAd = null
        _restrictAppAd = null
    }

    fun loadHomeAd() {
        InterstitialAd.load(
            context,
            "ca-app-pub-7055736346592282/7121992255",
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    _homeInterstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    _homeInterstitialAd = null
                }
            }
        )
    }

    fun loadRestrictAppsAd(){
        InterstitialAd.load(
            context,
            "ca-app-pub-7055736346592282/1407022728",
            adRequest,
            object : InterstitialAdLoadCallback(){
                override fun onAdLoaded(ad: InterstitialAd) {
                    _restrictAppAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    _restrictAppAd = null
                }
            }
        )
    }

    fun loadRestrictedPhoneAd(){
        InterstitialAd.load(
            context,
            "ca-app-pub-7055736346592282/9332575160",
            adRequest,
            object : InterstitialAdLoadCallback(){
                override fun onAdLoaded(ad: InterstitialAd) {
                    _restrictedPhoneAdd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    _restrictedPhoneAdd = null
                }
            }
        )
    }


    fun loadEditProfileAd() {
        InterstitialAd.load(
            context,
            "ca-app-pub-7055736346592282/6138468670",
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    _editProfileInterstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    _editProfileInterstitialAd = null
                }
            }
        )
    }

    fun loadMessageSaveAd() {
        InterstitialAd.load(
            context,
            "ca-app-pub-7055736346592282/6789205369",
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    _messageSaveInterstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    _messageSaveInterstitialAd = null
                }
            }
        )
    }

    fun loadCreateOrJoinGroupAd() {
        InterstitialAd.load(
            context,
            "ca-app-pub-7055736346592282/6573357608",
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    _joinOrCreateGroupInterstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    _joinOrCreateGroupInterstitialAd = null
                }
            }
        )
    }

    fun loadAiChatAd() {
        InterstitialAd.load(
            context,
            "ca-app-pub-7055736346592282/4705356095",
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    _aichatInterstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    _aichatInterstitialAd = null
                }
            }
        )
    }

    // Métodos manuales si los necesitas
    fun clearHomeAd() { _homeInterstitialAd = null }
    fun clearEditProfileAd() { _editProfileInterstitialAd = null }
    fun clearMessageSaveAd() { _messageSaveInterstitialAd = null }
    fun clearCreateOrJoinAd() { _joinOrCreateGroupInterstitialAd = null }
    fun clearAIChatAd() { _aichatInterstitialAd = null }
    fun clearRestrictionAppAd() { _restrictAppAd = null }
    fun clearRestrictedPhoneAd(){ _restrictedPhoneAdd =  null}
}