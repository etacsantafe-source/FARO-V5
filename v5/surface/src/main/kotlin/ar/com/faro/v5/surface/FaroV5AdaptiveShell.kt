package ar.com.faro.v5.surface

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceReadPort
import ar.com.faro.v5.kernel.contracts.ConversationTimelineReadPort
import ar.com.faro.v5.kernel.contracts.ConversationAssistantTurnDispatchPort
import ar.com.faro.v5.kernel.contracts.ConversationComposerPort
import ar.com.faro.v5.kernel.contracts.ConversationReviewPort
import ar.com.faro.v5.kernel.contracts.ConversationApplyRequestPort
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceSelectionPort

internal const val FARO_V5_EXPANDED_DRAWER_MIN_WIDTH_DP = 840

internal fun faroV5DrawerModeForWindowClassMinWidthDp(
    minWidthDp: Int,
): FaroV5DrawerMode =
    if (minWidthDp >= FARO_V5_EXPANDED_DRAWER_MIN_WIDTH_DP) {
        FaroV5DrawerMode.PERMANENT
    } else {
        FaroV5DrawerMode.MODAL
    }

@Composable
fun FaroV5AdaptiveShell(
    modifier: Modifier = Modifier,
    backStack: SnapshotStateList<FaroV5ShellDestination> = rememberFaroV5ShellBackStack(),
    transientMessageHostState: SnackbarHostState = remember { SnackbarHostState() },
    workspaceReadPort: ConversationWorkspaceReadPort? = null,
    timelineReadPort: ConversationTimelineReadPort? = null,
    composerPort: ConversationComposerPort? = null,
    reviewPort: ConversationReviewPort? = null,
    selectionPort: ConversationWorkspaceSelectionPort? = null,
    applyRequestPort: ConversationApplyRequestPort? = null,
    assistantTurnPort: ConversationAssistantTurnDispatchPort? = null,
    onOpenAiSettings: (() -> Unit)? = null,
    destinationContent: @Composable (FaroV5ShellDestination) -> Unit = {
        FaroV5UnavailableDestination(it)
    },
) {
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val drawerMode =
        faroV5DrawerModeForWindowClassMinWidthDp(
            adaptiveInfo.windowSizeClass.minWidthDp,
        )

    // A different Workspace/host generation invalidates all pending drawer
    // selection callbacks. Keep the navigation stack, but dispose the old
    // scaffold's coroutine scope and asynchronous read state.
    key(workspaceReadPort, timelineReadPort, composerPort, reviewPort, selectionPort, applyRequestPort, assistantTurnPort) {
        FaroV5ShellScaffold(
        drawerMode = drawerMode,
        modifier = modifier,
        backStack = backStack,
        transientMessageHostState = transientMessageHostState,
        workspaceReadPort = workspaceReadPort,
        timelineReadPort = timelineReadPort,
        composerPort = composerPort,
        reviewPort = reviewPort,
        selectionPort = selectionPort,
        applyRequestPort = applyRequestPort,
        assistantTurnPort = assistantTurnPort,
        onOpenAiSettings = onOpenAiSettings,
        destinationContent = destinationContent,
        )
    }
}
