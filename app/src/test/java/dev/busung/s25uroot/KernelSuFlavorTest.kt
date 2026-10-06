package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The flavour logic that decides which KernelSU a run installs.
 *
 * Three of these cover choices that would be silent if they were wrong. A manifest that names an
 * unknown flavour must refuse rather than fall back, because falling back installs the other
 * project's module into a kernel that asked for this one and nothing afterwards would explain it. A
 * spoofed manager build must not be preferred when a plain one is present, because it is the variant
 * that lies about its signature. And a run of the other flavour in a boot that already has one must
 * be refused, because both hook the same syscall paths and a late-load of the second does not
 * displace the first.
 */
class KernelSuFlavorTest {

    @Test
    fun `a feed entry that names no flavour is KernelSU`() {
        assertEquals(KernelSuFlavor.KernelSu, KernelSuFlavor.Default)
        assertEquals("kernelsu", KernelSuFlavor.Default.id)
    }

    @Test
    fun `an entry written before flavours existed still parses`() {
        // Every one of the 21 published payloads looks like this, so it has to keep meaning KernelSU.
        val parsed = SupportManifest.parse(manifest("""{ "payloadId": "p", "displayName": "d", """))

        assertEquals(KernelSuFlavor.KernelSu, parsed.targets.single().flavor)
    }

    @Test
    fun `an entry that names KernelSU-Next carries it`() {
        val parsed = SupportManifest.parse(
            manifest("""{ "payloadId": "p", "displayName": "d", "flavor": "kernelsu-next", """),
        )

        assertEquals(KernelSuFlavor.KernelSuNext, parsed.targets.single().flavor)
    }

    @Test
    fun `an unknown flavour id is left out rather than read as the default`() {
        // Two things at once, and both matter. The entry is not served - reading `kernel-su` as the
        // default would install another project's kernel on a phone that asked for one this build
        // cannot offer - and it is not fatal either, because a feed that gained a flavour must not
        // take every payload an older install had down with it. What is left out is reported, so a
        // payload that is in the file and nowhere in the app can be found.
        val parsed = SupportManifest.parse(
            manifest("""{ "payloadId": "p", "displayName": "d", "flavor": "kernel-su", """),
        )

        assertTrue(parsed.targets.isEmpty())
        assertEquals(listOf(UnreadablePayload(payloadId = "p", declaredFlavor = "kernel-su")), parsed.ignored)
    }

    @Test
    fun `ids are matched without case or padding`() {
        assertEquals(KernelSuFlavor.KernelSuNext, KernelSuFlavor.fromId("  KernelSU-Next "))
        assertEquals(KernelSuFlavor.ReSukiSU, KernelSuFlavor.fromId(" ReSukiSU "))
        assertNull(KernelSuFlavor.fromId(""))
        assertNull(KernelSuFlavor.fromId(null))
        assertNull(KernelSuFlavor.fromId("kernelsu-next-2"))
        // The id shares a substring with KernelSU's, so a match has to be whole rather than partial:
        // anything looser would file this project's kernel under the other one's setting.
        assertNull(KernelSuFlavor.fromId("resu"))
        assertNull(KernelSuFlavor.fromId("sukisu-ultra"))
    }

    @Test
    fun `each flavour names its own manager package and release`() {
        assertEquals("me.weishu.kernelsu", KernelSuFlavor.KernelSu.managerPackage)
        assertEquals("com.rifsxd.ksunext", KernelSuFlavor.KernelSuNext.managerPackage)
        // Each flavour offers the KernelSU this project's payloads for it are built from, so the daemon
        // a run stages and the manager that talks to it come from the same release. The two are not the
        // same number: the KernelSU-Next payload pins 3.4.0, KernelSU's is still 3.3.0. Nothing rejects
        // a manager installed by hand in their place.
        assertEquals("3.3.0", KernelSuFlavor.KernelSu.defaultManagerVersion)
        assertEquals("3.4.0", KernelSuFlavor.KernelSuNext.defaultManagerVersion)
        assertEquals(
            "https://github.com/tiann/KernelSU/releases/download/v3.3.0/" +
                "KernelSU_v3.3.0_32601-release.apk",
            KernelSuFlavor.KernelSu.defaultManagerRelease.url,
        )

        // ReSukiSU's own, and the one that is not shaped like the other two: it publishes every release
        // as a pre-release, so the version is a name with a suffix in it, and it ships one manager per
        // ABI, so the file the app would hand over is the universal one.
        assertEquals("com.resukisu.resukisu", KernelSuFlavor.ReSukiSU.managerPackage)
        assertEquals("ReSukiSU/ReSukiSU", KernelSuFlavor.ReSukiSU.repository)
        assertEquals("4.2.0-rc2", KernelSuFlavor.ReSukiSU.defaultManagerVersion)
        assertEquals(
            "https://github.com/ReSukiSU/ReSukiSU/releases/download/v4.2.0-rc2/" +
                "ReSukiSU_v4.2.0-rc2_35144-universal-release.apk",
            KernelSuFlavor.ReSukiSU.defaultManagerRelease.url,
        )
    }

    @Test
    fun `a default asset is named for the version it downloads`() {
        // A default is the one download that never asks the release lookup - its file name is the whole
        // answer - so a bump that moves the version without moving the asset name is a 404 that only
        // appears on the phone. These two facts are the release's own shape: the tag it is published
        // under, and the file inside it.
        for (flavor in KernelSuFlavor.entries) {
            assertTrue(
                "${flavor.label}: ${flavor.defaultManagerAsset} does not name " +
                    "v${flavor.defaultManagerVersion}",
                flavor.defaultManagerAsset.contains("v${flavor.defaultManagerVersion}_"),
            )
            assertTrue(flavor.defaultManagerAsset.endsWith("-release.apk"))
            assertTrue(flavor.defaultManagerRelease.url.contains(flavor.repository))
        }
    }

    @Test
    fun `a release resolves to its APK and skips the spoofed build`() {
        val body = """
            {
              "tag_name": "v3.3.0",
              "assets": [
                { "name": "android15-6.6_kernelsu.ko", "browser_download_url": "https://example.invalid/ko" },
                { "name": "KernelSU_Next_v3.3.0-spoofed_33214-release.apk", "browser_download_url": "https://example.invalid/spoofed.apk" },
                { "name": "KernelSU_Next_v3.3.0_33214-release.apk", "browser_download_url": "https://example.invalid/plain.apk" }
              ]
            }
        """.trimIndent()

        assertEquals("https://example.invalid/plain.apk", managerApkInRelease(body))
    }

    @Test
    fun `a release carrying only the spoofed build still resolves`() {
        // A user who named a version has already decided; a refusal here would leave them with a
        // version that cannot be installed for a reason they cannot act on.
        val body = """
            {
              "assets": [
                { "name": "KernelSU_Next_v3.3.0-spoofed_33214-release.apk", "browser_download_url": "https://example.invalid/spoofed.apk" }
              ]
            }
        """.trimIndent()

        assertEquals("https://example.invalid/spoofed.apk", managerApkInRelease(body))
    }

    @Test
    fun `a release with no APK resolves to nothing`() {
        assertNull(managerApkInRelease("""{ "assets": [ { "name": "ksud", "browser_download_url": "u" } ] }"""))
        assertNull(managerApkInRelease("""{ "assets": [] }"""))
        assertNull(managerApkInRelease("""{ "assets": [ { "name": "x.apk" } ] }"""))
        assertNull(managerApkInRelease("not json at all"))
    }

    @Test
    fun `the configured flavour is preferred and the other one is still usable`() {
        val ksu = profile("ksu", KernelSuFlavor.KernelSu)
        val next = profile("next", KernelSuFlavor.KernelSuNext)
        val device = snapshot()

        // Neither is preferred over the other by position: the flavour is what decides.
        assertEquals("next", listOf(ksu, next).resolveFor(device, KernelSuFlavor.KernelSuNext)?.profileId)
        assertEquals("ksu", listOf(next, ksu).resolveFor(device, KernelSuFlavor.KernelSu)?.profileId)
    }

    @Test
    fun `an exact release still wins inside the preferred flavour`() {
        val threePart = profile("next-three-part", KernelSuFlavor.KernelSuNext)
            .copy(kernelVersions = setOf("6.6.98"))
        val exact = profile("next-exact", KernelSuFlavor.KernelSuNext)
            .copy(kernelVersions = setOf("6.6.98", DEVICE_RELEASE))

        val selected = listOf(threePart, exact).resolveFor(snapshot(), KernelSuFlavor.KernelSuNext)

        assertEquals("next-exact", selected?.profileId)
    }

    @Test
    fun `a catalog carrying only the other flavour is still used`() {
        // Refusing here would leave a user whose sources hold one flavour with no run and nothing
        // that says which repository to add. The profile says which flavour it is, and the run logs it.
        val selected = listOf(profile("ksu", KernelSuFlavor.KernelSu))
            .resolveFor(snapshot(), KernelSuFlavor.KernelSuNext)

        assertEquals("ksu", selected?.profileId)
        assertEquals(KernelSuFlavor.KernelSu, selected?.flavor)
    }

    @Test
    fun `the other flavour in this boot is a restart rather than a retry`() {
        assertEquals(FlavorBootState.Loadable, flavorBootState(KernelSuFlavor.KernelSu, null))
        assertEquals(
            FlavorBootState.AlreadyLoaded,
            flavorBootState(KernelSuFlavor.KernelSu, KernelSuFlavor.KernelSu),
        )
        assertEquals(
            FlavorBootState.OtherFlavorLoaded,
            flavorBootState(KernelSuFlavor.KernelSuNext, KernelSuFlavor.KernelSu),
        )
    }

    /** The device these payloads are tested on, which is the release an exact match has to name. */
    private val DEVICE_RELEASE = "6.6.98-android15-8-pd6ff1cd-abogkiS938USQSCCZF9-4k"

    private fun profile(id: String, flavor: KernelSuFlavor) = TargetProfile(
        profileId = id,
        displayName = id,
        models = setOf("SM-S938U1"),
        kernelVersions = setOf("6.6.98"),
        exploit = RemoteArtifact("https://example.invalid/exploit", 1),
        kernelSu = RemoteArtifact("https://example.invalid/ksud", 1),
        flavor = flavor,
    )

    private fun snapshot() = DeviceSnapshot(
        manufacturer = "samsung",
        model = "SM-S938U1",
        device = "pa3q",
        kernelRelease = DEVICE_RELEASE,
        kernelVersionInfo = "#1 SMP PREEMPT",
        machine = "aarch64",
        buildId = "BP4A.251205.006",
        fingerprint = "samsung/pa3q",
        androidRelease = "16",
        sdk = 36,
        abi = "arm64-v8a",
        pageSize = 4096,
    )

    /** One payload, with the opening of its entry left to the caller so a field can be injected. */
    private fun manifest(entry: String): ByteArray = """
        {
          "schemaVersion": 3,
          "payloads": [
            ${entry.trimIndent()}
              "models": ["SM-S938U1"],
              "kernelVersions": ["6.6.98"],
              "exploit": { "url": "https://example.invalid/exploit", "size": 1 },
              "kernelsu": { "url": "https://example.invalid/ksud", "size": 1 }
            }
          ]
        }
    """.trimIndent().toByteArray()
}
