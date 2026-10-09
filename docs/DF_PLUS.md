# Veyra DF Compatible / DF+

## Goal

Veyra exposes two DirtyFrag-based root methods next to the unchanged Standard route:

- **DF Compatible** — conservative compatibility workflow.
- **Veyra DF+** — Veyra's stricter evidence/preflight layer on top of the compatible core.

The public DFRoot project is used as a compatibility/research reference. Its repository currently exposes no license metadata, so Veyra does not wholesale copy its source. Veyra's integration is independently written around the behavior and local DF assets that already existed in this project.

## Shared compatibility core

The compatibility layer keeps the important runtime ideas:

1. ARM64/API/KMI eligibility is checked before a run.
2. KernelSU or KernelSU-Next is selected explicitly.
3. The installed manager's real package is resolved by Veyra, including spoofed KernelSU-Next manager packages.
4. If that manager carries a usable `libksud.so`, the DF bridge stages that daemon.
5. If no usable manager daemon exists, Veyra uses the verified bundled daemon.
6. The native DF engine performs the runtime DF route.
7. Veyra verifies the resulting KernelSU control path instead of treating a zero exit code as proof of root.

## DF Compatible

DF Compatible intentionally stays close to the conservative DF workflow.

Required:

- ARM64
- Android API 28+
- a known Android/kernel KMI family
- matching DF native engine for the selected KernelSU flavour
- a usable manager or bundled `ksud`

The native engine's embedded KMI/module route is allowed. A separate external `.ko` asset is useful evidence but is not mandatory for this mode.

## Veyra DF+

DF+ adds stricter local proof before the same runtime family is attempted.

In addition to the compatibility requirements it requires:

- an exact local KMI module asset for the selected Android/kernel tuple
- explicit KernelSU flavour mapping
- OEM profile selection
- shared diagnostics with Magic Builder and Compatibility Radar

Current KMI matrix:

| Android | Kernel |
| --- | --- |
| 12 | 5.10 |
| 13 | 5.10 / 5.15 |
| 14 | 5.15 / 6.1 |
| 15 | 6.6 |
| 16 | 6.12 |
| 17 | 6.18 |

## OEM profiles

### Samsung / DEFEX

Samsung devices and `SM-*` models are classified into the Samsung/DEFEX profile. The local DF native engine already carries Samsung-specific compatibility behavior, while Veyra adds explicit route reporting, KMI checks and manager-daemon selection.

### OnePlus / Oppo / realme

OPlus-family devices receive a separate compatibility profile. This is shown consistently in Root, Compatibility Radar and Magic diagnostics.

### Generic GKI

Other supported GKI devices use the generic profile.

## What the planner proves

A green DF plan means:

- Veyra understands the Android/kernel tuple,
- the selected flavour has a coherent local engine/daemon route,
- and, in DF+ mode, the exact external KMI module exists.

It does **not** mean the target kernel is proven vulnerable. Runtime exploitability is still a fact about the actual target device and is verified by the runtime result and KernelSU state.

## Builder integration

Both Loading Builder and Magic Builder can report DF+ as an alternate exact route for an imported boot image.

This is deliberately independent of the CVE-2026-43499 payload builder. A failed baseline build can therefore still tell the user that an exact DF route exists instead of collapsing every root method into one pipeline.

## Vivo X60 / Kona

The Vivo X60/Kona family is **not** forced through DF. Its known kernel is 4.19.152-perf-family, outside the current DF KMI matrix.

Veyra instead uses the dedicated Vivo/Kona Evidence Ladder:

1. exact device/profile identity
2. exact 4.19.152-perf source-family match
3. ARM64 identity
4. CVeyra live backend
5. live kernel symbols
6. non-zero physical Kernel-code range from `/proc/iomem`
7. vivo/vr module state
8. relevant kernel config markers
9. registered executable baseline check

A complete evidence ladder is called **porting-ready evidence**, not runnable root. Runnable output remains blocked until Veyra has an exact executable 4.19 baseline/base-library.

The current public RootMyVivo payload catalog does not contain an exact V2045/V2046/PD2046/Kona 4.19.152 target, so Veyra does not substitute a nearby newer vivo payload.
