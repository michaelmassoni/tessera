# R8 rules every app gets, for reproducible builds (docs/fdroid.md). F-Droid rebuilds each
# release from source and checks it matches the APK on GitHub byte for byte, so two builds of the
# same commit must produce the same APK.

# R8 moves kotlinx.coroutines' emitAbort (behind Flow.take / first) into another class and
# sometimes narrows its Continuation parameter to ContinuationImpl, sometimes not, so builds of the
# same commit differed (seen in Music 0.14.3). Keeping the signatures of it and its accessor as written stops that;
# it can still be renamed, and removed when unused.
-keepclassmembers,allowobfuscation,allowshrinking class kotlinx.coroutines.flow.FlowKt__LimitKt {
    *** *emitAbort*(...);
}
