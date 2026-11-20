# FARO V5 — W04-C · Work Turbo Handoff completo (2026-11-20)
## Orden y recuperación
Leer AGENTS.md; docs/v5/FARO_CONTINUITY_POINTER.json; FARO_PROTOCOL_WORK_PERMIT.json; protocolo B1–B7; W04-B1 global B7; `FARO_S4_W04_C_COMPLETE_PREAUDIT_B1_B5_20261120.md`; `FARO_S4_W04_C0A_EXPLICIT_PRODUCT_BINDING_B1_B5_20261120.md`; `FARO_S4_W04_C1_C3_PREAUDIT_20261120.md`; este relevo. Revalidar HEAD remoto/lock CAS/CI. No repetir W01/W02/W03 ni W04-A/B0/B1. S4 DEV, V2 productiva OFF, G15 NOT_RUN, Auto/Noche OFF.

## Primera orden
Si y SOLO SI permiso `S4_W04_C0A_EXPLICIT_NEED_PRODUCT_MATCH_DEV` está READY exact3 y el guard de su SHA es SUCCESS: `python3 scripts/quality/faro-protocol-gate.py --mode preflight --unit S4_W04_C0A_EXPLICIT_NEED_PRODUCT_MATCH_DEV --path v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04CommercialNeedProductSelection.kt --path v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04CommercialNeedProductSelectionTest.kt --path .github/workflows/faro-v5-s4-w04-c0a-match.yml`; B6 implementar selección explícita tipada puro sin DB/host/price read, ejecutar Gradle JVM, comprobar XML y guard por SHA, B7 documento y consumir READY, writer RELEASED.
Si no READY, no escribir código: recuperar evidencia documental, verificar guard y pedir autorización propia; no autopasarse B1–B5.

## Cadena posterior
1. C0B: revisar delta de integración tras C0A B7, congelar exact paths, B1–B5 solo delta si cambió HEAD, guard/READY individual; B6 lectura Evidence/Household desde única sesión con dos grants y fences, JVM, B7.
2. C1: manual evidence OBSERVATIONAL_WRITE; validar contrato y autorización; localidad/vigencia/pack aún ausentes en DB v1. Si requeridos para cotización completa, versión/migración/backup/restore/compatibilidad y permiso separados. No guardar metadata en sourceRef ni anunciar promociones vigentes no probadas; CI+B7.
3. C2/C3: comparar por comercio/moneda/localidad/tiempo y refrescar anexando nuevos records, histórico intacto. Solo con fuentes/grants/quotes válidos, preflight y permiso propios; CI+B7.
4. Cierre W04-C B7 independiente con pruebas exactas, guard por SHA, puntaje de cobertura y limitaciones; no confundir B7 C con W04-F global.

## Guardias
Writer CAS `faro-v5-writer-lock` con epoch/token propios, revalidación HEAD y permiso antes de cada mutación, RELEASE antes de CI. Sin writer ajeno/takeover, sin duplicar Evidence DB, sin modificar main/V4.4, sin APIs pagas, scraper obligatorio, pagos/stock o mutación Finance. Si una CI es roja, RED_FIX de la unidad actual y bloquear siguiente.
## Repaso final documental
W04-D carrito necesita cotizaciones con localidad/fecha/vigencia; no convertir observación en presupuesto confirmado. W04-E Android API35/G15 no certificado; W04-F debe integrar. W05 recepción física separada; W06 House+Finance atomicidad bloqueada hasta pruebas propias. Monedas separadas, unidades de empaque explícitas, sourceRef opaca, cobertura PARCIAL cuando faltan datos. La limitación cardinal de C1 es schema1 Evidence sin locality/expiry; se resuelve en C1 con delta o se presenta desconocida, sin inventar PASS.
