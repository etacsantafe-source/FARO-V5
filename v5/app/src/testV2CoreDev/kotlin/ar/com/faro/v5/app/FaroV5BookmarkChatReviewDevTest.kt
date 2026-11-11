package ar.com.faro.v5.app

import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
class FaroV5BookmarkChatReviewDevTest {
    @TempDir lateinit var root: Path
    @Test fun pauseRevocationAndCloseAreSafeWithoutAnyGestureOrGrant() {
        FaroV5V2CoreDevAuthority(root).open().use { session ->
            session.revokeBookmarkReviewOnPause()
            session.revokeBookmarkReviewOnPause()
            assertNotNull(session.timelineReadPort)
        }
    }
}
