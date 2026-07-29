# WebView + JavascriptInterface safety
-keepattributes JavascriptInterface
-keepattributes *Annotation*,Signature,SourceFile,LineNumberTable
-keep public class * extends android.webkit.WebChromeClient
-keep public class * extends android.webkit.WebViewClient
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
# Suppress noisy warnings from optional deps
-dontwarn org.jetbrains.annotations.**
-dontwarn kotlin.**
