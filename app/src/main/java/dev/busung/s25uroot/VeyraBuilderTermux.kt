package ctrl.mietze.veyraroot

import android.content.Context

internal data class VeyraTermuxExport(
    val probePath: String,
    val fallback1Path: String,
    val fallback2Path: String,
    val fallback3Path: String,
) {
    fun commandFor(path: String): String {
        val name = path.substringAfterLast('/')
        return "bash \"\$HOME/storage/downloads/$name\""
    }
}

/**
 * Generates standalone Termux scripts. Every exported script includes the same autonomous
 * environment/dependency/storage preflight; no separate manual pkg-install sequence is required.
 */
internal object VeyraBuilderTermux {
    fun exportAll(context: Context): VeyraTermuxExport {
        val probe = DownloadStore.save(
            context,
            "VeyraRoot-VeyraBuilder-Probe.sh",
            script("Veyra Builder Probe", probeBody()).toByteArray(),
        )
        val f1 = DownloadStore.save(
            context,
            "VeyraRoot-VeyraBuilder-Fallback1.sh",
            script("Veyra Builder Fallback 1", fallback1Body()).toByteArray(),
        )
        val f2 = DownloadStore.save(
            context,
            "VeyraRoot-VeyraBuilder-Fallback2.sh",
            script("Veyra Builder Fallback 2", fallback2Body()).toByteArray(),
        )
        val f3 = DownloadStore.save(
            context,
            "VeyraRoot-VeyraBuilder-Fallback3.sh",
            script("Veyra Builder Fallback 3", fallback3Body()).toByteArray(),
        )
        return VeyraTermuxExport(probe, f1, f2, f3)
    }

    private fun script(name: String, body: String): String =
        (preflight(name) + "\n" + body).replace('§', '$')

    private fun preflight(name: String): String = """
#!/data/data/com.termux/files/usr/bin/bash
# $name — autonomous Veyra Builder environment preparation.
set -Eeuo pipefail
export LC_ALL=C

VEYRA_SCRIPT_NAME="$name"
TERMUX_HOME="§{HOME:-/data/data/com.termux/files/home}"
say() { printf '%s\n' "§*"; }
warn() { printf '[!] %s\n' "§*" >&2; }
have() { command -v "§1" >/dev/null 2>&1; }

if [[ "§{PREFIX:-}" != /data/data/com.termux/files/usr* ]] || ! have pkg; then
  warn "§VEYRA_SCRIPT_NAME must run inside Termux. Open Termux and start this script there."
  exit 2
fi

say "[i] §VEYRA_SCRIPT_NAME"
say "[i] Checking environment and dependencies…"
ensure_pkg() {
  local cmd="§1" pkg_name="§2"
  if have "§cmd"; then return 0; fi
  say "[i] Missing '§cmd' — installing '§pkg_name'…"
  if ! pkg install -y "§pkg_name" >/dev/null 2>&1; then
    warn "Could not install '§pkg_name'. Run 'pkg update' once in Termux, then start this script again."
    return 1
  fi
  have "§cmd"
}
maybe_update_pkg() {
  local pkg_name="§1" installed='' candidate=''
  have dpkg-query || return 0
  installed="§(dpkg-query -W -f='§{Version}' "§pkg_name" 2>/dev/null || true)"
  [[ -n "§installed" ]] || return 0
  if have apt-cache; then
    candidate="§(apt-cache policy "§pkg_name" 2>/dev/null | awk '/Candidate:/ {print §2; exit}')"
  fi
  [[ -n "§candidate" && "§candidate" != "(none)" ]] || return 0
  if dpkg --compare-versions "§installed" lt "§candidate" 2>/dev/null; then
    say "[i] Updating required '§pkg_name' from §installed to §candidate…"
    pkg install -y "§pkg_name" >/dev/null 2>&1 ||
      warn "Could not update '§pkg_name'; continuing with installed version §installed."
  fi
}
ensure_pkg timeout coreutils || true
ensure_pkg sha256sum coreutils || true
ensure_pkg stat coreutils || true
maybe_update_pkg coreutils
if ! have python; then
  say "[i] Installing optional Python runtime for Veyra report tooling…"
  pkg install -y python >/dev/null 2>&1 || warn "Python install failed; current collector can continue without it."
fi
have python && maybe_update_pkg python
if [[ "§{VEYRA_DISABLE_ADB_SETUP:-0}" != "1" ]] && ! have adb; then
  say "[i] Installing Termux android-tools for optional ADB evidence…"
  pkg install -y android-tools >/dev/null 2>&1 || warn "android-tools install failed; ADB checks will be skipped."
fi
have adb && maybe_update_pkg android-tools

if [[ ! -d "§TERMUX_HOME/storage/downloads" ]] && have termux-setup-storage; then
  say "[i] Storage access is not ready. Requesting Android storage permission…"
  say "[i] Approve the Android dialog if one appears, then return to Termux."
  termux-setup-storage >/dev/null 2>&1 || true
  for _ in 1 2 3 4 5; do
    [[ -d "§TERMUX_HOME/storage/downloads" ]] && break
    sleep 1
  done
fi

VEYRA_OUT="§TERMUX_HOME/veyra-reports"
if [[ -d "§TERMUX_HOME/storage/downloads" && -w "§TERMUX_HOME/storage/downloads" ]]; then
  VEYRA_OUT="§TERMUX_HOME/storage/downloads/VeyraRoot/TermuxReports"
fi
mkdir -p "§VEYRA_OUT" 2>/dev/null || {
  VEYRA_OUT="§TERMUX_HOME/veyra-reports"
  mkdir -p "§VEYRA_OUT"
}

VEYRA_RISH=0
if have rish && timeout 5 rish -c 'id >/dev/null 2>&1' >/dev/null 2>&1; then VEYRA_RISH=1; fi
VEYRA_ADB=0
if have adb && timeout 5 adb get-state 2>/dev/null | grep -q '^device§'; then VEYRA_ADB=1; fi
say "[i] Output directory: §VEYRA_OUT"
[[ "§VEYRA_RISH" = 1 ]] && say "[i] Shizuku/rish: ready" || warn "Shizuku/rish is unavailable or not authorized; continuing without it."
[[ "§VEYRA_ADB" = 1 ]] && say "[i] ADB: paired/connected" || warn "ADB is not paired/connected; continuing without it."
""".trimIndent()

    private fun probeBody(): String = """
STARTED="§(date -u +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || echo unknown)"
STAMP="§(date -u +%Y%m%dT%H%M%SZ 2>/dev/null || echo unknown)"
REPORT="§VEYRA_OUT/veyra-termux-report-§STAMP.json"
SUMMARY="§VEYRA_OUT/veyra-termux-summary-§STAMP.txt"
LOG="§VEYRA_OUT/veyra-termux-diagnostics-§STAMP.log"
TMP="§VEYRA_OUT/.veyra-§STAMP-observations.jsonl"
: > "§TMP"; : > "§LOG"
escape_json() { local s="§{1-}"; s="§{s//\\/\\\\}"; s="§{s//\"/\\\"}"; s="§{s//§'\n'/\\n}"; printf '%s' "§s"; }
probe() {
  local field="§1" source="§2" group="§3"; shift 3
  local value='' status='SUCCESS'
  value="§(timeout 8 "§@" 2>/dev/null)" || status='ERROR'
  value="§{value//§'\r'/}"; value="§{value%%§'\n'*}"
  [[ -n "§value" ]] || status='NO_DATA'
  printf '{"field":"%s","value":"%s","source_id":"%s","independent_group":"%s","outcome":"%s","collected_utc":"%s","trust":"DIRECT_OBSERVATION"}\n' \
    "§(escape_json "§field")" "§(escape_json "§value")" "§(escape_json "§source")" "§(escape_json "§group")" "§status" "§STARTED" >> "§TMP"
}
unavailable() {
  local field="§1" source="§2" group="§3" reason="§4"
  printf '{"field":"%s","value":null,"source_id":"%s","independent_group":"%s","outcome":"NOT_APPLICABLE","collected_utc":"%s","trust":"NONE","notes":"%s"}\n' \
    "§(escape_json "§field")" "§(escape_json "§source")" "§(escape_json "§group")" "§STARTED" "§(escape_json "§reason")" >> "§TMP"
}
probe device.uname_r termux.uname live_kernel uname -r
probe device.uname_r proc.osrelease live_kernel cat /proc/sys/kernel/osrelease
probe device.build_fingerprint getprop.fingerprint android_properties getprop ro.build.fingerprint
probe device.product_device getprop.device android_properties getprop ro.product.device
probe device.sdk getprop.sdk android_properties getprop ro.build.version.sdk
probe device.security_patch getprop.patch android_properties getprop ro.build.version.security_patch
probe device.android_release getprop.release android_properties getprop ro.build.version.release
probe device.board_platform getprop.platform android_properties getprop ro.board.platform
probe device.hardware getprop.hardware android_properties getprop ro.hardware
if [[ "§VEYRA_RISH" = 1 ]]; then probe device.uname_r shizuku.rish.uname live_kernel rish -c 'uname -r'; else unavailable device.uname_r shizuku.rish.uname live_kernel 'rish unavailable'; fi
if [[ "§VEYRA_ADB" = 1 ]]; then
  local_device="§(getprop ro.product.device 2>/dev/null || true)"
  adb_device="§(timeout 8 adb shell getprop ro.product.device 2>/dev/null | tr -d '\r' | head -1 || true)"
  if [[ -n "§local_device" && "§local_device" == "§adb_device" ]]; then probe device.uname_r adb.shell.uname live_kernel adb shell uname -r; else unavailable device.uname_r adb.shell.uname live_kernel 'ADB target differs'; fi
else unavailable device.uname_r adb.shell.uname live_kernel 'ADB unavailable'; fi
{
  printf '{\n  "format":"veyra.termux.report/v1",\n  "session_id":"external-import",\n  "created_utc":"%s",\n  "collector":"veyra_builder_termux_readonly_v2",\n  "storage_path":"%s",\n  "observations":[\n' "§STARTED" "§(escape_json "§VEYRA_OUT")"
  first=1; while IFS= read -r record; do [[ "§first" = 1 ]] && first=0 || printf ',\n'; printf '    %s' "§record"; done < "§TMP"
  printf '\n  ]\n}\n'
} > "§REPORT"
rm -f "§TMP"
{
  echo 'VEYRA BUILDER — READ-ONLY TERMUX REPORT'
  echo "UTC: §STARTED"; echo "JSON: §REPORT"; echo "LOG: §LOG"
  echo 'Unavailable Shizuku/ADB routes are recorded, not treated as evidence.'
  echo 'No exploit offsets or root privileges are derived by this collector.'
} > "§SUMMARY"
say "[OK] JSON: §REPORT"; say "[OK] Summary: §SUMMARY"; say "[OK] Log: §LOG"; say "Used output path: §VEYRA_OUT"
""".trimIndent()

    private fun fallback1Body(): String = """
STAMP="§(date -u +%Y%m%dT%H%M%SZ 2>/dev/null || echo unknown)"
OUT="§VEYRA_OUT/veyra-fallback1-§STAMP.txt"
{
  echo 'VEYRA BUILDER — FALLBACK 1 / EXTRA LOCAL METADATA'
  echo 'Read-only. Same-origin Android properties are not independent kernel evidence.'
  for key in ro.vendor.build.fingerprint ro.bootimage.build.fingerprint ro.system.build.fingerprint ro.build.version.incremental ro.boot.hardware ro.boot.slot_suffix ro.product.manufacturer ro.product.model ro.board.platform ro.hardware; do
    value="§(getprop "§key" 2>/dev/null || true)"; [[ -n "§value" ]] || value=UNAVAILABLE
    printf '%s = %s\n' "§key" "§value"
  done
} > "§OUT"
say "[OK] Fallback 1: §OUT"; say "Used output path: §VEYRA_OUT"
""".trimIndent()

    private fun fallback2Body(): String = """
STAMP="§(date -u +%Y%m%dT%H%M%SZ 2>/dev/null || echo unknown)"
IN="§TERMUX_HOME/storage/downloads/VeyraRoot/BuilderInputs"
OUT="§VEYRA_OUT/veyra-fallback2-artifacts-§STAMP.txt"
{
  echo 'VEYRA BUILDER — FALLBACK 2 / USER-PROVIDED ARTIFACTS'
  echo "Input: §IN"; echo 'Only presence/hash/size are recorded; compatibility is not inferred.'
  if [[ ! -d "§IN" ]]; then echo 'INPUT_MISSING: create Downloads/VeyraRoot/BuilderInputs'; else
    count=0
    while IFS= read -r -d '' file; do
      count=§((count+1)); (( count <= 100 )) || { echo 'LIMIT_REACHED: 100 files'; break; }
      case "§{file##*/}" in
        *.img|*.conf|*.json|*.btf|*.sym|*.map|*.zip|*.bin|*.elf|*.so)
          echo "FILE: §{file##*/}"; sha256sum "§file" 2>/dev/null || echo SHA256_UNAVAILABLE; stat -c 'SIZE: %s bytes' "§file" 2>/dev/null || true ;;
        *) echo "SKIP_UNSUPPORTED_EXTENSION: §{file##*/}" ;;
      esac
    done < <(find "§IN" -maxdepth 2 -type f -print0 2>/dev/null)
    echo "COUNT: §count"
  fi
} > "§OUT"
say "[OK] Fallback 2: §OUT"; say "Used output path: §VEYRA_OUT"
""".trimIndent()

    private fun fallback3Body(): String = """
START="§(date +%s)"; LIMIT="§{VEYRA_MAX_SECONDS:-1200}"
[[ "§LIMIT" =~ ^[0-9]+§ ]] || LIMIT=1200; (( LIMIT <= 1200 )) || LIMIT=1200; (( LIMIT >= 1 )) || LIMIT=1
STAMP="§(date -u +%Y%m%dT%H%M%SZ 2>/dev/null || echo unknown)"
IN="§TERMUX_HOME/storage/downloads/VeyraRoot/BuilderInputs"; OUT="§VEYRA_OUT/veyra-fallback3-passive-§STAMP.txt"; PREV=''
{
  echo 'VEYRA BUILDER — FALLBACK 3 / PASSIVE FORENSICS'; echo "Limit: §{LIMIT}s"; echo 'No kernel stress, exploit trigger or guessed offsets.'
  while :; do
    NOW="§(date +%s)"; ELAPSED=§((NOW-START)); (( ELAPSED < LIMIT )) || break
    STATE="§( { uname -r 2>/dev/null || true; getprop ro.build.fingerprint 2>/dev/null || true; if [[ -d "§IN" ]]; then find "§IN" -maxdepth 2 -type f -printf '%f:%s:%T@\n' 2>/dev/null | sort; fi; } | sha256sum | awk '{print §1}')"
    if [[ "§STATE" != "§PREV" ]]; then echo "[§(date -u +%FT%TZ)] state_changed elapsed=§{ELAPSED}s fingerprint=§STATE"; PREV="§STATE"; fi
    sleep 15
  done
  echo "Finished after §(( §(date +%s)-START ))s"
} > "§OUT"
say "[OK] Fallback 3: §OUT"; say "Used output path: §VEYRA_OUT"
""".trimIndent()
}
