package ctrl.mietze.veyraroot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuilderWorkbenchTest {
    @Test
    fun payloadManagementContainsOnlyTheWorkbenchEntryForBuilders() {
        val main = source("MainActivity.kt")
        val start = main.indexOf("settingsSectionHeading(\n            SettingsSection.Payloads")
        val end = main.indexOf(
            "settingsSectionHeading(\n            SettingsSection.Run",
            start + 1,
        )
        assertTrue("Payload Management section was not found", start >= 0 && end > start)
        val payloadBlock = main.substring(start, end)

        assertTrue(payloadBlock.contains("title = \"Builder Workingbench\""))
        assertTrue(payloadBlock.contains("showBuilderWorkbenchPage = true"))
        assertFalse(payloadBlock.contains("showPayloadBuilderPage = true"))
        assertFalse(payloadBlock.contains("showMagicBuilderPage = true"))
        assertFalse(payloadBlock.contains("showBuilderSettingsPage = true"))
    }

    @Test
    fun eachBuilderKeepsItsOwnSettingsRoute() {
        val main = source("MainActivity.kt")
        assertTrue(main.contains("VeyraGuidedBuilderSettingsPage("))
        assertTrue(main.contains("VeyraBuilderSettingsPage("))
        assertTrue(main.contains("VeyraMagicBuilderSettingsPage("))
        assertTrue(main.contains("showVeyraBuilderSettingsPage = true"))
        assertTrue(main.contains("showBuilderSettingsPage = true"))
        assertTrue(main.contains("showMagicBuilderSettingsPage = true"))
    }

    @Test
    fun generatedTermuxScriptsCarryAutonomousPreparation() {
        val scripts = source("VeyraBuilderTermux.kt")
        assertTrue(scripts.contains("must run inside Termux"))
        assertTrue(scripts.contains("pkg install -y"))
        assertTrue(scripts.contains("maybe_update_pkg"))
        assertTrue(scripts.contains("dpkg --compare-versions"))
        assertTrue(scripts.contains("apt-cache policy"))
        assertTrue(scripts.contains("termux-setup-storage"))
        assertTrue(scripts.contains("Shizuku/rish"))
        assertTrue(scripts.contains("android-tools"))
        assertTrue(scripts.contains("storage/downloads/VeyraRoot/TermuxReports"))
        assertTrue(scripts.contains("veyra-reports"))
        assertTrue(scripts.contains("Used output path:"))
        assertTrue(scripts.contains("veyra.termux.report/v1"))
    }

    @Test
    fun reportParserCountsIndependentGroupsAndConflicts() {
        val raw = """
            {
              "format":"veyra.termux.report/v1",
              "created_utc":"2026-10-08T03:00:00Z",
              "observations":[
                {
                  "field":"device.uname_r",
                  "value":"6.6.98-android15-8-test",
                  "source_id":"termux.uname",
                  "independent_group":"live_kernel",
                  "outcome":"SUCCESS"
                },
                {
                  "field":"device.uname_r",
                  "value":"6.6.98-android15-8-test",
                  "source_id":"proc.osrelease",
                  "independent_group":"proc_kernel",
                  "outcome":"SUCCESS"
                },
                {
                  "field":"device.board_platform",
                  "value":"kalama",
                  "source_id":"getprop.platform",
                  "independent_group":"android_properties",
                  "outcome":"SUCCESS"
                },
                {
                  "field":"device.board_platform",
                  "value":"pineapple",
                  "source_id":"second.source",
                  "independent_group":"second_platform",
                  "outcome":"SUCCESS"
                },
                {
                  "field":"device.sdk",
                  "value":null,
                  "source_id":"missing",
                  "independent_group":"android_properties",
                  "outcome":"NO_DATA"
                }
              ]
            }
        """.trimIndent()

        val report = VeyraBuilderReportParser.parse("report.json", raw)
        assertEquals("6.6.98-android15-8-test", report.kernel)
        assertEquals(4, report.successes)
        assertEquals(4, report.independentGroups)
        assertEquals(1, report.conflicts)
        assertEquals(5, report.observations)
    }

    @Test(expected = IllegalArgumentException::class)
    fun reportParserRejectsOtherFormats() {
        VeyraBuilderReportParser.parse(
            "wrong.json",
            "{\"format\":\"other\",\"observations\":[]}",
        )
    }

    private fun source(name: String): String {
        val roots = listOf(
            File("app/src/main/java/dev/busung/s25uroot"),
            File("src/main/java/dev/busung/s25uroot"),
        )
        val file = roots.map { File(it, name) }.firstOrNull(File::isFile)
            ?: throw AssertionError("source file not found: $name")
        return file.readText()
    }
}
