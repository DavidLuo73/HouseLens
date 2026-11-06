package tw.houselens.probe

import java.util.UUID
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProbeRecoveryTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun oldProcessCannotResumeOrSpliceElapsedTimeIntoNewRun() = runTest {
        val h = RunnerHarness(this, temp.root)
        val id = UUID.randomUUID().toString()
        val oldProcess = UUID.randomUUID().toString()
        val oldStore = ProbeStore(temp.root, oldProcess)
        oldStore.create(RunRecord(id, oldProcess, h.query, RunMode.BACKGROUND_10M, RunState.RUNNING, h.device, EvidenceSource.LIVE))
        assertEquals(RunState.INTERRUPTED, h.store.load(id).state)
        assertFalse(h.runner.resume(id))
        assertNotEquals(BackgroundVerdict.PASSED, backgroundVerdict(h.store.load(id).events, RunMode.BACKGROUND_10M))
        h.pages = listOf(h.end)
        assertTrue(h.runner.start(h.query, RunMode.FULL)); advanceUntilIdle()
        assertNotEquals(id, h.runner.state.value!!.runId)
        assertEquals(RunState.INTERRUPTED, h.store.load(id).state)
    }
}
