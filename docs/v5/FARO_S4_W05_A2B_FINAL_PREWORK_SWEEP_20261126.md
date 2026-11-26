# FARO V5 · A2B · última revisión de B1–B5

Fecha 26/11/2026. Baseline ddb20e57672c525122672d948435b312e1a9c159. A2B0A B7 PASS XML7/7 y guardia 45811909488 SUCCESS.
9/9 unidades B1–B5 DOCUMENTADAS. SOLO A2B0B1 tiene los bloques VERIFICADOS y permiso READY exact4; ocho sucesoras PREAUDITADAS CONDICIONALES, sin READY. No atribuir B6/B7 ni tests a futuros bloques.

## Hallazgos concretos

1. Dependencia C0/C1 circular: C0 exigía ticket SQL14 COMPLETED que C1 recién generaría. Cambiar orden a B0B3 -> C1 (health sobre STAGED, cutover journaled) -> C0 (reopen ACTIVE14 con ticket real). NO falsear selección.
2. SQL13 reader hardcode: RealityHousehold13CompensationRowsPort.captureConnection verifica versión13. B0B2 debe implementar lectura histórica sobre schema14 sin debilitar validación13.
3. Replay legado: FaroV5Household13V10StagedReplay.restoreAuthenticatedV10 acepta stages 9..13. B0B3 debe restaurar hasta13 antes de migrar copia a14 y adjuntar ledger14.
4. Catálogo probado: v5/spec/generated/contract-baseline.tsv contiene stock.reserve y stock.release con capability N-035, household, REALITY_MUTATION, BINDABLE. No heredar consentimiento N-035 de stock.receive.
5. Cross-operation: físico3/reservado2, Undo N-016 que deja físico1 debe ser denegado atómicamente o mantenerse deshabilitado en SQL14 hasta enlazar su protección; no receipts parciales.
6. Primera unidad A2B0B1 no modificada; conservar blob 8ef57db05d35acc6e9a062235f4e13499328cb49 y permiso actual exact4. Campo available continúa NO certificado antes de A2C. No tocar main/V4.4, V2 productiva, stock real, pagos ni Auto/Noche. G15 NOT_RUN.

## B3 · fuentes externas primarias

SQLite WAL https://www.sqlite.org/wal.html: commit puede residir en WAL; no copiar archivo ACTIVE arbitrariamente. SQLite transactions https://www.sqlite.org/lang_transaction.html y backup https://www.sqlite.org/backup.html: atomicidad y consistencia. Android offline-first https://developer.android.com/topic/architecture/data-layer/offline-first: fuente local canónica. Grocy https://github.com/grocy/grocy/blob/master/grocy.openapi.json: referencia de bookings, no importar servidor/código.

## Orden corregido para Work

A2B0B1 -> A2B0B2 -> A2B0B3 -> A2B0C1 -> A2B0C0 -> A2B1A -> A2B1B0 -> A2B1B1 -> A2B1C.
Work inicia B6/B7 A2B0B1 con permiso READY; para cada sucesora reauditar solo delta tras B7 anterior, emitir guardia+permiso propios, ejecutar preflight y CI original XML; documentar B7 y liberar writer CAS. No falsos PASS.
