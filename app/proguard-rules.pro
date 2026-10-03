# ffmpeg ses çözücüsü: DefaultRenderersFactory yansımayla yükler, JNI yerel metotları var
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keepclasseswithmembernames class * { native <methods>; }

# kotlinx.serialization (Catalog önbelleği vb.)
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.fitifiti.tv.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.fitifiti.tv.**$$serializer { *; }

# Çökme raporlarında okunur yığın (sınıf adları korunur, satır numaraları kalır)
-keepattributes SourceFile,LineNumberTable
-dontobfuscate

# zxing / okhttp uyarıları
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Hesap bilgilerini şifreleyen EncryptedSharedPreferences (Tink): protobuf alanları yansımayla okunur
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
