# FARO V5 · W05-A1 — registro de preparación anticipada B1–B5
Checkpoint base `d28bfae7199cedce988ae2e7a0376ea6e09b7191`, 2026-11-25. Se prepararon siete auditorías previas completas en estructura (B1–B5), **CONDICIONALES**. Ninguna constituye permiso READY, porque cada B7 predecessor altera el HEAD y posiblemente el scope del sucesor. Es incorrecto llamar a esto certificación B1–B5 independiente previa al commit predecesor.

## Estado por unidad
| Unidad | Documento | Trabajo que Work evita repetir | Qué falta antes de B6 |
| --- | --- | --- | --- |
| A1C0B2 | `docs/v5/FARO_S4_W05_A1C0B2_SQL12_CAPTURE_RESTORE_B1_B5_PREAUDIT_20261125.md` | Investigación, alternativas, vías de reutilización, riesgos y matriz de negativas | B7 anterior + cotejar rutas exactas + delta audit + guard + READY propio |
| A1C0B3 | `docs/v5/FARO_S4_W05_A1C0B3_SQL12_SAFE_CUTOVER_B1_B5_PREAUDIT_20261125.md` | Investigación, alternativas, vías de reutilización, riesgos y matriz de negativas | B7 anterior + cotejar rutas exactas + delta audit + guard + READY propio |
| A1C0B4 | `docs/v5/FARO_S4_W05_A1C0B4_MANUAL_PURCHASE_APPLY_B1_B5_PREAUDIT_20261125.md` | Investigación, alternativas, vías de reutilización, riesgos y matriz de negativas | B7 anterior + cotejar rutas exactas + delta audit + guard + READY propio |
| A1C1 | `docs/v5/FARO_S4_W05_A1C1_STOCK_RECEIVE_REVIEW_APPLY_B1_B5_PREAUDIT_20261125.md` | Investigación, alternativas, vías de reutilización, riesgos y matriz de negativas | B7 anterior + cotejar rutas exactas + delta audit + guard + READY propio |
| A1C2 | `docs/v5/FARO_S4_W05_A1C2_STOCK_UNDO_RECOVERY_B1_B5_PREAUDIT_20261125.md` | Investigación, alternativas, vías de reutilización, riesgos y matriz de negativas | B7 anterior + cotejar rutas exactas + delta audit + guard + READY propio |
| A1C3 | `docs/v5/FARO_S4_W05_A1C3_ANDROID_STOCK_REVIEW_B1_B5_PREAUDIT_20261125.md` | Investigación, alternativas, vías de reutilización, riesgos y matriz de negativas | B7 anterior + cotejar rutas exactas + delta audit + guard + READY propio |
| A1C4 | `docs/v5/FARO_S4_W05_A1C4_GLOBAL_DEV_ACCEPTANCE_B1_B5_PREAUDIT_20261125.md` | Investigación, alternativas, vías de reutilización, riesgos y matriz de negativas | B7 anterior + cotejar rutas exactas + delta audit + guard + READY propio |

## Alcance cerrado ahora
A1C0B1 FBS2v9 tiene B1–B5 **VERIFIED** (no condicionales), permiso READY exact4 ya emitido en el checkpoint base. Nada de lo anterior sustituye o modifica su permiso. Work debe empezar por su B6.

## Bloqueadores descubiertos y pre-resueltos en documentos
1. **Arquitectura SQL:** generation no incorpora driver SQLite de producción; usar port de Reality para SQL12 y adapter en generation. No abrir ACTIVE desde un port STAGED ni versionar SQL11 silenciosamente.
2. **Operación oficial:** `shopping.purchase.record` N-036 existe (catálogo oficial TSV blob `5653a9758f682f683a852dcf286211bae71c41d0`). No inventar purchase.confirm.
3. **Idempotencia:** Reality12 compra tiene purchase_ref PK pero sin intent_key+revision global; no basta para prometer Apply CAS. Validar receipts y/o evolución versionada antes de confirmar compras.
4. **H11R legacy:** receipts antiguos sin registro de compra no pueden inferir compra; conservarlos para auditoría, bloquear recepciones nuevas sin origen real admitido.
5. **Undo stock:** schema12 no certifica evento compensatorio append-only ni operación catálogo de Undo; inspeccionar y si necesita SQL13+FBS2v10, hacerlo como unidad con permiso separado.
6. **Android:** emulador no equivale al Moto G15 físico, y el fin de DEV no autoriza productivo.
7. **Protocolo:** permiso BLOCKED tras B7 es consumo normal; Work debe preparar delta B1–B5 del siguiente, emitir READY individual y seguir B6 sin pedir otra ronda de preparación si el alcance DEV ya autorizado lo permite.

## Para Work
Leer `docs/v5/FARO_S4_W05_A1_WORK_TURBO_CONTINUOUS_MISSION_20261125.md`, la misión de continuidad, este registro y **solo el documento de la unidad inmediata** al finalizar B7 de la anterior. Reconcilie con los códigos nuevos. No rehaga el análisis general; realice solo el delta estrictamente necesario. Conservar último B7/CI/XML por SHA. Si no hay autoría/composición/grant válida, no escribir stock, documentar bloqueo real.