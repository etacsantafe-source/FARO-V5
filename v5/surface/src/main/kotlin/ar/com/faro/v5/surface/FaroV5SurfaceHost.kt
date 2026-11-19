package ar.com.faro.v5.surface

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import android.view.View
import androidx.activity.compose.setContent
import android.net.Uri
import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceReadPort
import ar.com.faro.v5.kernel.contracts.ConversationTimelineReadPort
import ar.com.faro.v5.kernel.contracts.ConversationAssistantTurnDispatchPort
import ar.com.faro.v5.kernel.contracts.ConversationComposerPort
import ar.com.faro.v5.kernel.contracts.ConversationReviewPort
import ar.com.faro.v5.kernel.contracts.ConversationApplyRequestPort
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceSelectionPort

object FaroV5SurfaceHost {
    @JvmStatic
    @JvmOverloads
    fun install(
        activity: ComponentActivity,
        workspaceReadPort: ConversationWorkspaceReadPort? = null,
        timelineReadPort: ConversationTimelineReadPort? = null,
        composerPort: ConversationComposerPort? = null,
        reviewPort: ConversationReviewPort? = null,
        selectionPort: ConversationWorkspaceSelectionPort? = null,
        applyRequestPort: ConversationApplyRequestPort? = null,
        assistantTurnPort: ConversationAssistantTurnDispatchPort? = null,
        onOpenAiSettings: Runnable? = null,
        attachmentIngress: (suspend (ConversationId, String, List<Uri>) -> FaroV5AttachmentIngressResult)? = null,
        homeReadPort: FaroV5HomeReadPort? = null,
        searchReadPort: FaroV5SearchReadPort? = null,
        avisosReadPort: FaroV5AvisosReadPort? = null,
        activityReadPort: FaroV5ActivityReadPort? = null,
        bookmarkChatActions: FaroV5BookmarkChatActions? = null,
        bookmarkSourceTarget: FaroV5BookmarkSourceTarget? = null,
        assistantRequestMode: FaroV5AssistantRequestMode = FaroV5AssistantRequestMode.AUTOMATIC_ON_SEND,
        reviewDisclosureMode: FaroV5ReviewDisclosureMode = FaroV5ReviewDisclosureMode.LEGACY_EXPANDED,
        objectivesViewFactory: ((openChat: () -> Unit) -> View)? = null,
    ) {
        activity.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalFaroV5BookmarkChatActions provides bookmarkChatActions) {
            FaroV5Theme {
                val backStack = rememberFaroV5ShellBackStack()
                val objectivesReviewRefresh = remember { mutableIntStateOf(0) }
                val currentReviewPort = remember(reviewPort, objectivesReviewRefresh.intValue) {
                    if (objectivesReviewRefresh.intValue == 0) reviewPort
                    else reviewPort?.let { base -> object : ConversationReviewPort by base {} }
                }
                FaroV5AdaptiveShell(
                    backStack = backStack,
                    destinationContent = { destination ->
                        if (destination == FaroV5ShellDestination.OBJETIVOS && objectivesViewFactory != null) {
                            AndroidView(
                                factory = {
                                    objectivesViewFactory {
                                        backStack.clear()
                                        backStack.add(FaroV5ShellDestination.CHAT)
                                        objectivesReviewRefresh.intValue++
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            FaroV5UnavailableDestination(destination)
                        }
                    },
                    workspaceReadPort = workspaceReadPort,
                    timelineReadPort = timelineReadPort,
                    composerPort = composerPort,
                    reviewPort = currentReviewPort,
                    selectionPort = selectionPort,
                    applyRequestPort = applyRequestPort,
                    assistantTurnPort = assistantTurnPort,
                    assistantRequestMode = assistantRequestMode,
                    reviewDisclosureMode = reviewDisclosureMode,
                    onOpenAiSettings = onOpenAiSettings?.let { launcher -> { launcher.run() } },
                    attachmentIngress = attachmentIngress,
                    homeReadPort = homeReadPort,
                    searchReadPort = searchReadPort,
                    avisosReadPort = avisosReadPort,
                    activityReadPort = activityReadPort,
                    bookmarkSourceTarget = bookmarkSourceTarget,
                )
            }
            }
        }
    }
}
