plugins {
    id("com.android.application")
    // No org.jetbrains.kotlin.android here, deliberately. AGP 9 brings
    // its own Kotlin support, and applying the plugin as well registers
    // the `kotlin` extension twice. The rest of this workspace does it
    // the same way.
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.krafttools.barokraft"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.krafttools.barokraft"
        minSdk = 26
        targetSdk = 37
        versionCode = 6
        versionName = "0.3.3"
    }

    testOptions {
        unitTests {
            // `android.util.Log` throws by default in a JVM test, and the
            // sensor and network layers log on the way through. Returning
            // defaults for the log is what lets those tests assert the
            // thing they were written to assert.
            //
            // Only the log. Anything that actually needs Android is left
            // throwing, because a test that quietly got nulls from a
            // framework call is a test that quietly stopped testing.
            isReturnDefaultValues = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Debug-signed on purpose, and stated in the README. There is
            // no release keystore for this project: no store distribution,
            // GitHub releases only. A user who sideloads gets a build the
            // author also built.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    testImplementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    // For `viewModel()`. The screen's state has to outlive a rotation.
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.activity:activity-compose:1.12.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    // Kraft Foundation — composite build, substituted in settings.gradle.kts.
    implementation("com.kraft:kraft-ui")
    implementation("com.kraft:kraft-core")

    // OkHttp, and *not* a hand-rolled socket client like PulseKraft uses.
    //
    // That is a deliberate difference, not an inconsistency. PulseKraft
    // rolled its own HTTP because `setReceiveBufferSize` must be called
    // before connect, and no stock client exposes the socket at that
    // point — a real constraint with a real reason.
    //
    // BaroKraft has no such constraint. It makes ordinary HTTPS GETs to
    // one host and needs TLS validation, redirects and connection reuse
    // to be correct. Hand-rolling that would be re-implementing a
    // security-sensitive component for no gain. The right tool for the
    // job, and the reason differs from our sibling's.
    implementation("com.squareup.okhttp3:okhttp:5.1.0")

    testImplementation("junit:junit:4.13.2")
    // A real Application for the ViewModel tests. Robolectric is not used
    // and no android.jar is stubbed in: the ViewModel takes an
    // Application and a Context, and the test needs those to be real
    // objects rather than nulls that throw for the wrong reason.
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.robolectric:robolectric:4.16")

    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // `debug`, not `androidTest`. This artefact contributes the host
    // activity that Compose's test rule launches, and it has to be
    // merged into the app being tested.
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
