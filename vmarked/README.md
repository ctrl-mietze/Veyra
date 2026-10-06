# Veyra Marked +

This folder is the public registry root for future Veyra Marked + extensions.

The Android app currently keeps the Marked + screen intentionally unchanged and only refreshes
`vmarked/manifest.json` in the background. v1.0.0 ships with an empty plugin array.

## Manifest

```json
{
  "schemaVersion": 1,
  "channel": "stable",
  "plugins": []
}
```

Future plugin entries are expected to carry at least:

- `id`
- `name`
- `version`
- `minVeyra`
- `apkUrl`
- `sha256`

No plugin is installed or exposed in the v1.0.0 UI yet.
