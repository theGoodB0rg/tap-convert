# Consumer proguard rules for core:billing — keep BillingClient callbacks, obfuscate entitlement internals
-keep class com.android.billingclient.api.** { *; }
-keep class com.tapconvert.core.billing.EntitlementVerifier { *; }
-keep class com.tapconvert.core.billing.EntitlementSnapshot { *; }
