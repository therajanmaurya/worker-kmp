package io.github.mobilebytelabs.worker.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.mobilebytelabs.worker.WorkInfo
import io.github.mobilebytelabs.worker.WorkProgress
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test
import kotlin.uuid.Uuid

/**
 * Render-regression goldens for the `@Composable` surfaces of `cmp-worker-compose`.
 *
 * WHY THIS EXISTS: the Kover root filter excludes `@Composable` functions via
 * `annotatedBy(...)`, so every UI body in this module is off-coverage by design (see
 * [NonComposableSurfacesTest]). Compilation and unit tests therefore say nothing about
 * whether a Compose Multiplatform upgrade changed what these surfaces actually draw —
 * a compiles-clean/renders-wrong layout ships silently. These screenshots close that gap.
 *
 * Goldens live in `src/desktopTest/roborazzi/` per RULE-TEST-OUTPUT-LAYOUT-001 (the only
 * screenshots committed to this repo). Runs on the desktop JVM target — no device, no
 * emulator, deterministic.
 *
 * DETERMINISM CONTRACT — every input below is fixed on purpose:
 *  - [FIXED_ID] is a literal UUID; `Uuid.random()` would re-record a diff on every run.
 *  - the canvas is a fixed 400x200 so goldens are not host-window dependent.
 *  - no clock/animation-driven surface is captured.
 */
@OptIn(ExperimentalTestApi::class)
class RenderGoldenTest {

    private fun golden(name: String) = "src/desktopTest/roborazzi/$name.png"

    private fun capture(name: String, content: @Composable () -> Unit) =
        runDesktopComposeUiTest(width = 400, height = 200) {
            setContent {
                MaterialTheme {
                    Surface { Box(Modifier.padding(8.dp)) { content() } }
                }
            }
            onRoot().captureRoboImage(golden(name))
        }

    // ── WorkStatusChip — every state, since colour/label mapping is the whole point ──

    @Test
    fun workStatusChip_allStates() {
        WorkInfo.State.entries.forEach { state ->
            capture("WorkStatusChip_$state") { WorkStatusChip(state = state) }
        }
    }

    // ── WorkProgressIndicator — determinate vs indeterminate are different layouts ──

    @Test
    fun workProgressIndicator_determinate() = capture("WorkProgressIndicator_determinate") {
        WorkProgressIndicator(progress = WorkProgress(65), statusMessage = "Uploading…")
    }

    @Test
    fun workProgressIndicator_indeterminate() = capture("WorkProgressIndicator_indeterminate") {
        WorkProgressIndicator(progress = WorkProgress(0))
    }

    // ── WorkInfoCard — action callbacks change which controls render ──

    @Test
    fun workInfoCard_running_withoutActions() = capture("WorkInfoCard_running_withoutActions") {
        WorkInfoCard(info = workInfo(WorkInfo.State.RUNNING, WorkProgress(40)))
    }

    @Test
    fun workInfoCard_failed_withActions() = capture("WorkInfoCard_failed_withActions") {
        WorkInfoCard(
            info = workInfo(WorkInfo.State.FAILED),
            onCancel = {},
            onRetry = {},
        )
    }

    private fun workInfo(state: WorkInfo.State, progress: WorkProgress = WorkProgress.NONE) = WorkInfo(
        id = FIXED_ID,
        state = state,
        progress = progress,
        tags = setOf("sync"),
    )

    private companion object {
        /** Fixed so goldens are reproducible — see the determinism contract above. */
        val FIXED_ID: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000001")
    }
}
