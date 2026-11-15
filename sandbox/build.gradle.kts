import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    sourceSets["main"].kotlin.srcDir("../shared/src/main/kotlin")
}

dependencies {
    implementation(compose.desktop.currentOs)
    // Bundle the Windows renderer too so the uber jar built on any OS runs on Windows.
    implementation(compose.desktop.windows_x64)
    implementation(compose.material3)
    implementation(compose.foundation)
    implementation(libs.kotlinx.coroutines.swing)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

compose.desktop {
    application {
        mainClass = "com.truckdash.sandbox.MainKt"
        nativeDistributions {
            packageName = "truck-dash-sandbox"
            packageVersion = "1.0.0"
        }
    }
}

// Headless PNG renders of the real UI: ./gradlew :sandbox:screenshots
tasks.register<JavaExec>("screenshots") {
    group = "verification"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.truckdash.sandbox.ScreenshotsKt")
    args(layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
    systemProperty("java.awt.headless", "true")
}

// Cross-platform fat jar: ./gradlew :sandbox:sandboxJar  ->  build/libs/truck-dash-sandbox.jar
// Includes the Windows AND Linux Skia renderers so one jar runs on Windows with just `java -jar`.
tasks.register<Jar>("sandboxJar") {
    group = "build"
    archiveFileName.set("truck-dash-sandbox.jar")
    manifest { attributes["Main-Class"] = "com.truckdash.sandbox.MainKt" }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets["main"].output)
    from({ configurations["runtimeClasspath"].filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF.bak")
}
