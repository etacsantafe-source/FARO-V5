# FARO V5 — W04 F3A · Botón nativo de entrada a revisión histórica con fuente real · B1–B5

2026-11-23 local. Unidad `S4_W04_F3A_TRUSTED_NATIVE_HISTORY_ENTRY_DEV`. HEAD base `14db0a00c3a453f1d34fbf855107e306bef7b66e`. **Alcance pequeño: F3A DEV, NO F3 integral/global.**

## B1 — Evidencia y brecha binaria
F2C1B1B1B1B B7 PASS scoped: código `22c846519d7b701595719d8844428f6945ad5e43`, CI Android 35 `45306039037` SUCCESS 3/3 t04–t06 y APK aislado sin Internet; guardia `45306039102` SUCCESS, doc B7 `docs/v5/FARO_S4_W04_F2C1B1B1B1B_NATIVE_EXPLICIT_HISTORY_B7_PASS_20261123.md`, checkpoint `14db0a00c3a453f1d34fbf855107e306bef7b66e`, guard final `45308097194` SUCCESS. Writer último RELEASED 1752. NO repetir anteriores.
La Activity DEV inicia un `reviewPanel` inerte y conserva un único `trustedGate` + `consentIssuer`; las pruebas t04–t06 montan host real con Household10/Evidence2 ACTIVE y llaman directamente `host.presentNativeHistoricalChoicesForTrustedDevHost` para abrir opciones. Aún **falta una acción nativa de entrada** explícita dentro del panel para pedir el selector después de dos consentimientos. No inferir que el inicio de la Activity ya abre SmartCompra.

## B2 — Paridad UX y activos V5
V4.4 favorecía botones simples y navegación explícita. Reutilizar panel nativo existente y `FaroV5W04CommercialDevTrustedSelectedHost` owner-backed, `readProjectedOnce`/F1, el botón de observación histórico ya certificado, `Activity.onPause/onStop` con limpieza y `issuer` independiente de dos scopes. **No** reabrir original t01–t06; t07–t09 serán distintos. No crear nuevo root, nuevo grant ni mecanismo de selección de precio.

## B3 — Alternativas reales, análisis y elección
- AndroidX ActivityScenario: https://developer.android.com/guide/components/activities/testing : los gestos instrumentados sólo demuestran UI/lifecycle; no prueban identidad humana.
- Referencia Android de Views accesibles: https://developer.android.com/guide/topics/ui/accessibility/views/testing-views : botón debe tener texto legible, descripción de accesibilidad y borrarse al revocar.
- SQLite WAL: https://www.sqlite.org/wal.html y aislamiento https://www.sqlite.org/isolation.html : fuentes en sesión única bajo el mismo puntero, relectura con watermark, no mezclar snapshots por fuera.
- OWASP MASVS AUTH: https://mas.owasp.org/MASVS/07-MASVS-AUTH/ : un Boolean/Intent no sustituye autoridad real owner.
A) En onCreate abrir automáticamente selector: RECHAZAR (sin consentimientos/identidad). B) inyectar datos manuales al panel: RECHAZAR (false display). C) crear nueva Activity separada o nueva SQLite: RECHAZAR (segunda autoridad y complejidad). D) botón nativo simple **sólo ofertado por host admitido tras dos scopes** que llama al método F2C existente tras tap: ELEGIR. Panel solamente ejecuta callback con recheck y conserva el lifecycle.

## B4 — Diseño y cuatro rutas precisas
1. `v5/app/src/w04CommercialDev/kotlin/ar/com/faro/v5/app/FaroV5W04CommercialDevTrustedSelectedHost.kt`: método `offerNativeHistoricalEntryForTrustedDevHost(asOf,maxAge,count,readNow)` en host ya montado. Valida límites, `!closed`, `admitted()`, ambos consents y lectura actual. Llama al panel para mostrar acción; callback del botón vuelve a llamar a `presentNativeHistoricalChoicesForTrustedDevHost` ya certificado. Ninguna lectura previa de precio se pinta, ningún dato cacheado. Si el host fue revocado, no se abre selector.
2. `v5/app/src/w04CommercialDev/kotlin/ar/com/faro/v5/app/FaroV5W04CommercialReviewPanel.kt`: vista nativa `renderTrustedHistoricalEntry(authorizedNow,onOpen)` con Button tag `faro.w04.historical.open` y descripción clara; usa panel original y `clearForRevocation`; no abrir URL ni añadir importe/último valor.
3. `v5/app/src/w04AndroidTest/kotlin/ar/com/faro/v5/app/S4W04CommercialOwnerBackedPositiveAndroidTest.kt`: SÓLO tres tests nuevos:
   - `t07NativeEntryButtonRequiresTapBeforeHistoricalChooser`: real fixture Household10/Evidence2, dos consentimientos y host; presentar acción NO muestra observaciones, tap abre selector, segundo tap muestra D4/E histórico.
   - `t08ClosedOwnerHostCannotUseDetachedHistoricalEntryButton`: host cerrado + click de botón desprendido jamás revela selector ni importe.
   - `t09InvalidLiveClockAfterNativeEntryTapCannotDisplayHistoricalEstimate`: tras dos taps nativos, clock que retrocede antes de asOf => pantalla sin importe.
   No repetir t01–t06.
4. `.github/workflows/faro-v5-s4-w04-f3a-native-entry-android.yml`: API35 emulador, Java21, tres clases#métodos t07–t09, verificación estricta XML clase/nombres **exactamente 3 sin failure/error/skipped**, isolated offline APK manifest; adjuntar XML+APK, SHA; gate/prefight tres o cuatro rutas (exact4). Commit etiquetado FARO-UNIT.

## B5 — Auditoría adversarial, criterios de cierre
F3A se considera PASS acotado sólo si botón de apertura no muestra precio antes de click, no se puede registrar sin host+issuer+dos scopes, autorización real owner revalidada justo antes de tap, observación exacta aún necesita segundo click, y reloj inválido/revocación/Activity pause dejan panel sin precio. Mantener W_full/W_selected vigente, aislamiento de otro SKU, lease OS único, fuente histórica ≠ precio vigente, ARS/USD separados, sin paid API ni egress. Botón test `performClick` no prueba humano autenticado. No activar V2 productiva, pagos, Apply, stock, Finance, Auto/Noche/G15 físico ni tocar main/V4.4. Mantener pruebas previas históricas PASS sin rerun.
Después de B6: CI run real SHA+logs/XML, APK offline, guardia y B7 específico. F3 global todavía OPEN.
