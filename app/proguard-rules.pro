# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Names are kept as they are in the source. R8 still drops what nothing reaches and still
# optimizes what is left — which is where the whole of the saving is: renaming is worth a
# few per cent of the APK against the several times over the shrinking takes off.
#
# The code is public, so renaming hides nothing from anybody, and keeping the names takes
# away a class of failure the app is exposed to: the navigation routes and the preloaded
# dictionary names are both found by name at run time, the first as @Serializable classes,
# the second through getIdentifier.
-dontobfuscate

# Where a frame comes from, so a crash report can be read as it stands. Inlining still
# moves frames about, and mapping.txt is what untangles that.
-keepattributes SourceFile,LineNumberTable
