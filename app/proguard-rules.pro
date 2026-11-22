# R8 / ProGuard. The packet codec and template models are plain Kotlin.
# org.json is called with explicit keys, so field names do not need to survive,
# but keeping the model and protocol types makes a shrunk stack trace readable.

-keepattributes SourceFile,LineNumberTable

-keep class com.racingwheelpro.model.** { *; }
-keep class com.racingwheelpro.proto.** { *; }
-keep class com.racingwheelpro.catalog.WheelCatalog { *; }
-keep class com.racingwheelpro.catalog.EntitlementPolicy { *; }
-keep class com.racingwheelpro.catalog.EntitlementPolicy$Query { *; }

# Flavor entry point. Only one of these is on a given variant's classpath.
-keep class com.racingwheelpro.billing.PremiumGate { *; }
-keep class com.racingwheelpro.billing.FreePremiumGate { *; }
-keep class com.racingwheelpro.billing.OpenPremiumGate { *; }
-keep class com.racingwheelpro.billing.PlayPremiumGate { *; }
-keep class com.racingwheelpro.voice.** { *; }
-keep class io.github.jaredmdobson.concentus.** { *; }
