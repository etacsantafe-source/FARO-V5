# FARO V5 — W04 F2C1B1B0 · composición de lectura seleccionada real para Android DEV · B1–B5

Fecha: 2026-11-23 Argentina. Unidad `S4_W04_F2C1B1B0_TRUSTED_SELECTED_READ_HOST_DEV`.
HEAD base `13a5a48400087954664b08c6fa97b78d9be38244`; writer inicialmente RELEASED epoch1708. Permiso F2C1B1A BLOCKED consumido. Estado **PREAUDIT DOCUMENTAL, sin permiso funcional hasta guardia**.

## B1 — Evidencia y brecha funcional exacta
- F2C0 B7 PASS SQLite6 CI `45110499993`: `FaroV5Household10Evidence2DevSelectionReadPort.open` admite una generación Household10 + Evidence2 **ACTIVE**, una sola lease/graph, real staged descriptor, doble callback de grant, `readSelected` devuelve exactamente una necesidad abierta y registros del producto solicitado con watermark global. NO inventa grants.
- F2C1A B7 PASS Android35 4/4 CI `45116709185`: `FaroV5W04CommercialDevForegroundSessionGate.fromCertifiedDevPort`, actividad DEV con `attachTrustedGate`, revocación pause/stop. Nunca arranca fuente en launcher.
- F2C1B0 B7 PASS JVM 6/6 `45120468801`: `S4W04SelectedActiveTypedReadProjection` convierte `SelectedRead` a F1 contratos, requiere read nuevo y callbacks; NO es autorización.
- F2C1B1A B7 PASS Android35 5/5 `45175563251`, checkpoint base `13a5a48400087954664b08c6fa97b78d9be38244`: `FaroV5W04CommercialDevReadConsentIssuer` certifica sandbox real, directorio privado, puntero, gesto local por necesidad y producto, revocación. NO grant de owner, NO Store mount.
- **Brecha:** no hay fábrica que exija simultáneamente (a) mismo root/puntero/objeto del issuer, (b) grants reales de los owners Household N-033 y Commercial N-034 para ese objeto, (c) metadatos del catálogo READ_ONLY, y (d) apertura F2C0 + gate F2C1A con lease/foreground. No unir directamente consent a acceso. No hay todavía UI D4 autorizada ni selector de observación humana.
- Entrega de esta unidad: **host Android DEV solo invocable por el compositor de confianza**, que valida todos esos requisitos, abre F2C0 ya certificado en su única lease y lo enlaza al gate F2C1A; ofrece `readProjectedOnce` fail-closed, sin cache. NO se auto-engancha al launcher, NO otorga grants del owner, NO renderiza. Deja F2C1B1B1 para prueba positiva de store + D0/F1/D4/E.

## B2 — Reutilización y V4.4
- V4.4 UX: selección y acción humana legibles; conservar sólo interacción, nunca permiso de V4.4 ni su Reality/estructura.
- Reutilizar `FaroV5W04CommercialDevReadConsentIssuer`, `FaroV5W04CommercialDevForegroundSessionGate`, `FaroV5W04CommercialDevActivity.attachTrustedGate`, `FaroV5Household10Evidence2DevSelectionReadPort`, `S4W04SelectedActiveTypedReadProjection`, `ContractCatalog`/ `OperationContractLookupPort`, `CommercialEvidenceOperations.PRICE_HISTORY_READ`, `HouseholdNeedId`/ `CommercialProductKey`, MaterialWriteGate compartido provisto por el host. NO duplicar SQLite, lease, F1, renderer, Apply o política productiva.

## B3 — Investigación técnica y alternativas
- Android app-specific private files: https://developer.android.com/training/data-storage/app-specific — root canónico privado + pointer exacto; nunca ruta de Intent ni externa.
- Android lifecycle: https://developer.android.com/guide/components/activities/activity-lifecycle — F2C1A ya revoca `onPause`/stop/destroy, no recuperar al resume.
- SQLite serialización: https://www.sqlite.org/isolation.html — un único opener/lease del F2C0 previamente certificado; no open secundaria para Evidence2.
- Android security practices: https://developer.android.com/privacy-and-security/security-tips — UID debug ≠ autorización; doble policy admitida por owners.
- Opción A entregar `F2C1B1A.householdConsented` directamente a F2C0 como ACL: **DESCARTAR**, consentimiento ≠ owner grant.
- Opción B root/generación elegido por Intent o automatic startup: **DESCARTAR**, spoof/mixed generation.
- Opción C abrir `S4W04HouseholdNeedApplyHost` writable solo para leer: **DESCARTAR**, introduces Apply/sesión distinta.
- Opción D fábrica aislada que exige los dos owner policy *callbacks* del compositor confiable más validación de catálogo por cada read, reutiliza F2C0 + gate existente: **ADAPTAR**. Solo un policy callback provisto por dueño de confianza es verificable en runtime; tests con callbacks sintéticos prueban DENY y NO significan grants productivos.

## B4 — Alcance y cuatro rutas congeladas
`FARO-UNIT:S4_W04_F2C1B1B0_TRUSTED_SELECTED_READ_HOST_DEV`.
1. `v5/app/src/w04CommercialDev/kotlin/ar/com/faro/v5/app/FaroV5W04CommercialDevReadConsentIssuer.kt` MODIFICAR: conservar certificación F2C1B1A; vincular root canónico como propiedad privada del emisor, exponer a host interno validación de exacto root/pinned/need/product + ambos consentimientos y principal técnico de sandbox, sin crear grants de owners. Nunca cachear autorización sin recheck ni permitir alias.
2. `v5/app/src/w04CommercialDev/kotlin/ar/com/faro/v5/app/FaroV5W04CommercialDevTrustedSelectedHost.kt` NUEVO: `openForTrustedDevHost` comprueba Activity foreground, ambiente DEV, hostEpoch, issuer pinned root and double consent, operaciones `shopping.snapshot.read` N-033 household y `commercial.price_history.read` N-034 evidence READ_ONLY bindable del catálogo, y dos callbacks de permiso del owner específicos al objeto (NO del modelo). Abre F2C0 con root/staged/pin actuales + `MaterialWriteGate` único, conecta gate F2C1A y `attachTrustedGate`; un fallo cierra lease y no muestra nada. `readProjectedOnce(explicitHumanSelection)` vuelve a comprobar ambos consentimientos+ACL+pointer/epoch y convierte lectura real con F2C1B0, no persiste ni cachea. `close` revoca gate.
3. `v5/app/src/w04AndroidTest/kotlin/ar/com/faro/v5/app/S4W04CommercialTrustedSelectedHostAndroidTest.kt` NUEVO: pruebas API35 **5 casos negativos reales**; launcher nunca monta fuente; doble gesto es insuficiente sin policies N033/N034; root/generación equivocados; callbacks que arrojan fallo o revocación/epoch ABA; catalog vacío/identidad técnica no significa permiso. Fixture root privado/puntero real sin ninguna base; nunca afirmar lectura positiva, que requiere staged+owner grant real en próxima unidad. Además test fuente de gate no se abre con consent solamente.
4. `.github/workflows/faro-v5-s4-w04-f2c1b1b0-trusted-selected-host.yml` NUEVO: guard/preflight de 1 a 4 rutas autorizadas por commit, Java21/SDK37, emulador API35, XML 5/5 distinto 0/0/0, APK DEV offline sin INTERNET, publicar evidencia. La CI debe fallar si cualquier caso falla; el B7 solo certifica composición fail-closed/no source on startup, NO real positive store read.
- F2C1B1B1: apertura positiva con STAGED Household10/Evidence2 auténtica, ACL de owners reales, elección explícita de registro comercial y D4/E. F3 aceptación integral después. **No alterar** APP main, Finanzas, S2, S3, V4.4.

## B5 — Riesgos, pruebas adversariales y STOP
- Nunca conferir permiso a partir de UID, package name, debug, booleano de modelo, Intent, archivo de preferencias o consentimiento aislado. La factory NO inventa N-033/N-034: exige el catálogo estricto y callbacks suministrados por composición privilegiada y comprobados antes/durante/después del read. Si esos callbacks no están conectados a la política del owner real, el integrador **debe DENY**: aún no existe certificación productiva de su origen. No exponer un método que emita grants a la UI.
- Principal técnico de sandbox es etiqueta de sesión no identidad civil; usar sólo como correlación para policy trusted. `readProjectedOnce` devuelve null si pierde sincronía, aun si los datos se leyeron; no retener `SelectedRead` entre eventos ni repetir snapshot por cache.
- No abrir F2C0 si falta alguna precondición. El fixture Android no tiene generation staging real y NUNCA debe poder leer stock/precios. No inventar pruebas positivas.
- Contra A→B→A, pointer CAS monotónico y issuer revocado. Mismatches en consent scope, root externo, callback throw => deny sin capturar datos.
- B7 requiere guard real + API35 XML original descargado, 5 casos, regresiones automáticas no rojas, documento durable y writer RELEASED. Si falla, B7 RED_FIX para mismo unit antes de otro commit.
- No Apply, pagos, stock, red/API paga, V2 productiva, Auto/Noche, G15 físico, `main`, V4.4, permisos adicionales ni D4 falso.
