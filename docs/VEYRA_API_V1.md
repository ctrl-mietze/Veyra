# Veyra Web API v1

This document is the client contract already implemented in Veyra Root. The API is intentionally disabled in the bundled configuration until the backend is ready.

## Base configuration

Bundled client configuration:

- asset: `app/src/main/assets/api/veyra-api-v1.json`
- base URL: `https://api.veyracore.de/v1`
- client ID: `veyra-root`
- HTTPS is mandatory.
- access/refresh tokens are stored encrypted with an Android Keystore AES-GCM key.
- credentials are never written to AppLog.

## Authentication

### POST /auth/register

Request:

```json
{
  "username": "mietze",
  "password": "example-password",
  "displayName": "Mietze",
  "clientId": "veyra-root"
}
```

Response:

```json
{
  "session": {
    "accessToken": "...",
    "refreshToken": "...",
    "expiresAt": 1791392400
  },
  "account": {
    "id": "user-id",
    "username": "mietze",
    "displayName": "Mietze",
    "roles": ["user"],
    "entitlements": []
  }
}
```

### POST /auth/login

Same response shape as registration.

### POST /auth/refresh

Request contains `refreshToken` and `clientId`. Response contains a replacement `session`.

### POST /auth/logout

Authenticated request. The client clears its local encrypted session even if the remote logout cannot be completed.

## Account

### GET /account/me

Authenticated. Returns the account object.

Roles and entitlements are strings so the server can add new capabilities without requiring a client schema update.

## Market entitlements

### GET /market/entitlements

Response:

```json
{
  "entitlements": ["market.private", "pack.example"]
}
```

The Market itself remains usable for public entries without an account. Entitlements are intended for private/team/licensed entries later.

## Licenses

### GET /licenses

Response:

```json
{
  "licenses": [
    {
      "id": "license-id",
      "product": "veyra-root",
      "status": "active",
      "expiresAt": 1791392400
    }
  ]
}
```

## Device registration

### POST /devices

Authenticated. Veyra sends only technical device/build identity required for compatibility and account-linked device management:

- clientId
- packageName
- appVersion / appVersionCode
- manufacturer
- model
- Android device codename
- buildId
- fingerprint
- kernelRelease

Response:

```json
{
  "id": "device-registration-id",
  "trusted": false
}
```

## Error contract

Non-2xx responses should preferably return:

```json
{
  "message": "Human-readable reason",
  "code": "stable.machine.code"
}
```

The v1 client retries one request after a 401 by refreshing the session. Other HTTP errors are returned to the caller.

## Market separation

The GitHub-backed public Veyra Market and the future account API are separate layers:

- GitHub/Market manifest: public extension discovery.
- Web API entitlements: optional authorization for private/team/licensed extensions.
- Data packs: data-only, installed into Veyra's private storage.
- Companion APKs: separate Android packages installed through the Android package installer.
- No downloaded executable code is dynamically loaded into the Veyra Root process.
