import java.security.MessageDigest
import java.util.Properties
import org.gradle.api.artifacts.ProjectDependency

plugins {
    base
    id("faro.spec-bridge")
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
}

val expectedGradleVersion = "9.8.2"
val expectedDistributionSha256 = "21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607"
val expectedWrapperJarSha256 = "3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98"

fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

tasks.register("v5StructuralCheck") {
    group = "verification"
    description = "Verifies the FARO V5 clean-build and current module purity envelope."

    doLast {
        val root = rootProject.projectDir.canonicalFile.toPath()
        val currentProjects = rootProject.allprojects.associate { it.path to it.projectDir.canonicalFile }
        val expectedProjects = setOf(":", ":app", ":kernel", ":kernel:contracts", ":kernel:runtime", ":kernel:apply", ":control", ":workspace", ":evidence", ":delivery", ":maintenance", ":owner", ":owner:profile", ":owner:finance", ":store", ":store:reality", ":store:auxiliary", ":store:generation", ":platform", ":platform:android", ":platform:ai", ":platform:web", ":surface")

        check(currentProjects.keys == expectedProjects) {
            "Unexpected V5 project graph: ${currentProjects.keys.sorted()}"
        }
        currentProjects.values.forEach { dir ->
            check(dir.toPath().startsWith(root)) {
                "V5 projectDir escapes v5/: $dir"
            }
        }

        val kernelNamespace = project(":kernel")
        check(kernelNamespace.projectDir.canonicalFile == file("kernel").canonicalFile) {
            ":kernel namespace must map to v5/kernel"
        }
        check(!file("kernel/build.gradle").exists() && !file("kernel/build.gradle.kts").exists()) {
            ":kernel must remain a structural namespace, not a product module"
        }
        check(!file("kernel/src").exists()) {
            ":kernel structural namespace must not own source"
        }

        val contracts = project(":kernel:contracts")
        check(contracts.projectDir.canonicalFile == file("kernel/contracts").canonicalFile) {
            ":kernel:contracts must live under v5/kernel/contracts"
        }
        val runtime = project(":kernel:runtime")
        check(runtime.projectDir.canonicalFile == file("kernel/runtime").canonicalFile) {
            ":kernel:runtime must live under v5/kernel/runtime"
        }
        val apply = project(":kernel:apply")
        check(apply.projectDir.canonicalFile == file("kernel/apply").canonicalFile) {
            ":kernel:apply must live under v5/kernel/apply"
        }

        val control = project(":control")
        check(control.projectDir.canonicalFile == file("control").canonicalFile) {
            ":control must live under v5/control"
        }

        val workspace = project(":workspace")
        check(workspace.projectDir.canonicalFile == file("workspace").canonicalFile) {
            "S2.A1 :workspace must reside under v5/workspace"
        }
        check(workspace.plugins.hasPlugin("java-library")) {
            "S2.A1 :workspace must remain a JVM library"
        }
        val workspaceDependencies = listOf("api", "implementation", "compileOnly", "runtimeOnly")
            .mapNotNull(workspace.configurations::findByName)
            .flatMap { it.dependencies.toList() }
        check(
            workspaceDependencies.filterIsInstance<ProjectDependency>().map { it.path }.toSet() ==
                setOf(":kernel:contracts")
        ) { "S2.A1 Workspace must depend only on contracts" }
        check(workspaceDependencies.filterNot { it is ProjectDependency }.all {
            it.group == "androidx.sqlite" ||
                (it.group == "org.jetbrains.kotlin" && it.name == "kotlin-stdlib")
        }) { "S2.A1 Workspace dependencies exceed Kotlin+AndroidX SQLite envelope" }
        val workspaceForbidden = listOf(
            "ar.com.faro.v3", "ar.com.faro.v4", "ar.com.faro.v5.kernel.runtime",
            "ar.com.faro.v5.kernel.apply", "ar.com.faro.v5.store.reality",
            "ar.com.faro.v5.surface", "ar.com.faro.v5.platform",
            "import android.", "import androidx.room",
        )
        check(fileTree("workspace/src/main") { include("**/*.kt", "**/*.java") }
            .files.flatMap { source ->
                workspaceForbidden.filter(source.readText()::contains).map { token ->
                    "${source.relativeTo(projectDir)} -> $token"
                }
            }.isEmpty()) { "S2.A1 Workspace purity boundary violated" }

        val surface = project(":surface")
        check(surface.projectDir.canonicalFile == file("surface").canonicalFile) {
            "S1.A1 :surface module must live under v5/surface"
        }
        check(surface.plugins.hasPlugin("com.android.library")) {
            "S1.A1 :surface must use the Android library plugin"
        }
        check(surface.plugins.hasPlugin("org.jetbrains.kotlin.plugin.compose")) {
            "S1.A1 :surface must apply the Compose compiler plugin"
        }
        check(!surface.plugins.hasPlugin("org.jetbrains.kotlin.android")) {
            "S1.A1 AGP 9 built-in Kotlin must remain authoritative; kotlin-android plugin is forbidden"
        }
        val rootBuildScript = file("build.gradle.kts").readText()
        val rootConfigurationPreamble =
            rootBuildScript.substringBefore("val expectedGradleVersion")
        val appBuildScript = file("app/build.gradle.kts").readText()
        val surfaceBuildScript = file("surface/build.gradle.kts").readText()
        check(
            !rootConfigurationPreamble.contains("kotlin-gradle-plugin") &&
                rootConfigurationPreamble.contains("alias(libs.plugins.android.application) apply false") &&
                rootConfigurationPreamble.contains("alias(libs.plugins.android.library) apply false") &&
                rootConfigurationPreamble.contains("alias(libs.plugins.compose.compiler) apply false") &&
                !rootConfigurationPreamble.contains("kotlin.jvm")
        ) {
            "S1.A1 experiment requires AGP application/library + Compose compiler co-located in the root plugins scope"
        }
        check(
            !appBuildScript.contains("kotlin-gradle-plugin") &&
                appBuildScript.contains("id(\"com.android.application\")") &&
                !surfaceBuildScript.contains("kotlin-gradle-plugin") &&
                surfaceBuildScript.contains("id(\"com.android.library\")") &&
                surfaceBuildScript.contains("id(\"org.jetbrains.kotlin.plugin.compose\")")
        ) {
            "S1.A1 experiment requires versionless module plugin application and no module-local KGP classpath"
        }

        val surfaceProductDependencies =
            listOf("api", "implementation", "compileOnly", "runtimeOnly")
                .mapNotNull(surface.configurations::findByName)
                .flatMap { it.dependencies.toList() }
        val surfaceProjectDependencies =
            surfaceProductDependencies.filterIsInstance<ProjectDependency>()
        check(
            surfaceProjectDependencies.map { it.path }.toSet() ==
                setOf(":kernel:contracts", ":kernel:runtime")
        ) {
            "S1.A1 :surface project dependency boundary changed: " +
                surfaceProjectDependencies.map { it.path }
        }

        val surfaceExternalCoordinates =
            surfaceProductDependencies
                .filterNot { it is ProjectDependency }
                .map { dependency -> "${dependency.group}:${dependency.name}" }
                .toSet()
        check(
            surfaceExternalCoordinates ==
                setOf(
                    "org.jetbrains.kotlin:kotlin-stdlib",
                    "androidx.compose:compose-bom",
                    "androidx.compose.ui:ui",
                    "androidx.compose.foundation:foundation",
                    "androidx.compose.material3:material3",
                    "androidx.compose.material:material-icons-core",
                    "androidx.navigation3:navigation3-runtime",
                    "androidx.navigation3:navigation3-ui",
                    "androidx.compose.material3.adaptive:adaptive",
                    "androidx.activity:activity-compose",
                )
        ) {
            "S1.A1 :surface effective external dependency boundary changed " +
                "(Kotlin stdlib is injected by the Kotlin/AGP toolchain), got " +
                surfaceExternalCoordinates
        }

        val surfaceForbiddenTokens =
            listOf(
                "ar.com.faro.v3",
                "ar.com.faro.v4",
                "ar.com.faro.v5.owner.",
                "ar.com.faro.v5.store.",
                "ar.com.faro.v5.platform.",
                "okhttp3.",
                "SQLite",
                "OperationId(",
                "OperationContract(",
            )
        val surfaceSourceViolations =
            fileTree("surface/src/main") {
                include("**/*.kt", "**/*.java")
            }.files.flatMap { source ->
                val text = source.readText()
                surfaceForbiddenTokens
                    .filter(text::contains)
                    .map { token -> "${source.relativeTo(projectDir)} -> $token" }
            }
        check(surfaceSourceViolations.isEmpty()) {
            "S1.A1 :surface purity violation: $surfaceSourceViolations"
        }

        val surfaceThemeSource =
            file("surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5Theme.kt").readText()
        val surfacePrimitivesSource =
            file("surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5CommonPrimitives.kt").readText()
        val surfaceTokensSource =
            file("surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5DesignTokens.kt").readText()
        val surfaceShellSource =
            file("surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5ShellNavigation.kt").readText()
        val surfaceAdaptiveSource =
            file("surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5AdaptiveShell.kt").readText()

        listOf(
            "LocalMinimumInteractiveComponentSize provides FaroV5Minimums.interactiveTarget",
            "isSystemInDarkTheme()",
            "lightColorScheme(",
            "darkColorScheme(",
            "CompositionLocalProvider(",
        ).forEach { required ->
            check(surfaceThemeSource.contains(required)) {
                "S1.A2 theme invariant missing: $required"
            }
        }
        listOf(
            "dynamicLightColorScheme",
            "dynamicDarkColorScheme",
            "WindowCompat",
            "android.app.Activity",
            "ar.com.faro.v5.control",
        ).forEach { forbidden ->
            check(!surfaceThemeSource.contains(forbidden)) {
                "S1.A2 Surface theme must remain presentation-only/static baseline: $forbidden"
            }
        }
        listOf(
            "FaroV5PrimaryAction",
            "FaroV5SecondaryAction",
            "FaroV5TextAction",
            "FaroV5LoadingState",
            "FaroV5EmptyState",
            "FaroV5ErrorState",
            "FaroV5StatusBanner",
            "FaroV5TransientMessageHost",
            "LiveRegionMode.Polite",
            "WindowInsets.safeDrawing",
            ".minimumInteractiveComponentSize()",
            "CircularProgressIndicator(",
            "error(",
        ).forEach { required ->
            check(surfacePrimitivesSource.contains(required)) {
                "S1.A2 common primitive/accessibility invariant missing: $required"
            }
        }
        check(surfaceTokensSource.contains("interactiveTarget = 48.dp")) {
            "S1.A2 interactive target baseline must remain at least 48dp"
        }
        listOf(
            "Algo salió mal",
            "Completado con éxito",
            "100%",
            "LinearProgressIndicator(",
        ).forEach { forbidden ->
            check(!surfacePrimitivesSource.contains(forbidden)) {
                "S1.A2 primitives must not fabricate generic outcome/progress copy: $forbidden"
            }
        }

        listOf(
            "FaroV5ShellDestination.CHAT",
            "FaroV5ShellDestination.INICIO",
            "FaroV5ShellDestination.FINANZAS",
            "FaroV5ShellDestination.OBJETIVOS",
            "FaroV5ShellDestination.ASUNTOS",
            "FaroV5ShellDestination.SMART_COMPRA",
            "FaroV5ShellDestination.AJUSTES",
            "FaroV5ShellDestination.BUSCAR",
            "FaroV5ShellDestination.AVISOS",
            "faroV5ShellRoots",
            "faroV5PrimaryDrawerRoots",
            "faroV5SecondaryShellDestinations",
            "FaroV5DrawerMode.MODAL",
            "FaroV5DrawerMode.PERMANENT",
            "ModalNavigationDrawer(",
            "PermanentNavigationDrawer(",
            "NavDisplay(",
            "NavEntry(",
            "Abrir menú",
            "Buscar en FARO",
            "Abrir avisos",
            "Esta sección todavía no está disponible en esta versión de prueba.",
        ).forEach { required ->
            check(surfaceShellSource.contains(required)) {
                "S1.A3 shell navigation invariant missing: $required"
            }
        }
        listOf(
            "RADAR",
            "Radar",
            "rememberNavBackStack",
            "kotlinx.serialization",
            "@Serializable",
            "createChat(",
        ).forEach { forbidden ->
            check(!surfaceShellSource.contains(forbidden)) {
                "S1.A3 shell scope/authority violation: $forbidden"
            }
        }
        // S2.A6-F1 replaces the obsolete S1 ban on real "Nuevo chat" UI.
        // Enforce the original boundary instead: Shell can request mutations,
        // never create an authoritative conversation itself or bypass the gate.
        val selectionSessionSource = file(
            "app/src/main/kotlin/ar/com/faro/v5/app/FaroV5ActiveChatSelectionSession.kt"
        ).readText()
        check(
            surfaceShellSource.contains("selectionPort.createAndSelect(") &&
                surfaceShellSource.contains("selectionPort.select(") &&
                surfaceShellSource.contains("selectionAvailable = selectionPort != null") &&
                selectionSessionSource.contains("gate.withMaterialWrite") &&
                selectionSessionSource.contains("requireCurrent()")
        ) {
            "S2.A6-F1 shell chat commands must use gated active Workspace authority"
        }
        check(
            surfaceShellSource.contains(
                "mutableStateListOf(FaroV5ShellDestination.CHAT)"
            )
        ) {
            "S1.A3 shell must remain Chat-first"
        }
        check(
            surfaceShellSource.contains(
                "faroV5ShellRoots.filterNot { it == FaroV5ShellDestination.AJUSTES }"
            )
        ) {
            "S1.A3 Ajustes must remain a root while using footer placement"
        }

        listOf(
            "currentWindowAdaptiveInfoV2()",
            "FARO_V5_EXPANDED_DRAWER_MIN_WIDTH_DP = 840",
            "faroV5DrawerModeForWindowClassMinWidthDp",
            "FaroV5DrawerMode.MODAL",
            "FaroV5DrawerMode.PERMANENT",
            "FaroV5ShellScaffold(",
        ).forEach { required ->
            check(surfaceAdaptiveSource.contains(required)) {
                "S1.A4 adaptive/window invariant missing: $required"
            }
        }
        listOf(
            "rememberSaveable(saver = FaroV5ShellBackStackSaver)",
            "faroV5RestoreShellStack",
            "savedNames.size > 2",
            "FaroV5ShellDestinationKind.ROOT",
            "FaroV5ShellDestinationKind.SECONDARY",
            "drawerState = drawerState",
            "LaunchedEffect(drawerMode)",
            "drawerState.close()",
            "TextOverflow.Ellipsis",
        ).forEach { required ->
            check(surfaceShellSource.contains(required)) {
                "S1.A4 continuity/back/accessibility invariant missing: $required"
            }
        }
        listOf(
            "currentWindowAdaptiveInfo(",
            "supportLargeAndXLargeWidth",
            "BackHandler(",
            "PredictiveBackHandler(",
            "OnBackPressedCallback",
            "OnBackInvokedCallback",
            "LocalDensity provides",
            "DataStore",
            "SQLite",
            "screenWidthDp",
            "isTablet",
            "rememberNavBackStack",
            "kotlinx.serialization",
            "@Serializable",
        ).forEach { forbidden ->
            check(
                !surfaceAdaptiveSource.contains(forbidden) &&
                    !surfaceShellSource.contains(forbidden)
            ) {
                "S1.A4 must keep adaptive/back state presentation-only and platform-derived: $forbidden"
            }
        }
        check(!surfaceBuildScript.contains("org.jetbrains.kotlin.plugin.serialization")) {
            "S1.A4 closed-enum SavedState must not add a Kotlin serialization compiler plugin"
        }

        check(
            surfaceAdaptiveSource.contains("SnackbarHostState") &&
                surfaceAdaptiveSource.contains("transientMessageHostState")
        ) {
            "S1.A6 transient message state must be hoisted at the adaptive shell boundary"
        }
        check(
            surfaceShellSource.contains("snackbarHost =") &&
                surfaceShellSource.contains("FaroV5TransientMessageHost(")
        ) {
            "S1.A6 shell must mount the shared transient message host"
        }

        val maintenance = project(":maintenance")
        check(maintenance.projectDir.canonicalFile == file("maintenance").canonicalFile) {
            ":maintenance must live under v5/maintenance"
        }
        check(maintenance.plugins.hasPlugin("java-library")) {
            ":maintenance must remain a JVM library"
        }

        val maintenanceProductDependencies =
            listOf("api", "implementation", "compileOnly", "runtimeOnly")
                .mapNotNull(maintenance.configurations::findByName)
                .flatMap { it.dependencies.toList() }
        val maintenanceProjectDependencies =
            maintenanceProductDependencies.filterIsInstance<ProjectDependency>()
        check(
            maintenanceProjectDependencies.map { it.path }.toSet() ==
                setOf(
                    ":kernel:contracts",
                    ":store:reality",
                    ":evidence",
                    ":store:auxiliary",
                )
        ) {
            ":maintenance P4.A4 reviewed project dependencies changed: " +
                maintenanceProjectDependencies.map { it.path }
        }
        val maintenanceForbiddenTokens =
            listOf(
                "ar.com.faro.v3",
                "ar.com.faro.v4",
                "import android.",
                "import androidx.",
                "SQLiteConnection",
                "SQLiteDriver",
                "okhttp3.",
                "java.net.http",
                "HttpURLConnection",
            )
        val maintenanceSourceViolations =
            fileTree("maintenance/src/main") {
                include("**/*.kt", "**/*.java")
            }.files.flatMap { source ->
                val text = source.readText()
                maintenanceForbiddenTokens
                    .filter(text::contains)
                    .map { token -> "${source.relativeTo(projectDir)} -> $token" }
            }
        check(maintenanceSourceViolations.isEmpty()) {
            ":maintenance P4.A4 purity violation: $maintenanceSourceViolations"
        }

        val ownerNamespace = project(":owner")
        check(ownerNamespace.projectDir.canonicalFile == file("owner").canonicalFile) {
            ":owner namespace must map to v5/owner"
        }
        check(!file("owner/build.gradle").exists() && !file("owner/build.gradle.kts").exists()) {
            ":owner must remain a structural namespace, not a product module"
        }
        check(!file("owner/src").exists()) {
            ":owner structural namespace must not own source"
        }
        val profile = project(":owner:profile")
        check(profile.projectDir.canonicalFile == file("owner/profile").canonicalFile) {
            ":owner:profile must live under v5/owner/profile"
        }
        val finance = project(":owner:finance")
        check(finance.projectDir.canonicalFile == file("owner/finance").canonicalFile) {
            ":owner:finance must live under v5/owner/finance"
        }

        val storeNamespace = project(":store")
        check(storeNamespace.projectDir.canonicalFile == file("store").canonicalFile) {
            ":store namespace must map to v5/store"
        }
        check(!file("store/build.gradle").exists() && !file("store/build.gradle.kts").exists()) {
            ":store must remain a structural namespace, not a product module"
        }
        check(!file("store/src").exists()) {
            ":store structural namespace must not own source"
        }
        val realityStore = project(":store:reality")
        check(realityStore.projectDir.canonicalFile == file("store/reality").canonicalFile) {
            ":store:reality must live under v5/store/reality"
        }
        val auxiliaryStore = project(":store:auxiliary")
        val generationStore = project(":store:generation")
        check(generationStore.projectDir.canonicalFile == file("store/generation").canonicalFile &&
            generationStore.plugins.hasPlugin("java-library")) {
            "S2.A1 Generation integration must remain a pure JVM library"
        }
        val generationDependencies = listOf("api", "implementation", "compileOnly", "runtimeOnly")
            .mapNotNull(generationStore.configurations::findByName)
            .flatMap { it.dependencies.toList() }
        check(generationDependencies.filterIsInstance<ProjectDependency>().map { it.path }.toSet() ==
            setOf(":maintenance", ":workspace", ":store:reality", ":store:auxiliary", ":evidence")) {
            "S2.A1 Generation integration dependency boundary drifted"
        }
        check(fileTree("store/generation/src/main") { include("**/*.kt", "**/*.java") }
            .files.none { source ->
                val sourceText = source.readText()
                listOf("import android.", "import androidx.", "SQLiteDriver",
                    "ar.com.faro.v3", "ar.com.faro.v4").any(sourceText::contains)
            }) { "S2.A1 Generation integration must not introduce Android, SQL drivers, or legacy dependencies" }

        check(auxiliaryStore.projectDir.canonicalFile == file("store/auxiliary").canonicalFile) {
            ":store:auxiliary must live under v5/store/auxiliary"
        }

        val platformNamespace = project(":platform")
        check(platformNamespace.projectDir.canonicalFile == file("platform").canonicalFile) {
            ":platform namespace must map to v5/platform"
        }
        check(!file("platform/build.gradle").exists() && !file("platform/build.gradle.kts").exists()) {
            ":platform must remain a structural namespace, not a product module"
        }
        check(!file("platform/src").exists()) {
            ":platform structural namespace must not own source"
        }
        val platformAndroid = project(":platform:android")
        check(platformAndroid.projectDir.canonicalFile == file("platform/android").canonicalFile) {
            ":platform:android must live under v5/platform/android"
        }
        check(platformAndroid.plugins.hasPlugin("com.android.library")) {
            ":platform:android must use the Android library plugin"
        }
        val platformProductDependencies = listOf("api", "implementation", "compileOnly", "runtimeOnly")
            .mapNotNull(platformAndroid.configurations::findByName)
            .flatMap { it.dependencies.toList() }
        val platformProjectDependencies =
            platformProductDependencies.filterIsInstance<ProjectDependency>()
        check(
            platformProjectDependencies.map { it.path }.toSet() ==
                setOf(":kernel:contracts", ":control", ":delivery", ":evidence", ":maintenance")
        ) {
            ":platform:android may depend only on approved V5 platform ports (:kernel:contracts + :control + :delivery + :evidence + :maintenance), got ${platformProjectDependencies.map { it.path }}"
        }
        val platformExternalDependencies =
            platformProductDependencies.filterNot { it is ProjectDependency }
        val platformExternalDependencyCoordinates =
            platformExternalDependencies
                .map { dependency -> "${dependency.group}:${dependency.name}" }
                .toSet()
        check(
            platformExternalDependencyCoordinates ==
                setOf(
                    "androidx.work:work-runtime",
                    "androidx.biometric:biometric",
                    "androidx.appsearch:appsearch",
                    "androidx.appsearch:appsearch-local-storage",
                )
        ) {
            ":platform:android P6.A4 permits only approved WorkManager + Biometric + AppSearch LocalStorage dependencies, got $platformExternalDependencies"
        }

        val appSearchProjection = file(
            "platform/android/src/main/java/ar/com/faro/v5/platform/android/search/AndroidAppSearchProjection.java"
        ).readText()
        listOf(
            "LocalStorage.createSearchSessionAsync",
            "DATABASE_NAME = \"faro-v5-search-projection\"",
            "setForceOverride(true)",
            "SearchSpec.TERM_MATCH_PREFIX",
            "SearchSpec.RANKING_STRATEGY_RELEVANCE_SCORE",
            "SearchResults",
            "results.close()",
            "SearchProjectionHit"
        ).forEach { required ->
            check(appSearchProjection.contains(required)) {
                "P6.A4 AppSearch private projection primitive missing: $required"
            }
        }
        listOf(
            "PlatformStorage",
            "PlayServicesStorage",
            "createGlobalSearchSession",
            "setSchemaTypeVisibility",
            "setSchemaTypeDisplayedBySystem",
            "android.app.appsearch"
        ).forEach { forbidden ->
            check(!appSearchProjection.contains(forbidden)) {
                "P6.A4 AppSearch authority/privacy boundary violation: $forbidden"
            }
        }

        val externalIngressParser = file(
            "platform/android/src/main/java/ar/com/faro/v5/platform/android/ingress/AndroidExternalIngressParser.java"
        ).readText()
        listOf(
            "Intent.ACTION_SEND",
            "Intent.ACTION_SEND_MULTIPLE",
            "Intent.ACTION_VIEW",
            "ACTION_QUICK",
            "ContentResolver.SCHEME_CONTENT",
            "ExternalIngressTrigger",
            "clipDataMatches"
        ).forEach { required ->
            check(externalIngressParser.contains(required)) {
                "P6.A5 bounded external ingress primitive missing: $required"
            }
        }
        listOf(
            "startActivity(",
            "startService(",
            "sendBroadcast(",
            "PendingIntent",
            "takePersistableUriPermission",
            "Intent.EXTRA_INTENT"
        ).forEach { forbidden ->
            check(!externalIngressParser.contains(forbidden)) {
                "P6.A5 ingress authority/forwarding violation: $forbidden"
            }
        }

        listOf(
            "deep-link origin allowlist is empty",
            "deep-link path allowlist is empty",
            "quick allowlist is empty"
        ).forEach { forbidden ->
            check(!externalIngressParser.contains(forbidden)) {
                "P6.A7 optional ingress channel must degrade by rejection, not constructor failure: $forbidden"
            }
        }

        val readAloudAdapter = file(
            "platform/android/src/main/java/ar/com/faro/v5/platform/android/voice/AndroidReadAloudAdapter.java"
        ).readText()
        check(readAloudAdapter.contains("stopActive(true);\n        String utteranceId")) {
            "P6.A7 replacing active TTS must terminate the previous callback with STOPPED"
        }

        val quickShortcutAdapter = file(
            "platform/android/src/main/java/ar/com/faro/v5/platform/android/ingress/AndroidQuickShortcutAdapter.java"
        ).readText()
        listOf(
            "ShortcutManager",
            "isRateLimitingActive",
            "setDynamicShortcuts",
            "setComponent(targetActivity)",
            "EXTRA_QUICK_ID"
        ).forEach { required ->
            check(quickShortcutAdapter.contains(required)) {
                "P6.A5 Quick shortcut projection primitive missing: $required"
            }
        }

        val voiceRecognitionAdapter = file(
            "platform/android/src/main/java/ar/com/faro/v5/platform/android/voice/AndroidVoiceRecognitionIntentAdapter.java"
        ).readText()
        check(
            voiceRecognitionAdapter.contains(
                "catch (RuntimeException ignored)"
            )
        ) {
            "P6.A7 STT external result parsing must fail closed on runtime parcel/unmarshal failures"
        }

        val platformAdapterFactory = file(
            "platform/android/src/main/java/ar/com/faro/v5/platform/android/AndroidPlatformAdapterFactory.java"
        ).readText()
        listOf(
            "createAttachmentSource",
            "createVoiceRecognition",
            "createReadAloud",
            "createSearchProjection",
            "createExternalIngressParser",
            "createQuickShortcutAdapter"
        ).forEach { required ->
            check(platformAdapterFactory.contains(required)) {
                "P6.A6 platform adapter factory binding missing: $required"
            }
        }

        val compositionRootText = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5CompositionRoot.java"
        ).readText()
        listOf(
            "AndroidPlatformAdapterFactory",
            "getAndroidPlatformAdapters()"
        ).forEach { required ->
            check(compositionRootText.contains(required)) {
                "P6.A6 CompositionRoot adapter wiring missing: $required"
            }
        }

        val appManifestText = file("app/src/main/AndroidManifest.xml").readText()
        val platformManifestText = file("platform/android/src/main/AndroidManifest.xml").readText()
        check(appManifestText.contains("android:allowBackup=\"false\"")) {
            "P6.A6 app manifest must keep Android system backup disabled"
        }
        check(platformManifestText.contains("android:usesCleartextTraffic=\"false\"")) {
            "P6.A6 platform manifest must keep cleartext traffic disabled"
        }
        check(Regex("""<activity\b""").findAll(appManifestText).count() == 1) {
            "S0.A2 app manifest must expose exactly one bounded bootstrap Activity"
        }
        check(
            appManifestText.contains("ar.com.faro.v5.app.FaroV5BootstrapActivity") &&
                appManifestText.contains("android.intent.action.MAIN") &&
                appManifestText.contains("android.intent.category.LAUNCHER")
        ) {
            "S0.A2 only justified app Activity is the normal bootstrap launcher"
        }
        check(Regex("""android:exported="true"""").findAll(appManifestText).count() == 1) {
            "S0.A2 launcher must be the only exported=true app component"
        }
        check(!platformManifestText.contains("android:exported=\"true\"")) {
            "P6 platform components remain non-exported"
        }
        val combinedAndroidManifests = appManifestText + "\n" + platformManifestText
        listOf(
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_"
        ).forEach { forbidden ->
            check(!combinedAndroidManifests.contains(forbidden)) {
                "P6.A6 unnecessary/broad permission forbidden: $forbidden"
            }
        }
        listOf(
            "android.permission.INTERNET",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.SCHEDULE_EXACT_ALARM",
            "android.permission.REQUEST_INSTALL_PACKAGES"
        ).forEach { expected ->
            check(combinedAndroidManifests.contains(expected)) {
                "P6.A6 previously approved Android permission missing: $expected"
            }
        }

        val platformAi = project(":platform:ai")
        check(platformAi.projectDir.canonicalFile == file("platform/ai").canonicalFile) {
            ":platform:ai must live under v5/platform/ai"
        }
        check(platformAi.plugins.hasPlugin("java-library")) {
            ":platform:ai must remain a JVM library"
        }
        val platformAiProductDependencies =
            listOf("api", "implementation", "compileOnly", "runtimeOnly")
                .mapNotNull(platformAi.configurations::findByName)
                .flatMap { it.dependencies.toList() }
        val platformAiProjectDependencies =
            platformAiProductDependencies.filterIsInstance<ProjectDependency>()
        check(
            platformAiProjectDependencies.map { it.path }.toSet() ==
                setOf(":kernel:contracts", ":control")
        ) {
            ":platform:ai P1.A20 may depend only on :kernel:contracts + :control SPI, got ${platformAiProjectDependencies.map { it.path }}"
        }
        val platformAiExternalDependencies =
            platformAiProductDependencies.filterNot { it is ProjectDependency }
        check(
            platformAiExternalDependencies.all { dependency ->
                (dependency.group == "org.jetbrains.kotlin" &&
                    dependency.name == "kotlin-stdlib") ||
                    (dependency.group == "org.jetbrains.kotlinx" &&
                        dependency.name == "kotlinx-serialization-json") ||
                    (dependency.group == "com.squareup.okhttp3" &&
                        dependency.name == "okhttp")
            }
        ) {
            ":platform:ai P1.A20 external dependencies may be Kotlin stdlib + kotlinx JSON + OkHttp only: $platformAiExternalDependencies"
        }
        val platformAiForbiddenTokens = listOf(
            "ar.com.faro.v3",
            "ar.com.faro.v4",
            "ar.com.faro.v5.kernel.runtime",
            "ar.com.faro.v5.kernel.apply",
            "ar.com.faro.v5.owner.",
            "ar.com.faro.v5.store.",
            "ar.com.faro.v5.platform.android",
            "ar.com.faro.v5.control.spi.SecretStore",
            "import android.",
            "import androidx.",
            "import io.ktor.",
            "HttpURLConnection",
            "java.net.http"
        )
        val platformAiSourceViolations =
            fileTree("platform/ai/src/main") {
                include("**/*.kt", "**/*.java")
            }.files.flatMap { source ->
                val text = source.readText()
                platformAiForbiddenTokens
                    .filter(text::contains)
                    .map { token -> "${source.relativeTo(projectDir)} -> $token" }
            }
        check(platformAiSourceViolations.isEmpty()) {
            ":platform:ai P1.A20 purity violation: $platformAiSourceViolations"
        }
        val platformAiControlImportViolations =
            fileTree("platform/ai/src/main") {
                include("**/*.kt", "**/*.java")
            }.files.flatMap { source ->
                source.readLines()
                    .map(String::trim)
                    .filter { line ->
                        line.startsWith("import ar.com.faro.v5.control.") &&
                            !line.startsWith("import ar.com.faro.v5.control.spi.")
                    }
                    .map { line -> "${source.relativeTo(projectDir)} -> $line" }
            }
        check(platformAiControlImportViolations.isEmpty()) {
            ":platform:ai may consume only :control SPI imports: $platformAiControlImportViolations"
        }

        val platformWeb = project(":platform:web")
        check(platformWeb.projectDir.canonicalFile == file("platform/web").canonicalFile) {
            ":platform:web must live under v5/platform/web"
        }
        check(platformWeb.plugins.hasPlugin("java-library")) {
            ":platform:web must remain a JVM library"
        }
        val platformWebProductDependencies =
            listOf("api", "implementation", "compileOnly", "runtimeOnly")
                .mapNotNull(platformWeb.configurations::findByName)
                .flatMap { it.dependencies.toList() }
        val platformWebProjectDependencies =
            platformWebProductDependencies.filterIsInstance<ProjectDependency>()
        check(
            platformWebProjectDependencies.map { it.path }.toSet() ==
                setOf(":kernel:contracts", ":evidence", ":control")
        ) {
            ":platform:web may depend only on :kernel:contracts + :evidence + :control SPI, got ${platformWebProjectDependencies.map { it.path }}"
        }
        val platformWebExternalDependencies =
            platformWebProductDependencies.filterNot { it is ProjectDependency }
        check(
            platformWebExternalDependencies.all { dependency ->
                (dependency.group == "org.jetbrains.kotlin" &&
                    dependency.name == "kotlin-stdlib") ||
                    (dependency.group == "org.jetbrains.kotlinx" &&
                        dependency.name == "kotlinx-serialization-json") ||
                    (dependency.group == "com.squareup.okhttp3" &&
                        dependency.name == "okhttp")
            }
        ) {
            ":platform:web external dependencies may be Kotlin stdlib + kotlinx JSON + OkHttp only: $platformWebExternalDependencies"
        }
        val platformWebForbiddenTokens = listOf(
            "ar.com.faro.v3",
            "ar.com.faro.v4",
            "ar.com.faro.v5.kernel.runtime",
            "ar.com.faro.v5.kernel.apply",
            "ar.com.faro.v5.owner.",
            "ar.com.faro.v5.store.",
            "ar.com.faro.v5.control.spi.SecretStore",
            "ar.com.faro.v5.platform.ai",
            "ar.com.faro.v5.platform.android",
            "import android.",
            "import androidx.",
            "import io.ktor.",
            "HttpURLConnection",
            "java.net.http"
        )
        val platformWebSourceViolations =
            fileTree("platform/web/src/main") {
                include("**/*.kt", "**/*.java")
            }.files.flatMap { source ->
                val text = source.readText()
                platformWebForbiddenTokens
                    .filter(text::contains)
                    .map { token -> "${source.relativeTo(projectDir)} -> $token" }
            }
        check(platformWebSourceViolations.isEmpty()) {
            ":platform:web purity violation: $platformWebSourceViolations"
        }
        val platformWebControlImportViolations =
            fileTree("platform/web/src/main") {
                include("**/*.kt", "**/*.java")
            }.files.flatMap { source ->
                source.readLines()
                    .map(String::trim)
                    .filter { line ->
                        line.startsWith("import ar.com.faro.v5.control.") &&
                            !line.startsWith("import ar.com.faro.v5.control.spi.")
                    }
                    .map { line -> "${source.relativeTo(projectDir)} -> $line" }
            }
        check(platformWebControlImportViolations.isEmpty()) {
            ":platform:web may consume only :control SPI imports: $platformWebControlImportViolations"
        }

        val settingsText = file("settings.gradle.kts").readText()
        listOf("v4-build", "app-v4", "app-v3").forEach { forbidden ->
            check(!settingsText.contains(forbidden, ignoreCase = true)) {
                "Legacy build reference forbidden: $forbidden"
            }
        }
        check(!settingsText.contains("includeBuild(\"../") && !settingsText.contains("includeBuild('../")) {
            "Included builds must stay inside v5/"
        }

        val wrapperPropsFile = file("gradle/wrapper/gradle-wrapper.properties")
        check(wrapperPropsFile.isFile) { "Missing gradle-wrapper.properties" }
        val wrapperProps = Properties().apply {
            wrapperPropsFile.inputStream().use(::load)
        }
        val expectedUrl = "https://services.gradle.org/distributions/gradle-$expectedGradleVersion-bin.zip"
        check(wrapperProps.getProperty("distributionUrl") == expectedUrl) {
            "Unexpected Gradle distribution URL"
        }
        check(wrapperProps.getProperty("distributionSha256Sum") == expectedDistributionSha256) {
            "Unexpected Gradle distribution checksum"
        }

        val wrapperJar = file("gradle/wrapper/gradle-wrapper.jar")
        check(wrapperJar.isFile && wrapperJar.length() > 0L) {
            "Missing Gradle wrapper JAR"
        }
        check(wrapperJar.sha256() == expectedWrapperJarSha256) {
            "Unexpected Gradle wrapper JAR checksum"
        }

        val catalog = file("gradle/libs.versions.toml").readText()
        check(Regex("""(?m)^kotlin\s*=\s*"2\.4\.21"$""").containsMatchIn(catalog)) {
            "Kotlin catalog version drifted from reviewed V5 Kotlin 2.4.21"
        }
        val rootBuildPrefix =
            file("build.gradle.kts").readText().substringBefore("plugins {")
        check(!rootBuildPrefix.contains("buildscript")) {
            "Android KGP override must stay scoped to :app to avoid AGP classloader conflicts"
        }
        listOf("SNAPSHOT", "latest.", "+").forEach { token ->
            check(!catalog.contains(token, ignoreCase = true)) {
                "Uncontrolled version token in version catalog: $token"
            }
        }

        val productConfigurationNames = listOf("api", "implementation", "compileOnly", "runtimeOnly")
        val productDependencies = productConfigurationNames
            .mapNotNull(contracts.configurations::findByName)
            .flatMap { it.dependencies.toList() }

        check(productDependencies.none { it is ProjectDependency }) {
            ":kernel:contracts must have zero product project dependencies"
        }

        val forbiddenGroups = listOf(
            "androidx",
            "com.android",
            "com.squareup.okhttp3",
            "io.ktor",
            "org.xerial",
        )
        val forbiddenDependencies = productDependencies.filter { dependency ->
            val group = dependency.group ?: return@filter false
            forbiddenGroups.any { prefix -> group == prefix || group.startsWith("$prefix.") }
        }
        check(forbiddenDependencies.isEmpty()) {
            ":kernel:contracts contains forbidden technology dependencies: $forbiddenDependencies"
        }

        val forbiddenSourceTokens = listOf(
            "ar.com.faro.v3",
            "import android.",
            "import androidx.",
            "import android.database",
            "import okhttp3.",
            "import io.ktor.",
            "import org.sqlite.",
        )
        val sourceViolations = fileTree("kernel/contracts/src/main") {
            include("**/*.kt", "**/*.java")
        }.files.flatMap { source ->
            val text = source.readText()
            forbiddenSourceTokens
                .filter(text::contains)
                .map { token -> "${source.relativeTo(projectDir)} -> $token" }
        }
        check(sourceViolations.isEmpty()) {
            ":kernel:contracts purity violation: $sourceViolations"
        }

        val runtimeProductDependencies = productConfigurationNames
            .mapNotNull(runtime.configurations::findByName)
            .flatMap { it.dependencies.toList() }
        val runtimeProjectDependencies = runtimeProductDependencies.filterIsInstance<ProjectDependency>()
        check(runtimeProjectDependencies.size == 1 && runtimeProjectDependencies.single().path == ":kernel:contracts") {
            ":kernel:runtime may depend only on :kernel:contracts, got ${runtimeProjectDependencies.map { it.path }}"
        }

        val runtimeForbiddenDependencies = runtimeProductDependencies
            .filterNot { it is ProjectDependency }
            .filter { dependency ->
                val group = dependency.group ?: return@filter false
                forbiddenGroups.any { prefix -> group == prefix || group.startsWith("$prefix.") }
            }
        check(runtimeForbiddenDependencies.isEmpty()) {
            ":kernel:runtime contains forbidden technology dependencies: $runtimeForbiddenDependencies"
        }

        val runtimeSourceViolations = fileTree("kernel/runtime/src/main") {
            include("**/*.kt", "**/*.java")
        }.files.flatMap { source ->
            val text = source.readText()
            forbiddenSourceTokens
                .filter(text::contains)
                .map { token -> "${source.relativeTo(projectDir)} -> $token" }
        }
        check(runtimeSourceViolations.isEmpty()) {
            ":kernel:runtime purity violation: $runtimeSourceViolations"
        }

        val applyProductDependencies = productConfigurationNames
            .mapNotNull(apply.configurations::findByName)
            .flatMap { it.dependencies.toList() }
        val applyProjectDependencies = applyProductDependencies.filterIsInstance<ProjectDependency>()
        check(applyProjectDependencies.size == 1 && applyProjectDependencies.single().path == ":kernel:contracts") {
            ":kernel:apply may depend only on :kernel:contracts, got ${applyProjectDependencies.map { it.path }}"
        }

        val applyForbiddenDependencies = applyProductDependencies
            .filterNot { it is ProjectDependency }
            .filter { dependency ->
                val group = dependency.group ?: return@filter false
                forbiddenGroups.any { prefix -> group == prefix || group.startsWith("$prefix.") }
            }
        check(applyForbiddenDependencies.isEmpty()) {
            ":kernel:apply contains forbidden technology dependencies: $applyForbiddenDependencies"
        }

        val applySourceForbiddenTokens = forbiddenSourceTokens + listOf(
            "ar.com.faro.v5.kernel.runtime",
        )
        val applySourceViolations = fileTree("kernel/apply/src/main") {
            include("**/*.kt", "**/*.java")
        }.files.flatMap { source ->
            val text = source.readText()
            applySourceForbiddenTokens
                .filter(text::contains)
                .map { token -> "${source.relativeTo(projectDir)} -> $token" }
        }
        check(applySourceViolations.isEmpty()) {
            ":kernel:apply purity violation: $applySourceViolations"
        }

        val controlProductDependencies = productConfigurationNames
            .mapNotNull(control.configurations::findByName)
            .flatMap { it.dependencies.toList() }
        val controlProjectDependencies =
            controlProductDependencies.filterIsInstance<ProjectDependency>()
        check(
            controlProjectDependencies.size == 1 &&
                controlProjectDependencies.single().path == ":kernel:contracts"
        ) {
            ":control may depend only on :kernel:contracts, got ${controlProjectDependencies.map { it.path }}"
        }
        val controlExternalDependencies =
            controlProductDependencies.filterNot { it is ProjectDependency }
        check(
            controlExternalDependencies.all { dependency ->
                dependency.group == "androidx.datastore" ||
                    (
                        dependency.group == "org.jetbrains.kotlin" &&
                        dependency.name == "kotlin-stdlib"
                    )
            }
        ) {
            ":control external dependencies must be Kotlin stdlib + AndroidX DataStore only: $controlExternalDependencies"
        }
        val controlForbiddenTokens = listOf(
            "ar.com.faro.v3",
            "ar.com.faro.v4",
            "ar.com.faro.v5.kernel.runtime",
            "ar.com.faro.v5.kernel.apply",
            "ar.com.faro.v5.owner.",
            "ar.com.faro.v5.store.",
            "ar.com.faro.v5.surface",
            "ar.com.faro.v5.platform",
            "import android.",
            "import androidx.room",
            "import androidx.sqlite",
        )
        val controlSourceViolations = fileTree("control/src/main") {
            include("**/*.kt", "**/*.java")
        }.files.flatMap { source ->
            val text = source.readText()
            controlForbiddenTokens
                .filter(text::contains)
                .map { token -> "${source.relativeTo(projectDir)} -> $token" }
        }
        check(controlSourceViolations.isEmpty()) {
            ":control purity violation: $controlSourceViolations"
        }

        val evidence = project(":evidence")
        check(evidence.projectDir.canonicalFile == file("evidence").canonicalFile) {
            ":evidence must live under v5/evidence"
        }
        val evidenceProductDependencies = productConfigurationNames
            .mapNotNull(evidence.configurations::findByName)
            .flatMap { it.dependencies.toList() }
        val evidenceProjectDependencies =
            evidenceProductDependencies.filterIsInstance<ProjectDependency>()
        check(
            evidenceProjectDependencies.size == 1 &&
                evidenceProjectDependencies.single().path == ":kernel:contracts"
        ) {
            ":evidence may depend only on :kernel:contracts, got ${evidenceProjectDependencies.map { it.path }}"
        }
        val evidenceExternalDependencies =
            evidenceProductDependencies.filterNot { it is ProjectDependency }
        check(
            evidenceExternalDependencies.all { dependency ->
                dependency.group == "androidx.sqlite" ||
                    (
                        dependency.group == "org.jetbrains.kotlin" &&
                        dependency.name == "kotlin-stdlib"
                    )
            }
        ) {
            ":evidence external dependencies must be Kotlin stdlib + AndroidX SQLite only: $evidenceExternalDependencies"
        }
        val evidenceForbiddenTokens = listOf(
            "ar.com.faro.v3",
            "ar.com.faro.v4",
            "ar.com.faro.v5.kernel.runtime",
            "ar.com.faro.v5.kernel.apply",
            "ar.com.faro.v5.owner.",
            "ar.com.faro.v5.store.",
            "ar.com.faro.v5.control",
            "ar.com.faro.v5.surface",
            "ar.com.faro.v5.platform",
            "import android.",
            "import androidx.room",
        )
        val evidenceSourceViolations = fileTree("evidence/src/main") {
            include("**/*.kt", "**/*.java")
        }.files.flatMap { source ->
            val text = source.readText()
            evidenceForbiddenTokens
                .filter(text::contains)
                .map { token -> "${source.relativeTo(projectDir)} -> $token" }
        }
        check(evidenceSourceViolations.isEmpty()) {
            ":evidence purity violation: $evidenceSourceViolations"
        }

        val delivery = project(":delivery")
        check(delivery.projectDir.canonicalFile == file("delivery").canonicalFile) {
            ":delivery must live under v5/delivery"
        }
        check(delivery.plugins.hasPlugin("java-library")) {
            ":delivery must remain a JVM library"
        }
        val deliveryProductDependencies = productConfigurationNames
            .mapNotNull(delivery.configurations::findByName)
            .flatMap { it.dependencies.toList() }
        val deliveryProjectDependencies =
            deliveryProductDependencies.filterIsInstance<ProjectDependency>()
        check(
            deliveryProjectDependencies.size == 1 &&
                deliveryProjectDependencies.single().path == ":kernel:contracts"
        ) {
            ":delivery may depend only on :kernel:contracts, got ${deliveryProjectDependencies.map { it.path }}"
        }
        val deliveryExternalDependencies =
            deliveryProductDependencies.filterNot { it is ProjectDependency }
        check(
            deliveryExternalDependencies.all { dependency ->
                dependency.group == "org.jetbrains.kotlin" &&
                    dependency.name == "kotlin-stdlib"
            }
        ) {
            ":delivery external dependencies must be Kotlin stdlib only: $deliveryExternalDependencies"
        }
        val deliveryForbiddenTokens = listOf(
            "ar.com.faro.v3",
            "ar.com.faro.v4",
            "ar.com.faro.v5.kernel.runtime",
            "ar.com.faro.v5.kernel.apply",
            "ar.com.faro.v5.owner.",
            "ar.com.faro.v5.store.",
            "ar.com.faro.v5.control",
            "ar.com.faro.v5.surface",
            "ar.com.faro.v5.platform",
            "import android.",
            "import androidx.",
            "NotificationManager",
            "WorkManager",
            "AlarmManager",
        )
        val deliverySourceViolations = fileTree("delivery/src/main") {
            include("**/*.kt", "**/*.java")
        }.files.flatMap { source ->
            val text = source.readText()
            deliveryForbiddenTokens
                .filter(text::contains)
                .map { token -> "${source.relativeTo(projectDir)} -> $token" }
        }
        check(deliverySourceViolations.isEmpty()) {
            ":delivery purity violation: $deliverySourceViolations"
        }

        val profileProductDependencies = productConfigurationNames
            .mapNotNull(profile.configurations::findByName)
            .flatMap { it.dependencies.toList() }
        val profileProjectDependencies = profileProductDependencies.filterIsInstance<ProjectDependency>()
        check(profileProjectDependencies.size == 1 && profileProjectDependencies.single().path == ":kernel:contracts") {
            ":owner:profile may depend only on :kernel:contracts, got ${profileProjectDependencies.map { it.path }}"
        }
        val profileForbiddenDependencies = profileProductDependencies
            .filterNot { it is ProjectDependency }
            .filter { dependency ->
                val group = dependency.group ?: return@filter false
                forbiddenGroups.any { prefix -> group == prefix || group.startsWith("$prefix.") }
            }
        check(profileForbiddenDependencies.isEmpty()) {
            ":owner:profile contains forbidden technology dependencies: $profileForbiddenDependencies"
        }
        val profileForbiddenSourceTokens = forbiddenSourceTokens + listOf(
            "ar.com.faro.v5.kernel.runtime",
            "ar.com.faro.v5.kernel.apply",
            "ar.com.faro.v5.owner.",
            "ar.com.faro.v5.store.",
        )
        val profileSourceViolations = fileTree("owner/profile/src/main") {
            include("**/*.kt", "**/*.java")
        }.files.flatMap { source ->
            val text = source.readText()
            profileForbiddenSourceTokens
                .filter { token ->
                    if (token == "ar.com.faro.v5.owner.") {
                        text.contains(token) && !text.contains("ar.com.faro.v5.owner.profile")
                    } else {
                        text.contains(token)
                    }
                }
                .map { token -> "${source.relativeTo(projectDir)} -> $token" }
        }
        check(profileSourceViolations.isEmpty()) {
            ":owner:profile purity violation: $profileSourceViolations"
        }

        val financeProductDependencies = productConfigurationNames
            .mapNotNull(finance.configurations::findByName)
            .flatMap { it.dependencies.toList() }
        val financeProjectDependencies = financeProductDependencies.filterIsInstance<ProjectDependency>()
        check(financeProjectDependencies.size == 1 && financeProjectDependencies.single().path == ":kernel:contracts") {
            ":owner:finance may depend only on :kernel:contracts, got ${financeProjectDependencies.map { it.path }}"
        }
        val financeForbiddenDependencies = financeProductDependencies
            .filterNot { it is ProjectDependency }
            .filter { dependency ->
                val group = dependency.group ?: return@filter false
                forbiddenGroups.any { prefix -> group == prefix || group.startsWith("$prefix.") }
            }
        check(financeForbiddenDependencies.isEmpty()) {
            ":owner:finance contains forbidden technology dependencies: $financeForbiddenDependencies"
        }
        val financeForbiddenSourceTokens = forbiddenSourceTokens + listOf(
            "ar.com.faro.v5.kernel.runtime",
            "ar.com.faro.v5.kernel.apply",
            "ar.com.faro.v5.owner.profile",
            "ar.com.faro.v5.store.",
        )
        val financeSourceViolations = fileTree("owner/finance/src/main") {
            include("**/*.kt", "**/*.java")
        }.files.flatMap { source ->
            val text = source.readText()
            financeForbiddenSourceTokens
                .filter(text::contains)
                .map { token -> "${source.relativeTo(projectDir)} -> $token" }
        }
        check(financeSourceViolations.isEmpty()) {
            ":owner:finance purity violation: $financeSourceViolations"
        }
        val auxiliaryProductDependencies = productConfigurationNames
            .mapNotNull(auxiliaryStore.configurations::findByName)
            .flatMap { it.dependencies.toList() }
        val auxiliaryProjectDependencies =
            auxiliaryProductDependencies.filterIsInstance<ProjectDependency>()
        check(
            auxiliaryProjectDependencies.map { it.path }.toSet() ==
                setOf(":kernel:contracts", ":delivery")
        ) {
            ":store:auxiliary project dependencies must be contracts + delivery only, got ${auxiliaryProjectDependencies.map { it.path }}"
        }
        val auxiliaryExternalDependencies =
            auxiliaryProductDependencies.filterNot { it is ProjectDependency }
        check(
            auxiliaryExternalDependencies.all { dependency ->
                dependency.group == "androidx.sqlite" ||
                    (
                        dependency.group == "org.jetbrains.kotlin" &&
                            dependency.name == "kotlin-stdlib"
                    )
            }
        ) {
            ":store:auxiliary external dependencies must be Kotlin stdlib + AndroidX SQLite only: $auxiliaryExternalDependencies"
        }
        val auxiliaryForbiddenTokens = listOf(
            "ar.com.faro.v3",
            "ar.com.faro.v4",
            "ar.com.faro.v5.kernel.runtime",
            "ar.com.faro.v5.kernel.apply",
            "ar.com.faro.v5.owner.",
            "ar.com.faro.v5.store.reality",
            "ar.com.faro.v5.platform",
            "import android.",
            "import androidx.room",
            "WorkManager",
            "AlarmManager",
            "NotificationManager",
        )
        val auxiliarySourceViolations = fileTree("store/auxiliary/src/main") {
            include("**/*.kt", "**/*.java")
        }.files.flatMap { source ->
            val text = source.readText()
            auxiliaryForbiddenTokens
                .filter(text::contains)
                .map { token -> "${source.relativeTo(projectDir)} -> $token" }
        }
        check(auxiliarySourceViolations.isEmpty()) {
            ":store:auxiliary purity violation: $auxiliarySourceViolations"
        }

        val realityProductDependencies = productConfigurationNames
            .mapNotNull(realityStore.configurations::findByName)
            .flatMap { it.dependencies.toList() }
        val realityProjectDependencies = realityProductDependencies.filterIsInstance<ProjectDependency>()
        check(
            realityProjectDependencies.map { it.path }.toSet() ==
                setOf(":kernel:contracts", ":owner:profile", ":owner:finance")
        ) {
            ":store:reality project dependencies must be contracts + owner:profile + owner:finance, got ${realityProjectDependencies.map { it.path }}"
        }
        val realityExternalDependencies = realityProductDependencies.filterNot { it is ProjectDependency }
        check(
            realityExternalDependencies.all { dependency ->
                dependency.group == "androidx.sqlite" ||
                    (
                        dependency.group == "org.jetbrains.kotlin" &&
                        dependency.name == "kotlin-stdlib"
                    )
            }
        ) {
            ":store:reality external dependencies must be Kotlin stdlib + AndroidX SQLite only: $realityExternalDependencies"
        }
        val realityForbiddenTokens = listOf(
            "ar.com.faro.v3",
            "ar.com.faro.v4",
            "ar.com.faro.v5.kernel.runtime",
            "ar.com.faro.v5.kernel.apply",
            "ar.com.faro.v5.surface",
            "ar.com.faro.v5.platform",
            "import android.",
            "import androidx.room",
        )
        val realitySourceViolations = fileTree("store/reality/src/main") {
            include("**/*.kt", "**/*.java")
        }.files.flatMap { source ->
            val text = source.readText()
            realityForbiddenTokens
                .filter(text::contains)
                .map { token -> "${source.relativeTo(projectDir)} -> $token" }
        }
        check(realitySourceViolations.isEmpty()) {
            ":store:reality purity violation: $realitySourceViolations"
        }

        val app = project(":app")
        check(app.projectDir.canonicalFile == file("app").canonicalFile) {
            ":app must live under v5/app"
        }
        check(app.plugins.hasPlugin("com.android.application")) {
            ":app must be the terminal Android application module"
        }
        val appProductDependencies =
            productConfigurationNames
                .mapNotNull(app.configurations::findByName)
                .flatMap { it.dependencies.toList() }
        val appProjectDependencies = appProductDependencies.filterIsInstance<ProjectDependency>()
        check(
            appProjectDependencies.map { it.path }.toSet() ==
                setOf(":kernel:contracts", ":kernel:runtime", ":kernel:apply", ":surface", ":control", ":delivery", ":store:reality", ":evidence", ":store:auxiliary", ":platform:android", ":platform:web", ":maintenance", ":workspace", ":store:generation")
        ) {
            ":app composition dependencies drifted: ${appProjectDependencies.map { it.path }}"
        }
        val illegalAppConsumers =
            rootProject.subprojects
                .filter { it.path != ":app" }
                .flatMap { consumer ->
                    productConfigurationNames
                        .mapNotNull(consumer.configurations::findByName)
                        .flatMap { configuration ->
                            configuration.dependencies
                                .filterIsInstance<ProjectDependency>()
                                .filter { dependency -> dependency.path == ":app" }
                                .map { consumer.path }
                        }
                }
        check(illegalAppConsumers.isEmpty()) {
            ":app must remain terminal; consumers=$illegalAppConsumers"
        }

        println(
            "FARO_V5_T0_PASS projects=${currentProjects.size} structural-namespaces=4 app=composition-root kernel-contracts=pure kernel-runtime=pure kernel-apply=pure control=datastore-only evidence=sqlite+web-contracts delivery=pure store-auxiliary=sqlite-delivery owner-profile=pure owner-finance=pure store-reality=sqlite-only platform-android=security-envelope+secret-store+workmanager+notification+exact-alarm-boundary platform-ai=groq-text+json-wire+bounded-https+model-catalog+model-observation platform-web=safe-fetch+search-contracts legacy-projects=0 wrapper=$expectedGradleVersion"
        )
    }
}


tasks.register("appCompositionPolicyCheck") {
    group = "verification"
    description = "Verifies P3.A15 terminal app composition remains explicit, bounded and authority-neutral."

    doLast {
        val catalog = file("gradle/libs.versions.toml").readText()
        val appBuild = file("app/build.gradle.kts").readText()
        listOf(
            """id("com.android.application")""",
            """applicationId = "ar.com.faro.alpha"""",
            "compileSdk = 37",
            "minSdk = 29",
            "targetSdk = 36",
            "versionCode = 66",
            """applicationIdSuffix = ".v5dev"""",
            """implementation(project(":kernel:contracts"))""",
            """implementation(project(":kernel:runtime"))""",
            """implementation(project(":kernel:apply"))""",
            """implementation(project(":surface"))""",
            """implementation(project(":control"))""",
            """implementation(project(":delivery"))""",
            """implementation(project(":store:auxiliary"))""",
            """implementation(project(":platform:android"))""",
            """implementation(project(":platform:web"))""",
            """implementation(project(":maintenance"))""",
            """implementation(project(":workspace"))""",
            """implementation(project(":store:generation"))""",
            "implementation(libs.androidx.work.runtime)",
            "implementation(libs.androidx.activity)",
            "implementation(libs.androidx.core)",
            "implementation(libs.androidx.core.splashscreen)",
            "implementation(libs.okhttp)"
        ).forEach { required ->
            check(appBuild.contains(required)) {
                "P3/S1 app build primitive missing: $required"
            }
        }
        check(!appBuild.contains("kotlin-gradle-plugin") &&
            !appBuild.contains("org.jetbrains.kotlin.android")) {
            "S1.A1 built-in Kotlin baseline must remain authoritative in :app"
        }
        check(catalog.contains("""androidx-core = "1.19.1"""")) {
            "S1.A6 stable AndroidX Core 1.19.1 baseline missing"
        }

        val manifest = file("app/src/main/AndroidManifest.xml").readText()
        listOf(
            "ar.com.faro.v5.app.FaroV5Application",
            "androidx.startup.InitializationProvider",
            "androidx.work.WorkManagerInitializer",
            """tools:node="remove"""",
            """android:allowBackup="false"""",
            """android:windowSoftInputMode="adjustResize""""
        ).forEach { required ->
            check(manifest.contains(required)) {
                "P3.A15 app manifest primitive missing: $required"
            }
        }
        check(manifest.contains("ar.com.faro.v5.app.FaroV5BootstrapActivity")) {
            "S0.A2 bounded bootstrap Activity must be the app launcher"
        }
        check(manifest.contains("android.intent.action.MAIN") &&
            manifest.contains("android.intent.category.LAUNCHER")) {
            "S0.A2 bootstrap Activity must expose the normal launcher entry"
        }
        check(Regex("""<activity\b""").findAll(manifest).count() == 1) {
            "S0.A2 must not grow a general Activity/navigation surface"
        }

        val bootstrapActivity = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5BootstrapActivity.java"
        ).readText()
        listOf(
            "import androidx.core.view.WindowCompat;",
            "WindowCompat.enableEdgeToEdge(getWindow())"
        ).forEach { required ->
            check(bootstrapActivity.contains(required)) {
                "S1.A6 launcher edge-to-edge invariant missing: $required"
            }
        }

        listOf("<service", "<receiver").forEach { forbidden ->
            check(!manifest.contains(forbidden)) {
                "P3/S0 app scope must not introduce service/receiver: $forbidden"
            }
        }

        val application = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5Application.java"
        ).readText()
        listOf(
            "Configuration.Provider",
            "setWorkerFactory(compositionRoot().getWorkerFactory())",
            "Executors.newSingleThreadExecutor",
            "root.recoverPendingMaintenanceState(",
            "root.installProcessBridges()",
            "root.reprojectStartup()"
        ).forEach { required ->
            check(application.contains(required)) {
                "P3.A15 Application wiring primitive missing: $required"
            }
        }
        check(
            application.indexOf("root.recoverPendingMaintenanceState(") <
                application.indexOf("root.installProcessBridges()")
        ) {
            "P4/S0 startup recovery must complete before alarm/process bridges are installed"
        }

        val root = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5CompositionRoot.java"
        ).readText()

        val startupRecovery =
            root.substringAfter(
                "public FaroV5StartupReadiness recoverPendingMaintenanceState("
            ).substringBefore(
                "private boolean recoverStartupStep("
            )
        check(
            startupRecovery.indexOf("ensureInitialGenerationAndGetDirectory") <
                startupRecovery.indexOf("installUpdateProcessBridges(foregroundState)")
        ) {
            "S0 pristine storage bootstrap must precede update callback bridge construction"
        }
        check(
            startupRecovery.indexOf("installUpdateProcessBridges(foregroundState)") <
                startupRecovery.indexOf("updateRuntime().reconcilePending()")
        ) {
            "P5 update callback bridges must exist before update startup reconciliation"
        }
        listOf(
            "AuxiliaryDeliveryStore",
            "AndroidNotificationDeliverySink",
            "NotificationDeliveryDispatcher",
            "NotificationDeliveryReconciler",
            "NotificationDeliveryScheduledWorkHandler",
            "ScheduledWorkExecutionPort",
            "FaroWorkerFactory",
            "FaroExactAlarmExecutionBridge.install",
            "AndroidWorkManagerScheduler",
            "AndroidScheduledDeliveryWorkProjection",
            "NotificationDeliveryReprojector",
            "WorkManager.getInstance(appContext)",
            "getNoBackupFilesDir()",
            "ActiveGenerationStorageLocator",
            "FaroV5MaintenanceRuntime",
            "reconcileInterruptedExternalEffects",
            "recoverPendingResetsOrThrow",
            "recoverPendingMaintenanceState",
            "executeMaintenanceReset",
            "activateValidatedMaintenanceGeneration",
            "SingleProcessMaterialWriteGate",
            "supplyMaterialWrite",
            "closeMaterialWriteAdmission",
            "openMaterialWriteAdmission",
            "maintenanceAdmissionLock",
            "beginInteractiveMaintenanceOperation",
            "requireMaintenanceRecovery",
            "completeMaintenanceOperation",
            "resetResultAllowsMaterialWrites",
            "activationResultAllowsMaterialWrites",
            "maintenanceRecoveryComplete",
            "ScheduledWorkExecutionResult.RETRY",
            "getMaterialWriteGate",
            "ensureInitialGenerationAndGetDirectory",
            "activeGenerationDirectory()",
            "activateAndVerifyGeneration",
            "StartupReprojectionState.DEGRADED"
        ).forEach { required ->
            check(root.contains(required)) {
                "P3.A15 CompositionRoot primitive missing: $required"
            }
        }
        listOf(
            "ServiceLocator",
            "ar.com.faro.v5.owner.",
            "ar.com.faro.v5.kernel.runtime",
            "ar.com.faro.v5.kernel.apply",
            "AndroidExactAlarmProjection",
            "AlarmManager",
            "setExact",
            "SharedPreferences",
            "android.util.Log",
            "System.out",
            "System.err",
            "http://",
            "https://",
            "faro-v5-auxiliary.db"
        ).forEach { forbidden ->
            check(!root.contains(forbidden) && !application.contains(forbidden)) {
                "P3.A15 composition scope violation: $forbidden"
            }
        }

        // C127: the active root uses a pointer-verified, format-aware graph owner,
        // but its V2 activation flag MUST remain false until F1-F4 certification.
        // Canonical plan verification stays behind the dedicated Android adapter.
        val stagedAdapter = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5QuarantinedV2GraphAdmission.java"
        ).readText()
        check(root.contains("FaroV5QuarantinedV2GraphAdmission.canonical(") &&
            root.contains("materialWriteGate,") &&
            root.contains("FaroV5ActiveGenerationGraphOwner.open(") &&
            root.contains("materialWriteGate,\n                                FaroV5HostCertification.V2_ACTIVE_ENABLED);")) {
            "S2.A6 active graph owner must remain strictly V2-off until certification"
        }
        val hostCertificate = file(
            "app/src/main/kotlin/ar/com/faro/v5/app/FaroV5HostCertification.kt"
        ).readText()
        val preSwitchHost = file(
            "app/src/main/kotlin/ar/com/faro/v5/app/FaroV5PreSwitchHostAdmission.kt"
        ).readText()
        val maintenanceHost = file(
            "app/src/main/kotlin/ar/com/faro/v5/app/FaroV5MaintenanceRuntime.kt"
        ).readText()
        check(hostCertificate.contains("const val V2_ACTIVE_ENABLED: Boolean = false") &&
            maintenanceHost.contains("v2HostCertified = FaroV5HostCertification.V2_ACTIVE_ENABLED") &&
            maintenanceHost.contains("root.openQuarantinedV2WithHostGate(staged)") &&
            maintenanceHost.contains("activePointer = pointer") &&
            preSwitchHost.contains("LegacyV1HostGenerationAdmission(layout)") &&
            preSwitchHost.contains("pointerBefore == targetGenerationId") &&
            preSwitchHost.contains("activePointer.read()?.generationId == pointerBefore") &&
            preSwitchHost.contains("kind != MaintenanceOperationKind.RESTORE || !v2HostCertified")) {
            "S2.A6 V2 pre-pointer and active-owner admission must share a disabled certificate"
        }
        val activeOwner = file(
            "app/src/main/kotlin/ar/com/faro/v5/app/FaroV5ActiveGenerationGraphOwner.kt"
        ).readText()
        check(activeOwner.contains("ExistingGenerationFormatReader.requireAndRead(staged)") &&
            activeOwner.contains("check(v2HostCertified)") &&
            activeOwner.contains("CanonicalPreparedPlanEnvelopeVerifier") &&
            activeOwner.contains("openActiveV2WithSharedGate(") &&
            activeOwner.contains("pointer.read()?.generationId == expected")) {
            "S2.A6 active host must be pointer-bound, explicitly versioned and fail-closed"
        }
        val activeGraph = file(
            "store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt"
        ).readText()
        val activePreflight = file(
            "store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/ActiveSqlitePreflight.kt"
        ).readText()
        val stagedPreflight = file(
            "store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/StagedSqlitePreflight.kt"
        ).readText()
        check(activeGraph.contains("fun openActiveV2WithSharedGate(") &&
            activeGraph.contains("ActiveSqlitePreflight.requireAll(directory, schemas)") &&
            activeGraph.contains("StagedSqlitePreflight.requireAll(directory, schemas)") &&
            activePreflight.contains("SQLITE_OPEN_READONLY or SQLITE_OPEN_NOFOLLOW") &&
            !activePreflight.contains("?mode=ro&immutable=1") &&
            stagedPreflight.contains("immutable=1")) {
            "S2.A6 active WAL recovery and sealed staging must remain separate"
        }
        check(stagedAdapter.contains("CanonicalPreparedPlanEnvelopeVerifier.INSTANCE") &&
            stagedAdapter.contains("openValidatedV2WithSharedGate(") &&
            stagedAdapter.contains("staged.getDirectory().toRealPath()") &&
            stagedAdapter.contains("active.equals(target)") &&
            !stagedAdapter.contains("FaroV5GenerationStoreGraph.open(")) {
            "S2.A6 adapter must bind canonical verifier+single gate and reject active generation"
        }

        println(
            "FARO_V5_APP_P5_A8_PASS composition=explicit workmanager=custom_factory " +
                "update_callbacks=AFTER_STORAGE_BOOTSTRAP_BEFORE_UPDATE_RECONCILIATION " +
                "alarm_bridge=AFTER_RECOVERY " +
                "startup_reprojection=bounded_async update_network=LAZY ui=S0_TO_SURFACE_SINGLE_HOST"
        )
    }
}


tasks.register("startupReadinessPolicyCheck") {
    group = "verification"
    description = "Verifies S0.A1 startup readiness remains typed, fail-closed and bootstrap-safe."

    doLast {
        val application = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5Application.java"
        ).readText()
        val root = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5CompositionRoot.java"
        ).readText()
        val readiness = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5StartupReadiness.java"
        ).readText()
        val gate = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5StartupReadinessGate.java"
        ).readText()

        listOf("INITIALIZING", "LOCAL_READY", "MAINTENANCE_ONLY").forEach { state ->
            check(readiness.contains(state)) {
                "S0.A1 readiness state missing: $state"
            }
        }
        listOf(
            "INITIAL_STORAGE_BOOTSTRAP_FAILED",
            "MAINTENANCE_RUNTIME_INITIALIZATION_FAILED",
            "EXTERNAL_EFFECT_RECOVERY_FAILED",
            "UPDATE_RUNTIME_INITIALIZATION_FAILED",
            "UPDATE_BRIDGE_INITIALIZATION_FAILED",
            "UPDATE_RECONCILIATION_FAILED",
            "RESET_RECOVERY_FAILED",
            "GENERATION_ACTIVATION_RECOVERY_FAILED",
            "ACTIVE_GENERATION_HEALTH_FAILED"
        ).forEach { reason ->
            check(readiness.contains(reason)) {
                "S0.A1 typed maintenance reason missing: $reason"
            }
        }

        listOf(
            "materialWriteGate.closeMaterialWriteAdmission()",
            "FaroV5StartupReadiness.initializing()",
            "FaroV5StartupReadiness.maintenanceOnly(",
            "FaroV5StartupReadiness.localReady()",
            "materialWriteGate.openMaterialWriteAdmission()"
        ).forEach { required ->
            check(gate.contains(required)) {
                "S0.A1 readiness/write-admission primitive missing: $required"
            }
        }

        val startupRecovery =
            root.substringAfter(
                "public FaroV5StartupReadiness recoverPendingMaintenanceState("
            ).substringBefore(
                "private boolean recoverStartupStep("
            )
        val storage = startupRecovery.indexOf("ensureInitialGenerationAndGetDirectory")
        val maintenanceRuntime = startupRecovery.indexOf("MAINTENANCE_RUNTIME_INITIALIZATION_FAILED")
        val updateRuntime = startupRecovery.indexOf("UPDATE_RUNTIME_INITIALIZATION_FAILED")
        val updateBridge = startupRecovery.indexOf("installUpdateProcessBridges(foregroundState)")
        val updateReconcile = startupRecovery.indexOf("updateRuntime().reconcilePending()")
        val activationRecovery = startupRecovery.indexOf("recoverPendingActivationsOrThrow")
        val activeHealth = startupRecovery.indexOf("ACTIVE_GENERATION_HEALTH_FAILED")
        val localReady = startupRecovery.indexOf("completeStartupRecovery()")
        check(
            listOf(
                storage,
                maintenanceRuntime,
                updateRuntime,
                updateBridge,
                updateReconcile,
                activationRecovery,
                activeHealth,
                localReady,
            ).all { it >= 0 }
        ) {
            "S0.A1 startup sequence is incomplete"
        }
        check(root.contains("private ActiveGenerationStorageLocator generationStorage()")) {
            "S0.A1 StorageGeneration locator must be lazy behind typed startup recovery"
        }
        check(
            !root.substringAfter("public FaroV5CompositionRoot(Context context)")
                .substringBefore("public FaroWorkerFactory getWorkerFactory()")
                .contains("new ActiveGenerationStorageLocator")
        ) {
            "S0.A1 CompositionRoot constructor must not perform StorageGeneration filesystem bootstrap"
        }
        check(storage < maintenanceRuntime) {
            "S0.A1 disk-touching Maintenance runtime must be lazy until pristine bootstrap completes"
        }
        check(storage < updateRuntime) {
            "S0.A1 disk-touching Update runtime must be lazy until pristine bootstrap completes"
        }
        check(updateRuntime < updateBridge && updateBridge < updateReconcile) {
            "S0.A1/P5 update bridge ordering drifted"
        }
        check(activationRecovery < activeHealth && activeHealth < localReady) {
            "S0.A1 final active-generation health must gate LOCAL_READY"
        }
        check(root.contains("catch (Exception failure)")) {
            "S0.A1 operational recovery failures must converge to typed MAINTENANCE_ONLY"
        }
        check(!root.contains("catch (Throwable failure)")) {
            "S0.A1 must not swallow fatal VM/linkage errors"
        }

        check(application.contains("root.recoverPendingMaintenanceState(")) {
            "S0.A1 Application must consume typed readiness"
        }
        check(application.contains("startupExecutor.execute(")) {
            "S0.A2 startup recovery must leave the Application main thread"
        }
        check(application.contains("if (!readiness.isLocalReady())")) {
            "S0.A1 Application must stop normal startup outside LOCAL_READY"
        }
        check(
            application.indexOf("if (!readiness.isLocalReady())") <
                application.indexOf("root.installProcessBridges()")
        ) {
            "S0.A1 normal process bridges must remain unreachable in MAINTENANCE_ONLY"
        }

        println(
            "FARO_V5_S0_A1_READINESS_PASS " +
                "states=INITIALIZING|LOCAL_READY|MAINTENANCE_ONLY " +
                "writes=FAIL_CLOSED storage=BEFORE_DISK_RUNTIMES " +
                "update_bridge=BEFORE_RECONCILE active_generation_health=BEFORE_LOCAL_READY"
        )
    }
}




tasks.register("bootstrapPresentationPolicyCheck") {
    group = "verification"
    description = "Verifies S0.A2 bootstrap UI remains bounded, observable and authority-neutral."

    doLast {
        val manifest = file("app/src/main/AndroidManifest.xml").readText()
        val application = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5Application.java"
        ).readText()
        val activity = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5BootstrapActivity.java"
        ).readText()
        val presentation = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5BootstrapPresentation.java"
        ).readText()
        val gate = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5StartupReadinessGate.java"
        ).readText()
        val themes = file("app/src/main/res/values/themes.xml").readText()
        val nightThemes = file("app/src/main/res/values-night/themes.xml").readText()

        listOf(
            "FaroV5BootstrapActivity",
            """android:exported="true"""",
            "android.intent.action.MAIN",
            "android.intent.category.LAUNCHER",
            "@style/Theme.FaroV5.Starting"
        ).forEach { required ->
            check(manifest.contains(required)) {
                "S0.A2 launcher primitive missing: $required"
            }
        }
        check(Regex("""<activity\b""").findAll(manifest).count() == 1) {
            "S0.A2 must have exactly one Activity"
        }

        listOf(
            "SplashScreen.installSplashScreen(this)",
            "SPLASH_MAX_HOLD_MILLIS",
            "SystemClock.uptimeMillis() < splashHoldDeadline",
            "addStartupReadinessListener",
            "removeStartupReadinessListener",
            "FaroV5BootstrapPresentation.from(readiness)",
            "setAccessibilityHeading(true)",
            "setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE)",
            "WindowInsets.Type.systemBars()",
            "WindowInsets.Type.displayCutout()"
        ).forEach { required ->
            check(activity.contains(required)) {
                "S0.A2 Activity primitive missing: $required"
            }
        }

        listOf(
            "INITIALIZING",
            "LOCAL_READY",
            "MAINTENANCE_ONLY",
            "bootstrap_maintenance_storage",
            "bootstrap_maintenance_update",
            "bootstrap_maintenance_reset",
            "bootstrap_maintenance_health"
        ).forEach { required ->
            check(presentation.contains(required)) {
                "S0.A2 presentation mapping missing: $required"
            }
        }

        listOf(
            "CopyOnWriteArraySet",
            "addListener",
            "removeListener",
            "notifyListeners(next)",
            "catch (RuntimeException observerFailure)"
        ).forEach { required ->
            check(gate.contains(required)) {
                "S0.A2 readiness observer primitive missing: $required"
            }
        }

        check(application.contains("Executors.newSingleThreadExecutor")) {
            "S0.A2 recovery executor missing"
        }
        check(
            application.indexOf("startupExecutor.execute(") <
                application.indexOf("root.recoverPendingMaintenanceState(")
        ) {
            "S0.A2 canonical recovery must run inside the startup executor"
        }
        check(
            application.indexOf("root.recoverPendingMaintenanceState(") <
                application.indexOf("root.installProcessBridges()")
        ) {
            "S0.A2 must preserve recovery-before-normal-bridges ordering"
        }

        check(themes.contains("""<item name="android:windowLightStatusBar">true</item>""") &&
            themes.contains("""<item name="android:windowLightNavigationBar">true</item>""")) {
            "S1.A5 light bootstrap surface requires dark system-bar icons"
        }
        check(nightThemes.contains("""<item name="android:windowLightStatusBar">false</item>""") &&
            nightThemes.contains("""<item name="android:windowLightNavigationBar">false</item>""")) {
            "S1.A5 dark bootstrap surface requires light system-bar icons"
        }

        listOf(
            "Theme.SplashScreen",
            "windowSplashScreenBackground",
            "windowSplashScreenAnimatedIcon",
            "postSplashScreenTheme"
        ).forEach { required ->
            check(themes.contains(required)) {
                "S0.A2 SplashScreen theme primitive missing: $required"
            }
        }

        listOf(
            "androidx.compose",
            "NavController",
            "startActivity(",
            "SharedPreferences",
            "OkHttp",
            "http://",
            "https://",
            "ar.com.faro.v5.owner.",
            "ar.com.faro.v5.store."
        ).forEach { forbidden ->
            check(!activity.contains(forbidden) && !presentation.contains(forbidden)) {
                "S0.A2 presentation scope violation: $forbidden"
            }
        }

        println(
            "FARO_V5_S0_A2_BOOTSTRAP_PASS " +
                "activity=ONE splash=BOUNDED readiness=OBSERVED writes=AUTHORITY_NEUTRAL " +
                "states=INITIALIZING|LOCAL_READY|MAINTENANCE_ONLY navigation=false"
        )
    }
}

tasks.register("startupProgressPolicyCheck") {
    group = "verification"
    description = "Verifies S0.A3 startup progress is real-phase, bounded and authority-neutral."

    doLast {
        val readiness = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5StartupReadiness.java"
        ).readText()
        val gate = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5StartupReadinessGate.java"
        ).readText()
        val root = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5CompositionRoot.java"
        ).readText()
        val presentation = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5BootstrapPresentation.java"
        ).readText()
        val activity = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5BootstrapActivity.java"
        ).readText()
        val strings = file("app/src/main/res/values/strings.xml").readText()

        val phases =
            listOf(
                "PREPARING_STORAGE",
                "INITIALIZING_MAINTENANCE",
                "RECOVERING_EXTERNAL_EFFECTS",
                "RECOVERING_UPDATE",
                "RECOVERING_RESET",
                "RECOVERING_GENERATION",
                "VERIFYING_LOCAL_DATA",
            )
        phases.forEach { phase ->
            check(readiness.contains(phase)) {
                "S0.A3 startup phase missing: $phase"
            }
            check(root.contains("FaroV5StartupReadiness.StartupPhase.$phase")) {
                "S0.A3 startup phase is not bound to real recovery: $phase"
            }
        }

        check(gate.contains("void initializingPhase(") &&
            gate.contains("publishInitializing(") &&
            gate.contains("materialWriteGate.closeMaterialWriteAdmission()")) {
            "S0.A3 phase publication must remain fail-closed"
        }
        check(
            gate.contains(
                """publishInitializing(
                FaroV5StartupReadiness.StartupPhase.PREPARING_STORAGE,
                FaroV5StartupReadiness.RecoveryKind.GENERIC)"""
            )
        ) {
            "S0.A3 beginRecovery must use the current typed startup/recovery signature"
        }
        check(root.contains("startupReadinessGate.initializingPhase(phase)")) {
            "S0.A3 real recovery step must publish its phase before execution"
        }
        check(presentation.contains("startupBody(") &&
            presentation.contains("readiness.getStartupPhase()") &&
            presentation.contains("readiness.getRecoveryKind()")) {
            "S0.A3 presentation must derive copy from typed startup phase/context"
        }
        listOf(
            "bootstrap_phase_storage",
            "bootstrap_phase_maintenance",
            "bootstrap_phase_external_effects",
            "bootstrap_phase_update",
            "bootstrap_phase_reset",
            "bootstrap_phase_generation",
            "bootstrap_phase_health",
        ).forEach { resource ->
            check(strings.contains(resource)) {
                "S0.A3 human phase copy missing: $resource"
            }
        }
        check(activity.contains("progressView.setIndeterminate(true)")) {
            "S0.A3 must remain indeterminate without measurable unit progress"
        }
        check(!activity.contains("setProgress(") &&
            !presentation.contains("percent") &&
            !presentation.contains("percentage")) {
            "S0.A3 must not invent percentage progress"
        }

        println(
            "FARO_V5_S0_A3_PROGRESS_PASS " +
                "phases=REAL_TYPED progress=INDETERMINATE writes=FAIL_CLOSED " +
                "authority=READINESS_ONLY percentage=false"
        )
    }
}


tasks.register("startupRecoveryPresentationPolicyCheck") {
    group = "verification"
    description = "Verifies S0.A3 recovery meaning is canonical, humanized and presentation-only."

    doLast {
        val readiness = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5StartupReadiness.java"
        ).readText()
        val gate = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5StartupReadinessGate.java"
        ).readText()
        val root = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5CompositionRoot.java"
        ).readText()
        val runtime = file(
            "app/src/main/kotlin/ar/com/faro/v5/app/FaroV5MaintenanceRuntime.kt"
        ).readText()
        val presentation = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5BootstrapPresentation.java"
        ).readText()
        val strings = file("app/src/main/res/values/strings.xml").readText()

        listOf("GENERIC", "MIGRATION", "RESTORE", "RESET", "MULTIPLE").forEach { kind ->
            check(readiness.contains(kind)) {
                "S0.A3 recovery kind missing: $kind"
            }
        }
        check(runtime.contains("journal.pending().map { it.kind }.toSet()")) {
            "S0.A3 recovery meaning must derive from canonical bootstrap journal"
        }
        check(root.contains("pendingBootstrapKinds()") &&
            root.contains("catch (RuntimeException presentationDetailFailure)")) {
            "S0.A3 presentation detail must be best-effort and unable to break recovery"
        }
        check(root.contains("MaintenanceOperationKind.MIGRATION") &&
            root.contains("MaintenanceOperationKind.RESTORE") &&
            root.contains("MaintenanceOperationKind.RESET")) {
            "S0.A3 canonical maintenance kinds are not mapped"
        }
        check(gate.contains("initializingRecovery(") &&
            gate.contains("materialWriteGate.closeMaterialWriteAdmission()")) {
            "S0.A3 recovery context must remain fail-closed presentation metadata"
        }
        check(gate.contains("maintenanceOnly(") &&
            gate.contains("FaroV5StartupReadiness.RecoveryKind recoveryKind")) {
            "S0.A3 failed recovery must preserve human recovery meaning"
        }
        check(root.contains("startupReadinessGate.maintenanceOnly(") &&
            root.contains("recoveryKind")) {
            "S0.A3 failed recovery must carry the derived human recovery context"
        }
        listOf(
            "bootstrap_recovery_migration",
            "bootstrap_recovery_restore",
            "bootstrap_recovery_reset",
            "bootstrap_recovery_multiple",
            "bootstrap_maintenance_migration",
            "bootstrap_maintenance_restore",
            "bootstrap_maintenance_reset_specific",
            "bootstrap_maintenance_multiple",
        ).forEach { resource ->
            check(presentation.contains(resource) && strings.contains(resource)) {
                "S0.A3 human recovery copy missing: $resource"
            }
        }
        check(!presentation.contains("BootstrapPhase.") &&
            !strings.contains("BootstrapPhase.")) {
            "D-433 forbids raw BootstrapPhase exposure in primary UI"
        }
        listOf(
            "SWITCH_PREPARED",
            "HEALTH_VERIFIED",
            "ROLLBACK_PREPARED",
            "ROLLED_BACK_POST_SWITCH",
            "ROLLBACK_VERIFIED",
            "CLEANUP_PENDING",
            "ABORTED_PRE_SWITCH",
        ).forEach { technicalPhase ->
            check(!strings.contains(technicalPhase)) {
                "D-433 forbids raw technical journal phase in visible copy: $technicalPhase"
            }
        }

        println(
            "FARO_V5_S0_A3_RECOVERY_PRESENTATION_PASS " +
                "source=CANONICAL_JOURNAL meaning=HUMANIZED detail=BEST_EFFORT " +
                "writes=FAIL_CLOSED technical_phase_ui=false"
        )
    }
}


tasks.register("maintenanceActionsPolicyCheck") {
    group = "verification"
    description = "Verifies S0.A4 maintenance actions stay safe, humanized and non-destructive."

    doLast {
        val application = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5Application.java"
        ).readText()
        val activity = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5BootstrapActivity.java"
        ).readText()
        val presentation = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5BootstrapPresentation.java"
        ).readText()
        val diagnostics = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5MaintenanceDiagnostics.java"
        ).readText()
        val strings = file("app/src/main/res/values/strings.xml").readText()

        check(application.contains("public boolean retryStartupRecovery()") &&
            application.contains("AtomicBoolean startupRecoveryRunning") &&
            application.contains("recoverPendingMaintenanceState(tracker)")) {
            "S0.A4 retry must reuse canonical startup recovery and suppress duplicate attempts"
        }
        check(activity.contains("faroApplication.retryStartupRecovery()") &&
            activity.contains("bootstrap_retry_action")) {
            "S0.A4 human retry action is not wired"
        }
        check(activity.contains("bootstrap_diagnostics_show") &&
            activity.contains("FaroV5MaintenanceDiagnostics.from(readiness)") &&
            activity.contains("diagnosticsExpanded")) {
            "S0.A4 diagnostics must use progressive disclosure"
        }
        check(presentation.contains("showMaintenanceActions") &&
            presentation.contains("true);")) {
            "S0.A4 maintenance-only action visibility is missing"
        }
        listOf(
            "bootstrap_retry_action",
            "bootstrap_diagnostics_show",
            "bootstrap_diagnostics_hide",
            "bootstrap_diagnostics_title",
            "bootstrap_diagnostics_supporting",
        ).forEach { resource ->
            check(strings.contains(resource)) {
                "S0.A4 humanized action copy missing: $resource"
            }
        }

        listOf(
            "http://",
            "https://",
            "password",
            "secret",
            "token",
            "Authorization",
        ).forEach { forbidden ->
            check(!diagnostics.lowercase().contains(forbidden.lowercase())) {
                "S0.A4 diagnostics must remain local/redacted: $forbidden"
            }
        }

        listOf(
            "executeMaintenanceReset",
            "ResetPlanBuilder",
            "ResetApproval",
            "SemanticBackupRestoreCoordinator",
            "AndroidBiometricReauthentication",
        ).forEach { forbidden ->
            check(!activity.contains(forbidden)) {
                "S0.A4 bootstrap must not expose destructive/unprepared maintenance action: $forbidden"
            }
        }

        println(
            "FARO_V5_S0_A4_MAINTENANCE_ACTIONS_PASS " +
                "retry=CANONICAL_SINGLE_FLIGHT diagnostics=LOCAL_PROGRESSIVE " +
                "standalone_restore=HIDDEN reset=HIDDEN_WITHOUT_FRESH_AUTH"
        )
    }
}


tasks.register("platformAndroidSafetyCheck") {
    group = "verification"
    description = "Verifies the bounded P0.A1 Android platform security envelope."

    doLast {
        val moduleBuild = file("platform/android/build.gradle.kts").readText()
        check(moduleBuild.contains("compileSdk = 37")) {
            ":platform:android must compile against API 37"
        }
        check(moduleBuild.contains("minSdk = 29")) {
            ":platform:android must preserve the Android 10 compatibility floor"
        }
        check(!moduleBuild.contains("org.jetbrains.kotlin.android") && !moduleBuild.contains("kotlin-android")) {
            "AGP 9 built-in Kotlin baseline must not reintroduce legacy kotlin-android"
        }

        val manifest = file("platform/android/src/main/AndroidManifest.xml").readText()
        check(manifest.contains("""android:usesCleartextTraffic="false"""")) {
            "Platform manifest must deny cleartext"
        }
        check(manifest.contains("""android:networkSecurityConfig="@xml/network_security_config"""")) {
            "Platform manifest must bind the canonical Network Security Config"
        }
        listOf("<activity", "<service", "<provider").forEach { component ->
            check(!manifest.contains(component)) {
                "P3.A14 platform boundary must not introduce unrelated Android components: $component"
            }
        }
        check(
            manifest.contains("""android:name=".alarm.FaroExactAlarmReceiver"""") &&
                manifest.contains("""android:exported="false"""")
        ) {
            "P3.A14 exact-alarm receiver must exist and remain non-exported"
        }
        val permissionLines =
            manifest.lineSequence()
                .map(String::trim)
                .filter { it.startsWith("<uses-permission") }
                .toList()
        check(
            permissionLines.size == 3 &&
                permissionLines.any { it.contains("android.permission.POST_NOTIFICATIONS") } &&
                permissionLines.any { it.contains("android.permission.SCHEDULE_EXACT_ALARM") } &&
                permissionLines.any { it.contains("android.permission.REQUEST_INSTALL_PACKAGES") } &&
                permissionLines.none { it.contains("android.permission.USE_EXACT_ALARM") } &&
                permissionLines.none { it.contains("android.permission.RECEIVE_BOOT_COMPLETED") } &&
                permissionLines.none { it.contains("android.permission.INSTALL_PACKAGES") } &&
                permissionLines.none { it.contains("android.permission.DELETE_PACKAGES") }
        ) {
            "Platform permission allowlist drifted: $permissionLines"
        }

        val network = file("platform/android/src/main/res/xml/network_security_config.xml").readText()
        check(network.contains("""<base-config cleartextTrafficPermitted="false">""")) {
            "Network Security Config must deny cleartext globally"
        }
        check(!network.contains("<debug-overrides") && !network.contains("""src="user"""")) {
            "P0.A1 must not trust user CAs or debug overrides by default"
        }

        println("FARO_V5_PLATFORM_ANDROID_P0_A1_PASS compileSdk=37 minSdk=29 cleartext=DENY components=EXACT_ALARM+UPDATE_STATUS permissions=POST_NOTIFICATIONS+SCHEDULE_EXACT_ALARM+REQUEST_INSTALL_PACKAGES")
    }
}


tasks.register("platformAndroidSecretStorePolicyCheck") {
    group = "verification"
    description = "Verifies the P0.A2 device-local Android SecretStore security envelope."

    doLast {
        val moduleBuild = file("platform/android/build.gradle.kts").readText()
        check(moduleBuild.contains("""implementation(project(":control"))""")) {
            ":platform:android must bind only to the canonical :control SecretStore port"
        }

        val sourceFile = file(
            "platform/android/src/main/java/ar/com/faro/v5/platform/android/security/AndroidKeystoreSecretStore.java"
        )
        check(sourceFile.isFile) {
            "Android Keystore SecretStore binding is missing"
        }
        val source = sourceFile.readText()
        listOf(
            "AndroidKeyStore",
            "AES/GCM/NoPadding",
            "getNoBackupFilesDir()",
            "AtomicFile",
            "setRandomizedEncryptionRequired(true)",
            "setKeySize(256)"
        ).forEach { required ->
            check(source.contains(required)) {
                "SecretStore security primitive missing: $required"
            }
        }

        listOf(
            "SharedPreferences",
            "android.util.Log",
            "System.out",
            "System.err",
            "println(",
            "ar.com.faro.v3",
            "ar.com.faro.v4"
        ).forEach { forbidden ->
            check(!source.contains(forbidden)) {
                "SecretStore forbidden pattern: $forbidden"
            }
        }

        println(
            "FARO_V5_PLATFORM_ANDROID_P0_A2_PASS " +
                "keystore=AES_GCM storage=NO_BACKUP atomic=true plaintext_fallback=false"
        )
    }
}


tasks.register("platformAndroidManifestPolicyCheck") {
    group = "verification"
    description = "Verifies explicit Android component export policy for P0.A5."

    doLast {
        val manifestFile = file("platform/android/src/main/AndroidManifest.xml")
        val androidNs = "http://schemas.android.com/apk/res/android"
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val document = factory.newDocumentBuilder().parse(manifestFile)
        val allowlist = file("platform/android/exported-components.allowlist")
            .readLines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()

        val exported = linkedSetOf<String>()
        val componentTags = listOf("activity", "activity-alias", "service", "receiver", "provider")
        componentTags.forEach { tag ->
            val nodes = document.getElementsByTagName(tag)
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as org.w3c.dom.Element
                val name = element.getAttributeNS(androidNs, "name")
                check(name.isNotBlank()) {
                    "Android component <$tag> must declare android:name"
                }

                val exportedValue = element.getAttributeNS(androidNs, "exported")
                check(exportedValue == "true" || exportedValue == "false") {
                    "Android component $name must declare explicit android:exported"
                }

                if (exportedValue == "true") {
                    check(name in allowlist) {
                        "Exported Android component $name is not in the reviewed allowlist"
                    }
                    exported += name
                }
            }
        }

        check(exported == allowlist) {
            "Export allowlist must exactly match exported=true components: manifest=$exported allowlist=$allowlist"
        }

        val manifestText = manifestFile.readText()
        check(!manifestText.contains("""<grant-uri-permission android:pathPrefix="/"""")) {
            "Wildcard provider URI grants are forbidden"
        }

        println(
            "FARO_V5_PLATFORM_ANDROID_P0_A5_PASS " +
                "components_explicit_export=true exported_reviewed=${exported.size}"
        )
    }
}


tasks.register("platformAndroidWorkManagerPolicyCheck") {
    group = "verification"
    description = "Verifies P3.A1 WorkManager projection remains ID-only and authority-neutral."

    doLast {
        val catalog = file("gradle/libs.versions.toml").readText()
        check(catalog.contains("""androidx-work = "2.12.0"""")) {
            "P3.A1 requires the reviewed stable WorkManager 2.12.0 baseline"
        }
        check(
            catalog.contains(
                """androidx-work-runtime = { module = "androidx.work:work-runtime", version.ref = "androidx-work" }"""
            )
        ) {
            "P3.A1 WorkManager runtime alias missing"
        }

        val moduleBuild = file("platform/android/build.gradle.kts").readText()
        check(moduleBuild.contains("implementation(libs.androidx.work.runtime)")) {
            ":platform:android must bind WorkManager through the pinned version catalog alias"
        }

        val sourceFile = file(
            "platform/android/src/main/java/ar/com/faro/v5/platform/android/work/AndroidWorkManagerScheduler.java"
        )
        check(sourceFile.isFile) {
            "P3.A1 AndroidWorkManagerScheduler is missing"
        }
        val source = sourceFile.readText()

        listOf(
            "enqueueUniqueWork",
            "ExistingWorkPolicy.KEEP",
            "enqueueUniquePeriodicWork",
            "ExistingPeriodicWorkPolicy.UPDATE",
            "faro.v5.semantic_id",
            "faro.v5.work_kind",
            "MIN_PERIODIC_MINUTES = 15L",
            "cancelUniqueWork"
        ).forEach { required ->
            check(source.contains(required)) {
                "P3.A1 WorkManager projection primitive missing: $required"
            }
        }

        listOf(
            "Snapshot",
            "CanonicalPayload",
            "ar.com.faro.v5.owner.",
            "ar.com.faro.v5.store.",
            "ar.com.faro.v5.kernel.runtime",
            "ar.com.faro.v5.kernel.apply",
            "AlarmManager",
            "NotificationManager",
            "POST_NOTIFICATIONS",
            "SCHEDULE_EXACT_ALARM",
            "USE_EXACT_ALARM",
            "SharedPreferences",
            "android.util.Log",
            "System.out",
            "System.err"
        ).forEach { forbidden ->
            check(!source.contains(forbidden)) {
                "P3.A1 scheduler must remain authority-neutral and ID-only: $forbidden"
            }
        }

        val executionPort = file(
            "kernel/contracts/src/main/kotlin/ar/com/faro/v5/kernel/contracts/ScheduledWorkExecution.kt"
        )
        check(executionPort.isFile) {
            "P3.A11 ScheduledWorkExecutionPort is missing"
        }
        val executionPortSource = executionPort.readText()
        listOf(
            "ScheduledWorkExecutionPort",
            "ScheduledWorkExecutionResult",
            "SUCCESS",
            "RETRY",
            "FAILURE"
        ).forEach { required ->
            check(executionPortSource.contains(required)) {
                "P3.A11 scheduled-work SPI missing: $required"
            }
        }

        val workerFile = file(
            "platform/android/src/main/java/ar/com/faro/v5/platform/android/work/FaroScheduledWorkWorker.java"
        )
        val factoryFile = file(
            "platform/android/src/main/java/ar/com/faro/v5/platform/android/work/FaroWorkerFactory.java"
        )
        check(workerFile.isFile && factoryFile.isFile) {
            "P3.A11 Worker/WorkerFactory bridge is missing"
        }
        val workerSource = workerFile.readText()
        val factorySource = factoryFile.readText()

        listOf(
            "INPUT_WORK_KIND",
            "INPUT_SEMANTIC_ID",
            "ScheduledWorkExecutionPort",
            "ScheduledWorkExecutionResult",
            "Result.success()",
            "Result.retry()",
            "Result.failure()"
        ).forEach { required ->
            check(workerSource.contains(required)) {
                "P3.A11 worker bridge primitive missing: $required"
            }
        }
        listOf(
            "WorkerFactory",
            "FaroScheduledWorkWorker.class.getName()",
            "return null",
            "ScheduledWorkExecutionPort"
        ).forEach { required ->
            check(factorySource.contains(required)) {
                "P3.A11 WorkerFactory primitive missing: $required"
            }
        }

        listOf(
            "Snapshot",
            "CanonicalPayload",
            "ar.com.faro.v5.owner.",
            "ar.com.faro.v5.store.",
            "ar.com.faro.v5.kernel.runtime",
            "ar.com.faro.v5.kernel.apply",
            "NotificationManager",
            "AlarmManager",
            "SharedPreferences",
            "android.util.Log",
            "System.out",
            "System.err"
        ).forEach { forbidden ->
            check(!workerSource.contains(forbidden) && !factorySource.contains(forbidden)) {
                "P3.A11 worker bridge must remain authority-neutral: $forbidden"
            }
        }

        val manifest = file("platform/android/src/main/AndroidManifest.xml").readText()
        check(
            manifest.contains("SCHEDULE_EXACT_ALARM") &&
                !manifest.contains("USE_EXACT_ALARM") &&
                !manifest.contains("RECEIVE_BOOT_COMPLETED")
        ) {
            "P3.A14 must keep exact scheduling revocable and must not pull boot ownership forward"
        }

        println(
            "FARO_V5_PLATFORM_ANDROID_P3_A1_PASS " +
                "workmanager=2.12.0 unique=true periodic_policy=UPDATE input=IDS_ONLY " +
                "worker_factory=true execution_port=true"
        )
    }
}


tasks.register("platformAndroidNotificationPolicyCheck") {
    group = "verification"
    description = "Verifies P3.A4 notification sink remains a bounded OS projection."

    doLast {
        val manifest = file("platform/android/src/main/AndroidManifest.xml").readText()
        val permissionLines =
            manifest.lineSequence()
                .map(String::trim)
                .filter { it.startsWith("<uses-permission") }
                .toList()
        check(
            permissionLines.size == 2 &&
                permissionLines.any { it.contains("android.permission.POST_NOTIFICATIONS") } &&
                permissionLines.any { it.contains("android.permission.SCHEDULE_EXACT_ALARM") }
        ) {
            "P3.A14 manifest must contain only notification + revocable exact-alarm permissions: $permissionLines"
        }
        check(
            !manifest.contains("USE_EXACT_ALARM") &&
                !manifest.contains("RECEIVE_BOOT_COMPLETED")
        ) {
            "P3.A14 must not use auto-granted exact-alarm policy or claim boot ownership"
        }

        val port = file(
            "delivery/src/main/kotlin/ar/com/faro/v5/delivery/spi/NotificationDeliverySink.kt"
        ).readText()
        listOf(
            "NotificationProjectionRequest",
            "POSTED_TO_OS",
            "CANCELLED",
            "BLOCKED_PERMISSION",
            "FAILED",
            "NotificationDeliverySink",
            "NotificationDeliveryObserver",
            "NotificationProjectionObservation"
        ).forEach { required ->
            check(port.contains(required)) {
                "notification sink port primitive missing: $required"
            }
        }
        listOf(
            "android.",
            "androidx.",
            "NotificationManager",
            "PendingIntent"
        ).forEach { forbidden ->
            check(!port.contains(forbidden)) {
                "delivery notification port must remain platform-neutral: $forbidden"
            }
        }

        val sourceFile = file(
            "platform/android/src/main/java/ar/com/faro/v5/platform/android/notification/AndroidNotificationDeliverySink.java"
        )
        check(sourceFile.isFile) {
            "AndroidNotificationDeliverySink is missing"
        }
        val source = sourceFile.readText()
        listOf(
            "NotificationManager",
            "createNotificationChannel",
            "areNotificationsEnabled",
            "Manifest.permission.POST_NOTIFICATIONS",
            "IMPORTANCE_NONE",
            "BLOCKED_PERMISSION",
            "POSTED_TO_OS",
            "CANCELLED",
            "stableTag",
            "setPublicVersion",
            "VISIBILITY_PRIVATE",
            "NotificationDeliveryObserver",
            "getActiveNotifications",
            "getTag()",
            "getId()",
            "NotificationProjectionObservation.ACTIVE",
            "NotificationProjectionObservation.NOT_ACTIVE",
            "NotificationProjectionObservation.UNKNOWN"
        ).forEach { required ->
            check(source.contains(required)) {
                "P3.A4 notification projection primitive missing: $required"
            }
        }
        listOf(
            "PendingIntent",
            "startActivity(",
            "ar.com.faro.v5.owner.",
            "ar.com.faro.v5.store.",
            "ar.com.faro.v5.kernel.runtime",
            "ar.com.faro.v5.kernel.apply",
            "WorkManager",
            "AlarmManager",
            "SharedPreferences",
            "android.util.Log",
            "System.out",
            "System.err"
        ).forEach { forbidden ->
            check(!source.contains(forbidden)) {
                "P3.A4 notification sink scope violation: $forbidden"
            }
        }

        val resources = file("platform/android/src/main/res/values/strings.xml").readText()
        listOf(
            "faro_notice_channel_name",
            "faro_notification_title",
            "faro_notification_redacted"
        ).forEach { required ->
            check(resources.contains(required)) {
                "P3.A4 notification resource missing: $required"
            }
        }

        println(
            "FARO_V5_PLATFORM_ANDROID_P3_A4_PASS " +
                "permission=POST_NOTIFICATIONS+SCHEDULE_EXACT_ALARM channels=1 stable_identity=true " +
                "sensitive_redaction=true reconcile_observation=true pending_intent=DEFERRED"
        )
    }
}


tasks.register("platformAndroidExactAlarmPolicyCheck") {
    group = "verification"
    description = "Verifies P3.A14 exact-only AlarmManager projection remains revocable, ID-only and authority-neutral."

    doLast {
        val manifest = file("platform/android/src/main/AndroidManifest.xml").readText()
        check(manifest.contains("android.permission.SCHEDULE_EXACT_ALARM")) {
            "P3.A14 requires revocable SCHEDULE_EXACT_ALARM"
        }
        listOf(
            "android.permission.USE_EXACT_ALARM",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "<intent-filter"
        ).forEach { forbidden ->
            check(!manifest.contains(forbidden)) {
                "P3.A14 manifest scope violation: $forbidden"
            }
        }
        check(
            manifest.contains("""android:name=".alarm.FaroExactAlarmReceiver"""") &&
                manifest.contains("""android:exported="false"""")
        ) {
            "P3.A14 exact-alarm receiver must be explicit and non-exported"
        }

        val portFile = file(
            "delivery/src/main/kotlin/ar/com/faro/v5/delivery/spi/WallClockAlarmProjectionPort.kt"
        )
        check(portFile.isFile) {
            "P3.A14 wall-clock alarm projection SPI is missing"
        }
        val port = portFile.readText()
        listOf(
            "WallClockAlarmIdentity",
            "WallClockAlarmPrecision",
            "INEXACT",
            "EXACT_REQUIRED",
            "EXACT_ACCESS_REQUIRED",
            "EXECUTION_BRIDGE_UNAVAILABLE",
            "INEXACT_NOT_HANDLED",
            "WallClockAlarmProjectionPort"
        ).forEach { required ->
            check(port.contains(required)) {
                "P3.A14 alarm SPI primitive missing: $required"
            }
        }
        listOf(
            "android.",
            "androidx.",
            "AlarmManager",
            "PendingIntent"
        ).forEach { forbidden ->
            check(!port.contains(forbidden)) {
                "P3.A14 delivery alarm SPI must remain platform-neutral: $forbidden"
            }
        }

        val adapterFile = file(
            "platform/android/src/main/java/ar/com/faro/v5/platform/android/alarm/AndroidExactAlarmProjection.java"
        )
        val receiverFile = file(
            "platform/android/src/main/java/ar/com/faro/v5/platform/android/alarm/FaroExactAlarmReceiver.java"
        )
        val bridgeFile = file(
            "platform/android/src/main/java/ar/com/faro/v5/platform/android/alarm/FaroExactAlarmExecutionBridge.java"
        )
        check(adapterFile.isFile && receiverFile.isFile && bridgeFile.isFile) {
            "P3.A14 exact-alarm adapter/receiver/bridge is incomplete"
        }

        val adapter = adapterFile.readText()
        listOf(
            "AlarmManager",
            "canScheduleExactAlarms()",
            "setExactAndAllowWhileIdle",
            "AlarmManager.RTC_WAKEUP",
            "PendingIntent.FLAG_IMMUTABLE",
            "PendingIntent.FLAG_NO_CREATE",
            "FaroExactAlarmReceiver.class",
            "WallClockAlarmPrecision.EXACT_REQUIRED",
            "WallClockAlarmProjectionResult.EXACT_ACCESS_REQUIRED",
            "WallClockAlarmProjectionResult.EXECUTION_BRIDGE_UNAVAILABLE",
            "WallClockAlarmProjectionResult.INEXACT_NOT_HANDLED",
            "FaroExactAlarmExecutionBridge.isInstalled()",
            "Uri.Builder"
        ).forEach { required ->
            check(adapter.contains(required)) {
                "P3.A14 exact-alarm adapter primitive missing: $required"
            }
        }
        listOf(
            "setRepeating",
            "setInexactRepeating",
            "USE_EXACT_ALARM",
            "RECEIVE_BOOT_COMPLETED",
            "ar.com.faro.v5.owner.",
            "ar.com.faro.v5.store.",
            "ar.com.faro.v5.kernel.runtime",
            "ar.com.faro.v5.kernel.apply",
            "NotificationManager",
            "SharedPreferences",
            "android.util.Log",
            "System.out",
            "System.err"
        ).forEach { forbidden ->
            check(!adapter.contains(forbidden)) {
                "P3.A14 exact-alarm adapter scope violation: $forbidden"
            }
        }

        val receiver = receiverFile.readText()
        listOf(
            "BroadcastReceiver",
            "goAsync()",
            "WallClockAlarmIdentity",
            "ScheduledWorkExecutionPort",
            "FaroExactAlarmExecutionBridge.currentOrNull()"
        ).forEach { required ->
            check(receiver.contains(required)) {
                "P3.A14 exact-alarm callback primitive missing: $required"
            }
        }
        listOf(
            "CanonicalPayload",
            "Snapshot",
            "ar.com.faro.v5.owner.",
            "ar.com.faro.v5.store.",
            "ar.com.faro.v5.kernel.runtime",
            "ar.com.faro.v5.kernel.apply",
            "NotificationManager",
            "SharedPreferences",
            "android.util.Log",
            "System.out",
            "System.err"
        ).forEach { forbidden ->
            check(!receiver.contains(forbidden)) {
                "P3.A14 exact-alarm receiver must remain ID-only and authority-neutral: $forbidden"
            }
        }

        val bridge = bridgeFile.readText()
        check(
            bridge.contains("AtomicReference<ScheduledWorkExecutionPort>") &&
                bridge.contains("public static boolean isInstalled()")
        ) {
            "P3.A14 process-local bridge must hold only the canonical execution port and expose readiness"
        }

        println(
            "FARO_V5_PLATFORM_ANDROID_P3_A14_PASS " +
                "permission=SCHEDULE_EXACT_ALARM exact_only=true input=IDS_ONLY exported=false " +
                "boot_owner=false use_exact_alarm=false"
        )
    }
}

tasks.register("platformAndroidPortabilityPolicyCheck") {
    group = "verification"
    description = "Verifies the P4.A8 SAF portability adapter remains explicit, bounded and non-authoritative."

    doLast {
        val moduleBuild = file("platform/android/build.gradle.kts").readText()
        check(moduleBuild.contains("""implementation(project(":maintenance"))""")) {
            ":platform:android must implement portability through the canonical :maintenance boundary"
        }

        val adapterFile = file(
            "platform/android/src/main/java/ar/com/faro/v5/platform/android/portability/AndroidPortabilitySafAdapter.java"
        )
        val journalFile = file(
            "maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/PortabilityExternalWrite.kt"
        )
        check(adapterFile.isFile && journalFile.isFile) {
            "P4.A8 SAF adapter or durable external-attempt journal is missing"
        }

        val adapter = adapterFile.readText()
        listOf(
            "Intent.ACTION_CREATE_DOCUMENT",
            "Intent.CATEGORY_OPENABLE",
            "Intent.EXTRA_TITLE",
            "ContentResolver",
            "openFileDescriptor",
            "CancellationSignal",
            "ParcelFileDescriptor.AutoCloseOutputStream",
            "openInputStream",
            "MessageDigest.getInstance(\"SHA-256\")",
            "coordinator.markWriteStarted(source)",
            "coordinator.completeVerifiedSuccess(source)",
            "coordinator.completeUnknown"
        ).forEach { required ->
            check(adapter.contains(required)) {
                "P4.A8 SAF primitive missing: $required"
            }
        }
        listOf(
            "MANAGE_EXTERNAL_STORAGE",
            "WRITE_EXTERNAL_STORAGE",
            "takePersistableUriPermission",
            "startActivityForResult",
            "SharedPreferences",
            "android.util.Log",
            "System.out",
            "System.err",
            "destination.toString()",
            "destination.getPath()",
            "resolver.delete("
        ).forEach { forbidden ->
            check(!adapter.contains(forbidden)) {
                "P4.A8 SAF scope/privacy violation: $forbidden"
            }
        }

        val journal = journalFile.readText()
        listOf(
            "AWAITING_DESTINATION",
            "WRITE_STARTED",
            "EFFECT_UNKNOWN",
            "process_interrupted_after_write_start",
            "MaintenancePathIdentity.sha256Hex(effectIdentity)"
        ).forEach { required ->
            check(journal.contains(required)) {
                "P4.A8 external-attempt recovery primitive missing: $required"
            }
        }
        listOf(
            "android.net.Uri",
            "content://",
            "takePersistableUriPermission",
            "SharedPreferences"
        ).forEach { forbidden ->
            check(!journal.contains(forbidden)) {
                "P4.A8 journal must not persist Android destination authority: $forbidden"
            }
        }

        println(
            "FARO_V5_PLATFORM_ANDROID_P4_A8_PASS " +
                "saf=create_document uri_persisted=false verify=bytes+sha256 " +
                "picker_cancel=CANCELLED interrupted_write=EFFECT_UNKNOWN blind_replay=false"
        )
    }
}

tasks.register("maintenanceResetPolicyCheck") {
    group = "verification"
    description = "Verifies the P4.A9 reset lifecycle remains generation-based, crash-safe and platform-neutral."

    doLast {
        val resetFile = file(
            "maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/ResetLifecycle.kt"
        )
        val bootstrapFile = file(
            "maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/BootstrapLifecycle.kt"
        )
        val layoutFile = file(
            "maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/FileStorageGenerationLayout.kt"
        )
        check(resetFile.isFile && bootstrapFile.isFile && layoutFile.isFile) {
            "P4.A9 reset lifecycle files are incomplete"
        }

        val reset = resetFile.readText()
        listOf(
            "ResetInventoryPolicy",
            "ResetPlanFingerprint",
            "ResetAttemptStore",
            "FileResetAttemptStore",
            "ResetTargetGenerationPort",
            "GenerationRuntimeActivationPort",
            "ResetVaultReconciliationPort",
            "ResetOsReconciliationPort",
            "MaintenanceOperationKind.RESET",
            "BootstrapPhase.CLEANUP_PENDING",
            "generationSwitch.switch",
            "generationSwitch.recover",
            "generationLayout.deleteGeneration",
            "markMaterialWritesAccepted",
            "ResetBlockReason.APPROVAL_MISMATCH",
            "BootstrapPhase.ABORTED_PRE_SWITCH",
            "journal.read(attempt.operationId)?.phase"
        ).forEach { required ->
            check(reset.contains(required)) {
                "P4.A9 reset primitive missing: $required"
            }
        }
        listOf(
            "clearApplicationUserData",
            "import android.",
            "import androidx.",
            "SecretStore",
            "WorkManager",
            "AlarmManager",
            "NotificationManager",
            "SharedPreferences",
            "System.out",
            "System.err",
            "android.util.Log"
        ).forEach { forbidden ->
            check(!reset.contains(forbidden)) {
                "P4.A9 reset scope/platform violation: $forbidden"
            }
        }

        val bootstrap = bootstrapFile.readText()
        check(bootstrap.contains("entry.kind != MaintenanceOperationKind.RESET")) {
            "P4.A9 RESET must never auto-reactivate the previous generation"
        }

        val layout = layoutFile.readText()
        listOf(
            "fun deleteGeneration(",
            "LinkOption.NOFOLLOW_LINKS",
            "Files.walk(directory)",
            "normalized.startsWith(generationsRoot)",
            "Files.deleteIfExists(path)"
        ).forEach { required ->
            check(layout.contains(required)) {
                "P4.A9 safe generation cleanup primitive missing: $required"
            }
        }

        println(
            "FARO_V5_MAINTENANCE_P4_A9_PASS " +
                "reset=STAGED_GENERATION runtime_health=REQUIRED old_auto_reactivate=false " +
                "cleanup=VAULT+OS+OLD_GENERATION crash_resume=true clear_app_data=false"
        )
    }
}

tasks.register("maintenanceParentAuditPolicyCheck") {
    group = "verification"
    description = "Verifies P4 parent activation, quiescence, backup SAF and generation-aware app invariants."

    doLast {
        val activation = file(
            "maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/ValidatedGenerationActivation.kt"
        ).readText()
        val bootstrap = file(
            "maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/BootstrapLifecycle.kt"
        ).readText()
        val journal = file(
            "maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/FileBootstrapJournal.kt"
        ).readText()
        val quiescence = file(
            "maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/MaterialWriteQuiescence.kt"
        ).readText()
        val writeGate = file(
            "kernel/contracts/src/main/kotlin/ar/com/faro/v5/kernel/contracts/MaterialWriteGate.kt"
        ).readText()
        val backupAdapter = file(
            "platform/android/src/main/java/ar/com/faro/v5/platform/android/backup/AndroidBackupSafAdapter.java"
        ).readText()
        val backupStaging = file(
            "maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/PortableBackupArtifact.kt"
        ).readText()
        val backupAttempt = file(
            "maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/BackupExternalWrite.kt"
        ).readText()
        val generationAuthority = file(
            "maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/GenerationAuthorityStoreGraph.kt"
        ).readText()
        val generationLocator = file(
            "maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/ActiveGenerationStorageLocator.kt"
        ).readText()
        val appGenerationGraph = file(
            "app/src/main/kotlin/ar/com/faro/v5/app/FaroV5GenerationStoreGraph.kt"
        ).readText()
        val appRoot = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5CompositionRoot.java"
        ).readText()
        val appRuntime = file(
            "app/src/main/kotlin/ar/com/faro/v5/app/FaroV5MaintenanceRuntime.kt"
        ).readText()
        val resetVaultAdapter = file(
            "platform/android/src/main/java/ar/com/faro/v5/platform/android/reset/AndroidResetVaultReconciliation.java"
        ).readText()
        val resetOsAdapter = file(
            "platform/android/src/main/java/ar/com/faro/v5/platform/android/reset/AndroidResetOsReconciliation.java"
        ).readText()
        val secretStore = file(
            "platform/android/src/main/java/ar/com/faro/v5/platform/android/security/AndroidKeystoreSecretStore.java"
        ).readText()
        val application = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5Application.java"
        ).readText()

        listOf(
            "BootstrapPhase.SWITCH_PREPARED",
            "generationSwitch.switch",
            "runtime.activateAndVerify",
            "replayFence.install",
            "projections.reconcile",
            "markMaterialWritesAccepted",
            "rollbackToPreviousIfEligible",
            "BootstrapPhase.ROLLBACK_VERIFIED",
            "recoverPending()"
        ).forEach { required ->
            check(activation.contains(required)) {
                "P4 activation convergence primitive missing: $required"
            }
        }
        listOf(
            "ROLLBACK_PREPARED",
            "ROLLED_BACK_POST_SWITCH",
            "ROLLBACK_VERIFIED",
            "entry.kind != MaintenanceOperationKind.RESET"
        ).forEach { required ->
            check(bootstrap.contains(required)) {
                "P4 rollback/reset invariant missing: $required"
            }
        }
        check(journal.contains("override fun pending()")) {
            "P4 startup must discover pending bootstrap operations"
        }
        check(
            journal.contains("BootstrapPhase.ROLLBACK_VERIFIED") &&
                !journal.substringAfter(".filterNot").substringBefore(".sortedBy")
                    .contains("BootstrapPhase.ROLLED_BACK_POST_SWITCH")
        ) {
            "P4 unverified rollback must remain visible to startup recovery"
        }

        listOf(
            "SingleProcessMaterialWriteGate",
            "ReentrantReadWriteLock(true)",
            "withMaterialWrite",
            "withQuiescedMaterialWrites",
            "materialWriteAdmissionOpen",
            "closeMaterialWriteAdmission",
            "openMaterialWriteAdmission"
        ).forEach { required ->
            check(writeGate.contains(required)) {
                "P4 material-write gate primitive missing: $required"
            }
        }
        check(quiescence.contains("MaterialWriteGateQuiescence")) {
            "Maintenance quiescence must use the shared material-write gate"
        }

        listOf(
            "Intent.ACTION_CREATE_DOCUMENT",
            "Intent.ACTION_OPEN_DOCUMENT",
            "ContentResolver",
            "openFileDescriptor",
            "openInputStream",
            "stageSelectedRestoreArchive",
            "completeVerifiedSuccess",
            "completeUnknown"
        ).forEach { required ->
            check(backupAdapter.contains(required)) {
                "P4 backup SAF primitive missing: $required"
            }
        }
        listOf(
            "takePersistableUriPermission",
            "MANAGE_EXTERNAL_STORAGE",
            "WRITE_EXTERNAL_STORAGE",
            "startActivityForResult",
            "SharedPreferences",
            "System.out",
            "System.err",
            "android.util.Log"
        ).forEach { forbidden ->
            check(!backupAdapter.contains(forbidden)) {
                "P4 backup SAF scope/privacy violation: $forbidden"
            }
        }
        listOf(
            "ATOMIC_MOVE",
            "MAX_ARCHIVE_BYTES",
            "cleanupInterruptedTemps",
            "verifyLocked"
        ).forEach { required ->
            check(backupStaging.contains(required)) {
                "P4 backup private staging primitive missing: $required"
            }
        }
        listOf(
            "WRITE_STARTED",
            "EFFECT_UNKNOWN",
            "process_interrupted_after_write_start"
        ).forEach { required ->
            check(backupAttempt.contains(required)) {
                "P4 backup external-effect recovery primitive missing: $required"
            }
        }

        listOf(
            "const val REALITY = \"reality.db\"",
            "const val COMMERCIAL_EVIDENCE = \"commercial-evidence.db\"",
            "const val WEB_EVIDENCE = \"web-evidence.db\"",
            "const val AUXILIARY = \"auxiliary.db\"",
            "val ALL: List<String>",
            "ALL.forEach",
            "GenerationStoreFiles.requireMaterialized",
            "GenerationAuthorityStoreGraph",
            "openExisting",
            "EmptyGenerationResetTargetPort",
            "verifyFreshForReset",
        ).forEach { required ->
            check(generationAuthority.contains(required)) {
                "P4 canonical generation authority graph primitive missing: $required"
            }
        }
        listOf(
            "GenerationStoreFiles.requireMaterialized",
            "GenerationStoreFiles.REALITY",
            "GenerationStoreFiles.COMMERCIAL_EVIDENCE",
            "GenerationStoreFiles.WEB_EVIDENCE",
            "GenerationStoreFiles.AUXILIARY",
            "RealityStore",
            "CommercialEvidenceStore",
            "WebEvidenceStore",
            "AuxiliaryDeliveryStore",
            "verifyHealthy",
        ).forEach { required ->
            check(appGenerationGraph.contains(required)) {
                "P4 app generation graph must use the canonical four-store layout: $required"
            }
        }
        listOf(
            "recoverInterruptedInitialBootstrap",
            "expectedDirectory",
            "requireRecoverableIncompleteInitialGeneration",
            "requireOnlyInitialGenerationExists",
            "requireNoMaintenanceStateBeyondActivePointerTransport",
            "requirePristineInitialBootstrapStorage",
            "GenerationAuthorityStoreGraph.open(staged)",
            "verifyFreshForReset"
        ).forEach { required ->
            check(generationLocator.contains(required)) {
                "P4 initial generation bootstrap fail-closed primitive missing: $required"
            }
        }

        listOf(
            "ActiveGenerationStorageLocator",
            "activeGenerationDirectory()",
            "SingleProcessMaterialWriteGate",
            "supplyMaterialWrite",
            "FaroV5MaintenanceRuntime",
            "recoverPendingMaintenanceState",
            "closeMaterialWriteAdmission",
            "openMaterialWriteAdmission",
            "maintenanceAdmissionLock",
            "beginInteractiveMaintenanceOperation",
            "requireMaintenanceRecovery",
            "completeMaintenanceOperation",
            "resetResultAllowsMaterialWrites",
            "activationResultAllowsMaterialWrites",
            "activateAndVerifyGeneration"
        ).forEach { required ->
            check(appRoot.contains(required)) {
                "P4 app generation/quiescence primitive missing: $required"
            }
        }
        check(!appRoot.contains("faro-v5-auxiliary.db")) {
            "P4 app must not reopen a fixed auxiliary DB outside active StorageGeneration"
        }
        listOf(
            "ValidatedGenerationActivationCoordinator",
            "MaterialWriteGateQuiescence",
            "FileGenerationReplayFence",
            "BackupExternalEffectCoordinator",
            "PortabilityExternalEffectCoordinator",
            "ResetLifecycleCoordinator",
            "runtime = runtimeActivation",
            "AndroidResetVaultReconciliation",
            "AndroidResetOsReconciliation",
            "reconcileInterruptedExternalEffects",
            "recoverPendingResetsOrThrow",
            "executeReset",
            "recoverPendingActivations"
        ).forEach { required ->
            check(appRuntime.contains(required)) {
                "P4 app Maintenance runtime primitive missing: $required"
            }
        }
        listOf(
            "AndroidResetVaultReconciliation",
            "deleteAllFaroSecrets"
        ).forEach { required ->
            check(resetVaultAdapter.contains(required)) {
                "P4 reset Vault adapter primitive missing: $required"
            }
        }
        listOf(
            "cancelAllWorkByTag(AndroidWorkManagerScheduler.ROOT_TAG)",
            "AndroidNotificationDeliverySink.isFaroDeliveryTag",
            "getActiveNotifications"
        ).forEach { required ->
            check(resetOsAdapter.contains(required)) {
                "P4 reset OS adapter primitive missing: $required"
            }
        }
        listOf(
            "deleteNotificationChannel",
            "cancelAll()",
            "clearApplicationUserData",
            "AndroidExactAlarmProjection"
        ).forEach { forbidden ->
            check(!resetOsAdapter.contains(forbidden)) {
                "P4 reset OS adapter exceeds reviewed projection scope: $forbidden"
            }
        }
        listOf(
            "KEY_ALIAS_PREFIX",
            "faro-v5-secrets",
            "deleteAllFaroSecrets"
        ).forEach { required ->
            check(secretStore.contains(required)) {
                "P4 reset secret namespace cleanup primitive missing: $required"
            }
        }

        check(application.contains("root.recoverPendingMaintenanceState(")) {
            "P4/S0 Application startup must recover all pending Maintenance state"
        }

        println(
            "FARO_V5_MAINTENANCE_P4_PARENT_AUDIT_PASS " +
                "activation=VALIDATED_TO_SWITCH rollback=CRASH_SAFE_VERIFIED pending_scan=true " +
                "quiescence=SHARED_GATE write_admission=FAIL_CLOSED maintenance_admission=SINGLE_DEBT backup_saf=INGRESS+EGRESS " +
                "store_graph=CANONICAL_4 store_files=FAIL_CLOSED " +
                "bootstrap=PRISTINE_CRASH_RECOVERABLE reset_recovery=WIRED " +
                "external_effect_recovery=WIRED app_generation_aware=true"
        )
    }
}



tasks.register("startupOnboardingPolicyCheck") {
    group = "verification"
    description = "Verifies S0.A5 onboarding stays post-LOCAL_READY, deferable and permission/provider-free."

    doLast {
        val application = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5Application.java"
        ).readText()
        val root = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5CompositionRoot.java"
        ).readText()
        val activity = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5BootstrapActivity.java"
        ).readText()
        val strings = file("app/src/main/res/values/strings.xml").readText()
        val onboarding = file(
            "control/src/main/kotlin/ar/com/faro/v5/control/OnboardingControl.kt"
        ).readText()
        val store = file(
            "control/src/main/kotlin/ar/com/faro/v5/control/OnboardingPreferenceStore.kt"
        ).readText()
        val executor = file(
            "control/src/main/kotlin/ar/com/faro/v5/control/OnboardingControlExecutor.kt"
        ).readText()
        val facade = file(
            "control/src/main/kotlin/ar/com/faro/v5/control/OnboardingControlFacade.kt"
        ).readText()

        listOf(
            "onboarding.state.read",
            "onboarding.defer",
            "onboarding.complete",
            "PENDING",
            "DEFERRED",
            "COMPLETED",
            "shouldPresentAutomatically"
        ).forEach { required ->
            check(onboarding.contains(required)) {
                "S0.A5 onboarding contract/lifecycle primitive missing: $required"
            }
        }

        val renderSlice =
            activity.substringAfter(
                "private void render(FaroV5StartupReadiness readiness)"
            ).substringBefore(
                "private void renderBootstrap("
            )
        check(renderSlice.contains("if (!readiness.isLocalReady())") &&
            renderSlice.contains("return;") &&
            renderSlice.contains("renderLocalReady(readiness);") &&
            renderSlice.indexOf("if (!readiness.isLocalReady())") <
                renderSlice.indexOf("renderLocalReady(readiness);")) {
            "S0.A5 onboarding must remain unreachable until LOCAL_READY"
        }
        check(executor.contains("PreparedOperation") &&
            executor.contains("preparedOperation.contract != canonicalContract") &&
            executor.contains("OperationExecutionPort")) {
            "S0.A5 onboarding executor must validate PreparedOperation against canonical contract"
        }
        check(facade.contains("OnboardingMutationExecutor.prepared(") &&
            facade.contains("executor.execute(prepared)")) {
            "S0.A5 onboarding mutations must pass through PreparedOperation + canonical executor"
        }
        check(!facade.contains("store.mutate(")) {
            "S0.A5 facade must not bypass canonical onboarding executor"
        }
        check(store.contains("internal class OnboardingPreferenceStore")) {
            "S0.A5 onboarding store must remain internal to :control"
        }
        check(activity.contains("faroApplication.readOnboardingState(") &&
            activity.contains("faroApplication.deferOnboarding(") &&
            activity.contains("faroApplication.completeOnboarding(")) {
            "S0.A5 Activity must use the canonical onboarding-control facade"
        }
        check(root.contains("""resolve("onboarding.preferences_pb")""") &&
            root.contains("activeGenerationDirectory()")) {
            "S0.A5 onboarding state must remain generation-scoped"
        }
        check(application.contains("controlExecutor.execute(")) {
            "S0.A5 onboarding control must stay off the UI thread"
        }

        listOf(
            "onboarding_welcome_title",
            "onboarding_start_action",
            "onboarding_later_action",
            "onboarding_unavailable_supporting"
        ).forEach { required ->
            check(strings.contains(required)) {
                "S0.A5 human onboarding copy missing: $required"
            }
        }

        listOf(
            "requestPermissions(",
            "requestPermissions",
            "POST_NOTIFICATIONS",
            "RECORD_AUDIO",
            "BiometricPrompt",
            "OkHttp",
            "http://",
            "https://",
            "ModelProfile",
            "Groq",
            "displayName",
            "Tu nombre"
        ).forEach { forbidden ->
            check(!activity.contains(forbidden) &&
                !onboarding.contains(forbidden) &&
                !store.contains(forbidden)) {
                "S0.A5 onboarding wall/scope violation: $forbidden"
            }
        }

        check(store.contains("dataStore.data.first()") &&
            store.contains("updateData")) {
            "S0.A5 onboarding control persistence primitives missing"
        }

        println(
            "FARO_V5_S0_A5_ONBOARDING_PASS " +
                "entry=POST_LOCAL_READY lifecycle=VERSIONED_DEFERABLE " +
                "permissions=JIT_OUTSIDE_ONBOARDING provider_wall=false " +
                "profile_gate=false failure=NON_BLOCKING"
        )
    }
}


tasks.register("startupLifecycleCertificationPolicyCheck") {
    group = "verification"
    description = "Verifies S0.A6 lifecycle certification remains authority-neutral and uses real process-death evidence."

    doLast {
        val application = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5Application.java"
        ).readText()
        val root = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5CompositionRoot.java"
        ).readText()
        val activity = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5BootstrapActivity.java"
        ).readText()
        val readinessGate = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5StartupReadinessGate.java"
        ).readText()
        val instrumentation = file(
            "app/src/androidTest/java/ar/com/faro/v5/app/FaroV5S0LifecycleInstrumentationTest.java"
        ).readText()
        val physicalScript = file("../scripts/v5/faro-v5-s0-t4-physical").readText()

        val lifecycleSlice =
            activity.substringAfter("protected void onCreate(Bundle savedInstanceState)")
                .substringBefore("private View createContent()")

        listOf(
            "executeMaintenanceReset",
            "activateValidatedMaintenanceGeneration",
            "deferOnboarding(",
            "completeOnboarding(",
            "ar.com.faro.v5.owner.",
            "ar.com.faro.v5.store.",
            "ar.com.faro.v5.kernel.apply",
            "startActivity("
        ).forEach { forbidden ->
            check(!lifecycleSlice.contains(forbidden)) {
                "S0.A6 Activity lifecycle must remain authority-neutral: $forbidden"
            }
        }

        check(application.contains("AtomicBoolean startupRecoveryRunning") &&
            application.contains("root.recoverPendingMaintenanceState(tracker)") &&
            application.contains("if (!readiness.isLocalReady())")) {
            "S0.A6 startup must preserve canonical single-flight recovery/readiness gating"
        }
        check(root.contains("catch (Exception failure)") &&
            root.contains("startupReadinessGate.maintenanceOnly(")) {
            "S0.A6 failed recovery must converge to typed MAINTENANCE_ONLY"
        }
        val maintenanceGateSlice =
            readinessGate.substringAfter("void maintenanceOnly(")
                .substringBefore("void localReady()")
        check(maintenanceGateSlice.contains("materialWriteGate.closeMaterialWriteAdmission()")) {
            "S0.A6 MAINTENANCE_ONLY must close material write admission"
        }

        listOf(application, root, activity).forEach { source ->
            listOf("ar.com.faro.v4", "app-v4", "FaroV4").forEach { forbidden ->
                check(!source.contains(forbidden)) {
                    "S0.A6 V4 fallback is forbidden: $forbidden"
                }
            }
        }

        listOf(
            "ActivityScenario.launch",
            "scenario.recreate()",
            "executeStartupRecoveryStep",
            "MAINTENANCE_ONLY",
            "retryStartupRecovery()",
            "supplyMaterialWrite"
        ).forEach { required ->
            check(instrumentation.contains(required)) {
                "S0.A6 instrumentation primitive missing: $required"
            }
        }

        listOf(
            "FARO S0 TEST",
            "termux-setup-storage",
            ":app:assembleDebug",
            "WAIT_HUMAN_PHYSICAL",
            "Forzar detención",
            "Decime verde si los 5 pasos pasan"
        ).forEach { required ->
            check(physicalScript.contains(required)) {
                "S0.A6 human physical prep primitive missing: $required"
            }
        }
        listOf(
            "adb ",
            "adb -s",
            "am force-stop",
            "uiautomator"
        ).forEach { forbidden ->
            check(!physicalScript.contains(forbidden)) {
                "S0.A6 T4 human path must not depend on ADB/UI automation: $forbidden"
            }
        }

        println(
            "FARO_V5_S0_A6_LIFECYCLE_PASS " +
                "recreation=AUTHORITY_NEUTRAL failure=MAINTENANCE_FAIL_CLOSED " +
                "process_death=HUMAN_FORCE_STOP v4_fallback=false adb_t4=false"
        )
    }
}


tasks.register("s1A5HostIntegrationPolicyCheck") {
    group = "verification"
    description = "Verifies S1.A5 keeps the S0-to-Surface handoff single-host, readiness-gated and authority-neutral."

    doLast {
        val catalog = file("gradle/libs.versions.toml").readText()
        val appBuild = file("app/build.gradle.kts").readText()
        val surfaceBuild = file("surface/build.gradle.kts").readText()
        val manifest = file("app/src/main/AndroidManifest.xml").readText()
        val activity = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5BootstrapActivity.java"
        ).readText()
        val policy = file(
            "app/src/main/java/ar/com/faro/v5/app/FaroV5HostSurfacePolicy.java"
        ).readText()
        val host = file(
            "surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5SurfaceHost.kt"
        ).readText()
        val colors = file("app/src/main/res/values/colors.xml").readText()
        val nightColors = file("app/src/main/res/values-night/colors.xml").readText()
        val themes = file("app/src/main/res/values/themes.xml").readText()
        val nightThemes = file("app/src/main/res/values-night/themes.xml").readText()

        listOf(
            """androidx-activity = "1.13.0"""",
            """androidx-activity = { module = "androidx.activity:activity"""",
            """androidx-activity-compose = { module = "androidx.activity:activity-compose"""",
        ).forEach { required ->
            check(catalog.contains(required)) {
                "S1.A5 stable Activity dependency missing: $required"
            }
        }
        listOf("1.14.0-alpha", "1.14.0-beta", "activity-compose:1.14").forEach { forbidden ->
            check(!catalog.contains(forbidden)) {
                "S1.A5 must not introduce pre-release Activity: $forbidden"
            }
        }

        listOf(
            """implementation(project(":surface"))""",
            "implementation(libs.androidx.activity)",
        ).forEach { required ->
            check(appBuild.contains(required)) {
                "S1.A5 app host dependency missing: $required"
            }
        }
        check(surfaceBuild.contains("implementation(libs.androidx.activity.compose)")) {
            "S1.A5 Surface host bridge requires Activity Compose"
        }

        check(Regex("""<activity\b""").findAll(manifest).count() == 1) {
            "S1.A5 must preserve one launcher Activity"
        }
        listOf(
            "FaroV5BootstrapActivity",
            "android.intent.action.MAIN",
            "android.intent.category.LAUNCHER",
        ).forEach { required ->
            check(manifest.contains(required)) {
                "S1.A5 launcher contract missing: $required"
            }
        }

        listOf(
            "extends ComponentActivity",
            "faroApplication.readActiveWorkspacePort()",
            "faroApplication.readActiveTimelinePort()",
            "FaroV5HostSurfacePolicy.resolve(readiness, gate)",
            "if (!readiness.isLocalReady())",
            "onboardingEpoch",
            "acceptOnboardingCallback",
            "callbackEpoch == onboardingEpoch",
            "faroApplication.getStartupReadiness().isLocalReady()",
            "OnboardingLoadState.UNAVAILABLE",
            "stylePrimaryButton",
            "styleSecondaryButton",
            "setMinHeight(dp(48))",
        ).forEach { required ->
            check(activity.contains(required)) {
                "S1.A5 host/lifecycle invariant missing: $required"
            }
        }
        check(activity.split("FaroV5SurfaceHost.install(this)").size - 1 == 1) {
            "S1.A5 shell must have one bounded installation point"
        }

        listOf(
            "if (!readiness.isLocalReady())",
            "case UNRESOLVED:",
            "return Surface.BOOTSTRAP",
            "case PRESENT:",
            "return Surface.ONBOARDING",
            "case CLEAR:",
            "return Surface.SHELL",
        ).forEach { required ->
            check(policy.contains(required)) {
                "S1.A5 pure host policy invariant missing: $required"
            }
        }

        listOf(
            "activity.setContent",
            "FaroV5Theme",
            "FaroV5AdaptiveShell",
        ).forEach { required ->
            check(host.contains(required)) {
                "S1.A5 Surface bridge invariant missing: $required"
            }
        }

        listOf(
            "startActivity(",
            "ar.com.faro.v4",
            "app-v4",
            "ar.com.faro.v5.owner.",
            "ar.com.faro.v5.store.",
            "SQLite",
            "OkHttp",
            "http://",
            "https://",
        ).forEach { forbidden ->
            check(!activity.contains(forbidden) &&
                !policy.contains(forbidden) &&
                !host.contains(forbidden)) {
                "S1.A5 host must remain presentation-only: $forbidden"
            }
        }

        listOf("#F7F9FC", "#0B1324").forEach { required ->
            check(colors.contains(required)) {
                "S1.A5 light bootstrap palette drift: $required"
            }
        }
        listOf("#0B1324", "#F7F9FC", "#F6C85F").forEach { required ->
            check(nightColors.contains(required)) {
                "S1.A5 dark bootstrap palette drift: $required"
            }
        }
        check(themes.contains("""android:windowLightStatusBar">true""") &&
            themes.contains("""android:windowLightNavigationBar">true""")) {
            "S1.A5 light bootstrap system-bar contrast missing"
        }
        check(nightThemes.contains("""android:windowLightStatusBar">false""") &&
            nightThemes.contains("""android:windowLightNavigationBar">false""")) {
            "S1.A5 dark bootstrap system-bar contrast missing"
        }

        println(
            "FARO_V5_S1_A5_HOST_PASS " +
                "launcher=ONE host=COMPONENT_ACTIVITY shell=POST_LOCAL_READY " +
                "onboarding=NONBLOCKING_AFTER_FAILURE stale_callbacks=FENCED " +
                "surface=COMPOSE authority=UNCHANGED"
        )
    }
}
