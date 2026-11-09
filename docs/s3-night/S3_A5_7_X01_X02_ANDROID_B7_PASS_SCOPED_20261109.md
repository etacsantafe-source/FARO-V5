# FARO V5 — S3.A5.7 X01/X02 Android Activity/Avisos — B7 PASS_SCOPED 3/3
Fecha AR 2026-11-09. Unidad S3_A5_7_X01_X02_ANDROID_READ_ONLY_UI. **B7 PASS_SCOPED**; X01/X02 quedan parcialmente aceptados en UI emulada, NO la matriz A5 global.

## B7 — Auditoría independiente exacta
- Preaudit B1–B5 `docs/s3-night/S3_A5_7_X01_X02_REAL_ACTIVITY_AVISOS_UI_B1_B5_20261109.md` blob 16998c20bb9faded5c84637752ac735ee6a37200; B4/B5 precisión `docs/s3-night/S3_A5_7_X01_X02_ANDROID_B4_IMPLEMENTATION_PRECISION_20261109.md` blob f7888b606914cc90fa8c7284b941e02f32090432. Permiso READY en padre funcional `766c03e48dd4b5b088028e2b179d741e3d5da2f4`.
- Código SHA `675241634ce62e9ab4324ac178e0a9ab93318910` cambia exactamente dos ARCHIVOS NUEVOS test/workflow: `v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57ActivityAvisosComposeUiTest.kt` y `.github/workflows/faro-v5-s3-a5-7-activity-avisos-android-directed.yml`. CERO modificaciones productivas, 172 operaciones intactas.
- CI dirigida https://github.com/etacsantafe-source/FARO-V5/actions/runs/42623474908, job 129181194754, `COMPLETED SUCCESS` HEAD `675241634ce62e9ab4324ac178e0a9ab93318910`. `BUILD SUCCESSFUL in 5m 48s`, `S3_A5_7_ACTIVITY_AVISOS_APKS_COMPILED`. Android API35 emulador real, APK aislada `ar.com.faro.alpha.v2coredev`.
- Evidencia positiva de instrumentación **literal del log**: `OK (3 tests)`, `INSTRUMENTATION_CODE: -1`, `S3_A5_7_ACTIVITY_AVISOS_REAL_UI_PASS 3/3`. Los tres nombres ejecutados: `x01AuthorizedActivityShowsCanonicalUserEventAndRevokesOnBackground`, `x01NormalChatNeverGrantsActivityReadOrInventsEmptyHistory`, `x02AbsentAvisosOwnerNeverShowsFalseZeroOrMarksRead`.
- Guard obligatorio B1–B7 https://github.com/etacsantafe-source/FARO-V5/actions/runs/42623474973, `COMPLETED SUCCESS` **exacto mismo SHA**. Sin reruns de las 41 pruebas anteriores certificadas (33+4+2+2).
- El test positivo ejerció click real nativo foreground, compositor Compose real -> USER persistido en Workspace, recreación Android, selección explícita Actividad con permiso y evento de fuente Workspace con su tiempo; el resumen se produjo SOLO con click; al pausar/reanudar se revocó y la pantalla quedó `Actividad no disponible. No se consultaron datos.`.
- Los dos negativos verificaron que Chat normal sin solicitud Activity y Avisos sin owner se muestran NO DISPONIBLE, nunca historia vacía/avisos abiertos 0 falsos, sin mutación ni marca de leído.

## Alcance y deudas no falseadas
- X01 NO certifica receipts Reality COMMITTED reales en pantalla, filtros adicionales ni cierre global de historial; solo evento USER Workspace autorizado, proveniencia y revocación.
- X02 NO certifica presentación de Avisos con source autorizado ni cobertura real de todas las fuentes de Pendientes; testó fail-closed de Avisos sin owner y Activity sin grant.
- A5 GLOBAL X01–X12 OPEN. X04/X05 A3 BLOCKED_PERMISSION, catálogo oficial 172 sin cambios; X03 IA/Consultar DEV sin cablear, X09/X10 Review/Apply sin aceptación global, X08 parcial requiere rama determinista. X12 Compose API35 scoped 2/2, X12 native 2/2, owner revision 4/4 y seis unidades 33/33 conservadas.
- V2 productiva/S4/Auto/Noche OFF. Writer CAS exclusivo, G15 físico NOT_RUN (campaña única final). Sin datos reales, APIs pagas, generación productiva ni aplicación de planes.
- Próximo bloque independiente preaudit X06/X08 documentado en `docs/s3-night/S3_A5_7_X06_X08_FEDERATED_SEARCH_ANDROID_B1_B5_20261109.md`: antes de código, revisar viabilidad del fixture de coverage y crear permiso READY específico; sin repetir 44 pruebas.
**DICTAMEN: B7 PASS_SCOPED 3/3 API35; S3.A5 global aún OPEN.**
