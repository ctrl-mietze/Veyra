package ctrl.mietze.veyraroot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VivoPayloadCatalogTest {
    private val catalog = """
        {
          "schemaVersion": 5,
          "updated": "2026-10-03",
          "exploits": {},
          "builds": {
            "gready": {
              "match": ["6.6.89-android15-8-gabcdef12345"],
              "exploit": "ghostlock-cve-2026-43499",
              "status": "ready",
              "file": {
                "name": "preload.so",
                "url": "https://example.invalid/gready.so",
                "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "size": 151784
              }
            },
            "gexperimental": {
              "match": ["6.6.89-android15-8-gabcdef12345"],
              "exploit": "ghostlock-cve-2026-43499",
              "status": "experimental",
              "file": {
                "name": "preload.so",
                "url": "https://example.invalid/gexperimental.so",
                "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                "size": 151700
              }
            }
          },
          "devices": [
            {
              "id": "iqoo13-china",
              "marketName": "iQOO 13",
              "code": "V2408A",
              "models": ["PD2408", "PD2408A", "V2408A"],
              "names": ["iQOO 13", "V2408A"],
              "kernels": [
                {"build": "gready"},
                {"build": "gexperimental"}
              ]
            }
          ]
        }
    """.trimIndent()

    private fun snapshot(
        model: String,
        device: String,
        kernel: String,
    ) = DeviceSnapshot(
        manufacturer = "vivo",
        model = model,
        device = device,
        kernelRelease = kernel,
        kernelVersionInfo = "#1 SMP PREEMPT",
        machine = "aarch64",
        buildId = "TEST.001",
        fingerprint = "vivo/$device/$device:16/TEST",
        androidRelease = "16",
        sdk = 36,
        abi = "arm64-v8a",
        pageSize = 4096L,
    )

    @Test
    fun exactDeviceAndKernelIsTrusted() {
        val report = VivoPayloadCatalog.parse(
            catalog,
            snapshot(
                model = "V2408A",
                device = "PD2408",
                kernel = "6.6.89-android15-8-gabcdef12345-ab12345678-4k",
            ),
        )

        assertEquals(listOf("iqoo13-china"), report.deviceMatches)
        assertEquals(2, report.candidates.size)
        assertTrue(report.candidates.all { it.trust == VivoCatalogTrust.ExactDeviceKernel })

        val ready = report.candidates.first { it.buildId == "gready" }
        assertTrue(ready.ready)
        assertEquals(151784L, ready.size)
        assertEquals(64, ready.sha256?.length)

        val experimental = report.candidates.first { it.buildId == "gexperimental" }
        assertFalse(experimental.ready)
        assertEquals("experimental", experimental.status)
    }

    @Test
    fun sameKernelOnUnknownDeviceStaysResearchOnly() {
        val report = VivoPayloadCatalog.parse(
            catalog,
            snapshot(
                model = "V9999A",
                device = "PD9999",
                kernel = "6.6.89-android15-8-gabcdef12345-ab12345678-4k",
            ),
        )

        assertTrue(report.deviceMatches.isEmpty())
        assertEquals(2, report.candidates.size)
        assertTrue(report.candidates.all { it.trust == VivoCatalogTrust.KernelOnlyResearch })
    }

    @Test
    fun x60KonaDoesNotBorrowModernVivoPayloads() {
        val report = VivoPayloadCatalog.parse(
            catalog,
            snapshot(
                model = "V2046A",
                device = "PD2046F",
                kernel = "4.19.152-perf+",
            ),
        )

        assertTrue(report.deviceMatches.isEmpty())
        assertTrue(report.candidates.isEmpty())
    }
}
