package ar.com.faro.v5.surface

import androidx.activity.ComponentActivity
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
    ) {
        activity.setContent {
            androidx.compose.runtime.CompositionLocalProvider(LocalFaroV5BookmarkChatActions provides bookmarkChatActions) {
            FaroV5Theme {
                FaroV5AdaptiveShell(
                    workspaceReadPort = workspaceReadPort,
                    timelineReadPort = timelineReadPort,
                    composerPort = composerPort,
                    reviewPort = reviewPort,
                    selectionPort = selectionPort,
                    applyRequestPort = applyRequestPort,
                    assistantTurnPort = assistantTurnPort,
                    assistantRequestMode = assistantRequestMode,
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
