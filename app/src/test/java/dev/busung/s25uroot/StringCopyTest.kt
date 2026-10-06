package ctrl.mietze.veyraroot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The copy in the default strings file, checked for the mistakes that survive a review.
 *
 * A wording pass is read for what it says, and the things that slip through are the ones the eye fills in:
 * a comma with no space after it, two spaces where one belongs, a value that starts with one. All three were
 * in this file at once - `writes,flashing`, `by %2$s`, and an old ` %1$s; %2$s is installed` that opened with
 * a space - and none of them is visible in a diff that shows the line being changed.
 *
 * Only the default file, and only its punctuation: a translation keeps its own conventions, and nothing here
 * can judge whether a sentence says the right thing. What it cannot catch is the reason to read the diff
 * anyway - "could not read" where the passive needs a "be" is grammar, and this is not.
 */
class StringCopyTest {

    private val strings = source("src/main/res/values/strings.xml")

    /**
     * Every `<string>` element's value, by name, from the default file only.
     *
     * `DOTALL` and the extra `[^>]*` are not decoration: one value in this file is wrapped over two lines,
     * and eleven elements carry `translatable="false"` after the name. Either one missed is a value that is
     * never checked, which is worse than no check at all - the file would look clean because it was not read.
     */
    private val values: List<Pair<String, String>> =
        Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .findAll(strings)
            .map { match -> match.groupValues[1] to match.groupValues[2] }
            .toList()

    @Test
    fun `the file is read as whole elements rather than lines`() {
        // The guard below is only as good as this parse, and the mistakes it is about hide exactly at the
        // seam a partial read creates: a value split over two lines, or an element with an attribute after
        // its name, would be checked as a fragment or not at all - and the file would look clean.
        //
        // Compared against the file rather than against a number, deliberately. A count written down here
        // would fail every time a string is added, and the obvious repair is to bump the number, which
        // teaches everyone that this assertion is noise.
        assertEquals(
            "the elements parsed are not the elements in the file, so part of this file's copy is unchecked",
            Regex("<string name=").findAll(strings).count(),
            values.size,
        )
    }

    @Test
    fun `no value puts a word straight after a comma`() {
        val broken = values.filter { (_, value) -> Regex("[a-z],[^\\s<]").containsMatchIn(value) }

        assertFalse(
            "a comma is followed by a word with no space: ${broken.map { it.first }}",
            broken.isNotEmpty(),
        )
    }

    @Test
    fun `no value has a run of spaces in the middle of a sentence`() {
        val broken = values.filter { (_, value) -> Regex("[a-z]  +[a-z]").containsMatchIn(value) }

        assertFalse(
            "double spaces where one belongs: ${broken.map { it.first }}",
            broken.isNotEmpty(),
        )
    }

    @Test
    fun `the installed manager's description cannot restate the version drawn beside it`() {
        // The row this one is about read "Manager 3.3.0; KernelSU is installed" under a value of "v3.3.0" -
        // the same number twice and the flavour's own name twice. A description takes an argument for one
        // reason here, so a `%` in this value is the version creeping back into the sentence.
        val installed = values.firstOrNull { (name, _) -> name == "settings_manager_summary_installed" }
            ?: throw AssertionError(
                "settings_manager_summary_installed is gone, so this guard would check nothing",
            )

        assertFalse(
            "the manager row's description is formatted again: ${installed.second}",
            "%" in installed.second,
        )
    }

    @Test
    fun `no value starts with a space`() {
        // This is the shape a value takes when it is meant to be glued to another: it reads correctly on the
        // line it is written on and wrong on the row it is drawn on, where the row supplies its own gap.
        val broken = values.filter { (_, value) -> value.firstOrNull()?.isWhitespace() == true }

        assertFalse(
            "values opening with a space: ${broken.map { it.first }}",
            broken.isNotEmpty(),
        )
    }

    private fun source(relativeToApp: String): String = listOf(
        File(relativeToApp),
        File("app/$relativeToApp"),
    ).firstOrNull(File::isFile)?.readText()
        ?: throw AssertionError("$relativeToApp was not found from ${File(".").absolutePath}")
}
