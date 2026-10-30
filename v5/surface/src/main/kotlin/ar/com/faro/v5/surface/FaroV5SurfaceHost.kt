package ar.com.faro.v5.surface

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceReadPort
import ar.com.faro.v5.kernel.contracts.ConversationTimelineReadPort
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
    ) {
        activity.setContent {
            FaroV5Theme {
                FaroV5AdaptiveShell(
                    workspaceReadPort = workspaceReadPort,
                    timelineReadPort = timelineReadPort,
                    composerPort = composerPort,
                    reviewPort = reviewPort,
                    selectionPort = selectionPort,
                    applyRequestPort = applyRequestPort,
                )
            }
        }
    }
}
