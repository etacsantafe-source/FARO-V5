package ar.com.faro.v5.app

import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
class FaroV5BookmarkFoldersDevTest {
    @TempDir lateinit var root: Path
    @Test fun openingAndPausingSessionGrantsNoFolderOrRecoveryAction() {
        FaroV5V2CoreDevAuthority(root).open().use { session ->
            session.revokeBookmarkFolderActionsOnPause()
            session.revokeBookmarkReadingOnPause()
            session.revokeBookmarkFolderActionsOnPause()
            assertNotNull(session.timelineReadPort)
        }
    }
}
