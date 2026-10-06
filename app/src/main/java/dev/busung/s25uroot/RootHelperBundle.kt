package ctrl.mietze.veyraroot

import android.content.Context
import java.io.File

/**
 * Root-helper contract for Veyra.
 *
 * Current v3 firmware profiles publish one canonical helper contract. Veyra bundles a byte-for-byte
 * copy of that helper except for one same-length data-path rewrite that points the root-side bootstrap
 * at Veyra's own private files directory instead of the upstream app package.
 *
 * Legacy feeds without rootHelper metadata keep using the untouched RMG Next v0.4 helper.
 */
internal object RootHelperBundle {
    const val CURRENT_LIBRARY = "libcve43499root.so"
    const val LEGACY_LIBRARY = "librmgn04root.so"

    private const val CURRENT_UPSTREAM_SIZE = 32888L
    private const val CURRENT_UPSTREAM_SHA256 =
        "4dd29619caae2b08aa491d7fcfc2e5d0d1ea11d0b6b9d1673d49f7580a40858f"
    private const val CURRENT_VEYRA_SHA256 =
        "ef18aa3842a4ddd677a09882510503de045f1f4366819a73ff34df6f0e258756"

    private const val LEGACY_SIZE = 21464L
    private const val LEGACY_SHA256 =
        "c37cef0dd05b97b4845e1dde3927b15b9615be8fda551c23dc8b2cce0c62fc07"

    fun forProfile(context: Context, profile: TargetProfile): File {
        val expected = profile.rootHelper
        val library = if (expected != null) CURRENT_LIBRARY else LEGACY_LIBRARY
        val file = File(context.applicationInfo.nativeLibraryDir, library)
        require(file.isFile && file.canExecute()) {
            "The bundled root helper $library is unavailable or not executable"
        }

        if (expected == null) {
            require(file.length() == LEGACY_SIZE && sha256Of(file) == LEGACY_SHA256) {
                "The bundled legacy RMG Next root helper failed integrity verification"
            }
            return file
        }

        val declaredSha = expected.sha256?.lowercase()
        require(
            expected.size == CURRENT_UPSTREAM_SIZE &&
                declaredSha == CURRENT_UPSTREAM_SHA256
        ) {
            "The selected payload feed uses a newer root-helper contract than this Veyra build. " +
                "Update Veyra before running this payload."
        }

        require(
            file.length() == CURRENT_UPSTREAM_SIZE &&
                sha256Of(file) == CURRENT_VEYRA_SHA256
        ) {
            "Veyra's bundled root helper failed integrity verification"
        }
        return file
    }

    fun currentFile(context: Context): File =
        File(context.applicationInfo.nativeLibraryDir, CURRENT_LIBRARY)
}
