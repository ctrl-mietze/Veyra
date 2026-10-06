# Veyra Root release protection

The public source contains the protection logic, but distributed builds still pin the official signing certificate.
Layers used in the hardened release build:

- signature/certificate pinning
- package/debuggable identity checks
- critical APK entry SHA-256 verification
- debugger + TracerPid detection
- process-local Frida/Xposed/LSPosed/Substrate markers
- suspicious injected thread checks
- repeated watchdog verification
- R8 app-code obfuscation/minification in release builds

No client-side Android protection is unbreakable. These layers are designed to raise the cost of casual repacking and runtime patching without rejecting normal KernelSU/Magisk/root use.
