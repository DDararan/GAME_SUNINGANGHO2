import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// 게임 규칙: Android에 의존하지 않는 순수 Kotlin 모듈 (전투 시뮬레이션, 성장 공식, 세이브).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
