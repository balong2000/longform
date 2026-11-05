import java.util.Properties

plugins {
    id("com.android.application")
}

// CPU to build for. Phones are arm64-v8a (the default); emulator test builds use
//   build.cmd assembleDebug -Pabi=x86_64
val abi = (findProperty("abi") as String?) ?: "arm64-v8a"

// Release signing comes from keystore.properties (git-ignored). Without it the
// release build is left unsigned and only the debug build is installable.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "app.longform"
    // GeckoView 159 is compiled against Android 17 QPR2 (API 37.2).
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "app.longform"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.1"
        // GeckoView is native code; ship only the 64-bit ARM build that every
        // current phone uses. This keeps the APK at roughly a third of a fat build.
        ndk { abiFilters += abi }
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        // Compress Gecko's 147 MB libxul.so inside the APK. The file you send to
        // the phone shrinks from ~190 MB to ~75 MB; Android unpacks it on install.
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    implementation("org.mozilla.geckoview:geckoview-$abi:159.0.20261102144711")
    testImplementation("junit:junit:4.13.2")
}
