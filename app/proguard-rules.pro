# Application Proguard Rules — launch hardening (R8 full mode compatible)
-keep class com.android.billingclient.api.** { *; }
-keep class com.google.android.play.integrity.** { *; }
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.ump.** { *; }
-keep class androidx.security.crypto.** { *; }
# Keep entitlement/verifier public surface so Play callbacks survive obfuscation;
# internals are still renamed.
-keep class com.tapconvert.core.billing.EntitlementVerifier { *; }
-keep class com.tapconvert.core.billing.EntitlementSnapshot { *; }
-keep class com.tapconvert.core.billing.EntitlementSource { *; }
-keep class com.tapconvert.core.ads.BillingManager { *; }
# Compose / Room / Media3 keeps (minimal; AGP adds the rest)
-dontwarn com.google.android.play.integrity.**
