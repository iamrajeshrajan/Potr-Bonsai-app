# Keep WebView JavascriptInterface methods
-keepclassmembers class in.potr.bonsai.bridge.BonsaiJavascriptBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
