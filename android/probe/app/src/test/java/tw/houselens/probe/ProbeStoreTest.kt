package tw.houselens.probe

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProbeStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val runId = "11111111-1111-4111-8111-111111111111"
    private val processId = "22222222-2222-4222-8222-222222222222"
    private val otherProcess = "33333333-3333-4333-8333-333333333333"
    private val record = RunRecord(runId, processId,
        ProbeQuery(Site.CtHouse, "新北市", "中和區", BigDecimal("2000.00")),
        RunMode.FOREGROUND, RunState.RUNNING,
        DeviceSnapshot("S25+", "17", 37, "test", "2026-11-01", null, null, "WIFI", 80, false, false, null),
        EvidenceSource.LIVE)
    private val listing = ProbeListing("新北市", "中和區", "SECRET_ADDRESS", BigDecimal("28.50"), null, null,
        false, BigDecimal("798.00"), null, Site.CtHouse, "A123", "電話0912345678_SECRET_TITLE",
        "https://example.invalid/?token=SECRET_TOKEN&cookie=SECRET_COOKIE", null,
        "https://example.invalid/SECRET_IMAGE", setOf("hasParking"))
    private val page = PageResult(PageKind.RESULTS, listOf("A123"), listOf(listing), emptyMap(), NextPage.END, "last_page")
    private fun store(process: String = processId) = ProbeStore(temp.root, process,
        utcNow = { "2026-11-03T00:00:00Z" }, elapsedNow = { 1000L })
    private fun event(kind: ProbeEventKind, time: Long = 100) = ProbeEvent(runId, processId, Site.CtHouse,
        if (kind == ProbeEventKind.PAGE_PARSED) 0 else null, kind, "2026-11-03T00:00:00Z", time,
        if (kind == ProbeEventKind.PAGE_PARSED) 1 else null,
        if (kind == ProbeEventKind.PAGE_PARSED) 1 else null,
        if (kind == ProbeEventKind.PAGE_PARSED) 1 else null, null, null, EvidenceSource.LIVE)

    @Test fun completePagesRebuildProgressAndKeepDecimalScale() {
        val store = store()
        store.create(record)
        store.savePage(runId, 0, page)
        store.savePage(runId, 0, page)
        val loaded = store().load(runId)
        assertEquals(setOf(0), loaded.savedPageIndices)
        assertEquals(page, store().loadPage(runId, 0))
        assertEquals(BigDecimal("798.00"), store().loadPage(runId, 0).listings.single().totalPrice)
        assertThrows(IllegalStateException::class.java) { store.savePage(runId, 0, page.copy(evidence = "zero_total")) }
    }

    @Test fun temporaryPagesDoNotAdvanceButCorruptFinalPagesFail() {
        val store = store()
        store.create(record)
        File(temp.root, "$runId/page-0.json.tmp").writeText("{half")
        assertTrue(store.load(runId).savedPageIndices.isEmpty())
        store.savePage(runId, 0, page)
        File(temp.root, "$runId/page-0.json").writeText("{broken")
        assertThrows(IOException::class.java) { store.load(runId) }
    }

    @Test fun oldActiveRunsAreInterruptedWithoutRewritingCompletedHistory() {
        val store = store()
        store.create(record)
        assertEquals(RunState.INTERRUPTED, store(otherProcess).load(runId).state)
        assertEquals(RunState.RUNNING, store.load(runId).state)
        store.finish(runId, RunState.COMPLETED)
        assertEquals(RunState.COMPLETED, store(otherProcess).load(runId).state)
        assertEquals(ProbeEventKind.RUN_COMPLETED, store.load(runId).events.last().kind)
        assertThrows(IllegalStateException::class.java) { store.savePage(runId, 0, page) }
        assertThrows(IllegalStateException::class.java) { store.finish(runId, RunState.CANCELLED) }
    }

    @Test fun commitFailureNeverProducesCheckpointOrCompletion() {
        var failPage = true
        val store = ProbeStore(temp.root, processId, utcNow = { "2026-11-03T00:00:00Z" }, elapsedNow = { 1000L },
            commit = { from, to ->
                if (failPage && to.name.startsWith("page-")) throw IOException("disk full")
                Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
            })
        store.create(record)
        assertThrows(IOException::class.java) { store.savePage(runId, 0, page) }
        assertTrue(store.load(runId).savedPageIndices.isEmpty())
        failPage = false
        assertThrows(IllegalStateException::class.java) { store.finish(runId, RunState.COMPLETED) }
        store.finish(runId, RunState.FAILED)
        assertEquals(RunState.FAILED, store.load(runId).state)
    }

    @Test fun tornEventTailIsVisibleAndCannotPassEvidenceChecks() {
        val store = store()
        store.create(record)
        store.append(event(ProbeEventKind.APP_FOREGROUND))
        File(temp.root, "$runId/events.jsonl").appendText("{partial")
        val loaded = store.load(runId)
        assertTrue(loaded.eventTailIncomplete)
        assertEquals(ProbeEventKind.EVIDENCE_INCOMPLETE, loaded.events.last().kind)
        assertThrows(IOException::class.java) { store.append(event(ProbeEventKind.HEARTBEAT, 200)) }
    }

    @Test fun corruptCompleteEventLineIsAnError() {
        store().create(record)
        File(temp.root, "$runId/events.jsonl").writeText("{broken}\n")
        assertThrows(IOException::class.java) { store().load(runId) }
    }

    @Test fun wrongTypedNullableFieldsAreCorruptionNotUnknownValues() {
        val store = store()
        store.create(record)
        store.savePage(runId, 0, page)
        val file = File(temp.root, "$runId/page-0.json")
        file.writeText(file.readText().replace("\"ageYears\":null", "\"ageYears\":\"broken\""))
        assertThrows(IOException::class.java) { store.load(runId) }
    }

    @Test fun tornLogBlocksFurtherPageWritesAndPreservesExistingEvidence() {
        val store = store()
        store.create(record)
        val file = File(temp.root, "$runId/events.jsonl")
        file.writeText("{partial")
        assertThrows(IOException::class.java) { store.savePage(runId, 0, page) }
        assertTrue(store.load(runId).savedPageIndices.isEmpty())
        assertEquals("{partial", file.readText())
    }

    @Test fun storedPageEventsComeAfterCommitAndDoNotDoubleCount() {
        val store = store()
        store.create(record)
        store.append(event(ProbeEventKind.PAGE_PARSED))
        store.savePage(runId, 0, page)
        store.savePage(runId, 0, page)
        val saved = store.load(runId).events.filter { it.kind == ProbeEventKind.PAGE_SAVED }
        assertEquals(1, saved.size)
        assertEquals(1, saved.single().newRawCount)
        assertEquals(1000L, saved.single().elapsedMs)
        assertThrows(IllegalArgumentException::class.java) { store.append(saved.single()) }
    }

    @Test fun pageCommittedBeforeEventFailureRecoversDataWithoutInventingSavedTime() {
        val store = ProbeStore(temp.root, processId, commit = { from, to ->
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
            if (to.name.startsWith("page-")) throw IOException("process stopped after rename")
        })
        store.create(record)
        store.append(event(ProbeEventKind.PAGE_PARSED))
        assertThrows(IOException::class.java) { store.savePage(runId, 0, page) }
        val recovered = store(otherProcess).load(runId)
        assertEquals(setOf(0), recovered.savedPageIndices)
        assertFalse(recovered.events.any { it.kind == ProbeEventKind.PAGE_SAVED })
        assertNotEquals(BackgroundVerdict.PASSED, backgroundVerdict(recovered.events, RunMode.BACKGROUND_10M))
    }

    @Test fun failedTerminalWriteCannotExportSuccessfulBackgroundEvidence() {
        var time = 0L
        val store = ProbeStore(temp.root, processId, utcNow = { "2026-11-03T00:00:00Z" }, elapsedNow = { time },
            commit = { from, to ->
                if (to.name == "terminal.json") throw IOException("disk full")
                Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
            })
        store.create(record.copy(mode = RunMode.BACKGROUND_10M))
        store.append(event(ProbeEventKind.APP_FOREGROUND, 0))
        store.append(event(ProbeEventKind.SCREEN_ON, 0))
        store.append(event(ProbeEventKind.APP_BACKGROUND, 1000))
        for ((index, started) in listOf(61_000L, 301_000L, 541_000L).withIndex()) {
            val parsed = event(ProbeEventKind.PAGE_PARSED, started).copy(pageIndex = index, navigationId = index.toLong())
            store.append(parsed.copy(kind = ProbeEventKind.REQUEST_STARTED))
            store.append(parsed.copy(kind = ProbeEventKind.RESPONSE_RECEIVED, elapsedMs = started + 100, networkObserved = true))
            store.append(parsed.copy(elapsedMs = started + 200))
            time = started + 300
            val key = "A$index"
            store.savePage(runId, index, page.copy(rawKeys = listOf(key), listings = listOf(listing.copy(sourceListingKey = key))))
        }
        time = 601_000
        store.append(event(ProbeEventKind.HEARTBEAT, time))
        assertEquals(BackgroundVerdict.PASSED, backgroundVerdict(store.load(runId).events, RunMode.BACKGROUND_10M))
        assertThrows(IOException::class.java) { store.finish(runId, RunState.COMPLETED) }
        assertThrows(IOException::class.java) { store.export(runId, ByteArrayOutputStream()) }
    }

    @Test fun rejectsPathTraversalForeignWritersAndUntrustedCodes() {
        val store = store()
        assertThrows(IllegalArgumentException::class.java) { store.create(record.copy(runId = "../escape")) }
        store.create(record)
        assertThrows(IllegalStateException::class.java) { store(otherProcess).savePage(runId, 0, page) }
        assertThrows(IllegalArgumentException::class.java) { store.savePage(runId, 0, page.copy(evidence = "SECRET_COOKIE")) }
        assertThrows(IllegalArgumentException::class.java) { store.savePage(runId, 0, page.copy(excluded = mapOf("A123" to "SECRET_TOKEN"))) }
        assertThrows(IllegalArgumentException::class.java) { store.append(event(ProbeEventKind.HEARTBEAT).copy(utc = "SECRET_TOKEN")) }
    }

    @Test fun exportContainsOnlyTheThreeWhitelistedArtifacts() {
        val store = store()
        store.create(record)
        store.savePage(runId, 0, page)
        store.finish(runId, RunState.COMPLETED)
        val output = ByteArrayOutputStream()
        store.export(runId, output)
        val files = mutableMapOf<String, String>()
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                files[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertEquals(setOf("result.md", "events.jsonl", "sample.json"), files.keys)
        val all = files.values.joinToString()
        for (secret in listOf("SECRET_", "0912345678", "example.invalid", "address", "title", "imageUrl")) assertFalse(secret, all.contains(secret))
        assertTrue(all.contains("798.00"))
        assertTrue(all.contains("A123"))
        assertTrue(all.contains("NOT_TESTED"))
        assertTrue(all.contains("hasParking"))
        assertTrue(files.getValue("result.md").contains("S25+"))
        assertTrue(files.getValue("result.md").contains("maxTotalPrice"))
    }
}
