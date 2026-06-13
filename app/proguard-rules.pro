# Google Play Billing
-keep class com.android.vending.billing.** { *; }
-keep class com.android.billingclient.** { *; }

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.* <methods>;
}
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel
-keepclassmembers class * {
    @dagger.hilt.android.internal.lifecycle.HiltViewModelMap$Key *;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Moshi / Retrofit
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keep @com.squareup.moshi.JsonClass class *
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-keep class **JsonAdapter { *; }
-keep class com.cheradip.ailanguagetutor.core.locale.** { *; }
-keep class com.cheradip.ailanguagetutor.core.network.** { *; }
-keep class com.cheradip.ailanguagetutor.core.pack.** { *; }
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Speech recognition
-keep class android.speech.** { *; }

# App models & enums used by DataStore / JSON
-keep class com.cheradip.ailanguagetutor.core.model.** { *; }
-keep class com.cheradip.ailanguagetutor.core.speech.** { *; }
-keep class com.cheradip.ailanguagetutor.core.audio.** { *; }

# PDFBox Android (scanner PDF export) — optional JPEG2000 codec not bundled
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn com.google.android.gms.vision.**
-keep class com.tom_roush.pdfbox.** { *; }
-keep class org.bouncycastle.** { *; }

# Scanner image pipeline + ML Kit document scanner
-keep class com.cheradip.ailanguagetutor.core.image.** { *; }
-keep class com.cheradip.ailanguagetutor.feature.scanner.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class com.cheradip.ailanguagetutor.feature.scanner.** { *; }
-keep class com.google.mlkit.vision.documentscanner.** { *; }

# OpenCV (lazy-loaded for document edge detection)
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
