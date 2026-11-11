package ar.com.faro.v5.app

import ar.com.faro.v5.kernel.contracts.*
import ar.com.faro.v5.maintenance.*
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class FaroV5LocalInstallBookmarkMutationAuthorityT2Test {
    @TempDir lateinit var root: Path
    private inner class Fixture {
        val pointer = FileActiveGenerationPointer(root.resolve("pointer"))
        val pinned = requireNotNull(pointer.compareAndSet(null, StorageGenerationId("gen")))
        var foreground = true
        var host = true
        var epoch: String? = "a"
        var facts = AndroidSandboxProcessFacts(10200,10200,true,false,false,"ar.com.faro.alpha.v2coredev")
        val sandbox = FaroV5AndroidSandboxPrincipal("ar.com.faro.alpha.v2coredev", pinned, pointer,
            "a", { epoch }, AndroidSandboxFactsReadPort { facts })
        val owner = FaroV5LocalInstallBookmarkMutationAuthority(sandbox,pointer,{host},{foreground})
        val principal = requireNotNull(owner.technicalPrincipalOrNull())
        val target = BookmarkTarget(ConversationId("chat"),ConversationMessageId("msg"),"gen","token")
        val request = BookmarkMutationRequest("gen","token",0,IdempotencyKey("add"),
            BookmarkMutation.Add(BookmarkMetadata(BookmarkId("b"),target)))
        fun grant() = owner.grantAfterTrustedForegroundMutationAction(request,target)
        fun current() = owner.epochOrNull(principal,request,target)
    }
    @Test fun noGrantAndWrongRequestTargetPrincipalFailClosed() {
        val f=Fixture()
        assertNull(f.current()); assertTrue(f.grant()); assertNotNull(f.current())
        assertNull(f.owner.epochOrNull("foreign",f.request,f.target))
        assertNull(f.owner.epochOrNull(f.principal,f.request.copy(idempotencyKey=IdempotencyKey("other")),f.target))
        assertNull(f.owner.epochOrNull(f.principal,f.request,f.target.copy(conversationId=ConversationId("other"))))
        assertNull(f.owner.epochOrNull(f.principal,f.request,null))
    }
    @Test fun revokeRegrantAndPauseRotateConsentWithoutResurrection() {
        val f=Fixture();f.grant();val initial=f.current()
        f.owner.revokeAll();assertNull(f.current());f.grant();assertNotEquals(initial,f.current())
        f.foreground=false;assertNull(f.current());assertFalse(f.grant())
        f.foreground=true;assertNull(f.current());assertTrue(f.grant())
        f.owner.close();assertNull(f.current());assertFalse(f.grant())
    }
    @Test fun generationAbaPermanentlyPoisonsAuthority() {
        val f=Fixture();f.grant()
        f.pointer.compareAndSet(f.pinned.generationId,StorageGenerationId("other"))
        f.pointer.compareAndSet(StorageGenerationId("other"),f.pinned.generationId)
        assertNull(f.current());assertFalse(f.grant())
    }
    @Test fun sandboxMismatchAndHostDeathArePermanent() {
        val f=Fixture();f.grant();f.facts=f.facts.copy(applicationUid=10201)
        assertNull(f.current());f.facts=f.facts.copy(applicationUid=10200);assertFalse(f.grant())
    }
    @Test fun folderOnlyConsentCannotAuthorizeSourceMutationsOrAnotherFolder() {
        val f=Fixture()
        val folder=f.request.copy(mutation=BookmarkMutation.FolderCreate(BookmarkFolder(BookmarkFolderId("f"),"Folder")))
        assertFalse(f.owner.grantAfterTrustedForegroundMutationAction(folder,f.target))
        assertTrue(f.owner.grantAfterTrustedForegroundMutationAction(folder,null))
        assertNotNull(f.owner.epochOrNull(f.principal,folder,null));assertNull(f.current())
        assertNull(f.owner.epochOrNull(f.principal,folder.copy(mutation=BookmarkMutation.FolderDelete(BookmarkFolderId("other"))),null))
        f.host=false;assertNull(f.owner.epochOrNull(f.principal,folder,null));f.host=true;assertFalse(f.grant())
    }
}
