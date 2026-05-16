# Keep Compose runtime annotations & Kotlin metadata used at reflection time.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-dontwarn org.jetbrains.annotations.**
