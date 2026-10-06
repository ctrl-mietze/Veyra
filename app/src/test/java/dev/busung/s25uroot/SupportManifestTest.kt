package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The manifest parser on real `org.json` rather than Android's stubbed copy, so what is asserted
 * here is what the app does when it reads a feed.
 */
class SupportManifestTest {

    private val manifest = """
        {
          "schemaVersion": 3,
          "payloads": [
            {
              "payloadId": "e2s-S926BXXUEDZDR",
              "displayName": "Galaxy S25",
              "models": ["SM-S931B", "SM-S931N"],
              "kernelVersions": ["6.6.98"],
              "requiresFreshP0Session": true,
              "exploit": {
                "url": "https://raw.githubusercontent.com/example/feed/main/exploit.so",
                "size": 4096
              },
              "kernelsu": {
                "url": "https://raw.githubusercontent.com/example/feed/main/ksud",
                "size": 8192
              }
            },
            {
              "payloadId": "dm3q-S918B",
              "displayName": "Galaxy S23 Ultra",
              "models": ["SM-S918B"],
              "kernelVersions": ["5.15.149", "5.15.149-android13-8-build"],
              "exploit": {
                "url": "https://raw.githubusercontent.com/example/feed/main/old.so",
                "size": 0,
                "verifySize": false
              },
              "kernelsu": {
                "url": "https://raw.githubusercontent.com/example/feed/main/old-ksud",
                "size": 1024
              }
            }
          ]
        }
    """.trimIndent().toByteArray()

    @Test
    fun readsEveryPayloadWithItsModelsAndKernelVersions() {
        val parsed = SupportManifest.parse(manifest)

        assertEquals(3, parsed.schemaVersion)
        assertEquals(2, parsed.targets.size)

        val first = parsed.targets[0]
        assertEquals("e2s-S926BXXUEDZDR", first.profileId)
        assertEquals(setOf("SM-S931B", "SM-S931N"), first.models)
        assertEquals(setOf("6.6.98"), first.kernelVersions)
        assertEquals(4096L, first.exploit.size)
        assertEquals("https://raw.githubusercontent.com/example/feed/main/ksud", first.kernelSu.url)
    }

    @Test
    fun keepsTheFreshSessionFlagAndTheSizeEscapeHatch() {
        val parsed = SupportManifest.parse(manifest)

        // Both are per-payload opt-ins the app acts on, so a parse that dropped either would change
        // how a run behaves without anyone editing the feed.
        assertTrue(parsed.targets[0].requiresFreshP0Session)
        assertFalse(parsed.targets[1].requiresFreshP0Session)
        assertFalse(parsed.targets[1].exploit.verifySize)

        // And the default stays strict for a payload that does not ask otherwise.
        assertTrue(parsed.targets[1].kernelSu.verifySize)
    }

    @Test
    fun resolvesProfilesForADeviceFromParsedInput() {
        val parsed = SupportManifest.parse(manifest)
        val snapshot = DeviceSnapshot(
            manufacturer = "samsung",
            model = "SM-S931B",
            device = "e2s",
            kernelRelease = "6.6.98-android15-8-build",
            kernelVersionInfo = "#1 SMP PREEMPT",
            machine = "aarch64",
            buildId = "BP4A.251205.006",
            fingerprint = "samsung/e2s",
            androidRelease = "16",
            sdk = 36,
            abi = "arm64-v8a",
            pageSize = 4096,
        )

        assertEquals("e2s-S926BXXUEDZDR", parsed.targets.resolveFor(snapshot)?.profileId)
    }

    @Test
    fun exactMatchKernelReleaseBecomesExactCoverage() {
        val release = "6.6.98-android15-8-pd6ff1cd-abogkiS938BXXSBCZG3-4k"
        val parsed = SupportManifest.parse(
            """
            {"schemaVersion":3,"payloads":[{
              "payloadId":"pa3q-S938BXXSBCZG3",
              "displayName":"Galaxy S25 Ultra SM-S938B | S938BXXSBCZG3",
              "models":["SM-S938B"],
              "kernelVersions":["6.6.98"],
              "exactMatch":{"kernelRelease":"$release"},
              "exploit":{"url":"https://example.invalid/exploit.so","size":4096},
              "kernelsu":{"url":"https://example.invalid/ksud","size":8192}
            }]}
            """.trimIndent().toByteArray(),
        )
        val snapshot = DeviceSnapshot(
            manufacturer = "samsung",
            model = "SM-S938B",
            device = "pa3q",
            kernelRelease = release,
            kernelVersionInfo = "#1 SMP PREEMPT",
            machine = "aarch64",
            buildId = "BP4A.251205.006",
            fingerprint = "samsung/pa3qxeea/pa3q",
            androidRelease = "16",
            sdk = 36,
            abi = "arm64-v8a",
            pageSize = 4096,
        )

        val target = parsed.targets.single()
        assertTrue(release in target.kernelVersions)
        assertEquals(KernelMatch.Exact, target.kernelMatch(snapshot))
        assertEquals(target.profileId, parsed.targets.resolveFor(snapshot)?.profileId)
    }

    @Test
    fun readsTheKernelSuVersionAnEntryDeclares() {
        // A feed may write the tag it was built from or the version, and the two have to arrive here as
        // the same value: this is compared against a manager's own `versionName` and against the
        // flavour's fallback, both of which are dotted numbers.
        val tagged = SupportManifest.parse(
            """
            {"schemaVersion":3,"payloads":[{
              "payloadId":"pa3q-S938USQSCCZF9-ksun340",
              "displayName":"Galaxy S25 Ultra | KernelSU-Next 3.4.0 (test)",
              "models":["SM-S938U1"],
              "kernelVersions":["6.6.98"],
              "flavor":"kernelsu-next",
              "exploit":{"url":"https://example.invalid/exploit.so","size":4096},
              "kernelsu":{"url":"https://example.invalid/ksud","size":8192,"version":"v3.4.0"}
            }]}
            """.trimIndent().toByteArray(),
        )
        assertEquals("3.4.0", tagged.targets.single().kernelSuVersion)
    }

    @Test
    fun anEntryThatDeclaresNoVersionReadsAsNothing() {
        // Rather than as a version: the manager offer falls back to the flavour's own release there, and
        // a value invented here would be a version no release can be looked up for.
        val parsed = SupportManifest.parse(manifest)

        assertNull(parsed.targets[0].kernelSuVersion)
        assertNull(parsed.targets[1].kernelSuVersion)
    }

    @Test
    fun dropsAnEntryWhoseFlavourThisBuildDoesNotKnow() {
        // The rest of the feed has to survive it. Refusing the whole manifest is what this used to do,
        // and it makes every install older than a new flavour lose every payload it had - while the
        // entry must still not be read as the default, because a device offered the other project's
        // kernel because a name looked close is the outcome nothing can explain afterwards.
        val parsed = SupportManifest.parse(
            """
            {"schemaVersion":3,"payloads":[
              {
                "payloadId":"pa3q-S938USQSCCZF9-sukisu",
                "displayName":"Galaxy S25 Ultra | SukiSU-Ultra (test)",
                "models":["SM-S938U1"],
                "kernelVersions":["6.6.98"],
                "flavor":"sukisu-ultra",
                "exploit":{"url":"https://example.invalid/exploit.so","size":4096},
                "kernelsu":{"url":"https://example.invalid/ksud-sukisu","size":8192}
              },
              {
                "payloadId":"pa3q-S938USQSCCZF9-ksu330",
                "displayName":"Galaxy S25 Ultra | KernelSU 3.3.0 (test)",
                "models":["SM-S938U1"],
                "kernelVersions":["6.6.98"],
                "exploit":{"url":"https://example.invalid/exploit.so","size":4096},
                "kernelsu":{"url":"https://example.invalid/ksud","size":8192}
              }
            ]}
            """.trimIndent().toByteArray(),
        )

        assertEquals(1, parsed.targets.size)
        val kept = parsed.targets.single()
        assertEquals("pa3q-S938USQSCCZF9-ksu330", kept.profileId)
        assertEquals(KernelSuFlavor.Default, kept.flavor)
        // Dropped silently would be the one outcome worse than either: the payload is in the file and
        // nowhere in the app. The caller logs what this list names.
        assertEquals(
            listOf(UnreadablePayload(payloadId = "pa3q-S938USQSCCZF9-sukisu", declaredFlavor = "sukisu-ultra")),
            parsed.ignored,
        )
    }

    @Test
    fun keepsTheFlavourAnEntryItKnowsDeclares() {
        // An entry that declares none is the default rather than dropped: every feed written before
        // flavours existed reads as KernelSU.
        val parsed = SupportManifest.parse(manifest)

        assertEquals(2, parsed.targets.size)
        assertEquals(KernelSuFlavor.Default, parsed.targets[0].flavor)
        assertEquals(KernelSuFlavor.Default, parsed.targets[1].flavor)
        assertTrue(parsed.ignored.isEmpty())

        val declared = SupportManifest.parse(
            """
            {"schemaVersion":3,"payloads":[{
              "payloadId":"pa3q-S938USQSCCZF9-ksun340",
              "displayName":"Galaxy S25 Ultra | KernelSU-Next 3.4.0 (test)",
              "models":["SM-S938U1"],
              "kernelVersions":["6.6.98"],
              "flavor":"kernelsu-next",
              "exploit":{"url":"https://example.invalid/exploit.so","size":4096},
              "kernelsu":{"url":"https://example.invalid/ksud-next","size":8192}
            }]}
            """.trimIndent().toByteArray(),
        )
        assertEquals(KernelSuFlavor.KernelSuNext, declared.targets.single().flavor)

        // The third project, and the one whose entry is easiest to get wrong: its id shares a
        // substring with KernelSU's, so a parser that matched loosely would file it under the wrong
        // kernel and a run would stage one project's daemon against the other's module.
        val reSuki = SupportManifest.parse(
            """
            {"schemaVersion":3,"payloads":[{
              "payloadId":"pa3q-S938USQSCCZF9-rsksu420",
              "displayName":"Galaxy S25 Ultra | ReSukiSU 4.2.0-rc2 (test)",
              "models":["SM-S938U1"],
              "kernelVersions":["6.6.98"],
              "flavor":"resukisu",
              "exploit":{"url":"https://example.invalid/exploit.so","size":4096},
              "kernelsu":{"url":"https://example.invalid/ksud-rsksu","size":8192}
            }]}
            """.trimIndent().toByteArray(),
        )
        assertEquals(KernelSuFlavor.ReSukiSU, reSuki.targets.single().flavor)
    }

    @Test
    fun aPreReleaseTagKeepsItsSuffix() {
        // ReSukiSU marks every release it publishes as a pre-release, so `v4.2.0-rc2` is the release's
        // own name and there is no `v4.2.0` behind it. Reducing it to its dotted number - what a leading
        // `v` used to be stripped together with - would leave the app offering a tag that does not exist
        // and looking up a release that was never published.
        val parsed = SupportManifest.parse(
            """
            {"schemaVersion":3,"payloads":[{
              "payloadId":"pa3q-S938USQSCCZF9-rsksu420",
              "displayName":"Galaxy S25 Ultra | ReSukiSU 4.2.0-rc2 (test)",
              "models":["SM-S938U1"],
              "kernelVersions":["6.6.98"],
              "flavor":"resukisu",
              "exploit":{"url":"https://example.invalid/exploit.so","size":4096},
              "kernelsu":{
                "url":"https://example.invalid/ksud-rsksu","size":8192,"version":"v4.2.0-rc2"
              }
            }]}
            """.trimIndent().toByteArray(),
        )

        // The tag's `v` is still taken off, because a tag and the version it names are one value here.
        assertEquals("4.2.0-rc2", parsed.targets.single().kernelSuVersion)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesASchemaItCannotRead() {
        // A feed that moves to a schema this build does not know must fail loudly at parse time
        // rather than have its fields silently ignored.
        SupportManifest.parse("""{"schemaVersion":4,"payloads":[]}""".toByteArray())
    }
}
