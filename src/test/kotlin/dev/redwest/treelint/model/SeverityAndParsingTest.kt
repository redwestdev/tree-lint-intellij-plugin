package dev.redwest.treelint.model

import dev.redwest.treelint.runner.ReportParser
import dev.redwest.treelint.runner.TreeLintException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SeverityAndParsingTest {

    @Test
    fun `severity mapping covers known and unknown types`() {
        assertEquals(TreeLintSeverity.ERROR, severityOf("error"))
        assertEquals(TreeLintSeverity.ERROR, severityOf(" Error "))
        assertEquals(TreeLintSeverity.WARNING, severityOf("WARNING"))
        assertEquals(TreeLintSeverity.UNKNOWN, severityOf("info"))
        assertEquals(TreeLintSeverity.UNKNOWN, severityOf(""))
    }

    @Test
    fun `max severity prefers error then warning`() {
        assertEquals(TreeLintSeverity.ERROR, maxSeverity(TreeLintSeverity.WARNING, TreeLintSeverity.ERROR))
        assertEquals(TreeLintSeverity.WARNING, maxSeverity(TreeLintSeverity.UNKNOWN, TreeLintSeverity.WARNING))
        assertEquals(TreeLintSeverity.UNKNOWN, maxSeverity(TreeLintSeverity.UNKNOWN, TreeLintSeverity.UNKNOWN))
    }

    @Test
    fun `parses a path-grouped report`() {
        val json = """
            {
              "generatedAt": "2026-01-01T00:00:00.000Z",
              "root": ["src"],
              "errors": 1,
              "warnings": 2,
              "grouped": {
                "/project/src/a": {"error": ["bad"], "warning": []},
                "/project/src/b": {"error": [], "warning": ["meh", "still meh"]}
              }
            }
        """.trimIndent()

        val report = ReportParser.parse(json)

        assertEquals(listOf("src"), report.roots)
        assertEquals(1, report.errors)
        assertEquals(2, report.warnings)
        assertEquals(
            listOf(
                TreeLintViolation(type = "error", path = "/project/src/a", message = "bad"),
                TreeLintViolation(type = "warning", path = "/project/src/b", message = "meh"),
                TreeLintViolation(type = "warning", path = "/project/src/b", message = "still meh"),
            ),
            report.violations,
        )
        assertEquals(TreeLintSeverity.ERROR, severityOf(report.violations[0].type))
    }

    @Test
    fun `report without violations parses to an empty list`() {
        val report = ReportParser.parse(
            """{"generatedAt":"x","root":["."],"errors":0,"warnings":0,"grouped":{}}""",
        )

        assertTrue(report.violations.isEmpty())
        assertEquals(0, report.errors)
        assertEquals(0, report.warnings)
    }

    @Test
    fun `missing counters and message lists fall back to defaults`() {
        val report = ReportParser.parse("""{"grouped":{"/p/a":{"warning":["w"]},"/p/b":null}}""")

        assertEquals(0, report.errors)
        assertEquals(1, report.warnings)
        assertEquals(1, report.violations.size)
        assertEquals("w", report.violations[0].message)
    }

    @Test
    fun `invalid json throws with context`() {
        val error = assertFailsWith<TreeLintException> {
            ReportParser.parse("{not a json")
        }
        assertTrue(error.message!!.contains("Cannot parse tree-lint report"))
    }

    @Test
    fun `non-report json is rejected`() {
        assertFailsWith<TreeLintException> {
            ReportParser.parse("""{"error":"[Config Error]: boom"}""")
        }
    }

    @Test
    fun `cli error payload is unwrapped`() {
        assertEquals(
            "[Config Error]: Configuration file not found.",
            ReportParser.parseCliError("""{"error":"[Config Error]: Configuration file not found."}"""),
        )
        assertNull(ReportParser.parseCliError("error: unknown option '--report'"))
        assertNull(ReportParser.parseCliError(""))
    }
}
