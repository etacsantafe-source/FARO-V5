# FARO V5 — S4 W04 F2C1B1A · Android DEV, consentimiento de lectura local por alcance · B1–B5

Fecha 2026-11-23 (Argentina). Unidad `S4_W04_F2C1B1A_ANDROID_LOCAL_SCOPED_READ_CONSENT_DEV`.
Baseline **`a92ca2ff96c0a39909508f586a8d8f063fa0abb7`** (verificado). Estado **B1–B5 documental solamente; READY prohibido hasta guardia documental SUCCESS**. Permiso anterior F2C1B0 consumido/BLOCKED. Single writer inicial RELEASED 1700.

## B1 — Evidencia, brecha y resultados exigidos
- `F2C0` B7 PASS (CI 45110499993 SQLite6): selected ACTIVE Household10 + Evidence2 bajo lease. No issuer Android.
- `F2C1A` B7 PASS API35 4/4 (CI 45116709185): `FaroV5W04CommercialDevForegroundSessionGate` revoca al dejar primer plano; Activity `ar.com.faro.alpha.w04commercialdev` y panel nativo arrancan sin fuente/grants.
- `F2C1B0` B7 PASS JVM 6/6 (CI 45120468801, guard 45120468792, checkpoint `a92ca2ff96c0a39909508f586a8d8f063fa0abb7`): transforma una lectura previamente autorizada de F2C0 a F1/D4 sin emitir permisos, montar UI ni abrir SQLite. No repetir esas 6 pruebas.
- App dispone de `AndroidPlatformSandboxFactsReader(Application)`, `FaroV5AndroidSandboxPrincipal` que comprueba UID de proceso, aplicación verdadera, epoch y pointer. `FileActiveGenerationPointer` usa registro durable monotónico. Son reutilizables. La UI actual NO solicita consentimiento local de Household/Commercial y sigue vacía.
- **Brecha para esta unidad acotada:** ninguna clase Android real obtiene/retiene/revoca *consentimiento humano de lectura* para exactamente 1 `needId` y 1 `productKey`, separado por dominio. **No equivale todavía a permiso de dueño ni grant N-033/N-034**: ese segundo límite requiere real F2C0 host/F2C1B1B antes de una lectura.
- Criterio binario: en emulador real API35 un sandbox Android verdadero + puntero activo canónico en directorio privado permiten pedir consentimiento local separadamente mediante eventos `View.performClick` en controles locales. Sin gesto, con root falso, scope ajeno, UID inadecuado, pointer cambiado, epoch, fondo, callback de owner fallido: DENY/REVOKE. Activity nunca concede automáticamente ni devuelve datos comerciales.

## B2 — Contraste V4.4 y activos certificados
- V4.4: acción explícita y UI sencilla; reutilizar el principio *humano decide*, no permisos ni conexiones V4.4; V5 requiere separar Household10 (Reality), Evidence2 (historial) y Finanzas (dinero).
- Reusar unívocamente `FaroV5AndroidSandboxPrincipal` + `AndroidPlatformSandboxFactsReader(application)`, `FileActiveGenerationPointer`, F2C1A Activity lifecycle, emulador W04 y gradle variant aislada. NO reutilizar datos de esquema1, no abrir una segunda connection, no dar grant de Apply.
- F2C1B1A solo emite y revoca consentimiento humano de lectura en memoria, **no ACL de dueño**. No interfiere con permiso del Store ni autoriza por package/debug/UID solamente.

## B3 — Investigación de alternativas
- Android lifecycle: https://developer.android.com/guide/components/activities/activity-lifecycle — ejecutar revocación al entrar `onPause`/`onStop` y exigir nuevo gesto tras `onResume`.
- Almacenamiento privado: https://developer.android.com/training/data-storage/app-specific — exigir root dentro de `Application.filesDir` canónico; nunca `Intent`, SAF, external storage o path enviado por modelo.
- Android sandbox por aplicación: https://developer.android.com/privacy-and-security/security-tips — los hechos de proceso limitan *origen técnico*, NO identidad humana ni permiso N-033/N-034.
- Alternativa 1: aceptar un booleano `consent=true` o selección de `Intent`: **DESCARTAR** por spoof.
- Alternativa 2: grant genérico único para todos los productos/necesidades: **DESCARTAR** por fuga lateral.
- Alternativa 3: montar SQLite y selección automática al iniciar: **DESCARTAR** por falta de selección, ACL y autoridad.
- Alternativa 4: emisor real Android DEV, memoria, pinned private pointer, origen Application, doble consentimiento por objetivo concreto, revocación lifecycle; **ADAPTAR**. Futuro F2C1B1B enlazará a autoridad owner real y ruta F2C0→F1/D4/E, sujeto a nuevo READY.

## B4 — Diseño exacto y cuatro rutas permitidas
`FARO-UNIT:S4_W04_F2C1B1A_ANDROID_LOCAL_SCOPED_READ_CONSENT_DEV`.
1. **Nuevo** `v5/app/src/w04CommercialDev/kotlin/ar/com/faro/v5/app/FaroV5W04CommercialDevReadConsentIssuer.kt`: emisor interno tipo `AutoCloseable`, factory desde `FaroV5W04CommercialDevActivity` real + `Application.filesDir` rooted, `FileActiveGenerationPointer` con `ActiveGenerationPointer` exacto, `FaroV5AndroidSandboxPrincipal` respaldado por `AndroidPlatformSandboxFactsReader`; epoch del host + callback ownerReady. Inmutable necesidad/producto elegido por host de confianza (sin Intent ni URL). Métodos distintos `grantHouseholdAfterTrustedLocalTap` y `grantCommercialAfterTrustedLocalTap`, `householdConsented`, `commercialConsented`; dos scopes nunca transferibles, verificaciones antes/después, revocación irreversible y sin discos. **Son consentimientos, NO ACL ni permisos del Store.**
2. **Modificar solo** `v5/app/src/w04CommercialDev/kotlin/ar/com/faro/v5/app/FaroV5W04CommercialDevActivity.kt`: permitir instalar 1 issuer interno ya validado; cerrar al `onPause/onStop/onDestroy` junto a gate F2C1A; sin solicitud por `onCreate/onResume`, sin aceptar Intent, sin montar fuente real ni modificar panel. Métodos internos para tests; duplicado cierra nuevo.
3. **Nuevo** `v5/app/src/w04AndroidTest/kotlin/ar/com/faro/v5/app/S4W04CommercialReadConsentIssuerAndroidTest.kt`: 5 pruebas instrumentadas reales: T01 Activity vacía y sin issuer de inicio; T02 Application real/private-pointer/2 botones de consentimiento local, exact need/product + fuentes independientes; T03 pause/recreate/resume cierra sin restaurar; T04 cambio de puntero + epoch ABA revoca; T05 scope/root ajeno, owner callback throw/duplicate issuer rechazado. No datos de producción ni precios sintéticos.
4. **Nuevo** `.github/workflows/faro-v5-s4-w04-f2c1b1a-read-consent.yml`: guard/preflight exact4 de SHA padre, Android emulator API35 build W04 dev offline, 5 XML tests distintos 0/0/0, Android manifest sin INTERNET, archivo XML/APK artefacto real para B7. Para RED_FIX en unidad, contar 1..4 rutas con TODAS guardadas (evitar oráculo falso `==4`); pruebas siguen exact5.
F2C1B1B queda OPEN para montar fuente real, owner ACL doble, F1/D4 y selección humana de evidencia. F3 integral queda OPEN.

## B5 — Auditoría adversarial pre-código
- `grant` SOLO bajo callbacks de botones locales (sin llamadas en onCreate/onResume, intents, notificaciones, modelos). La factory no muestra botones por sí sola; próximo host debe mostrar opción solo cuando su fuente F2C0 entregue selección verificada. El test controla botones locales en emulador, NO afirma que el host real esté instalado. Nunca usar un tap como permiso de N-033/N-034.
- Comprobar directory físico, aplicación exacta DEV, debug y Application verdadera; root dentro de `filesDir`, nunca symlink fuera. Pointer real exacto por lectura repetida y monotonic sequence; sandbox técnico real de Application. No aceptar process UID del solicitante por parámetro.
- Necesidad y SKU opacos válidos, no strings con control; rechazo scope ajeno sin filtrar ninguna existencia o datos.
- Si ownerReady, epoch, pointer, sandbox o foreground fallan incluso transitoriamente, envenenar y revocar ambos scopes para evitar ABA; cerrar en pause/stop/destroy. Remontar solo con issuer nuevo y nuevo gesto, nunca restaurar guardados.
- No abrir SELECT/SQLite, no escribir real/archivo para grants, no conectar Finance ni conversiones ARS/USD, no precio vigente, no red, pagos, stock, Apply, Restore, main, V4.4, G15, Auto/Noche o V2 productiva.
- Hasta B7: CI real SHA, Android API35 y manifest/XML descargados y comprobados, guard SUCCESS y writer RELEASED. Fallo => RED_FIX misma unidad, no declarar PASS ni reutilizar pruebas ajenas.
