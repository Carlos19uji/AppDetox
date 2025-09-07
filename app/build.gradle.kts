import java.util.Properties

plugins {
    alias(libs.plugins.android.application) apply true
    alias(libs.plugins.compose.compiler)
    id("com.google.gms.google-services") version "4.4.2" apply true
    id("org.jetbrains.kotlin.android") version "2.0.0" apply true
}

android {
    namespace = "com.carlosrmuji.detoxapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.carlosrmuji.detoxapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "1.3.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val propFile = file("signing.properties")
    val props = if (propFile.exists()) {
        val props = Properties()
        props.load(propFile.inputStream())
        props
    } else {
        null
    }

    if (props != null) {
        val pStoreFile = props["STORE_FILE"] as? String
        val pStorePassword = props["STORE_PASSWORD"] as? String
        val pKeyAlias = props["KEY_ALIAS"] as? String
        val pKeyPassword = props["KEY_PASSWORD"] as? String
        if (!pStoreFile.isNullOrBlank()
            && !pStorePassword.isNullOrBlank()
            && !pKeyAlias.isNullOrBlank()
            && !pKeyPassword.isNullOrBlank()
        ) {
            signingConfigs {
                create("release") {
                    storeFile = file(pStoreFile)
                    storePassword = pStorePassword
                    keyAlias = pKeyAlias
                    keyPassword = pKeyPassword
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Aplica la firma solo si existe
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.preference.ktx)
    val accompanistVersion = "0.28.0"

    // UI/Jetpack Compose
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.compose.material:material:1.5.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Accompanist
    implementation("com.google.accompanist:accompanist-systemuicontroller:$accompanistVersion")
    implementation("com.google.accompanist:accompanist-insets:$accompanistVersion")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.9.0"))
    implementation("com.google.firebase:firebase-perf")
    implementation("com.google.firebase:firebase-analytics:21.0.0")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-messaging:23.0.0")
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha03")

    // Branch.io (Dynamic Links)
    implementation("io.branch.sdk.android:library:5.18.0")

    // Referrer tracking para diferentes tiendas
    implementation("com.google.android.gms:play-services-ads-identifier:18.0.1")
    implementation("com.huawei.hms:ads-identifier:3.4.62.300")
    implementation("com.huawei.hms:ads-installreferrer:3.4.39.302")
    implementation("store.galaxy.samsung.installreferrer:samsung_galaxystore_install_referrer:4.0.0")
    implementation("com.miui.referrer:homereferrer:1.0.0.7")

    // Billing y Ads
    implementation("com.android.billingclient:billing:8.0.0")
    implementation("com.google.android.gms:play-services-ads:22.6.0")

    // Otros
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("io.coil-kt:coil-compose:2.4.0")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.compose.material:material-icons-extended:1.6.1")

    // Version Catalog (libs.*)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.firebase.storage.ktx)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // Debug
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}