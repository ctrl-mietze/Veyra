<p align="center">
  <img src="assets/veyra-root-banner.jpg" alt="Veyra Root — Exact evidence before kernel writes" width="100%">
</p>

<p align="center">
  <a href="https://github.com/ctrl-mietze/Veyra/releases/latest"><img src="https://img.shields.io/github/v/release/ctrl-mietze/Veyra?display_name=tag&style=for-the-badge&color=7c3aed&label=release" alt="Latest release"></a>
  <img src="https://img.shields.io/badge/Android-API%2026%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android API 26+">
  <img src="https://img.shields.io/badge/tests-796%20%2F%20796-7c3aed?style=for-the-badge" alt="796 / 796 tests">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-8b5cf6?style=for-the-badge" alt="Apache 2.0"></a>
</p>

<p align="center">
  <strong>Android root · kernel research · privileged-device management</strong><br>
  <sub>Evidence first. No guessed kernel writes.</sub>
</p>

<p align="center">
  <a href="https://github.com/ctrl-mietze/Veyra/releases/tag/v2.0.0"><strong>⬇ Download v2.0.0</strong></a>
  &nbsp;•&nbsp;
  <a href="docs/RELEASE_NOTES_v2.0.0.md">Release notes</a>
  &nbsp;•&nbsp;
  <a href="SECURITY.md">Security</a>
  &nbsp;•&nbsp;
  <a href="docs/THIRD_PARTY_RESEARCH.md">Research credits</a>
</p>

---

# Veyra Root

**Veyra Root** is an Android root, kernel-research and privileged-device-management project built around one rule:

> **Exact evidence before kernel writes.**

A matching phone name, kernel family or installed root manager is **not** treated as proof that a payload is safe to run. When exact evidence is missing, Veyra prefers analysis, source cross-checks and support-bundle collection over guessed offsets.

The current public release is **Veyra Root v2.0.0**.

## ✦ At a glance

| Root & Providers | Kernel Research | Privileged Management | Builder Tooling |
| --- | --- | --- | --- |
| Real UID 0 verification | Magic Builder | CVeyra Permission Provider 2.0.0 | Builder Workingbench |
| Temporary-root awareness | Boot/kernel evidence | VeyraKSU 2.0.0 | Session + report export |
| KernelSU / KernelSU-Next | KMI / source matching | Permission broker | OTA range extraction |
| Magisk-style boot-root awareness | Analysis-only fallback | Firewall + system controls | Termux helper generation |

## ⬇ Public release

**Veyra Root v2.0.0** is the current official public build.

| | |
| --- | --- |
| **Package** | `ctrl.mietze.veyraroot` |
| **Version** | `2.0.0` |
| **versionCode** | `200000000` |
| **APK** | [VeyraRoot-2.0.0.apk](https://github.com/ctrl-mietze/Veyra/releases/download/v2.0.0/VeyraRoot-2.0.0.apk) |
| **SHA-256** | `edc2af1e93861d60e94e99fff7aa776ba119280d63fa8bd5473e2ed84b55bb7c` |
| **Signer SHA-256** | `f1d8f55217d1149f88db9e1642735363d0198c33c8da8d77547987cedf08c582` |

> The official public APK uses the established Veyra signing identity so compatible earlier official Veyra Root builds can be updated in place.

[**→ Open the complete v2.0.0 release**](https://github.com/ctrl-mietze/Veyra/releases/tag/v2.0.0)

---

## ✦ What makes Veyra different

Veyra separates **what can be observed** from **what is actually proven runnable**.

A device can match a family, architecture or kernel generation without automatically receiving a writable/root payload. Veyra keeps those states distinct:

- **Verified runnable** — exact evidence is sufficient for the route.
- **Compatible / candidate** — the device or kernel matches known constraints but still needs validation.
- **Analysis-only** — Veyra can inspect and export evidence, but refuses to invent missing physical addresses, offsets or payload constants.

That boundary is intentional.

## ⚡ Root detection & provider awareness

Veyra does not mark a device rooted simply because a manager app exists.

It checks whether **Veyra itself can execute as UID 0**, then classifies the active route. The app can distinguish:

- Veyra temporary root
- external temporary-root providers
- KernelSU root
- Magisk-style boot root
- other real root providers

External providers can be routed into Veyra's provider-migration flow instead of being silently mislabeled as a Veyra session.

## 🧠 Magic Builder

Magic Builder is Veyra's kernel/device research layer.

It can:

- capture and parse boot images
- identify ARM64 Android kernel Images from the real Image header
- identify ELF64 / AArch64 kernels
- read the Linux kernel banner independently from kallsyms decoding
- analyze kallsyms and symbol layouts
- generate analysis artifacts such as `target.generated.h` and `offsets.json`
- cross-check source-derived evidence
- collect support bundles
- keep runnable output blocked when required evidence is missing

### Kernel-family routing

The analysis architecture covers both legacy and modern Android kernel families, including:

- 4.x
- 5.x
- 6.x
- 7.x research routing

A family match is **not** an automatic exploit match.

### Deep diagnostics

Magic Builder exposes dedicated diagnostics for difficult ports:

1. **Source Match Matrix** — ranks research sources against the current device/kernel.
2. **Kernel Gate** — shows the exact baseline/family decision.
3. **KMI Matrix** — compares the device with Veyra's local DF/KMI inventory.
4. **Live Symbols** — reads selected symbols when the privileged backend permits it.
5. **Boot Evidence Report** — exports architecture/version evidence from the captured boot image.

## 🧩 Builder Workingbench

The current Veyra generation consolidates builder workflows into **Builder Workingbench** while keeping builder-specific settings where they belong.

Current tooling includes:

- resumable builder sessions
- OTA range extraction
- local image/hash comparison
- strategy matrices
- evidence grouping and conflict tracking
- risk modes
- report import/export
- candidate/session export
- Termux helper generation
- automatic dependency checks/preparation
- Android Download output handling with fallback paths
- ADB and Shizuku/rish-aware helper paths

## 🧬 DF Compatible / Veyra DF+

The DF route uses **kernel/KMI evidence** instead of blindly following the userspace Android version.

The current architecture includes:

- DF Compatible
- Veyra DF+
- Samsung / DEFEX-aware routing
- OnePlus / Oppo / realme profiles
- generic GKI routing
- installed manager / ksud awareness
- KernelSU-Next package awareness
- kernel-release-first KMI selection
- recovery/runtime controls
- root-on-boot and soft-reboot options where supported
- module-disable controls
- image-partition protection controls

Legacy 4.19 targets such as Kona remain evidence-driven and are not silently promoted into modern GKI routes.

## 💜 CVeyra Permission Provider 2.0.0

CVeyra is Veyra's privileged action broker and permission-management layer.

The 2.0.0 activation flow verifies:

1. provider information has been read,
2. VeyraKSU 2.0.0 is installed,
3. the required KernelSU soft reboot has completed,
4. the permission bridge is live,
5. Veyra itself has the required root grant,
6. existing direct KernelSU grants are backed up,
7. the CVeyra root broker can be enforced.

After activation, **CVeyra Access** becomes the always-on privileged broker mode.

### Permission Management

| Mode | Behaviour |
| --- | --- |
| **Offline** | Keeps stored rules but manages no applications. |
| **Self** | The user chooses which applications are managed. |
| **Auto** | Reserved for the later policy engine. |

Supported state changes are persisted only after the device-side action succeeds. Impossible transitions are refused rather than displayed as successful fake grants.

## 🛡 Public Release hardening

The public v2 release includes Veyra's dedicated **ReleaseGuard** layer.

Current checks include:

- official signing-certificate pinning
- package/build identity verification
- real non-debuggable / non-test-only verification
- split/repack detection
- trusted APK source/path checks
- SHA-256 integrity checks for critical APK entries and native libraries
- debugger detection
- `TracerPid` / tracing checks
- Frida marker detection
- Xposed / LSPosed marker detection
- Substrate marker detection
- `LD_PRELOAD` checks
- suspicious process-map, thread and file-descriptor checks
- repeated runtime guard/watchdog verification
- backups disabled
- cleartext traffic disabled
- shell profiling disabled

No Android client-side protection is mathematically unbreakable. The goal is to make casual resigning, repacking, asset replacement, binary patching and runtime instrumentation substantially harder without rejecting normal legitimate root use.

See [Release protection](docs/RELEASE_PROTECTION.md) and [Security Policy](SECURITY.md).

## 🔄 Mandatory public update channel

The hardened public build uses Veyra's public GitHub update channel.

When the published channel reports a `versionCode` higher than the installed public build, the update is treated as required. The download path validates:

- package identity
- versionCode
- published APK SHA-256
- Veyra signer identity
- HTTPS transport

Public v2 establishes `200000000` as the current stable public versionCode baseline.

## 🔌 ADB Manager

The Veyra ADB Manager groups the supported startup paths:

- Start via Wireless Debugging
- Start via USB debugging
- Start via Computer

Wireless ADB retains Veyra's own authenticated pairing/session handling.

## 🧱 System & recovery tools

Veyra also contains system/recovery utilities, including reversible system-update control through CVeyra.

Veyra remembers only updater components it disabled itself so restoring updates does not blindly re-enable unrelated packages that were already disabled by the user.

---

<details>
<summary><strong>🔬 Research sources & project lineage</strong></summary>

Veyra supports normal runnable payload feeds and built-in **Local / Research** sources.

Research-only entries remain analysis/reference material until a real Veyra-compatible exact payload definition exists.

Current research references include public work from:

- [p2p3p/GhostLock-for-OnePlus](https://github.com/p2p3p/GhostLock-for-OnePlus)
- [NanoTurtle1145/root-my-s24](https://github.com/NanoTurtle1145/root-my-s24)
- [yakidango-official/GhostLock-H80GT](https://github.com/yakidango-official/GhostLock-H80GT)
- [JoinChang/ghostlock-oneplus](https://github.com/JoinChang/ghostlock-oneplus)
- [sarabpal-dev/IonStack-S22U](https://github.com/sarabpal-dev/IonStack-S22U)
- [zenyxx-xd/RootMyVivo](https://github.com/zenyxx-xd/RootMyVivo)
- [zenyxx-xd/RootMyVivo-Payloads](https://github.com/zenyxx-xd/RootMyVivo-Payloads)
- [rushiranpise/Shizuku-Next](https://github.com/rushiranpise/Shizuku-Next)
- [ZProtons/android_kernel_vivo_kona](https://github.com/ZProtons/android_kernel_vivo_kona)

Veyra Root evolved from work based on **Root My Galaxy Next / Root My Galaxy** and retains compatibility code where required. Those projects and other referenced repositories remain the work of their respective authors.

See [THIRD_PARTY_RESEARCH.md](docs/THIRD_PARTY_RESEARCH.md).

</details>

<details>
<summary><strong>🛠 Build from source</strong></summary>

### Requirements

- JDK 21
- Android SDK 37
- Android build tools compatible with API 37
- arm64-v8a target

Point Gradle at the Android SDK through `local.properties` or `ANDROID_HOME`.

Development build:

```bash
./gradlew :app:assembleDebug
```

Release build:

```bash
./gradlew :app:assembleRelease
```

The official hardened public build additionally depends on the official Veyra signing identity. A differently signed build from public source is intentionally not considered an official Veyra binary.

</details>

## 🔐 Release authenticity

Official signing-certificate SHA-256:

```text
f1d8f55217d1149f88db9e1642735363d0198c33c8da8d77547987cedf08c582
```

APK checksums are published with each GitHub release.

The Public-v2 source baseline completed:

**796 / 796 unit tests passed — 0 failures · 0 errors · 0 skipped**

---

<p align="center">
  <strong>Veyra Root</strong><br>
  <sub>Control. Understand. Verify. Go further.</sub>
</p>
