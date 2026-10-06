# Veyra Root v1.0.0 — First Public Release

Veyra Root v1.0.0 is the first public release of the Veyra Root project.

Veyra is an Android root and kernel-research application built around exact-device evidence rather than blind cross-device assumptions. The project combines the original compatible Root My Galaxy route with Veyra-owned device analysis, CVeyra privilege management, KernelSU integration, payload/source management, recovery tools and an expanded Magic Builder.

> **Important:** support detection is not the same as a guaranteed runnable exploit. Veyra deliberately keeps analysis-only paths separate from verified runnable baselines. It does not invent kernel addresses, payload constants or physical-load values when the required evidence is missing.

## Highlights

### Root detection and provider awareness

The Home status no longer treats the presence of a manager app as proof that Veyra has root.

Veyra verifies whether its own process can actually execute as UID 0 and distinguishes the active route:

- Veyra temporary/jailbreak root
- external temporary-root providers
- KernelSU root
- Magisk-style boot root
- other real root providers

External providers can be routed into the provider-migration flow instead of being mislabeled as a Veyra session.

### Standard and Magic root workflows

Veyra keeps the proven Standard route as a compatibility anchor and adds the much larger Magic Builder research workflow.

Magic Builder includes:

- exact boot-image capture
- ARM64 Image-header and ELF architecture detection
- kernel banner detection independent from kallsyms decoding
- exact kernel-family gating
- legacy 4.x analysis
- 5.x legacy analysis
- 6.x mainline/GKI analysis
- 7.x analysis routing with runnable output blocked until an exact baseline exists
- source-assisted analysis for difficult vendor kernels
- support-bundle export
- generated target header / offsets analysis
- no automatic guessing of physical kernel addresses

### Magic Builder deep diagnostics

v1.0.0 includes dedicated diagnostics for difficult ports:

- **Source Match Matrix** — ranks research sources against the current OEM/kernel family.
- **Kernel Gate** — shows exact baseline/family decisions.
- **KMI Matrix** — checks the local DF/KMI inventory against the current kernel.
- **Live Symbols** — reads selected live kernel symbols when the privileged backend permits it.
- **Boot Evidence Report** — parses the captured boot image and exports exact architecture/version evidence.

### vivo / iQOO and Kona research

The Magic Builder contains a dedicated vivo legacy intelligence path.

For the vivo X60/Kona family it can use source evidence such as:

- Qualcomm Kona / SM8250
- ARM64
- Linux 4.19.152 source family
- `-perf` local version evidence
- kallsyms-related source configuration
- exact source matching without converting research evidence into an unverified runnable baseline

When a generic kallsyms decode is not sufficient, Veyra can preserve the captured device evidence as analysis artifacts rather than terminating with a generic architecture error.

### Research sources integrated into Payload Sources

The following projects are represented as built-in **Local / Research** sources where they do not expose Veyra's runnable `targets-v3.json` feed format:

- p2p3p/GhostLock-for-OnePlus
- NanoTurtle1145/root-my-s24
- yakidango-official/GhostLock-H80GT
- JoinChang/ghostlock-oneplus
- sarabpal-dev/IonStack-S22U
- zenyxx-xd/RootMyVivo and RootMyVivo-Payloads
- rushiranpise/Shizuku-Next

These entries are deliberately research-only until an exact Veyra-compatible runnable manifest exists. Veyra will not fabricate payload binaries or offsets simply because a repository targets a similar kernel.

### CVeyra Permission Provider 2.0.0

CVeyra is no longer presented as a permission-preview concept.

The 2.0.0 flow contains a real activation state machine:

1. provider information must be read,
2. VeyraKSU 2.0.0 must be installed,
3. KernelSU soft reboot is requested where required,
4. the live bridge is verified,
5. existing direct KernelSU grants are backed up,
6. CVeyra Access is accepted and the root broker becomes the enforced privileged backend.

After activation, the old "use CVeyra / start as root / promote after boot" switches are no longer needed. CVeyra Access is treated as an always-on root-broker mode.

### Permission Management

The permission manager has separate modes:

- **Offline** — keeps rules but manages no applications.
- **Self** — the user selects managed applications.
- **Auto** — reserved for the future policy engine and intentionally returns to the prior mode for now.

The manager displays applications with direct KernelSU Superuser grants separately so those grants are not silently rewritten.

Provider changes are persisted only after the device-side action succeeds.

Current enforcement includes:

- CVeyra broker allow-list state
- ADB / WRITE_SECURE_SETTINGS grant handling when the target app actually requests it
- Shizuku API permission handling when the target app requests it
- real KernelSU-grant detection for Superuser state
- real Android UID 1000 state detection
- real Android Device Owner state detection

Unsupported state transitions are refused rather than shown as successful fake grants.

### VeyraKSU 2.0.0

The bundled VeyraKSU compatibility module is now version 2.0.0.

It provides:

- CVeyra permission-bridge state
- access activation marker
- migration-compatible state files
- boot/service/late-load status
- persistent firewall re-application
- retained provider-migration compatibility

### CVeyra App Module Loader

CVeyra Management includes system-state modules for:

- Hide accessibility
- Hide developer options
- Hide USB debugging
- Hide Private DNS

These modules operate on the **real Android setting** while enabled and remember the exact previous value for restoration. They are not per-app hook spoofers.

### CVeyra Firewall

The CVeyra firewall uses more than one layer:

- Android package networking control where supported
- persistent root-owned IPv4 UID rules
- persistent root-owned IPv6 UID rules
- VeyraKSU re-application after module/service startup

Only Veyra-managed package state is persisted.

### ADB Manager

ADB start methods are grouped into the Veyra ADB Manager:

- Start via Wireless Debugging
- Start via USB debugging
- Start via Computer

The implementation retains Veyra's own authenticated Wireless ADB/session handling rather than cloning another application's UI.

### Navigation

After CVeyra Access 2.0.0 becomes active, the Veyra flower is added as the center item in the Home / History / Logs / Settings navigation island and opens CVeyra Management directly.

### System Update control

The System Manager can disable automatic OTA behavior through CVeyra and selected installed OEM updater components.

Veyra remembers only updater packages that **Veyra itself** disabled, so restoring updates does not blindly enable packages the user had disabled beforehand.

### UI and performance

v1.0.0 keeps the native Android/Veyra visual language:

- dark/OLED-first themes
- native settings rows
- restrained purple accent
- persistent page/home state
- cached Home readiness state
- background refresh instead of visibly resetting status cards during tab changes
- optional 120 Hz display-mode request where the device provides a matching mode

## Release hardening

The official release APK contains a separate hardened runtime layer that is not enabled in normal development builds.

Protections include:

- official signing-certificate SHA-256 pinning
- package identity verification
- non-debuggable release manifest
- backup disabled
- shell profiling disabled
- critical APK entry SHA-256 verification
- debugger detection
- `TracerPid` / ptrace detection
- process-local Frida/Xposed/LSPosed/Substrate marker detection
- suspicious injected thread detection
- suspicious process file-descriptor detection
- repeated runtime integrity watchdog
- R8 obfuscation/minification focused on Veyra-owned code
- explicit preservation of the legacy JNI ABI required by `libs25u_native.so`

No client-side Android protection is mathematically unbreakable. These layers are intended to make casual repacking, resigning and runtime patching substantially harder while still allowing normal KernelSU/Magisk/root use.

## Security model

Veyra is intentionally conservative around kernel evidence.

A repository name, matching kernel family or installed manager is **not** treated as sufficient proof for a runnable kernel payload.

Where exact data is missing, Veyra prefers:

- analysis-only output,
- support-bundle collection,
- live-device verification,
- source cross-checks,
- explicit "not runnable yet" status,

instead of substituting nearby offsets.

## Compatibility notes

The application package remains:

`ctrl.mietze.veyraroot`

The official v1.0.0 APK is signed with the established Veyra certificate so it can update over prior Veyra builds using the same signer.

Android minimum API remains API 26, while individual root routes have their own stricter kernel/device requirements.

## Credits and research

Veyra's research layer references public work from multiple Android/kernel projects. See `docs/THIRD_PARTY_RESEARCH.md` for the distinction between research evidence, compatibility inspiration and runnable Veyra payload sources.

## First public release

This is the first public Veyra Root release. The project is intentionally shipping the architecture, diagnostics and safety boundaries together rather than publishing a list of kernels that Veyra cannot actually prove it can handle.

Future releases can expand exact device baselines and the automatic CVeyra policy engine without weakening the evidence requirements introduced here.
