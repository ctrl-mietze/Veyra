# Veyra Root

**Veyra Root** is an Android root, kernel-research and privileged-device-management app built around one rule:

> **exact evidence before kernel writes.**

Veyra does not treat a matching phone name, kernel family or installed root manager as proof that a payload is safe to run. When exact evidence is missing, it prefers analysis, source cross-checks and support-bundle collection over guessed offsets.

This repository contains the first public release: **Veyra Root v1.0.0**.

[Latest release](https://github.com/ctrl-mietze/Veyra-Root/releases/latest)

## Package

```text
ctrl.mietze.veyraroot
```

The public v1.0.0 APK keeps the established Veyra signing certificate so it can update over prior Veyra builds signed with the same key.

## What Veyra does

Veyra combines several layers that used to live separately:

- real root-access detection for Veyra itself
- temporary-root / jailbreak routes
- KernelSU / KernelSU-Next / ReSukiSU integration
- Magisk-style boot-root awareness
- provider migration
- Magic Builder kernel analysis
- exact payload/source management
- CVeyra Permission Provider 2.0.0
- VeyraKSU 2.0.0
- Wireless ADB and Shizuku integration
- system/recovery utilities
- local diagnostics and support bundles

## Root detection

Veyra does not call a device rooted merely because KernelSU Manager or Magisk is installed.

It checks whether **Veyra itself can execute UID 0**, then classifies the active route. The UI can distinguish:

- Veyra temporary root
- external temporary-root providers
- KernelSU root
- Magisk-style boot root
- other real root providers

External providers can be routed into Veyra's provider-migration flow instead of being silently treated as a Veyra session.

## Magic Builder

Magic Builder is Veyra's device/kernel research layer.

It can:

- capture and parse boot images
- identify ARM64 Android kernel Images by the real Image header
- identify ELF64/AArch64 kernels
- read the Linux kernel banner independently from kallsyms decoding
- analyze kallsyms and symbol layouts
- produce analysis-only `target.generated.h` and `offsets.json`
- cross-check source-derived evidence
- collect support bundles
- keep runnable output blocked when required evidence is missing

### Kernel families

The current analysis architecture covers legacy and modern Android kernel families across:

- 4.x
- 5.x
- 6.x
- 7.x research routing

A family match is **not** an automatic exploit match. Runnable output still depends on an exact baseline or equivalent target evidence.

### Five deep diagnostics

Magic Builder v1.0.0 includes:

1. **Source Match Matrix** — ranks research sources against the current device/kernel.
2. **Kernel Gate** — shows the exact baseline/family decision.
3. **KMI Matrix** — compares the device against the local DF/KMI inventory.
4. **Live Symbols** — reads selected live symbols when the privileged backend permits it.
5. **Boot Evidence Report** — exports exact architecture/version evidence from the captured boot image.

## vivo / iQOO / Kona research

Veyra includes a dedicated vivo legacy intelligence path.

For the vivo X60/Kona family the source layer can use evidence such as:

- Qualcomm Kona / SM8250
- ARM64
- Linux 4.19.152
- `-perf` source configuration
- kallsyms source configuration
- full live kernel release matching

A difficult vendor kallsyms layout can fall back to **source-assisted analysis** instead of ending in a generic architecture exception.

That fallback is intentionally analysis-only until the remaining live physical/symbol evidence is verified.

## Payload Sources

Veyra supports normal runnable payload feeds and built-in Local/Research sources.

Research-only entries are visible in **Settings → Payload Management → Payload Sources**, but use an empty runnable manifest until a real Veyra-compatible exact payload definition exists.

Included research references currently cover:

- [p2p3p/GhostLock-for-OnePlus](https://github.com/p2p3p/GhostLock-for-OnePlus)
- [NanoTurtle1145/root-my-s24](https://github.com/NanoTurtle1145/root-my-s24)
- [yakidango-official/GhostLock-H80GT](https://github.com/yakidango-official/GhostLock-H80GT)
- [JoinChang/ghostlock-oneplus](https://github.com/JoinChang/ghostlock-oneplus)
- [sarabpal-dev/IonStack-S22U](https://github.com/sarabpal-dev/IonStack-S22U)
- [zenyxx-xd/RootMyVivo](https://github.com/zenyxx-xd/RootMyVivo)
- [zenyxx-xd/RootMyVivo-Payloads](https://github.com/zenyxx-xd/RootMyVivo-Payloads)
- [rushiranpise/Shizuku-Next](https://github.com/rushiranpise/Shizuku-Next)
- [ZProtons/android_kernel_vivo_kona](https://github.com/ZProtons/android_kernel_vivo_kona)

See [Third-party research notes](docs/THIRD_PARTY_RESEARCH.md).

## CVeyra Permission Provider 2.0.0

CVeyra is Veyra's privileged action broker and permission-management layer.

The 2.0.0 activation flow verifies:

1. provider information has been read,
2. VeyraKSU 2.0.0 is installed,
3. the required KernelSU soft reboot has completed,
4. the permission bridge is live,
5. Veyra itself has the required root grant,
6. existing direct KernelSU grants are backed up,
7. the CVeyra root broker can be enforced.

After activation, CVeyra Access becomes an always-on root-broker mode.

### Permission Management modes

- **Offline** — stored rules remain, but no apps are managed.
- **Self** — you choose managed applications.
- **Auto** — reserved for a later policy engine.

Detailed provider privileges stay visible on **Grant CVeyra Access**.

### Real enforcement

V-SPR/CVeyra v2 no longer treats permission rows as decorative preview state.

Supported changes are persisted only after the device-side operation succeeds.

The policy engine can work with:

- CVeyra broker allow-list state
- Android runtime permission grants such as `WRITE_SECURE_SETTINGS` when requested by the target app
- Shizuku API permission state when requested by the target app
- actual KernelSU Superuser-grant detection
- actual Android system-UID state
- actual Android Device Owner state

Impossible runtime transitions are refused rather than displayed as successful fake grants.

## VeyraKSU 2.0.0

VeyraKSU is the KernelSU compatibility/service layer used by CVeyra Access.

It provides:

- permission-bridge state
- access activation state
- provider-migration compatibility
- boot/service/late-load status
- persistent CVeyra firewall state
- root-broker integration

## App Module Loader

CVeyra Management includes restore-safe system-state modules for:

- Hide accessibility
- Hide developer options
- Hide USB debugging
- Hide Private DNS

These modules change the **real Android setting** while enabled and restore the exact previous value when disabled.

They are not per-app hook spoofers.

## CVeyra Firewall

The CVeyra firewall combines:

- Android package networking control where supported
- root-owned IPv4 UID rules
- root-owned IPv6 UID rules
- persistent re-application through VeyraKSU

Only Veyra-managed package state is persisted.

## ADB Manager

The Veyra ADB Manager groups:

- Start via Wireless Debugging
- Start via USB debugging
- Start via Computer

Wireless ADB keeps Veyra's own authenticated pairing/session implementation.

## System Manager

System Manager includes reversible system-update blocking through CVeyra.

Veyra remembers only update components it disabled itself, so restoring updates does not blindly re-enable unrelated packages the user had already disabled.

## Release hardening

The official v1.0.0 APK enables a release-only protection layer.

It includes:

- official certificate pinning
- package identity checks
- critical APK-entry SHA-256 verification
- non-debuggable release configuration
- backup disabled
- shell profiling disabled
- cleartext traffic disabled
- debugger detection
- `TracerPid` / ptrace detection
- process-local Frida/Xposed/LSPosed/Substrate detection
- suspicious injected-thread detection
- suspicious process file-descriptor detection
- `LD_PRELOAD` detection
- repeated runtime integrity checks
- R8 minification/obfuscation focused on Veyra-owned code
- legacy JNI ABI preservation

No Android client-side protection is mathematically unbreakable. The goal is to make casual repacking, resigning and runtime patching substantially harder without rejecting normal root use.

See [Release protection](docs/RELEASE_PROTECTION.md) and [Security Policy](SECURITY.md).

## Build

Requirements:

- JDK 21
- Android SDK 37
- Android build tools compatible with API 37
- arm64-v8a target

Point Gradle at the Android SDK using `local.properties` or `ANDROID_HOME`.

Development build:

```bash
./gradlew :app:assembleDebug
```

Release build:

```bash
./gradlew :app:assembleRelease
```

The official hardened release additionally requires the official Veyra signer. A differently signed public-source build is intentionally not considered an official Veyra binary.

## Release authenticity

Official signing-certificate SHA-256:

```text
f1d8f55217d1149f88db9e1642735363d0198c33c8da8d77547987cedf08c582
```

Release checksums are published with each GitHub release.

## Project lineage and credits

Veyra Root evolved from work based on Root My Galaxy Next / Root My Galaxy and retains compatibility code where required.

Public third-party repositories listed in the research layer remain the work of their respective authors. Veyra does not relabel those repositories as its own payloads.

See:

- [THIRD_PARTY_RESEARCH.md](docs/THIRD_PARTY_RESEARCH.md)
- [LICENSE](LICENSE)

## Status

**v1.0.0 is the first public Veyra Root release.**

Future releases can add exact device baselines and broader CVeyra policy automation without weakening the exact-evidence rules introduced here.
