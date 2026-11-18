# FARO W01 D1B1C1 — RED_FIX de autorización en CI de regresión

Fecha local 2026-11-17. Unidad `S4_W01_D1B1C1_FINANCE9_DEV_JOURNALED_CUTOVER`. Delta acotado a dos workflows, con el mismo identificador de unidad.

## B1
En `9561ee8659b39ed333c98c10ee8da814fc4b5fbc` se publicaron cinco rutas funcionales exactas. La [CI dirigida 44224896409](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44224896409) pasó 6/6 y la [guardia 44224896391](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44224896391) pasó. Artefacto 14392263688, XML SHA-256 `0a83d17bd0d79481d56b39cc142ff41e0fdab943c651eb138c28a26c57906b0e` analizado por separado. Balance final en el SHA: 28 workflows verdes, dos rojos.
- [44224896087](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44224896087) falló en la precondición con `file outside preaudited unit: .../RealityFinance9Acl.kt`.
- [44224896239](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44224896239) falló en la precondición con `file outside preaudited unit: .../RealityFinance9TrustedReadPolicyRevocationPort.kt`.
Ambos flujos ven `RealityStore.kt` en el trigger y validan el permiso histórico fijo antes de ejecutar los casos; las dependencias históricas no fueron editadas. Quedan por obtener regresiones de ambos flujos sobre el árbol de código ya publicado.

## B2
Reusar `scripts/quality/faro-protocol-gate.py --mode ci --head HEAD` con fetch-depth 0. La auditoría revisa archivos mutados, unidad del mensaje, B1–B5 del padre y B7 previo usando la cadena duradera. Esta reparación consiste en usar el mismo control en ambos flujos afectados. Sin cambios en V4.4 ni en producto, Kotlin, tests o fixtures de las unidades certificadas.

## B3
Mantener preflight fijo a una unidad cerrada vuelve a saltar por archivo compartido. Eliminar los triggers perdería pruebas de regresión y quitar validación degradaría el gate. Se conserva el trigger y se elige validación del SHA en ejecución con el verificador oficial, que resuelve el permiso exacto del commit real.

## B4
Solo `.github/workflows/faro-v5-s4-w01-b3b1b1b1b1a-read-revoke-directed.yml` y `.github/workflows/faro-v5-s4-w01-b3b1b1b1b1b1c-post-commit-policy-notice.yml`. Cambiar el paso de preflight fijo por la auditoría real. Mantener checkout, historial completo, suite, reportes y artefactos. READY EXACT2 en checkpoint separado después del guard documental.

## B5
Comprueba fail-closed, ejecución sin bypass, scopes históricos intactos y 6/6 del delta inicial con identidad de blobs inalterada. Validar los dos jobs sobre SHA de RED_FIX, sus pruebas y guard. Si un job falla de nuevo, detener el cierre y revisar su causa real. W2 STOP y V2 productiva OFF.

## B7
**RED_FIX**. El cutover DEV dirigido pasó sus seis escenarios, pero los dos flujos colaterales quedaron rojos antes de ejecutar pruebas por la autorización fija. Única corrección habilitable: CI EXACT2 del mismo unit. Ningún B7 PASS final ni otra unidad funcional hasta resolver esos resultados.
