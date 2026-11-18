# LibGDX ProGuard Rules
-keep class com.badlogic.gdx.backends.android.** { *; }
-keep class com.koceng.oren.** { *; }
-keepclassmembers class * {
    native <methods>;
}
