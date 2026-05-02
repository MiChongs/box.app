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

// ───── git-derived versioning ─────
//
// 设计目标：
//   1. versionName 以最近的 v* tag 为锚（如 v1.0.0 → 1.0.0）；
//      偏离 tag 时附 ahead 数 + short hash（1.0.0-3-abc1234）。
//   2. versionCode 由 SemVer 主次补 + ahead 编码，保证全局单调：
//      major*1_000_000 + minor*10_000 + patch*100 + min(ahead, 99)。
//      patch 内最多 99 个 dev 构建上限即满；范围远小于 Android 上限 2.1e9。
//   3. 不在 git 仓库 / 无 tag 时退化到 epoch-based timestamp，构建仍可进行。
//
// 触发新版本：打 v<major>.<minor>.<patch> tag → push → release workflow 自动构建。
// 同一 tag 上重复构建：versionCode 与 versionName 完全一致（可重复构建友好）。

private fun String.exec(): String = try {
    val proc = ProcessBuilder(*this.split(" ").toTypedArray())
        .redirectErrorStream(true)
        .start()
    proc.waitFor(2, TimeUnit.SECONDS)
    proc.inputStream.bufferedReader().readText().trim()
} catch (_: Exception) { "" }

private data class GitVersionInfo(
    val baseVersion: String,    // "1.2.3"（无 v 前缀，无 pre-release 后缀）
    val preRelease: String,     // "-rc.1" / "-beta" / ""
    val ahead: Int,             // tag → HEAD 的 commit 数；无 tag 时是 HEAD 总 commit 数
    val shortHash: String,
    val hasTag: Boolean,
)

private fun gitVersionInfo(): GitVersionInfo? {
    if ("git rev-parse --is-inside-work-tree".exec() != "true") return null

    val rawTag = "git describe --tags --abbrev=0 --match v*".exec()
    val hasTag = rawTag.isNotBlank()

    val tagBody = if (hasTag) rawTag.removePrefix("v") else "0.0.0"
    val dashIdx = tagBody.indexOf('-')
    val baseVersion = if (dashIdx >= 0) tagBody.substring(0, dashIdx) else tagBody
    val preRelease = if (dashIdx >= 0) tagBody.substring(dashIdx) else ""

    val ahead = if (hasTag) {
        "git rev-list --count $rawTag..HEAD".exec().toIntOrNull() ?: 0
    } else {
        "git rev-list --count HEAD".exec().toIntOrNull() ?: 0
    }
    val shortHash = "git rev-parse --short HEAD".exec()

    return GitVersionInfo(baseVersion, preRelease, ahead, shortHash, hasTag)
}

fun getVersionCode(): Int {
    val info = gitVersionInfo()
    if (info != null) {
        val parts = info.baseVersion.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        val ahead = info.ahead.coerceAtMost(99)
        val code = major * 1_000_000 + minor * 10_000 + patch * 100 + ahead
        if (code > 0) return code
    }
    // fallback：自 2022-01-01 起的分钟数
    return ((System.currentTimeMillis() - 1_640_995_200_000L) / 60_000L).toInt()
}

fun getVersionName(): String {
    val info = gitVersionInfo()
        ?: return SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())

    val tag = "${info.baseVersion}${info.preRelease}"
    return when {
        info.hasTag && info.ahead == 0 -> tag
        info.shortHash.isNotBlank() -> "$tag-${info.ahead}-${info.shortHash}"
        else -> "$tag-${info.ahead}"
    }
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
        aidl = true
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

// 主 APK 名：BoxReApp-{versionName}-{flavor}-{buildType}.apk
// flavor / buildType 后缀由 Android Plugin 自动追加。
base {
    archivesName.set("BoxReApp-${getVersionName()}")
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
        val versionName = getVersionName()
        val prefix = when {
            variant.productFlavors.any { it.second == "bfr" } -> "bfr"
            variant.productFlavors.any { it.second == "box" } -> "box"
            else -> "app"
        }
        // debug 构建在副本名上区分一下，避免与 release 同名相互覆盖。
        val buildTypeSuffix = if (variant.buildType == "debug") "-debug" else ""

        val variantName = variant.name
        val variantNameCap = variantName.replaceFirstChar { c ->
            if (c.isLowerCase()) c.titlecase(Locale.ROOT) else c.toString()
        }

        val copyTaskName = "copy${variantNameCap}Apk"
        val assembleTaskName = "assemble${variantNameCap}"

        // 提供一份"flat"命名的 APK 副本到 outputs/apkRenamed/{variant}/，
        // 便于本地分发：BoxReApp-{prefix}-{versionName}{-debug}.apk
        val copyTaskProvider = tasks.register<Copy>(copyTaskName) {
            val srcDir = layout.buildDirectory.dir("outputs/apk/$variantName")
            from(srcDir) {
                include("*.apk")
            }
            into(layout.buildDirectory.dir("outputs/apkRenamed/$variantName"))
            rename { _ -> "BoxReApp-${prefix}-${versionName}${buildTypeSuffix}.apk" }
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


configurations.configureEach {
    // fan.miuix:appcompat 替代了 androidx appcompat-resources，排除后者避免重复类
    exclude(group = "androidx.appcompat", module = "appcompat-resources")
}

dependencies {
    implementation(project(":libs:hyperx-compose"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // androidx.appcompat + material 由 fan.miuix:appcompat 替代
    // implementation(libs.androidx.appcompat)
    // implementation(libs.material)

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

    implementation(libs.material.kolor)

    // fan.miuix (MIUI provision animation framework)
    implementation(libs.fan.miuix.folme)
    implementation(libs.fan.miuix.animation)
    implementation(libs.fan.miuix.core)
    implementation(libs.fan.miuix.appcompat)
    implementation(libs.fan.miuix.transition)
    implementation(libs.fan.miuix.theme)
    implementation(libs.fan.miuix.basewidget)
    implementation(libs.fan.miuix.preference)
    implementation(libs.fan.miuix.bottomsheet)
    implementation(libs.fan.miuix.springback)

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

