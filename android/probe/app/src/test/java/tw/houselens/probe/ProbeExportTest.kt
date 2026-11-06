package tw.houselens.probe

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProbeExportTest {
    @get:Rule val temp = TemporaryFolder()
    private val process = UUID.randomUUID().toString()
    private val record = RunRecord(UUID.randomUUID().toString(), process, testQuery(Site.CtHouse),
        RunMode.FOREGROUND, RunState.RUNNING,
        DeviceSnapshot(null, null, null, null, null, null, null, null, null, null, null, null),
        EvidenceSource.LOCAL_SELF_CHECK, appVersion = "0.1.7")
    private fun store() = ProbeStore(temp.root, process, elapsedNow = { 1 })

    @Test fun exportUsesRunVersionAndContainsUntestedStatusWithoutPrivateFields() {
        val store = store()
        store.create(record)
        val listing = ProbeListing("新北市", "中和區", "SECRET_ADDRESS", "28.50".toBigDecimal(), null,
            null, false, "798.00".toBigDecimal(), null, Site.CtHouse, "A1", "SECRET_PHONE",
            "https://example.invalid/SECRET_TOKEN", null, "https://example.invalid/SECRET_COOKIE")
        store.savePage(record.runId, 0, PageResult(PageKind.RESULTS, listOf("A1"), listOf(listing), emptyMap(), NextPage.END, "last_page"))
        store.finish(record.runId, RunState.COMPLETED)
        val bytes = ByteArrayOutputStream().also { store.export(record.runId, it) }.toByteArray()
        val entries = mutableMapOf<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8) }
        }
        assertEquals(setOf("result.md", "events.jsonl", "sample.json"), entries.keys)
        val report = entries.getValue("result.md")
        assertEquals("# HouseLens probe 0.1.7", report.lineSequence().first())
        assertTrue(report.contains("exportedWith: ${BuildConfig.VERSION_NAME}"))
        assertTrue(report.contains("FOREGROUND"))
        assertTrue(report.contains("NOT_TESTED"))
        assertTrue(report.contains("LOCAL_SELF_CHECK"))
        assertFalse(entries.values.joinToString().contains("SECRET_"))
    }

    @Test fun historyListsOnlyRunDirectoriesAndOldVersionStaysUnknown() {
        val store = store()
        store.create(record)
        File(temp.root, "unrelated").mkdir()
        assertEquals(listOf(record.runId), store.runIds())
        val json = recordJson(record).toMutableMap().apply { remove("appVersion") }
        assertNull(recordFromJson(kotlinx.serialization.json.JsonObject(json)).appVersion)
    }
}
