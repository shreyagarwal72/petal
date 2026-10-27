# Baseline profile generator (optional, one-time setup)

`app/src/main/baseline-prof.txt` already ships a wildcard profile. This module records an exact one.

1. In `settings.gradle` add: `include ':baselineprofile'`
2. In root `build.gradle` buildscript classpath add: `classpath 'androidx.baselineprofile:androidx.baselineprofile.gradle.plugin:1.3.3'`
   (use a version compatible with your AGP 9.x)
3. In `app/build.gradle` plugins add `id 'androidx.baselineprofile'` and dependency
   `baselineProfile project(':baselineprofile')`
4. With a phone connected run `./gradlew :app:generateBaselineProfile`
   (then delete `app/src/main/baseline-prof.txt` if you want only the recorded one).

Not wired in by default because it could not be built or verified offline.
