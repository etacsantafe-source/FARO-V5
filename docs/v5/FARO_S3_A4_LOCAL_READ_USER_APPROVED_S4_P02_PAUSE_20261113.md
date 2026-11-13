# FARO V5 — aprobación humana A4, pausa S3 y reanudación S4 P02
Fecha: 2026-11-13 AR. Rama: `work/s3-night-20261102`. Commit base antes de este documento: `13f34f4e1ffc037deda14f4adf974bb3708e5769`.

## Resolución humana
Usuario: **"Autorizo. El único problema es que estoy fuera del wifi. Podemos poner en pausa esto y seguir avanzando con S4 en chat nuevo."**
Esta aprobación responde afirmativamente a la decisión A4 planteada explícitamente en el chat, y **solo** a la siguiente política de lectura:
- Una instalación local / un perfil de aplicación. Datos privados exclusivamente de esa instalación, sin lectura de otra aplicación, usuario Android ni cuenta externa.
- Acceso a lectura únicamente por gesto explícito del usuario con app en primer plano.
- Permiso independiente por fuente/owner, revocable al pausar, salir o cambiar de generación/puntero; negación por defecto.
- El UID y paquete pueden identificar sandbox técnico, **nunca identidad humana**. No extender automáticamente a cuentas múltiples, sincronización, equipos o perfiles.
- El permiso de lectura no concede escritura/Apply, acceso remoto, compartición ni política de egreso a Internet.
- Toda lectura debe conservar procedencia, cobertura parcial y fail-closed; no inferir cero global ni autoridad faltante.
- Esta aprobación **no** aprueba un patch, rutas de código, permiso READY o activación de V2 productiva; cualquier nueva integración A4 requerirá B1–B5 con delta y permiso individual, guard, B6, Android CI y B7.

## B1 — Evidencia y estado a preservar
- Checkpoint base `13f34f4e1ffc037deda14f4adf974bb3708e5769`, writer RELEASED epoch860 antes de abrir esta unidad documental, permiso funcional global S3 U02 BLOCKED/consumido. Luego writer DOC_ONLY epoch861 con CAS.
- `docs/s3-night/S3_FINAL_U00_U03_DELTA_PREAUDIT_20261112.md` U01 estaba `BLOCKED_NEEDS_HUMAN_POLICY_DECISION`; esta autorización resuelve **la decisión de producto**, no prueba host, ACL, realidad positiva ni integración.
- `docs/s3-night/S3_FINAL_U04_HUMAN_ACCEPTANCE_20261112.md` describe la política condicionante y H03 pendiente.
- `docs/s3-night/S3_G15_AUTO_H05_X03_PHYSICAL_USER_EVIDENCE_20261113.md` conserva H01/H02/H04 PASS físico reportado y H05 técnico, capturas visuales no inspeccionadas, H06 Groq Free + clave + consentimiento, H07 interrupción remota, H08 Review/Apply pendientes. Evitar repetir.
- S3 permanece formal **0/5**, scoped **3/5**, estimación antigua 40% no recalculada. Groq live NO_CERTIFIED. A4/A5 global OPEN.

## B2 — Reutilización y comparación
No repetir core de política local DEV, A3 M01–M05, X01–X12, CI Android35, IME ni evaluación V4.4 ya aprobada. La política recién autorizada es un delta de decisión; verificar los grants por owner y root, el mount real y el revoked epoch sin desactivar el fail-closed. S2 8/8 congelada.

## B3 — Opciones
Se elige **pausar ejecución física por falta de Wi-Fi** y reanudar trabajo exclusivamente documental de S4 P02 por GitHub. Se rechaza usar Termux UI y un nuevo Groq HTTP sin consentimiento/plan Free. El modo de datos móviles no autoriza pagos ni egress. P02 requiere auditar V4.4 READ_ONLY; no instalar APK ni tocar el G15.

## B4 — Separación de líneas de trabajo
**S3 EN PAUSA**: A4 policy humana APROBADA; próxima unidad S3 será B1–B5 delta y preflight de autoridad/montaje y luego U03, no implementada en este turno. H03 positivo sujeto a integración; H05 visual/TalkBack y H06–H08 humanos más adelante, cuando el usuario tenga condiciones para test. No programar ni ejecutar automáticamente en segundo plano.
**S4 PREPARACIÓN**: P00 `DONE_VERIFIED_DOC_SCOPE`; P01 `DONE_VERIFIED_DOC_SCOPE`; **P02 `READY_TO_START_READ_ONLY`**. Estudiar el repo histórico `etacsantafe-source/FARO` V4.4 y docs originales: Finanzas, Objetivos, Asuntos, Radar, SmartCompra, Cocina, proyecciones, menú UX; diferenciar capacidades implementadas, prototipos, contratos, fallos y tests; matriz con fuentes y hashes; no copiar runtime ni reabrir A1/A2; terminar B1–B7 documental. Luego P03 comparativa V4.4↔V5. S4 FORMAL OFF.
Fuentes: `AGENTS.md`, `docs/v5/FARO_CONTINUITY_POINTER.json`, `docs/v5/FARO_S4_PREPARATION_P00_P08_HANDOFF_20261112.md`, `docs/v5/FARO_S4_P00_DECISION_AUTHORITY_MATRIX_20261112.md`, `docs/v5/FARO_S4_P01_V5_IMPLEMENTATION_INVENTORY_20261112.md`, `docs/v5/FARO_S4_P01_B7_20261112.md`.

## B5 — Auditoría de permisos y riesgos
No confundir la política aprobada con acceso ya implementado; no saltar guard ni transformar `BLOCKED` en `READY`. Writer global único CAS, HEAD reconfirmado. No crear/abrir rama nueva ni tocar `main`, V4.4, S2, catálogos, bases, secretos o APK. S4 P02 solo lectura/documentación, no S4 productiva. Mantener decisión de user para cuando vuelva a S3 sin pedirle que apruebe otra vez lo mismo.

## B6/B7 de esta unidad
B6: **DOC_ONLY** documento + pointer, cero código/CI Android. B7: verificar el SHA publicado, contenido, guard `seven-block-evidence` del HEAD y writer RELEASED epoch861. No alegar cierre si falta cualquiera. El usuario no requiere conexión ADB/Wi-Fi para P02.
