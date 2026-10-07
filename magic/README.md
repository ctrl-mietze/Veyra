# Magic Builder OTA Intelligence

`ota-catalog-v1.json` is the remote data layer used by the new Magic Builder boot resolver.

The resolver does not treat an OTA URL as proof of compatibility. A candidate is only accepted after the extracted boot image reports the **same full kernel release** as the running device.

## Resolution order

1. Exact live boot capture through CVeyra Root or the authenticated storage proxy.
2. Previously successful exact-kernel OTA history.
3. Veyra's remote OTA catalog.
4. Supported external OTA indexes.
5. Installed Veyra Market OTA data packs.

The catalog can define up to ten ranked candidates per device profile. Only the strongest few are allowed to consume OTA/boot network data.

## Built-in provider adapters

The current client can consume:

- Google Pixel full-OTA index data
- Nothing firmware/archive data
- Veyra-hosted exact OTA/stock-boot entries
- installed Market `magic-ota-catalog` packs

## Safety

A matching model name or kernel family does not make an OTA candidate runnable.

Before Magic Builder uses a downloaded/extracted image, Veyra reads the kernel release from that image and compares it against the device's live full `uname` release. Mismatches are rejected.

This preserves the project's evidence-first rule while still allowing automatic OTA fallback when the live boot partition cannot be read.
