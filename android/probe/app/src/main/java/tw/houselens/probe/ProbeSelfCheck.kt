package tw.houselens.probe

import android.content.Context
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.*

class ProbeSelfCheck(private val context: Context) {
    suspend fun run(): List<Pair<String, Boolean>> = withContext(Dispatchers.Main.immediate) {
        val results = mutableListOf<Pair<String, Boolean>>()
        var session: WebViewSession? = null
        try {
            withTimeout(60_000) {
                val browser = WebViewSession(context, RequestPacer(), selfCheck = true).also { session = it }
                val url = "https://$SELF_CHECK_HOST/assets/selfcheck.html"
                browser.capture(PageRequest(url, browser = true, captureJson = true), 1)
                results += localCaptureChecks(browser.capture(PageRequest("$url?previousNavigationId=1", browser = true, captureJson = true), 2))
                for (reason in listOf(ProbeReason.TIMEOUT, ProbeReason.RENDERER_GONE)) {
                    results += "SIMULATED：$reason 故障收尾（非真機系統事件）" to browser.checkInjectedFailure(reason)
                }
            }
        } catch (_: TimeoutCancellationException) { results += "LOCAL_SELF_CHECK：超時" to false }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { results += "LOCAL_SELF_CHECK：WebView／橋接可用性" to false }
        finally { session?.destroy() }
        try {
            val record = ProbeService.selfCheckRecord(context)
            withContext(Dispatchers.IO) {
                val store = ProbeService.store(context)
                store.create(record)
                val item = ProbeListing("新北市", "中和區", null, "28.50".toBigDecimal(), null, null, false,
                    "798.00".toBigDecimal(), null, Site.CtHouse, "LOCAL1", "本機樣本", "", null,
                    unknownFields = setOf("address", "floor", "ageYears", "hasParking"))
                store.savePage(record.runId, 0, PageResult(PageKind.RESULTS, listOf("LOCAL1"), listOf(item), emptyMap(), NextPage.END, "last_page"))
                store.finish(record.runId, RunState.COMPLETED)
                check(store.load(record.runId).savedPageIndices == setOf(0))
                check(store.loadPage(record.runId, 0).listings.single().totalPrice == "798.00".toBigDecimal())
                val bytes = ByteArrayOutputStream().also { store.export(record.runId, it) }.toByteArray()
                check(bytes.isNotEmpty())
            }
            results += "LOCAL_SELF_CHECK：樣本保存／重讀／ZIP；可在歷史選取 ${record.runId}" to true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { results += "LOCAL_SELF_CHECK：樣本保存／重讀／ZIP" to false }
        results
    }
}
