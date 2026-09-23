# Android maintenance

This Android project deliberately lives beside the upstream WinForms source. `Source/GrasscutterTools/Resources` remains the data authority.

After an upstream update, run:

```sh
git pull --rebase upstream main
cd Android
./tools/sync-upstream-resources.sh
./gradlew assembleRelease
```

`syncUpstreamResources` also runs automatically before every Gradle build, so a CI APK includes the resources from the exact Git revision being built. Keep Android-specific command templates in Kotlin; do not copy or edit upstream resource files here.

The original project is licensed under AGPL-3.0-or-later. Any distributed derivative must preserve the corresponding license obligations.

The GitHub Actions build uses the Android SDK preinstalled on the hosted Ubuntu runner.
