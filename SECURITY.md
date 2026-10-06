# Security Policy

## Supported release

Security reports should target the latest public Veyra Root release.

## Release authenticity

Official APKs are signed with the Veyra signing certificate. The hardened build also verifies that certificate at runtime.

SHA-256 certificate digest:

`f1d8f55217d1149f88db9e1642735363d0198c33c8da8d77547987cedf08c582`

A source build signed with a different key is not an official Veyra binary and the hardened runtime is expected to reject it unless the builder deliberately changes the protection configuration.

## Reporting

When reporting a security problem, include:

- Veyra version / versionCode
- Android version
- device model and codename
- full kernel release
- whether the issue occurs before or after CVeyra/VeyraKSU activation
- relevant Veyra logs with private tokens removed

Do not publish private signing material, ADB keys, CVeyra start tokens or other credentials in an issue.
