# Veyra Root v2.0.0 — Public Release

Veyra Root 2.0 is the second public release of Veyra Root and the first public release based on the current Builder Workingbench, DF+, Magic Builder, Market/API and mandatory-update generation.

This release keeps the established Android package identity and Veyra signing certificate so it remains update-compatible with prior official Veyra Root builds that use the same signer.

## Release identity

- Package: `ctrl.mietze.veyraroot`
- Version: `2.0.0`
- versionCode: `200000000`
- Signer SHA-256: `f1d8f55217d1149f88db9e1642735363d0198c33c8da8d77547987cedf08c582`
- APK SHA-256: `edc2af1e93861d60e94e99fff7aa776ba119280d63fa8bd5473e2ed84b55bb7c`

The release asset is the already prepared and signed Public 2.0 APK. It is not a newly generated CI replacement artifact.

## Mandatory public update channel

Veyra Root 2.0 introduces the public release channel used by the hardened build.

When the published GitHub channel reports a `versionCode` higher than the installed public build, the Public Release treats that update as mandatory.

The update path verifies:

- the Veyra package name,
- the published versionCode,
- the downloaded APK SHA-256 when supplied,
- the Veyra signing certificate.

The stable public baseline for this release is `200000000`. Future public releases must use a higher versionCode.

## ReleaseGuard / anti-tamper hardening

Public 2.0 contains the dedicated Veyra ReleaseGuard layer rather than relying only on Android's normal package-signature checks.

The current protection set includes:

- official signing-certificate pinning,
- package and build identity checks,
- non-debuggable / non-test-only verification,
- split/repack detection,
- trusted APK source/path checks,
- SHA-256 integrity checks for 22 critical APK entries and native libraries,
- debugger detection,
- `TracerPid` / tracing checks,
- Frida marker detection,
- Xposed / LSPosed marker detection,
- Substrate marker detection,
- `LD_PRELOAD` checks,
- suspicious process-map checks,
- suspicious thread checks,
- suspicious file-descriptor checks,
- repeated runtime guard/watchdog verification.

The public manifest also disables backups, disables cleartext traffic and does not expose shell profiling.

No client-side Android protection is mathematically unbreakable. These layers are intended to make casual resigning, repacking, asset replacement, binary patching and runtime instrumentation substantially harder while preserving legitimate root use.

## Public packaging pipeline

The final Public 2.0 packaging path uses the proven lighter Android packaging route while retaining public-release security semantics.

For the distributed build:

- Android debuggable state is disabled,
- JNI debugging is disabled,
- `PUBLIC_RELEASE=true`,
- `RELEASE_HARDENED=true`,
- the established Veyra signer is used,
- ReleaseGuard remains active,
- the release identity is `2.0.0 / 200000000`.

In other words, the lightweight Gradle path is only a packaging route; the shipped APK is not a debuggable public build.

## Version information and Veyra links

Veyra Root 2.0 exposes the public release identity directly in the application:

- System Management → Version Info,
- About → Version Info,
- Veyra Root 2.0 / Public Release labeling,
- Explore Veyra opens the official Veyra website instead of the older disabled placeholder.

## Builder Workingbench

The current Builder Workingbench generation is included.

It consolidates builder workflows into a dedicated area while keeping per-builder configuration with the corresponding builder.

Current functionality includes:

- Veyra Builder sessions and resume,
- OTA range extraction,
- local image/hash comparison,
- strategy matrix,
- evidence grouping and conflict tracking,
- risk modes,
- candidate/session/report export,
- report import,
- Termux helper generation,
- dependency checking and automatic package preparation,
- Android Download output handling with fallback paths,
- ADB and Shizuku/rish-aware helper paths.

## DF Compatible / Veyra DF+

The DF route now uses explicit kernel/KMI evidence instead of blindly following the userspace Android version.

Included work covers:

- DF Compatible,
- Veyra DF+,
- Samsung / DEFEX-aware routing,
- OnePlus / Oppo / realme profiles,
- generic GKI routing,
- installed manager/ksud awareness,
- KernelSU-Next package awareness,
- kernel-release-first KMI selection,
- recovery/runtime controls,
- root-on-boot and soft-reboot options where supported,
- module-disable controls,
- image-partition protection controls.

Legacy 4.19 targets such as Kona remain evidence-driven and are not falsely promoted into a runnable modern GKI route.

## Magic Builder and source intelligence

Magic Builder retains Veyra's evidence-first model:

- OTA/catalog-assisted source discovery,
- boot/kernel evidence extraction,
- device/kernel-family matching,
- local and remote research sources,
- analysis-only outcomes when a runnable baseline is not proven.

Veyra does not invent kernel addresses, payload constants or physical-load values merely because a nearby device or kernel family looks similar.

## Market / API / remote-data foundation

The current generation also contains the newer Veyra Market, API and remote-data foundation used by the application:

- public update metadata,
- market manifest/schema,
- diagnostics-pack foundation,
- Magic OTA catalog,
- HTTPS-only update transport,
- package/version/hash/signer validation for downloaded updates.

## Compatibility

Veyra Root 2.0 keeps:

- package `ctrl.mietze.veyraroot`,
- the established Veyra signing certificate,
- Android minimum API 26 at application level.

Individual root routes can have stricter device, kernel and firmware requirements.

## Validation baseline

The Public-v2 source state passed the complete unit-test suite:

**796 / 796 tests passed**

- 0 failures
- 0 errors
- 0 skipped

A passing application test suite is not a claim that every kernel-specific root route is runnable on every device. Exact-device evidence remains part of Veyra's design.

## Upgrade from v1.0.0

Because the official public builds use the same package and signing identity and v2.0.0 has the higher versionCode `200000000`, Android can install v2.0.0 as an update over compatible earlier official Veyra Root builds.

## Release files

- `VeyraRoot-2.0.0.apk` — official signed APK
- `VeyraRoot-2.0.0.sha256` — APK SHA-256
- `VeyraRoot-2.0.0-source.tar.gz` — sanitized Public-v2 source snapshot used for this release
- `VeyraRoot-2.0.0-source.tar.gz.sha256` — source archive SHA-256

The Git tag is created only after the Public-v2 source is synchronized to the repository, so GitHub's generated source archives correspond to the v2 source state rather than the historical v1 tree.
