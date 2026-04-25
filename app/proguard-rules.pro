# ══════════════════════════════════════════════════════════════════════
# R8 Full Mode — 精确 keep，最大化代码/资源裁剪
# ══════════════════════════════════════════════════════════════════════

# ── Compose Runtime ──
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ── fan.miuix（XML 布局/drawable/preference 大量反射引用，必须整体保留） ──
-keep class fan.** { *; }
-dontwarn com.android.internal.view.menu.**
-dontwarn miui.util.**

# ── AndroidX Preference（fan.preference 内部调用 getClass().getPackage().getName()，R8 full mode 需保留 package 元数据） ──
-keep class androidx.preference.** { *; }

# ── Provision OOBE ──
-keep class com.box.app.provision.** { *; }

# ── Miuix（仅保留公开 API，内部实现允许混淆） ──
-keep class top.yukonga.miuix.kmp.basic.** { *; }
-keep class top.yukonga.miuix.kmp.preference.** { *; }
-keep class top.yukonga.miuix.kmp.overlay.** { *; }
-keep class top.yukonga.miuix.kmp.theme.** { *; }
-keep class top.yukonga.miuix.kmp.window.** { *; }
-keep class top.yukonga.miuix.kmp.shapes.** { *; }
-keep class top.yukonga.miuix.kmp.icon.** { *; }

# ── Kyant Backdrop ──
-keep class com.kyant.backdrop.** { *; }

# ── OkHttp ──
-dontwarn okhttp3.internal.platform.**
-keep class okhttp3.internal.publicsuffix.PublicSuffixDatabase { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ── Coil ──
-keep class coil3.network.** { *; }
-keep class coil3.decode.** { *; }

# ── Sora Editor (使用反射加载语言/主题) ──
-keep class io.github.rosemoe.sora.** { *; }
-keep class io.github.rosemoe.sora.langs.** { *; }

# ── Dexlib2 ──
-keep class org.smali.dexlib2.** { *; }
-dontwarn com.google.common.**
-dontwarn javax.annotation.**

# ── App 数据模型（序列化/反射） ──
-keep class com.box.app.data.model.** { *; }
-keep class com.box.app.data.backend.BoxApi$* { *; }

# ── AboutLibraries ──
-keep class com.mikepenz.aboutlibraries.** { *; }

# ── R8 Full Mode：ServiceLoader/反射安全 ──
-keep class kotlin.reflect.jvm.internal.** { *; }
-dontwarn kotlin.reflect.jvm.internal.**

# ── Kotlin Cloneable (Sora Editor 依赖) ──
-dontwarn kotlin.Cloneable$DefaultImpls

# ── 保留堆栈跟踪信息 ──
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── 移除日志（release 构建） ──
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
