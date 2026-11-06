package tw.houselens.probe

import android.os.SystemClock
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.*

private val evidenceCodes = setOf("next_link", "last_page", "zero_total", "total_pages", "total_count",
    "no_next_link", "more_pages", "unknown", "empty_marker", "challenge", "invalid_structure", "parse_error", "html_fallback")
private val exclusionCodes = setOf("missing_id", "wrong_source", "unknown_price", "invalid_price", "wrong_city",
    "wrong_district", "over_price", "over_age", "no_parking", "non_residential", "unsupported_type", "duplicate_key")
private val unknownFieldNames = setOf("city", "district", "address", "areaPing", "floor", "ageYears", "hasParking",
    "totalPrice", "unitPrice", "sourceListingKey", "title", "url", "postedDate", "imageUrl",
    "areaBasis", "typeCodes", "useCode", "rooms", "parkingCodes")
private val listingKey = Regex("[A-Za-z0-9_-]{1,80}")
private val terminalKinds = mapOf(
    RunState.COMPLETED to ProbeEventKind.RUN_COMPLETED, RunState.CANCELLED to ProbeEventKind.RUN_CANCELLED,
    RunState.INTERRUPTED to ProbeEventKind.RUN_INTERRUPTED, RunState.FAILED to ProbeEventKind.RUN_FAILED,
    RunState.TRUNCATED to ProbeEventKind.RUN_TRUNCATED, RunState.INCONCLUSIVE to ProbeEventKind.RUN_INCONCLUSIVE,
)

class ProbeStore(
    root: File,
    private val processId: String,
    private val utcNow: () -> String = { Instant.now().toString() },
    private val elapsedNow: () -> Long = { SystemClock.elapsedRealtime() },
    private val commit: (File, File) -> Unit = { from, to ->
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE)
    },
) {
    private val root = root.canonicalFile
    private val failedWrites = mutableSetOf<String>()

    @Synchronized fun create(record: RunRecord) {
        require(record.processId == processId && record.state == RunState.RUNNING)
        require(record.savedPageIndices.isEmpty() && record.events.isEmpty() && !record.eventTailIncomplete)
        uuid(processId)
        validateQuery(record.query)
        val directory = directory(record.runId)
        check(!directory.exists()) { "run_already_exists" }
        writing(record.runId) {
            Files.createDirectories(root.toPath())
            Files.createDirectory(directory.toPath())
            atomic(File(directory, "record.json"), recordJson(record))
        }
    }

    @Synchronized fun savePage(runId: String, index: Int, result: PageResult) {
        val record = writable(runId)
        require(index in 0 until 100)
        validatePage(result, record.query.site)
        if (index in record.savedPageIndices) {
            check(loadPage(runId, index) == result) { "page_already_committed" }
            return
        }
        require(index == record.savedPageIndices.size) { "page_gap" }
        val parsed = record.events.lastOrNull { it.kind == ProbeEventKind.PAGE_PARSED && it.pageIndex == index }
        if (parsed != null) require(parsed.rawCount == result.rawKeys.size && parsed.acceptedCount == result.listings.size)
        val seen = record.savedPageIndices.flatMap { loadPage(runId, it).rawKeys }.toSet()
        writing(runId) {
            atomic(File(directory(runId), "page-$index.json"), pageJson(result))
            if (parsed != null) {
                val saved = parsed.copy(kind = ProbeEventKind.PAGE_SAVED, utc = utcNow(), elapsedMs = elapsedNow(),
                    newRawCount = (result.rawKeys.toSet() - seen).size)
                validateEvent(saved, record)
                appendLine(saved)
            }
        }
    }

    @Synchronized fun append(event: ProbeEvent) {
        require(event.kind !in terminalKinds.values && event.kind != ProbeEventKind.PAGE_SAVED &&
            event.kind != ProbeEventKind.EVIDENCE_INCOMPLETE) { "store_owned_event" }
        val record = writable(event.runId)
        if (record.eventTailIncomplete) throw IOException("event_tail_incomplete")
        validateEvent(event, record)
        writing(event.runId) { appendLine(event) }
    }

    @Synchronized fun finish(runId: String, state: RunState, reason: ProbeReason? = null, batteryPercent: Int? = null) {
        require(state in terminalKinds) { "non_terminal_state" }
        val record = load(runId)
        check(record.processId == processId) { "foreign_process" }
        if (record.state in terminalKinds) {
            check(record.state == state) { "run_already_finished" }
            return
        }
        check(runId !in failedWrites || state == RunState.FAILED) { "previous_write_failed" }
        check(!record.eventTailIncomplete || state == RunState.FAILED) { "incomplete_evidence" }
        val event = ProbeEvent(runId, processId, record.query.site, null, terminalKinds.getValue(state),
            utcNow(), elapsedNow(), null, null, null, null, reason, record.evidenceSource, batteryPercent = batteryPercent)
        validateEvent(event, record)
        writing(runId) { atomic(File(directory(runId), "terminal.json"), eventJson(event)) }
    }

    @Synchronized fun loadPage(runId: String, index: Int): PageResult {
        require(index in 0 until 100)
        return read(File(directory(runId), "page-$index.json")) { pageFromJson(it) }
    }

    @Synchronized fun load(runId: String): RunRecord {
        val directory = directory(runId)
        val metadata = read(File(directory, "record.json")) {
            recordFromJson(it).also { r ->
                require(r.runId == runId && r.state == RunState.RUNNING)
                uuid(r.processId)
                validateQuery(r.query)
            }
        }
        val pageFiles = directory.listFiles() ?: throw IOException("run_unreadable")
        val indices = pageFiles.filter { it.name.startsWith("page-") && it.name.endsWith(".json") }
            .map { it.name.removePrefix("page-").removeSuffix(".json").toIntOrNull() ?: throw IOException("invalid_page_name") }
            .sorted()
        if (indices != indices.indices.toList() || indices.size > 100) throw IOException("page_gap")
        val pages = indices.associateWith { index -> read(File(directory, "page-$index.json")) {
            pageFromJson(it).also { page -> validatePage(page, metadata.query.site) }
        } }
        val seen = mutableSetOf<String>()
        val newCounts = pages.mapValues { (_, page) ->
            (page.rawKeys.toSet() - seen).size.also { seen.addAll(page.rawKeys) }
        }
        val eventFile = File(directory, "events.jsonl")
        val log = if (eventFile.exists()) eventFile.readText(Charsets.UTF_8) else ""
        val incomplete = log.isNotEmpty() && !log.endsWith('\n')
        val complete = if (incomplete) log.substringBeforeLast('\n', "") else log.trimEnd('\n')
        val events = mutableListOf<ProbeEvent>()
        if (complete.isNotEmpty()) for (line in complete.split('\n')) {
            try {
                val event = eventFromJson(Json.parseToJsonElement(line).jsonObject)
                validateEvent(event, metadata.copy(events = events))
                require(event.kind !in terminalKinds.values && event.kind != ProbeEventKind.EVIDENCE_INCOMPLETE)
                if (event.kind == ProbeEventKind.PAGE_SAVED) {
                    val page = pages.getValue(event.pageIndex!!)
                    require(event.rawCount == page.rawKeys.size && event.acceptedCount == page.listings.size &&
                        event.newRawCount == newCounts.getValue(event.pageIndex))
                }
                events += event
            } catch (error: Exception) { throw IOException("invalid_event_log", error) }
        }
        val terminal = File(directory, "terminal.json")
        var state = if (events.lastOrNull { it.kind in listOf(ProbeEventKind.WAITING_FOR_USER, ProbeEventKind.RESUMED) }?.kind ==
            ProbeEventKind.WAITING_FOR_USER) RunState.WAITING_FOR_USER else RunState.RUNNING
        if (terminal.exists()) {
            val event = read(terminal) { eventFromJson(it).also { e -> validateEvent(e, metadata.copy(events = events)) } }
            state = terminalKinds.entries.singleOrNull { it.value == event.kind }?.key ?: throw IOException("invalid_terminal")
            events += event
        } else if (metadata.processId != processId) {
            state = RunState.INTERRUPTED
            events += diagnostic(metadata, events, ProbeEventKind.RUN_INTERRUPTED, ProbeReason.PROCESS_LOST)
        }
        if (incomplete) events += diagnostic(metadata, events, ProbeEventKind.EVIDENCE_INCOMPLETE, ProbeReason.EVIDENCE_LOST)
        return metadata.copy(state = state, savedPageIndices = indices.toSet(), events = events, eventTailIncomplete = incomplete)
    }

    @Synchronized fun runIds(): List<String> {
        if (!root.exists()) return emptyList()
        return (root.listFiles() ?: throw IOException("runs_unreadable"))
            .filter { it.isDirectory && runCatching { uuid(it.name) }.isSuccess }
            .sortedByDescending { it.lastModified() }.map { it.name }
    }

    @Synchronized fun export(runId: String, output: OutputStream) {
        val record = load(runId)
        if (runId in failedWrites && record.state != RunState.FAILED) throw IOException("previous_write_failed")
        val pages = record.savedPageIndices.associateWith { loadPage(runId, it) }
        val keys = pages.values.flatMap { it.listings }.map { it.sourceListingKey }.toSet()
        val report = """
            # HouseLens probe ${record.appVersion ?: "UNKNOWN"}
            exportedWith: ${BuildConfig.VERSION_NAME}
            runId: ${record.runId}
            site: ${record.query.site}
            mode: ${record.mode}
            source: ${record.evidenceSource}
            state: ${record.state}
            pages: ${pages.size}
            uniqueListings: ${keys.size}
            background: ${backgroundVerdict(record.events, record.mode)}
            incompleteEventTail: ${record.eventTailIncomplete}
            batteryStartPercent: ${record.deviceSnapshot.batteryPercent}
            batteryEndPercent: ${record.events.lastOrNull { it.kind in terminalKinds.values }?.batteryPercent}
            query: ${queryJson(record.query)}
            device: ${deviceJson(record.deviceSnapshot)}
        """.trimIndent()
        ZipOutputStream(output).use { zip ->
            for ((name, content) in listOf("result.md" to report,
                "events.jsonl" to record.events.joinToString(separator = "\n", postfix = if (record.events.isEmpty()) "" else "\n") { eventJson(it).toString() },
                "sample.json" to sampleJson(pages).toString())) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    private fun writable(runId: String): RunRecord {
        check(runId !in failedWrites) { "previous_write_failed" }
        return load(runId).also {
            check(it.processId == processId && it.state !in terminalKinds) { "run_not_writable" }
            if (it.eventTailIncomplete) throw IOException("event_tail_incomplete")
        }
    }

    private fun directory(runId: String): File {
        uuid(runId)
        return File(root, runId).canonicalFile.also { require(it.parentFile == root) { "invalid_run_path" } }
    }

    private fun uuid(value: String) { require(UUID.fromString(value).toString() == value) { "invalid_uuid" } }

    private fun validatePage(page: PageResult, site: Site) {
        require(page.kind in setOf(PageKind.RESULTS, PageKind.EMPTY)) { "unsuccessful_page" }
        require(page.evidence in evidenceCodes && page.excluded.values.all { it in exclusionCodes }) { "untrusted_code" }
        require(page.rawKeys.all(listingKey::matches) && page.excluded.keys.all { it in page.rawKeys })
        require(page.listings.all { it.sourceSite == site && it.sourceListingKey in page.rawKeys &&
            it.city == "新北市" && it.district in setOf("中和區", "永和區", "新店區") &&
            it.unknownFields.all { field -> field in unknownFieldNames } })
        require(if (page.kind == PageKind.EMPTY) page.rawKeys.isEmpty() && page.listings.isEmpty() && page.excluded.isEmpty()
            else page.rawKeys.isNotEmpty())
    }

    private fun validateEvent(event: ProbeEvent, record: RunRecord) {
        require(event.runId == record.runId && event.processId == record.processId && event.site == record.query.site &&
            event.evidenceSource == record.evidenceSource) { "event_identity_mismatch" }
        try { Instant.parse(event.utc) } catch (error: DateTimeParseException) {
            throw IllegalArgumentException("invalid_utc", error)
        }
        require(event.elapsedMs >= (record.events.lastOrNull()?.elapsedMs ?: 0)) { "clock_reversed" }
        require(event.pageIndex == null || event.pageIndex in 0 until 100)
        require(event.navigationId == null || event.navigationId >= 0)
        require(listOf(event.rawCount, event.acceptedCount, event.newRawCount).all { it == null || it >= 0 })
        require(event.batteryPercent == null || event.batteryPercent in 0..100)
        if (event.kind in setOf(ProbeEventKind.REQUEST_STARTED, ProbeEventKind.RESPONSE_RECEIVED,
                ProbeEventKind.PAGE_PARSED, ProbeEventKind.PAGE_SAVED)) require(event.pageIndex != null && event.navigationId != null)
        if (event.kind in setOf(ProbeEventKind.PAGE_PARSED, ProbeEventKind.PAGE_SAVED)) {
            require(event.rawCount != null && event.acceptedCount != null && event.acceptedCount <= event.rawCount)
        }
    }

    private fun diagnostic(record: RunRecord, events: List<ProbeEvent>, kind: ProbeEventKind, reason: ProbeReason) =
        ProbeEvent(record.runId, record.processId, record.query.site, null, kind, events.lastOrNull()?.utc ?: Instant.EPOCH.toString(),
            events.lastOrNull()?.elapsedMs ?: 0, null, null, null, null, reason, record.evidenceSource)

    private fun writing(runId: String, action: () -> Unit) {
        try { action() } catch (error: IOException) { failedWrites += runId; throw error }
    }

    private fun atomic(file: File, value: JsonObject) {
        val temporary = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(temporary).use { it.write(value.toString().toByteArray(Charsets.UTF_8)); it.flush(); it.fd.sync() }
        commit(temporary, file)
    }

    private fun appendLine(event: ProbeEvent) {
        FileOutputStream(File(directory(event.runId), "events.jsonl"), true).use {
            it.write((eventJson(event).toString() + "\n").toByteArray(Charsets.UTF_8)); it.flush(); it.fd.sync()
        }
    }

    private fun <T> read(file: File, decode: (JsonObject) -> T): T = try {
        decode(Json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject)
    } catch (error: Exception) { throw IOException("invalid_record_file: ${file.name}", error) }
}
