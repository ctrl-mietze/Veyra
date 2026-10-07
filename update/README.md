# Veyra Root update channel

`channel.json` is the lightweight update metadata read by Veyra Root.

The client compares **versionCode**, not only a version string, so a newer development build is not accidentally downgraded by the stable channel.

Before installation the client can validate:

- package name
- APK versionCode
- APK SHA-256
- signing certificate SHA-256

If CVeyra has a root backend the verified APK may be installed through that privileged path. Otherwise Veyra opens Android's normal package installer.

The current stable entry points at the already-published v1.0.0 release. Updating this metadata does not create a GitHub release.
