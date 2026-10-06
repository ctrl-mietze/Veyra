package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {

    @Test
    fun aBuildSuffixIsNotAnUpdateOverItsOwnVersion() {
        // Every build carries this suffix so two installs of a version can be told apart; treating
        // it as a difference offered the release already installed as an update, forever.
        assertFalse(
            AppUpdater.isUpdateAvailable(
                latestVersion = "0.2.65",
                currentVersion = "0.2.65+local.4f9a2c1",
            ),
        )
        assertFalse(
            AppUpdater.isUpdateAvailable(
                latestVersion = "0.2.65",
                currentVersion = "0.2.65+ci.42.4f9a2c1",
            ),
        )
    }

    @Test
    fun aNewerReleaseIsAnUpdate() {
        assertTrue(
            AppUpdater.isUpdateAvailable(
                latestVersion = "0.2.66",
                currentVersion = "0.2.65+ci.42.4f9a2c1",
            ),
        )
        assertTrue(
            AppUpdater.isUpdateAvailable(
                latestVersion = "v0.3.0",
                currentVersion = "0.2.65+local.4f9a2c1",
            ),
        )
    }

    @Test
    fun anOlderOrEqualReleaseIsNotAnUpdate() {
        assertFalse(AppUpdater.isUpdateAvailable("0.2.64", "0.2.65+local"))
        assertFalse(AppUpdater.isUpdateAvailable("0.2.65", "0.2.65"))
        assertFalse(AppUpdater.isUpdateAvailable("", "0.2.65"))
    }

    @Test
    fun versionPartsAreComparedAsNumbersNotText() {
        assertTrue(AppUpdater.isUpdateAvailable("0.2.10", "0.2.9"))
        assertTrue(AppUpdater.isUpdateAvailable("0.3", "0.2.9"))
        assertTrue(AppUpdater.compareVersions("1.0.0", "0.9.9") > 0)
        assertEquals(0, AppUpdater.compareVersions("0.2.65", "0.2.65.0"))
        assertTrue(AppUpdater.compareVersions("0.2.64", "0.2.65") < 0)
    }

    @Test
    fun aTagThatIsNotADottedVersionFallsBackToBeingDifferent() {
        assertNull(AppUpdater.versionBase("nightly"))
        assertTrue(AppUpdater.isUpdateAvailable("nightly", "0.2.65+local"))
        assertFalse(AppUpdater.isUpdateAvailable("nightly", "nightly"))
    }

    @Test
    fun versionBaseStripsTheDecoration() {
        assertEquals("0.2.65", AppUpdater.versionBase("0.2.65"))
        assertEquals("0.2.65", AppUpdater.versionBase("v0.2.65"))
        assertEquals("0.2.65", AppUpdater.versionBase("0.2.65+ci.42.4f9a2c1"))
        assertEquals("0.2.65", AppUpdater.versionBase("0.2.65-rc1"))
        assertNull(AppUpdater.versionBase(""))
    }
}
