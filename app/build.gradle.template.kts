plugins {
    id("com.android.application")
}

android {
    compileSdk = 26
    defaultConfig {
        applicationId = "com.pitecan.slime2"
        minSdk = 26
        targetSdk = 26
        versionCode = VERSIONCODE
        versionName = "VERSION"
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

    useLibrary("org.apache.http.legacy")
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("com.android.support.constraint:constraint-layout:1.1.3")
    implementation("com.android.support:design:26.0.0")
    testImplementation("junit:junit:4.12")
    androidTestImplementation("com.android.support.test:runner:1.0.2")
    androidTestImplementation("com.android.support.test.espresso:espresso-core:3.0.2")
}
