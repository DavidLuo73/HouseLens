package tw.houselens.probe

import java.math.BigDecimal

enum class Site(val code: Int) {
    F591(0), Rakuya(1), Sinyi(2), Yungching(3), TwHouse(4), HBHousing(5), CtHouse(6),
}

data class ProbeQuery(
    val site: Site,
    val city: String,
    val district: String,
    val maxTotalPrice: BigDecimal,
    val minSizePing: BigDecimal = BigDecimal.ZERO,
    val rooms: String = "",
    val typeCodes: String = "R1,R2",
    val useCode: String = "1",
    val maxAgeYears: Int = 0,
    val parkingCodes: String = "",
)

// Prices are in 萬; area is in 坪, matching the existing PropertyDto contract.
data class ProbeListing(
    val city: String,
    val district: String,
    val address: String?,
    val areaPing: BigDecimal,
    val floor: String?,
    val ageYears: Int?,
    val hasParking: Boolean,
    val totalPrice: BigDecimal,
    val unitPrice: BigDecimal?,
    val sourceSite: Site,
    val sourceListingKey: String,
    val title: String,
    val url: String,
    val postedDate: String?,
    val imageUrl: String? = null,
    val unknownFields: Set<String> = emptySet(),
)

data class PageRequest(
    val url: String,
    val method: String = "GET",
    val body: String? = null,
    val browser: Boolean = false,
    val warmupUrl: String? = null,
    val captureJson: Boolean = false,
)

data class CapturedJson(val url: String, val body: String)

// Raw page content stays in memory; it is never part of an exported record.
data class RawCapture(
    val documentUrl: String,
    val statusCode: Int?,
    val html: String?,
    val json: List<CapturedJson>,
    val navigationId: Long,
    val networkObserved: Boolean,
)

enum class NextPage { MORE, END, UNKNOWN }
enum class PageKind { RESULTS, EMPTY, CHALLENGE, INVALID }
enum class PageDecision { CONTINUE, COMPLETE, WAIT_FOR_USER, FAIL, TRUNCATE, INCONCLUSIVE }

data class PageResult(
    val kind: PageKind,
    val rawKeys: List<String>,
    val listings: List<ProbeListing>,
    val excluded: Map<String, String>,
    val next: NextPage,
    val evidence: String,
)

enum class RunMode { FOREGROUND, BACKGROUND_10M, LOCKED_10M, FULL }
enum class RunState {
    RUNNING, WAITING_FOR_USER, COMPLETED, CANCELLED, INTERRUPTED, FAILED, TRUNCATED, INCONCLUSIVE,
}
enum class EvidenceSource { LIVE, LOCAL_SELF_CHECK, SIMULATED }
enum class BackgroundVerdict { PASSED, FAILED, INCONCLUSIVE, NOT_TESTED }

enum class ProbeEventKind {
    RUN_STARTED, APP_BACKGROUND, APP_FOREGROUND, SCREEN_OFF, SCREEN_ON, HEARTBEAT,
    REQUEST_STARTED, RESPONSE_RECEIVED, PAGE_PARSED, PAGE_SAVED, WAITING_FOR_USER, RESUMED,
    RUN_COMPLETED, RUN_CANCELLED, RUN_INTERRUPTED, RUN_FAILED, RUN_TRUNCATED, RUN_INCONCLUSIVE,
    EVIDENCE_INCOMPLETE,
}

enum class ProbeReason {
    NETWORK_ERROR, HTTP_ERROR, RATE_LIMITED, ROBOTS_DISALLOWED, ROBOTS_UNKNOWN,
    CHALLENGE_WAIT_TIMEOUT, RENDERER_GONE, STORAGE_ERROR, SERVICE_START_DENIED,
    TIMEOUT, USER_CANCELLED, UNSUPPORTED, PARSE_ERROR, EVIDENCE_LOST, PROCESS_LOST,
}

data class ProbeEvent(
    val runId: String,
    val processId: String,
    val site: Site,
    val pageIndex: Int?,
    val kind: ProbeEventKind,
    val utc: String,
    val elapsedMs: Long,
    val navigationId: Long?,
    val rawCount: Int?,
    val acceptedCount: Int?,
    val networkObserved: Boolean?,
    val reason: ProbeReason?,
    val evidenceSource: EvidenceSource,
    val newRawCount: Int? = null,
    val batteryPercent: Int? = null,
)

data class DeviceSnapshot(
    val model: String?,
    val androidVersion: String?,
    val sdkInt: Int?,
    val buildId: String?,
    val securityPatch: String?,
    val webViewPackage: String?,
    val webViewVersion: String?,
    val network: String?,
    val batteryPercent: Int?,
    val powerSaveMode: Boolean?,
    val batteryOptimizationExempt: Boolean?,
    val thermalStatus: Int?,
)

data class RunRecord(
    val runId: String,
    val processId: String,
    val query: ProbeQuery,
    val mode: RunMode,
    val state: RunState,
    val deviceSnapshot: DeviceSnapshot,
    val evidenceSource: EvidenceSource,
    val savedPageIndices: Set<Int> = emptySet(),
    val events: List<ProbeEvent> = emptyList(),
    val eventTailIncomplete: Boolean = false,
    val appVersion: String? = null,
)
