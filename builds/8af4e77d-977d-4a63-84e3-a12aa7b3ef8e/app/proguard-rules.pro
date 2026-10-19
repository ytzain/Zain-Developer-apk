# Keep WebView JS bridges if added later
-keepattributes JavascriptInterface
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
-keep class com.lovable.app.zainworkplace.** { *; }
-dontwarn **
