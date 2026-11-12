# X06 — cuatro owners simultáneos Android, B1–B5 delta
Fecha 2026-11-12 AR. Unidad propuesta S3_A5_7_X06_FOUR_OWNER_SIMULTANEOUS_ANDROID.
Estado PREAUDITED_QUEUED, NO READY hasta cierre B7 X12 y guard documental. No código nuevo autorizado por este archivo.

## B1
Matriz S3_A5_7_ACCEPTANCE_MATRIX_20261112.md registra brecha exacta: X06 Workspace API35 2/2 (42627471693, e0978468015ab4c794272249b10d4f00dfe47a59) y tres owners Reality8 API35 2/2 (43151250329, 67bee2004800c2f81bbca7fe268d0f50d7228a92) están certificados por separado. Falta una única consulta con cuatro namespaces simultáneos desde la misma Activity/Session/schema8, y correspondencia de los cuatro resultados UI. No repetir apertura de cada detalle, lifecycle, ABA ni truncación ya certificados.
Inspeccionados test X06ThreeOwnerSearchUiTest, Activity.openLocalChat, Authority.open, WorkspaceSearchReadSource NAMESPACE workspace.conversations y SearchSurface Ready LazyColumn/TextButton.
El store Workspace se confirma por USER persistido desde compositor real, Reality8 por transacción oficial de Finance/Objective/Matter en fixture privada antes de abrir Activity. SearchPort real del host y una consulta con token común deben devolver cuatro namespaces exactamente, COMPLETE, sin IDs/importes ni Apply.
## B2
Reusar upgradePlanning8AfterExplicitForegroundRequest, graph canónico abierto con puntero completo, SingleProcessMaterialWriteGate y puertos oficiales. Cerrar graph de seed antes del gesto nativo; la Activity conserva cinco stores/única Session. Reusar patrón de envío con waitUntil editor vacío+USER visible de pruebas X03 y A3.
V4.4 solo referencia de experiencia búsqueda/drawer documentada en S3_A5_X01_X12_B1_B5_20261109.md; no es owner Reality8 ni autoridad V5. No copiar código/DB V4.4.
## B3
Primarias consultadas 2026-11-12:
https://www.sqlite.org/isolation.html — conexiones separadas ven cambios confirmados; no simular lector multiowner con transacción abierta de seed. Cerrar owner de preparación antes de abrir lector real. REFERENCIA SQLite public-domain, sin nueva dependencia.
https://developer.android.com/develop/ui/compose/testing/synchronization — referencia de sincronización ya usada en X12 B3: esperar estado observable, no sleeps para “asegurar” commit. AndroidX existente Apache2.0, sin coste añadido.
A USAR: solo un test nuevo que integra las cuatro fuentes en una consulta y obliga cuatro filas semánticas de origen en UI real, usando performScrollTo si LazyColumn recicla filas. B DESCARTAR: suma documental de 2+2 suites como prueba de concurrencia; C DESCARTAR: fake SearchPage, DTO hit o scaffold inyectado; D DESCARTAR: reejecutar suites históricas o añadir índice/owner/runtime nuevo.
## B4
Rutas nuevas propuestas:
1. v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57X06FourOwnerSearchUiTest.kt
2. .github/workflows/faro-v5-s3-a5-7-x06-four-owner-search-android-directed.yml
Un método x06SingleQueryShowsFourCanonicalOwnersInReality8.
Seed private DEV (nunca G15) con schema8 real y tres filas Reality oficiales token FourCanon. Native Chat foreground; un USER FourCanon Workspace desde editor real, esperar aceptación/editor vacío. Comprobar schema8 en Session. Una consulta real SearchPort: COMPLETE, exactamente4 hits/4 namespaces, sin duplicados, mensajes e importes privados ausentes. UI Buscar Role.Button (no Tab del drawer); consulta FourCanon y verificar cobertura completa, cuatro fuentes con etiqueta exacta mediante LazyColumn scroll semántico, sin depender de que todas quepan simultáneamente en viewport. Caso simultáneo es una página/consulta, no cuatro búsquedas separadas.
API35 offline; compilar app+test, identidades/permisos/manifiesto/firma/hash/source; instrumentación OK(1 test), START1+END0 una vez, XML1/0/0/0 y artifact. Independent B7 exacto después de CI. Ningún cambio runtime/Gradle/catálogo.
## B5
Cero seed mientras Session abierta; seed en root test-only y graph cerrado, único writer global CAS de repo. No elegir primer match ambiguo; Role.Button para Buscar. No tratar PARTIAL como COMPLETE, no omitir fuente ausente, no aumentar presupuesto ni truncar asserts. Garantizar1 hit de Workspace (única conversación) +1 por cada owner; no comparar solo count4 sin namespaces. No exponer source IDs/importes. Cuatro fuentes distintas pueden requerir scroll: exigir assertIsDisplayed al recorrer cada una. No volver a probar apertura de detalle/lifecycle/ACL ya PASS, no certificar cuatro owners transaccionalmente simultáneos: son snapshot bounded por fuente y host.
Si fixture o UI exige hook/producción -> STOP NEEDS_PREP; jamás ampliar las dos rutas. No Internet, Groq, datos usuario, G15, main/V4.4/S2, V2 productiva/S4/Auto/Noche. Permiso individual independiente solo después de X12 B7 y guard; no heredar READY X12.
