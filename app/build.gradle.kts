import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import java.util.concurrent.TimeUnit
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinCompose)
    alias(libs.plugins.aboutLibraries)
}

val keystorePropertiesFile = rootProject.file("app/keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

fun getApkTimestamp(): String {
    return SimpleDateFormat("yyyyMMdd-HHmm").format(Date())
}

private fun String.execute(): String {
    return try {
        val process = ProcessBuilder(this.split(" ")).start()
        process.waitFor(1, TimeUnit.SECONDS)
        process.inputStream.bufferedReader().readText().trim()
    } catch (e: Exception) {
        ""
    }
}

fun getVersionCode(): Int {
    val gitCommitCount = "git rev-list --count HEAD".execute()
    if (gitCommitCount.isNotEmpty()) {
        return gitCommitCount.toInt()
    }
    val epochStart = 1640995200000 // 2022-01-01 00:00:00
    return ((System.currentTimeMillis() - epochStart) / 60000).toInt()
}

fun getVersionName(): String {
    val gitShortHash = "git rev-parse --short HEAD".execute()
    if (gitShortHash.isNotEmpty()) {
        return gitShortHash
    }
    return SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
}

android {
    namespace = "com.box.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.box.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = getVersionCode()
        versionName = getVersionName()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions.add("brand")
    productFlavors {
        create("box") {
            dimension = "brand"
        }
        create("bfr") {
            dimension = "brand"
            applicationId = "com.bfr.app"
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // R8 完整模式
    @Suppress("UnstableApiUsage")
    experimentalProperties["android.experimental.r8.fullMode"] = true

    packaging {
        resources {
            excludes += setOf(
                "META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/*.kotlin_module",
                "kotlin/**",
                "DebugProbesKt.bin"
            )
        }
    }

}

base {
    archivesName.set("app-${getApkTimestamp()}")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            // 移除运行时断言，减小 release 体积
            "-Xno-call-assertions",
            "-Xno-param-assertions",
            "-Xno-receiver-assertions"
        )
    }
}

composeCompiler {
    // 启用强跳过模式 — 减少不必要的重组
    enableStrongSkippingMode = true
    // 启用内在记忆化 — 自动 remember lambda
    enableIntrinsicRemember = true
    // 非跳过组合函数生成更小的代码
    enableNonSkippingGroupOptimization = true
    // 稳定性配置文件
    stabilityConfigurationFile = project.layout.projectDirectory.file("compose-stability.conf")
}

aboutLibraries {
    collect {
        filterVariants.addAll("boxRelease", "bfrRelease")
    }
    export {
        outputFile = layout.buildDirectory.file("generated/aboutlibraries/aboutlibraries.json").get().asFile
        variant = "boxRelease"
    }

    library {
        duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.MERGE
        duplicationRule = com.mikepenz.aboutlibraries.plugin.DuplicateRule.SIMPLE
    }
}

abstract class PrepareAboutLibrariesResTask : DefaultTask() {
    @get:InputFile
    abstract val inputJson: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val inFile = inputJson.get().asFile
        val outDir = outputDir.get().asFile
        outDir.mkdirs()
        inFile.copyTo(outDir.resolve("aboutlibraries.json"), overwrite = true)
    }
}

val prepareAboutLibrariesRes = tasks.register<PrepareAboutLibrariesResTask>("prepareAboutLibrariesRes") {
    dependsOn("exportLibraryDefinitions")
    inputJson.set(layout.buildDirectory.file("generated/aboutlibraries/aboutlibraries.json"))
    outputDir.set(layout.buildDirectory.dir("generated/aboutlibraries/res/raw"))
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val ts = getApkTimestamp()
        val prefix = when {
            variant.productFlavors.any { it.second == "bfr" } -> "bfr"
            variant.productFlavors.any { it.second == "box" } -> "box"
            else -> "app"
        }

        val variantName = variant.name
        val variantNameCap = variantName.replaceFirstChar { c ->
            if (c.isLowerCase()) c.titlecase(Locale.ROOT) else c.toString()
        }

        val copyTaskName = "copy${variantNameCap}Apk"
        val assembleTaskName = "assemble${variantNameCap}"

        val copyTaskProvider = tasks.register<Copy>(copyTaskName) {
            val srcDir = layout.buildDirectory.dir("outputs/apk/$variantName")
            from(srcDir) {
                include("*.apk")
            }
            into(layout.buildDirectory.dir("outputs/apkRenamed/$variantName"))
            rename { _ -> "${prefix}-${ts}.apk" }
        }

        afterEvaluate {
            tasks.findByName(assembleTaskName)?.finalizedBy(copyTaskProvider)
        }

        variant.sources.res?.addGeneratedSourceDirectory(
            prepareAboutLibrariesRes,
            PrepareAboutLibrariesResTask::outputDir
        )
    }
}

tasks.named("preBuild") {
    dependsOn("exportLibraryDefinitions")
    dependsOn(prepareAboutLibrariesRes)
}

dependencies {
    implementation("com.github.MiChongs:hyperx-compose:v0.1.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(libs.okhttp)
    implementation(libs.libsu.core)
    implementation(libs.libsu.io)
    implementation(libs.xxpermissions)

    implementation(platform(libs.sora.bom))
    implementation(libs.sora.editor)
    implementation(libs.sora.language.textmate)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.ripple)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)

    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.shapes)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.navigation3.ui)

    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.dexlib2)

    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.core)
    implementation(libs.aboutlibraries.compose.m3)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
