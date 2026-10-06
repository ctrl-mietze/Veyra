package ctrl.mietze.veyraroot

import android.content.Context
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class VeyraKsuExport(
    val path: String,
    val bytes: Int,
)

internal data class VeyraKsuState(
    val installed: Boolean = false,
    val enabled: Boolean = false,
    val stage: String = "",
    val bootId: String = "",
    val tempRootSeen: Boolean = false,
    val permissionBridge: String = "inactive",
    val moduleVersion: String = "",
    val moduleVersionCode: Int = 0,
    val updatedAt: Long = 0L,
)

internal object VeyraKsuBridge {
    private const val MODULE_DIR = "/data/adb/modules/veyra_ksu"
    private const val STATE_DIR = "/data/adb/veyra_ksu"
    private const val STATE_FILE = "$STATE_DIR/state.properties"
    fun readState(): VeyraKsuState {
        val result = SuShell.run(
            "printf 'installed=%s\\n' \"\$( [ -d $MODULE_DIR ] && echo 1 || echo 0 )\"; " +
                "printf 'enabled=%s\\n' \"\$( [ -d $MODULE_DIR ] && [ ! -f $MODULE_DIR/disable ] && echo 1 || echo 0 )\"; " +
                "[ -r $STATE_FILE ] && cat $STATE_FILE || true; " +
                "if [ -r $MODULE_DIR/module.prop ]; then " +
                "printf 'module_prop_version=%s\\n' \"\$(sed -n 's/^version=//p' $MODULE_DIR/module.prop | head -n1)\"; " +
                "printf 'module_prop_version_code=%s\\n' \"\$(sed -n 's/^versionCode=//p' $MODULE_DIR/module.prop | head -n1)\"; fi; " +
                "printf 'access_active=%s\n' \"\$( [ -f $STATE_DIR/permission_enabled ] && echo 1 || echo 0 )\"",
            timeoutSeconds = 8,
        ) ?: return VeyraKsuState()

        val values = result.output.lineSequence()
            .mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
            }
            .toMap()

        return VeyraKsuState(
            installed = values["installed"] == "1",
            enabled = values["enabled"] == "1",
            stage = values["stage"].orEmpty(),
            bootId = values["boot_id"].orEmpty(),
            tempRootSeen = values["temp_root"] == "1",
            permissionBridge = if (values["access_active"] == "1") {
                "active"
            } else {
                values["permission_bridge"] ?: "inactive"
            },
            moduleVersion = values["module_prop_version"]
                ?.takeIf(String::isNotBlank)
                ?: values["module_version"].orEmpty(),
            moduleVersionCode = values["module_prop_version_code"]?.toIntOrNull()
                ?: values["module_version_code"]?.toIntOrNull()
                ?: 0,
            updatedAt = values["updated_at"]?.toLongOrNull() ?: 0L,
        )
    }
    fun setEnabled(enabled: Boolean): Boolean {
        val command = if (enabled) {
            "[ -d $MODULE_DIR ] && rm -f $MODULE_DIR/disable"
        } else {
            "[ -d $MODULE_DIR ] && : > $MODULE_DIR/disable"
        }
        val result = SuShell.run(command)
        return result != null && result.exitCode == 0
    }

    fun activatePermissionBridge(): Boolean {
        val result = SuShell.run(
            "mkdir -p $STATE_DIR && " +
                ": > $STATE_DIR/permission_enabled && " +
                "chmod 600 $STATE_DIR/permission_enabled && " +
                "if [ -r $STATE_FILE ]; then " +
                "sed 's/^permission_bridge=.*/permission_bridge=active/' $STATE_FILE > $STATE_FILE.tmp && " +
                "mv -f $STATE_FILE.tmp $STATE_FILE && chmod 600 $STATE_FILE; fi",
            timeoutSeconds = 8,
        )
        return result != null && result.exitCode == 0
    }

    fun setPermissionBridge(enabled: Boolean): Boolean {
        val command = if (enabled) {
            "mkdir -p $STATE_DIR && : > $STATE_DIR/permission_enabled && chmod 600 $STATE_DIR/permission_enabled"
        } else {
            "rm -f $STATE_DIR/permission_enabled"
        }
        val result = SuShell.run(command)
        return result != null && result.exitCode == 0
    }

    fun markDirectRootVerified(): Boolean {
        val result = SuShell.run(
            "mkdir -p $STATE_DIR && " +
                ": > $STATE_DIR/direct_root_verified && " +
                "chmod 600 $STATE_DIR/direct_root_verified",
        )
        return result != null && result.exitCode == 0
    }

    fun markTempRootStarted(flavor: String, bootId: String?): Boolean {
        val safeFlavor = shellQuote(flavor)
        val safeBoot = shellQuote(bootId.orEmpty())
        val result = SuShell.run(
            "mkdir -p $STATE_DIR && " +
                "printf 'flavor=%s\\nboot_id=%s\\n' $safeFlavor $safeBoot > $STATE_DIR/temp_root.properties",
        )
        return result != null && result.exitCode == 0
    }
}

internal object VeyraKsuModule {
    private const val MODULE_ID = "veyra_ksu"

    private fun shellScript(text: String): String =
        text.trimIndent().replace('§', '$') + "\n"

    fun export(context: Context): VeyraKsuExport {
        val bytes = ByteArrayOutputStream().use { buffer ->
            ZipOutputStream(buffer).use { zip ->
                fun entry(name: String, text: String) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(text.toByteArray())
                    zip.closeEntry()
                }

                entry(
                    "module.prop",
                    """
id=$MODULE_ID
name=VeyraKSU
version=2.0.0
versionCode=20000
author=Veyra
description=Veyra KernelSU bridge for CVeyra Access 2.0, permission-policy state, module lifecycle, migration and recovery integration.
                    """.trimIndent() + "\n",
                )
                entry("skip_mount", "")
                entry(
                    "customize.sh",
                    shellScript(
                        """
SKIPUNZIP=0
set_perm §MODPATH/post-fs-data.sh 0 0 0755
set_perm §MODPATH/service.sh 0 0 0755
set_perm §MODPATH/late-load.sh 0 0 0755
set_perm §MODPATH/firewall.sh 0 0 0755
set_perm §MODPATH/action.sh 0 0 0755
                        """,
                    ),
                )
                entry(
                    "post-fs-data.sh",
                    shellScript(
                        """
#!/system/bin/sh
STATE=/data/adb/veyra_ksu
mkdir -p "§STATE"
chmod 700 "§STATE"
BOOT="§(cat /proc/sys/kernel/random/boot_id 2>/dev/null)"
NOW="§(date +%s 2>/dev/null)"
TEMP=0
[ -f "§STATE/temp_root.properties" ] && TEMP=1
PERM=ready
[ -f "§STATE/permission_enabled" ] && PERM=active
mkdir -p "§STATE/policies" "§STATE/firewall" "§STATE/audit"
chmod 700 "§STATE/policies" "§STATE/firewall" "§STATE/audit"
cat >"§STATE/state.tmp" <<EOF
stage=post-fs-data
boot_id=§BOOT
ksu_active=1
temp_root=§TEMP
module_version=2.0.0
module_version_code=20000
permission_bridge=§PERM
updated_at=§NOW
EOF
mv -f "§STATE/state.tmp" "§STATE/state.properties"
chmod 600 "§STATE/state.properties"
                        """,
                    ),
                )
                entry(
                    "service.sh",
                    shellScript(
                        """
#!/system/bin/sh
STATE=/data/adb/veyra_ksu
mkdir -p "§STATE"
BOOT="§(cat /proc/sys/kernel/random/boot_id 2>/dev/null)"
NOW="§(date +%s 2>/dev/null)"
TEMP=0
[ -f "§STATE/temp_root.properties" ] && TEMP=1
PERM=ready
[ -f "§STATE/permission_enabled" ] && PERM=active
mkdir -p "§STATE/policies" "§STATE/firewall" "§STATE/audit"
chmod 700 "§STATE/policies" "§STATE/firewall" "§STATE/audit"
cat >"§STATE/state.tmp" <<EOF
stage=service
boot_id=§BOOT
ksu_active=1
temp_root=§TEMP
module_version=2.0.0
module_version_code=20000
permission_bridge=§PERM
updated_at=§NOW
EOF
mv -f "§STATE/state.tmp" "§STATE/state.properties"
chmod 600 "§STATE/state.properties"
MODDIR=§{0%/*}
[ -x "§MODDIR/firewall.sh" ] && "§MODDIR/firewall.sh" apply >/dev/null 2>&1 || true
                        """,
                    ),
                )
                entry(
                    "late-load.sh",
                    shellScript(
                        """
#!/system/bin/sh
STATE=/data/adb/veyra_ksu
mkdir -p "§STATE"
BOOT="§(cat /proc/sys/kernel/random/boot_id 2>/dev/null)"
NOW="§(date +%s 2>/dev/null)"
PERM=ready
[ -f "§STATE/permission_enabled" ] && PERM=active
mkdir -p "§STATE/policies" "§STATE/firewall" "§STATE/audit"
chmod 700 "§STATE/policies" "§STATE/firewall" "§STATE/audit"
cat >"§STATE/state.tmp" <<EOF
stage=late-load
boot_id=§BOOT
ksu_active=1
temp_root=1
module_version=2.0.0
module_version_code=20000
permission_bridge=§PERM
updated_at=§NOW
EOF
mv -f "§STATE/state.tmp" "§STATE/state.properties"
chmod 600 "§STATE/state.properties"
                        """,
                    ),
                )
                entry(
                    "firewall.sh",
                    shellScript(
                        """
#!/system/bin/sh
STATE=/data/adb/veyra_ksu
UIDS="§STATE/firewall.uids"
CHAIN=VEYRA_OUT

apply_family() {
  BIN="§1"
  command -v "§BIN" >/dev/null 2>&1 || return 0
  "§BIN" -w 2 -N "§CHAIN" 2>/dev/null || true
  "§BIN" -w 2 -C OUTPUT -j "§CHAIN" 2>/dev/null ||
    "§BIN" -w 2 -I OUTPUT 1 -j "§CHAIN" || return 1
  "§BIN" -w 2 -F "§CHAIN" || return 1
  if [ -r "§UIDS" ]; then
    while IFS= read -r UID; do
      case "§UID" in
        ''|*[!0-9]*) continue ;;
      esac
      "§BIN" -w 2 -A "§CHAIN" -m owner --uid-owner "§UID" -j REJECT || return 1
    done < "§UIDS"
  fi
}

clear_family() {
  BIN="§1"
  command -v "§BIN" >/dev/null 2>&1 || return 0
  "§BIN" -w 2 -D OUTPUT -j "§CHAIN" 2>/dev/null || true
  "§BIN" -w 2 -F "§CHAIN" 2>/dev/null || true
  "§BIN" -w 2 -X "§CHAIN" 2>/dev/null || true
}

case "§1" in
  clear)
    clear_family iptables
    clear_family ip6tables
    ;;
  *)
    mkdir -p "§STATE"
    chmod 700 "§STATE"
    apply_family iptables
    apply_family ip6tables
    ;;
esac
                        """,
                    ),
                )
                entry(
                    "action.sh",
                    shellScript(
                        """
#!/system/bin/sh
KSUD="§(command -v ksud 2>/dev/null)"
[ -n "§KSUD" ] || KSUD=/data/adb/ksud
if [ ! -x "§KSUD" ]; then
  echo "[VeyraKSU] ksud not found"
  exit 1
fi
if "§KSUD" --help 2>&1 | grep -q 'soft-reboot'; then
  echo "[VeyraKSU] requesting KernelSU soft reboot"
  exec "§KSUD" soft-reboot
fi
echo "[VeyraKSU] installed ksud has no soft-reboot command"
exit 1
                        """,
                    ),
                )
            }
            buffer.toByteArray()
        }

        val path = DownloadStore.save(context, "VeyraKSU.zip", bytes)
        return VeyraKsuExport(path, bytes.size)
    }

    /**
     * One install/update command shared by the automatic post-root path and the VeyraKSU screen.
     *
     * The ZIP is first copied to shell/root-owned temporary storage. That avoids relying on the
     * installed ksud being able to traverse MediaStore/FUSE paths while Android is in the middle of a
     * root transition. Success means more than ksud returning zero: either the active module tree or
     * KernelSU's modules_update tree must contain VeyraKSU afterwards.
     */
    fun installCommand(path: String): String {
        val quoted = shellQuote(path)
        return shellScript(
            """
SRC=$quoted
STAGE=/data/local/tmp/.veyra-ksu-install.zip
ACTIVE=/data/adb/modules/$MODULE_ID
UPDATE=/data/adb/modules_update/$MODULE_ID
rm -f "§STAGE"
cp "§SRC" "§STAGE" || exit 121
chmod 0644 "§STAGE" || exit 122
K=§(command -v ksud 2>/dev/null)
[ -n "§K" ] || K=/data/adb/ksud
[ -x "§K" ] || { rm -f "§STAGE"; exit 127; }
"§K" module install "§STAGE"
RC=§?
rm -f "§STAGE"
[ "§RC" = "0" ] || exit "§RC"
[ -f "§ACTIVE/module.prop" ] || [ -f "§UPDATE/module.prop" ] || exit 123
rm -f "§ACTIVE/disable" "§ACTIVE/remove" "§UPDATE/disable" "§UPDATE/remove" 2>/dev/null || true
echo VeyraKSU_INSTALL_OK
            """,
        )
    }

    /** Installs/updates VeyraKSU through the verified KernelSU root broker used everywhere else. */
    fun installWithKernelSu(path: String): ShizukuController.ShellResult? =
        KernelSuRuntime.rootShell(installCommand(path), timeoutSeconds = 60)

    /** A small independent verification used after install/reload. */
    fun verifyInstallCommand(): String = shellScript(
        """
ACTIVE=/data/adb/modules/$MODULE_ID
UPDATE=/data/adb/modules_update/$MODULE_ID
if [ -f "§ACTIVE/module.prop" ]; then
  [ ! -e "§ACTIVE/disable" ] && [ ! -e "§ACTIVE/remove" ] || exit 132
  echo VeyraKSU_ACTIVE
  exit 0
fi
if [ -f "§UPDATE/module.prop" ]; then
  [ ! -e "§UPDATE/disable" ] && [ ! -e "§UPDATE/remove" ] || exit 133
  echo VeyraKSU_PENDING_UPDATE
  exit 0
fi
exit 131
        """,
    )
}
