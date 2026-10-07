# Veyra Market

This directory is the public registry consumed by Veyra Root's Market client.

The Market deliberately does **not** dynamically load downloaded executable code into the Veyra Root process.

Two extension types are supported:

## 1. Data packs

Data packs are JSON-only extensions installed into Veyra's private app storage.

Supported data-pack kinds currently include:

- `settings-data`
- `magic-ota-catalog`

A data pack can expose one or more Settings entries through its registry entry. Veyra validates the manifest, artifact size and optional SHA-256 before writing the pack to private storage.

Example registry entry:

```json
{
  "id": "veyra-diagnostics-pack",
  "name": "Veyra Diagnostics Pack",
  "summary": "Example first-party data extension.",
  "version": "1.0.0",
  "versionCode": 1,
  "minVeyraCode": 0,
  "kind": "data-pack",
  "artifactUrl": "https://raw.githubusercontent.com/ctrl-mietze/Veyra/main/market/packs/veyra-diagnostics-pack.json",
  "sha256": "635a3d64641669574517085ff120c3bbe875ed61ff0940676f4925d629e3650f",
  "size": 192,
  "capabilities": ["settings-entry"],
  "settingsEntries": [
    {
      "id": "diagnostics-pack",
      "title": "Veyra Diagnostics Pack",
      "summary": "Installed through Veyra Market",
      "action": "info",
      "value": "The diagnostics pack is installed and active."
    }
  ]
}
```

## 2. Companion APKs

A companion extension is a separate Android package.

Its registry entry can pin:

- package name
- SHA-256 of the APK
- signing certificate SHA-256
- minimum compatible Veyra build

Veyra downloads and verifies the APK, then hands it to Android's package installer. Cancelling the installer does not create an installed receipt.

Companion APKs can expose a Settings entry using the `open-package` action.

## Settings actions

Supported actions:

- `info` — show the entry's value as information
- `open-package` — launch the named Android package
- `open-uri` — open an HTTP/HTTPS page

Unknown actions are refused by older Veyra builds rather than executed generically.

## Registry

The active public registry is:

`market/manifest.json`

Schema version: **1**

The app caches the latest valid registry and falls back to its bundled manifest if GitHub is temporarily unavailable.

## Security rules

1. Remote artifacts must use HTTPS.
2. A data pack must use a schema/kind Veyra explicitly understands.
3. A companion APK cannot replace the Veyra Root package.
4. Optional SHA-256 and signer pins are enforced when declared.
5. Data packs are stored in app-private storage.
6. Downloaded code is not loaded into the Veyra process.
7. Installed extension receipts are separate from the remote registry, so removing an item from GitHub does not silently rewrite local state.
