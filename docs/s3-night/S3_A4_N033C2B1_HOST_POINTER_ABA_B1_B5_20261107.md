# FARO V5 — S3.A4 N033C2B1 · Host pointer ABA fence · B1–B5
Fecha: 2026-11-07 AR. Unidad exacta: `S3_A4_N033C2B1_HOST_POINTER_ABA`. Rama `work/s3-night-20261102`, reviewed head `454c593ef4a104f6758ea302e68d7e7e4ce7f63f`.
**Alcance cerrado:** proteger dos admitidores host existentes contra generación que cambia y vuelve al mismo ID; esto es infraestructura para Reality8 Planning. **NO monta Reality8 en Android, NO habilita V2, NO da ACL ni Review.** El usuario pospuso la eliminación de código muerto, migraciones de test, consolidación de versiones y reestructuración modular a S5, después de S3/S4. V5 limpia; datos pasados solo pruebas, pero NO borrado silencioso.

## B1 — Evidencia y aceptación medible
- N033C2A B7 PASS_SCOPED: native five-owner schema8 cutover/journal/rollback 17/17 en commit `7812ca8e1ef9b5de2b76b1fcf3b23410a7acfc01`, Actions 42330841932 SUCCESS y guard 42330841858 SUCCESS 67 unidades. Estado HEAD `454c593ef4a104f6758ea302e68d7e7e4ce7f63f` guard 42331273257 SUCCESS, writer RELEASED epoch438 en preflight, permiso BLOCKED.
- `FaroV5PreSwitchHostAdmission.canOpenAsActive`: lee `activePointer.read()?.generationId` antes y después de health STAGED y en rama post-switch. Si pointer hace **old→target→old** entre lecturas, los IDs coinciden; el método podría devolver true aunque la `sequence` avanzada invalida autoridad anterior.
- `FaroV5V2RecoveryRuntimeHealthOwner.verify`: compara `pointer.read()?.generationId` antes de opener, luego de health, e ignora `ActiveGenerationPointer.sequence`. Inyectando `target→other→target` durante `opener.open` puede informar `Verified` para un runtime invalidado.
- `FileActiveGenerationPointer` YA expone `ActiveGenerationPointer(sequence,generationId)` con secuencia monótona, comparables por igualdad de data class; no es necesario crear otro token, otro store ni cambiar protocolo.
- Aceptación binaria de la unidad: capturar **un snapshot completo de pointer (ID+sequence)** en el método, y exigir igualdad exacta antes y después de operaciones. Pre/admission V1 igual. Pruebas nuevas: pre-switch ABA vuelve al mismo ID y queda DENIED; post-switch ABA simulado entre dos lecturas queda DENIED; health real `target→other→target` durante opener devuelve Blocked, cierra el grafo y no altera el journal. Retener las 17 pruebas existentes, nuevos 3; dos clases deben arrojar 9 y 11, total 20/20 XML exacto.

## B2 — Comparación, reuso y alternativa V4.4
- V4.4 sirve como referencia de semántica de dominio de recordatorios y planes mensuales documentada en `docs/s3-night/S3_A4_N031_REMINDER_MONTHLYPLAN_TYPED_CONTRACTS_B1_B5_20261106.md`, no como autoridad de generación de V5. No portar clases V4.4 ni datos históricos.
- Reusar `FileActiveGenerationPointer`, `ActiveGenerationPointerPort.read()`, `GenerationSwitchCoordinator`, `FaroV5PreSwitchHostAdmission`, `FaroV5V2RecoveryRuntimeHealthOwner` y dos suites ya existentes. Reuso exacto de la `data class` evita nueva capa de tokens/owners.
- Elegir **A adaptar guardias pre/post al snapshot completo**. Descartar B comparar solo ID (ABA), C crear token/contador paralelo, D deshabilitar recovery o convertir generational ID en ACL, E migrar todo Reality ahora (S5). No cambio de contratos públicos, no costo/API.

## B3 — Investigación externa, proyectos reales y tradeoffs
1. SQLite official WAL https://www.sqlite.org/wal.html y aislamiento https://www.sqlite.org/isolation.html: un lector WAL observa snapshot de datos, no debe confundirse con selección durable de generación de FARO. **REFERENCIA:** la secuencia de puntero debe validarse independientemente del health de SQLite, especialmente en un swap/reopen.
2. Android offline-first https://developer.android.com/topic/architecture/data-layer/offline-first: la DB local es fuente canónica para capas superiores. **USAR** autoridad local única y bloquear un runtime obsoleto; sin servicio remoto ni segunda base.
3. Proyecto Android oficial Now in Android https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md: capas data/domain/UI, CI y pruebas; **REFERENCIA**, no copiar código/atribución ni introducir librerías. La solución en FARO usa sus propios contratos existentes, sin licencia nueva ni nuevas dependencias.
4. OWASP API1:2023 https://api-security.owasp.org/editions/2023/en/0xa1-broken-object-level-authorization/: ID válido no autoriza operaciones de un objeto; **REFERENCIA** para separar puntero/seq (autenticidad de generación) y ACL/Review (autorización humana). **No** contar este fence como ACL revocable de Planning.
5. Documentación Room de migraciones https://developer.android.com/training/data-storage/room/migrating-db-versions: fallback destructivo implica pérdida de datos. **DESCARTAR** reset/migración destructiva silenciosa; la decisión S5 no altera recovery actual.
- Hipótesis falsable: con ID-only, ABA devuelve falsamente `true/Verified`; con snapshot completo, el mismo test adversarial falla cerrado. El test existente de pointer switch a otro ID pasa igual, y éxito normal conserva semántica.

## B4 — Cambio DELTA ONLY y archivos permitidos
**Solo cinco rutas exactas**, en orden: 
1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5PreSwitchHostAdmission.kt` — snapshot completo y comparación pre/post, sin mutar puntero.
2. `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5V2RecoveryRuntimeHealthOwner.kt` — lectura inicial y revalidación de pointer ID+sequence a través de apertura y health bajo quiescence, sin nuevos permisos.
3. `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5PreSwitchHostAdmissionT2Test.kt` — 2 casos negativos ABA (pre, post) sin SQLite.
4. `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5V2RecoveryRuntimeHealthOwnerT2Test.kt` — 1 caso ABA durante opener; verifica graph cerrado.
5. `.github/workflows/faro-v5-s3-a4-n033c2b1-host-pointer-aba-directed.yml` — job GitHub sobre rama work/s3-night-20261102, `:app:testDebugUnitTest --tests` dos clases; XML exacto 9+11=20, 0 skip/fail/error. SDK37/JDK21, no emulator, no G15.
La ruta App activa Reality7, `FaroV5HostCertification.V2_ACTIVE_ENABLED=false` y RecoveryRuntimeHealthOwner.canonical sigue como antes. Un N033C2B2 posterior, con su propio B1–B7, decidirá cómo montar Reality8 con ACL/Review sin ampliar este permiso.

## B5 — Preauditoría adversarial y STOP
- Caso ABA PRE: puntero anterior A (seq N) → objetivo B → A (seq N+2) durante probe STAGED, SAME ID pero `read()!=before` y DENY. Caso ABA POST: inyección de puerto con mismo ID y secuencias cambiantes, DENY sin ejecutar quarantined opener.
- Health recovery: journal SWITCHED y target válido; al abrir cinco stores simular target→foreign→target, exigir Blocked y close exactamente una vez, sin `Verified`; journal intacto. Si función queda en excepción, `runCatching` fail-closed.
- Nunca sustituir pointer check por mera `schemaVersion`, `generationId` ni WAL commit. Quiescence no concede ACL. El mismo puntero original debe permanecer correcto para el caso sano, sin romper rollback ni replay fence. No escribir fuera de rutas, ni permitir RESTORE en V2 no certificado; no mutar S2, V4.4, main, S4, catálogo172, estado de Noche, producción ni datos G15.
- **Resultado B5: READY_SCOPE_N033C2B1_HOST_POINTER_ABA_ONLY** condicionado a permiso exacto docs-only y CI preflight. Nada funcional escrito en este expediente. Si B6 da rojo, registrar RED_FIX B7 antes de cualquier reintento y no ampliar el alcance; B7 final independiente según SHA/XML/guard.
