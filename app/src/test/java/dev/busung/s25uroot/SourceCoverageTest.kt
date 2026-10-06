package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceCoverageTest {

    @Test
    fun reportsTheUnionOfEveryPayloadsModelsAndKernels() {
        val catalog = SupportManifest(
            schemaVersion = 3,
            targets = listOf(
                profile("e2s-S926BXXUEDZDR", setOf("SM-S938B", "SM-S938N"), setOf("6.6.98")),
                profile("dm3q-S918B", setOf("SM-S938B"), setOf("6.6.94", "6.6.98-android15-8-build")),
            ),
        )

        val coverage = catalog.coverageFor(snapshot("SM-S938N", "6.6.98"), "4f9a2c1")

        assertEquals(2, coverage.payloadCount)
        // One model is offered by both payloads, so the union has to deduplicate it, and the order
        // has to be the same on every read of the same catalog.
        assertEquals(listOf("SM-S938B", "SM-S938N"), coverage.models)
        assertEquals(listOf("6.6.94", "6.6.98", "6.6.98-android15-8-build"), coverage.kernelVersions)
        assertEquals("4f9a2c1", coverage.commit)
    }

    @Test
    fun namesThePayloadARunWouldPickNotMerelyTheFirstThatFits() {
        val zcs = profile("pa2q-S9360ZCSCCZG1", setOf("SM-S9360"), setOf("6.6.98"))
        val zhs = profile(
            "pa2q-S9360ZHSCCZG1",
            setOf("SM-S9360"),
            setOf("6.6.98", "6.6.98-android15-8-abogkiS9360ZHSCCZG1-4k"),
        )

        val coverage = SupportManifest(3, listOf(zcs, zhs))
            .coverageFor(snapshot("SM-S9360", "6.6.98-android15-8-abogkiS9360ZHSCCZG1-4k"), "abc1234")

        // The exact kernel release wins here exactly as it does at install time, so a summary can
        // never promise a payload that a run would then decline to use.
        assertEquals("pa2q-S9360ZHSCCZG1", coverage.deviceProfileId)
        assertEquals(2, coverage.deviceProfileCount)
    }

    @Test
    fun reportsNoDeviceMatchForACatalogThatDoesNotCoverThisPhone() {
        val coverage = SupportManifest(3, listOf(profile("a54x-A546E", setOf("SM-A546E"), setOf("5.15.149"))))
            .coverageFor(snapshot("SM-S938B", "6.6.98"), "abc1234")

        assertNull(coverage.deviceProfileId)
        assertEquals(0, coverage.deviceProfileCount)
        // Still readable, and still worth showing: the models line is what tells the user this
        // source is for another phone rather than broken.
        assertEquals(listOf("SM-A546E"), coverage.models)
    }

    @Test
    fun anEmptyCatalogIsCoverageOfNothingRatherThanAFailure() {
        val coverage = SupportManifest(3, emptyList())
            .coverageFor(snapshot("SM-S938B", "6.6.98"), "abc1234")

        assertEquals(0, coverage.payloadCount)
        assertTrue(coverage.models.isEmpty())
        assertTrue(coverage.kernelVersions.isEmpty())
        assertNull(coverage.deviceProfileId)
    }

    private fun profile(
        id: String,
        models: Set<String>,
        kernelVersions: Set<String>,
    ) = TargetProfile(
        profileId = id,
        displayName = id,
        models = models,
        kernelVersions = kernelVersions,
        exploit = RemoteArtifact("https://example.invalid/exploit", 1),
        kernelSu = RemoteArtifact("https://example.invalid/ksud", 1),
    )

    private fun snapshot(
        model: String,
        kernelRelease: String,
    ) = DeviceSnapshot(
        manufacturer = "samsung",
        model = model,
        device = "unused",
        kernelRelease = kernelRelease,
        kernelVersionInfo = "#1 SMP PREEMPT",
        machine = "aarch64",
        buildId = "BP4A.251205.006.S938BCZG1",
        fingerprint = "samsung/example",
        androidRelease = "16",
        sdk = 36,
        abi = "arm64-v8a",
        pageSize = 4096,
    )
}
