package ar.com.faro.v5.surface

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import ar.com.faro.v5.kernel.contracts.ConversationId
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceDrawerProjection
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceEntry
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceProjector
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceReadPort
import ar.com.faro.v5.kernel.contracts.ConversationTimelineReadPort
import ar.com.faro.v5.kernel.contracts.ConversationAssistantTurnDispatchPort
import ar.com.faro.v5.kernel.contracts.ConversationComposerPort
import ar.com.faro.v5.kernel.contracts.ConversationReviewPort
import ar.com.faro.v5.kernel.contracts.ConversationApplyRequestPort
import ar.com.faro.v5.kernel.contracts.ConversationWorkspaceSelectionPort
import ar.com.faro.v5.kernel.contracts.ConversationSelectionResult
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.launch

enum class FaroV5ShellDestination(
    val label: String,
    val kind: FaroV5ShellDestinationKind,
) {
    CHAT("Chat", FaroV5ShellDestinationKind.ROOT),
    INICIO("Inicio", FaroV5ShellDestinationKind.ROOT),
    FINANZAS("Finanzas", FaroV5ShellDestinationKind.ROOT),
    OBJETIVOS("Objetivos", FaroV5ShellDestinationKind.ROOT),
    ASUNTOS("Asuntos", FaroV5ShellDestinationKind.ROOT),
    SMART_COMPRA("SmartCompra", FaroV5ShellDestinationKind.ROOT),
    AJUSTES("Ajustes", FaroV5ShellDestinationKind.ROOT),
    BUSCAR("Buscar", FaroV5ShellDestinationKind.SECONDARY),
    AVISOS("Avisos", FaroV5ShellDestinationKind.SECONDARY),
}

enum class FaroV5ShellDestinationKind {
    ROOT,
    SECONDARY,
}

enum class FaroV5DrawerMode {
    MODAL,
    PERMANENT,
}

val faroV5ShellRoots: List<FaroV5ShellDestination> =
    listOf(
        FaroV5ShellDestination.CHAT,
        FaroV5ShellDestination.INICIO,
        FaroV5ShellDestination.FINANZAS,
        FaroV5ShellDestination.OBJETIVOS,
        FaroV5ShellDestination.ASUNTOS,
        FaroV5ShellDestination.SMART_COMPRA,
        FaroV5ShellDestination.AJUSTES,
    )

val faroV5PrimaryDrawerRoots: List<FaroV5ShellDestination> =
    faroV5ShellRoots.filterNot { it == FaroV5ShellDestination.AJUSTES }

val faroV5SecondaryShellDestinations: Set<FaroV5ShellDestination> =
    setOf(
        FaroV5ShellDestination.BUSCAR,
        FaroV5ShellDestination.AVISOS,
    )

internal fun faroV5NextShellStack(
    current: List<FaroV5ShellDestination>,
    target: FaroV5ShellDestination,
): List<FaroV5ShellDestination> {
    if (target.kind == FaroV5ShellDestinationKind.ROOT) {
        return listOf(target)
    }

    val root =
        current.firstOrNull()
            ?.takeIf { it.kind == FaroV5ShellDestinationKind.ROOT }
            ?: FaroV5ShellDestination.CHAT
    return listOf(root, target)
}

internal fun faroV5BackShellStack(
    current: List<FaroV5ShellDestination>,
): List<FaroV5ShellDestination> =
    if (current.size > 1) {
        listOf(
            current.firstOrNull()
                ?.takeIf { it.kind == FaroV5ShellDestinationKind.ROOT }
                ?: FaroV5ShellDestination.CHAT,
        )
    } else {
        current
    }

internal fun faroV5RestoreShellStack(
    savedNames: List<String>,
): List<FaroV5ShellDestination> {
    if (savedNames.isEmpty() || savedNames.size > 2) {
        return listOf(FaroV5ShellDestination.CHAT)
    }

    val restored =
        savedNames.map { savedName ->
            FaroV5ShellDestination.entries.firstOrNull { it.name == savedName }
                ?: return listOf(FaroV5ShellDestination.CHAT)
        }

    if (restored.first().kind != FaroV5ShellDestinationKind.ROOT) {
        return listOf(FaroV5ShellDestination.CHAT)
    }
    if (
        restored.size == 2 &&
        restored[1].kind != FaroV5ShellDestinationKind.SECONDARY
    ) {
        return listOf(FaroV5ShellDestination.CHAT)
    }

    return restored
}

private val FaroV5ShellBackStackSaver =
    Saver<SnapshotStateList<FaroV5ShellDestination>, List<String>>(
        save = { stack ->
            stack.map { it.name }
        },
        restore = { savedNames ->
            mutableStateListOf<FaroV5ShellDestination>().apply {
                addAll(faroV5RestoreShellStack(savedNames))
            }
        },
    )

@Composable
fun rememberFaroV5ShellBackStack(): SnapshotStateList<FaroV5ShellDestination> =
    rememberSaveable(saver = FaroV5ShellBackStackSaver) {
        mutableStateListOf(FaroV5ShellDestination.CHAT)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaroV5ShellScaffold(
    drawerMode: FaroV5DrawerMode,
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
    require(backStack.isNotEmpty()) {
        "FARO shell navigation requires a presentation root."
    }

    val currentDestination = backStack.last()
    val currentRoot =
        backStack.firstOrNull()
            ?.takeIf { it.kind == FaroV5ShellDestinationKind.ROOT }
            ?: FaroV5ShellDestination.CHAT
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectionBusy by remember(selectionPort) { mutableStateOf(false) }
    var selectionUncertain by remember(selectionPort) { mutableStateOf(false) }
    var workspaceRefresh by remember(workspaceReadPort, selectionPort) { mutableStateOf(0L) }
    // Keyed state prevents stale data from a previously bound Workspace reader.
    var workspaceReadState by remember(workspaceReadPort) {
        mutableStateOf<FaroV5WorkspaceReadState>(
            if (workspaceReadPort == null) FaroV5WorkspaceReadState.Unbound
            else FaroV5WorkspaceReadState.Loading
        )
    }
    LaunchedEffect(workspaceReadPort, workspaceRefresh) {
        if (workspaceReadPort != null) {
            workspaceReadState = try {
                FaroV5WorkspaceReadState.Ready(
                    ConversationWorkspaceProjector.project(
                        // Canonical Workspace reads must never block Compose's main thread.
                        // Cancellation on port replacement prevents stale results reaching UI.
                        withContext(Dispatchers.IO) { workspaceReadPort.readSnapshot() }
                    )
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                FaroV5WorkspaceReadState.Failed
            }
        }
    }
    val workspaceProjection =
        (workspaceReadState as? FaroV5WorkspaceReadState.Ready)?.projection

    // A confirmed Workspace result, never an optimistic local ID, selects chat.
    fun selectChat(newChat: Boolean, id: ConversationId? = null) {
        if (selectionBusy || selectionUncertain || workspaceProjection == null ||
            selectionPort == null || (!newChat && id == null)) return
        selectionBusy = true
        scope.launch {
            try {
                val key = IdempotencyKey(UUID.randomUUID().toString())
                val result = withContext(Dispatchers.IO) {
                    if (newChat) selectionPort.createAndSelect(
                        ConversationId(UUID.randomUUID().toString()),
                        System.currentTimeMillis(), key,
                    ) else selectionPort.select(requireNotNull(id), key)
                }
                when (result) {
                    is ConversationSelectionResult.Saved -> {
                        workspaceReadState = FaroV5WorkspaceReadState.Ready(
                            ConversationWorkspaceProjector.project(result.snapshot)
                        )
                        backStack.clear()
                        backStack.add(FaroV5ShellDestination.CHAT)
                        if (drawerMode == FaroV5DrawerMode.MODAL) drawerState.close()
                    }
                    is ConversationSelectionResult.Stale -> {
                        workspaceReadState = FaroV5WorkspaceReadState.Ready(
                            ConversationWorkspaceProjector.project(result.snapshot)
                        )
                        transientMessageHostState.showSnackbar("El chat cambió. Revisá la selección.")
                    }
                    else -> {
                        workspaceRefresh++
                        transientMessageHostState.showSnackbar("No se pudo seleccionar el chat.")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Owner might have committed: do not blindly reissue the action.
                selectionUncertain = true
                workspaceRefresh++
                transientMessageHostState.showSnackbar("No se pudo verificar. Reabrí FARO.")
            } finally {
                selectionBusy = false
            }
        }
    }

    LaunchedEffect(drawerMode) {
        if (
            drawerMode == FaroV5DrawerMode.PERMANENT &&
            drawerState.isOpen
        ) {
            drawerState.close()
        }
    }

    fun navigate(target: FaroV5ShellDestination) {
        val next = faroV5NextShellStack(backStack, target)
        if (next != backStack.toList()) {
            backStack.clear()
            backStack.addAll(next)
        }
        if (drawerMode == FaroV5DrawerMode.MODAL) {
            scope.launch { drawerState.close() }
        }
    }

    val shellContent: @Composable () -> Unit = {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            snackbarHost = {
                FaroV5TransientMessageHost(
                    hostState = transientMessageHostState,
                )
            },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            currentDestination.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        if (drawerMode == FaroV5DrawerMode.MODAL) {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Abrir menú",
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { navigate(FaroV5ShellDestination.BUSCAR) },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar en FARO",
                            )
                        }
                        IconButton(
                            onClick = { navigate(FaroV5ShellDestination.AVISOS) },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Abrir avisos",
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            NavDisplay(
                backStack = backStack,
                onBack = {
                    val next = faroV5BackShellStack(backStack)
                    if (next != backStack.toList()) {
                        backStack.clear()
                        backStack.addAll(next)
                    }
                },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                entryProvider = { key ->
                    NavEntry(key) {
                        if (key == FaroV5ShellDestination.CHAT && timelineReadPort != null) {
                            FaroV5ActiveChatScreen(
                                activeConversationId = workspaceProjection?.activeConversationId,
                                timelineReadPort = timelineReadPort,
                                composerPort = composerPort,
                                reviewPort = reviewPort,
                                applyRequestPort = applyRequestPort,
                                assistantTurnPort = assistantTurnPort,
                            )
                        } else if (key == FaroV5ShellDestination.AJUSTES &&
                            onOpenAiSettings != null) {
                            Column(modifier = Modifier.fillMaxSize().padding(FaroV5Spacing.large)) {
                                Text("Ajustes", style = MaterialTheme.typography.headlineSmall)
                                Text("IA remota: consentimiento y clave local protegida.")
                                TextButton(onClick = onOpenAiSettings) {
                                    Text("Configurar IA")
                                }
                            }
                        } else {
                            destinationContent(key)
                        }
                    }
                },
            )
        }
    }

    val drawerContent: @Composable () -> Unit = {
        FaroV5ShellDrawerContent(
            currentRoot = currentRoot,
            workspaceProjection = workspaceProjection,
            workspaceReadFailed = workspaceReadState == FaroV5WorkspaceReadState.Failed,
            selectionAvailable = selectionPort != null && !selectionBusy && !selectionUncertain,
            onNewChat = { selectChat(true) },
            onSelectConversation = { selectChat(false, it) },
            onNavigate = ::navigate,
        )
    }

    when (drawerMode) {
        FaroV5DrawerMode.MODAL ->
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerState = drawerState,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        drawerContent()
                    }
                },
                content = shellContent,
            )

        FaroV5DrawerMode.PERMANENT ->
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        drawerContent()
                    }
                },
                content = shellContent,
            )
    }
}

@Composable
private fun FaroV5ShellDrawerContent(
    currentRoot: FaroV5ShellDestination,
    workspaceProjection: ConversationWorkspaceDrawerProjection?,
    workspaceReadFailed: Boolean,
    selectionAvailable: Boolean,
    onNewChat: () -> Unit,
    onSelectConversation: (ConversationId) -> Unit,
    onNavigate: (FaroV5ShellDestination) -> Unit,
) {
    var showAllConversations by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxHeight()
                .padding(vertical = FaroV5Spacing.small),
    ) {
        Text(
            text = "FARO",
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            modifier =
                Modifier
                    .padding(
                        horizontal = FaroV5Spacing.large,
                        vertical = FaroV5Spacing.medium,
                    )
                    .semantics { heading() },
        )

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
        ) {
            faroV5PrimaryDrawerRoots.forEach { target ->
                NavigationDrawerItem(
                    label = { Text(target.label) },
                    selected = faroV5DrawerRootIsSelected(
                        currentRoot, target, workspaceProjection?.activeConversationId
                    ),
                    onClick = { onNavigate(target) },
                    modifier = Modifier.padding(horizontal = FaroV5Spacing.small),
                )
            }
            if (workspaceReadFailed) {
                Text(
                    "No se pudieron cargar las conversaciones",
                    modifier = Modifier.padding(FaroV5Spacing.large),
                )
            }
            // Read-only presentation: rows are deliberately not clickable while
            // the canonical Workspace mutation/activation owner is unavailable.
            if (workspaceProjection != null) {
                FaroV5WorkspaceGroup(
                    heading = "Fijados",
                    entries = workspaceProjection.pinned,
                    activeId = workspaceProjection.activeConversationId,
                    onSelect = if (selectionAvailable) onSelectConversation else null,
                )
                FaroV5WorkspaceGroup(
                    heading = "Recientes",
                    entries = workspaceProjection.recent,
                    activeId = workspaceProjection.activeConversationId,
                    onSelect = if (selectionAvailable) onSelectConversation else null,
                )
                if (workspaceProjection.hasMore) {
                    TextButton(
                        onClick = { showAllConversations = !showAllConversations },
                        modifier = Modifier.padding(horizontal = FaroV5Spacing.small),
                    ) {
                        Text(if (showAllConversations) "Ocultar anteriores" else "Ver todos…")
                    }
                    FaroV5WorkspaceGroup(
                        heading = "Anteriores y archivados",
                        entries = faroV5AdditionalWorkspaceEntries(
                            workspaceProjection, showAllConversations
                        ),
                        activeId = workspaceProjection.activeConversationId,
                        onSelect = if (selectionAvailable) onSelectConversation else null,
                    )
                }
            }
        }

        HorizontalDivider(
            modifier =
                Modifier.padding(
                    horizontal = FaroV5Spacing.large,
                    vertical = FaroV5Spacing.small,
                ),
        )

        if (workspaceProjection != null && selectionAvailable) {
            TextButton(
                onClick = onNewChat,
                modifier = Modifier.padding(horizontal = FaroV5Spacing.small),
            ) { Text("Nuevo chat") }
        }
        NavigationDrawerItem(
            label = { Text(FaroV5ShellDestination.AJUSTES.label) },
            selected = currentRoot == FaroV5ShellDestination.AJUSTES,
            onClick = { onNavigate(FaroV5ShellDestination.AJUSTES) },
            modifier = Modifier.padding(horizontal = FaroV5Spacing.small),
        )
    }
}

internal sealed interface FaroV5WorkspaceReadState {
    data object Unbound : FaroV5WorkspaceReadState
    data object Loading : FaroV5WorkspaceReadState
    data object Failed : FaroV5WorkspaceReadState
    data class Ready(val projection: ConversationWorkspaceDrawerProjection) : FaroV5WorkspaceReadState
}

/** A root is never highlighted simultaneously with a selected conversation. */
internal fun faroV5DrawerRootIsSelected(
    currentRoot: FaroV5ShellDestination,
    target: FaroV5ShellDestination,
    activeConversationId: ConversationId?,
): Boolean =
    currentRoot == target &&
        (currentRoot != FaroV5ShellDestination.CHAT || activeConversationId == null)

/** Archived and older remain recoverable without contaminating seven recents. */
internal fun faroV5AdditionalWorkspaceEntries(
    projection: ConversationWorkspaceDrawerProjection?,
    expanded: Boolean,
): List<ConversationWorkspaceEntry> =
    if (projection != null && expanded) projection.older + projection.archived else emptyList()

@Composable
private fun FaroV5WorkspaceGroup(
    heading: String,
    entries: List<ConversationWorkspaceEntry>,
    activeId: ConversationId?,
    onSelect: ((ConversationId) -> Unit)? = null,
) {
    if (entries.isEmpty()) return
    Text(
        text = heading,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(
            horizontal = FaroV5Spacing.large,
            vertical = FaroV5Spacing.small,
        ),
    )
    entries.forEach { entry ->
        Text(
            text = entry.title +
                (if (entry.archived) " · Archivado" else "") +
                (if (entry.hasPendingReview) " · Cambios pendientes" else ""),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (entry.id == activeId) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier
                .clickable(enabled = onSelect != null && !entry.archived) { onSelect?.invoke(entry.id) }
                .padding(
                    horizontal = FaroV5Spacing.large,
                    vertical = FaroV5Spacing.small,
                ),
        )
    }
}

@Composable
fun FaroV5UnavailableDestination(
    destination: FaroV5ShellDestination,
    modifier: Modifier = Modifier,
) {
    FaroV5EmptyState(
        title = destination.label,
        supportingText = "Esta sección todavía no está disponible en esta versión de prueba.",
        modifier = modifier,
    )
}
