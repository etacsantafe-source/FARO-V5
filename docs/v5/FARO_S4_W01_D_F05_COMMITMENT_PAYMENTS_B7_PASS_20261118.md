# FARO W01 D · Pagos registrados de compromisos

## B6

SHA `44cd027abce43b2cd2a9be06b7afd25e109d4f14`, parent `8f4a132052c065d3b66dfc724e25384e6f91d69f`. Las siete rutas coinciden con el permiso y preflight 7/7. Los IDs canónicos de registro de deuda y ejecución de cuota delegan al mismo puerto ACTIVE9. La confirmación registra un gasto y su asignación a una cuota junto con el recibo original. El saldo revisado se compara con asignaciones válidas dentro de la misma transacción. Un pago directo sobre deuda usa una cuota manual vinculada.

## B7

PASS acotado a SQLite ACTIVE9 DEV y JVM. CI dirigida 44376679445 SUCCESS y guard 44376678868 SUCCESS. El ZIP y XML se revalidaron aparte del workflow. Artefacto 14460849029, ZIP SHA-256 `f8d07540d7f2544fe53c12f5af79863806b394bb3cb3a28a31d43e0c10678a69`, XML SHA-256 `8abe8d5797f9a30e9a586ac353c4b50efe5e8291a41c9bb913bca41c13c40eb0`. Cuatro casos, cero fallos, errores u omisiones. Se verificaron pagos de 35 y 65 sobre 100, recuperación de ambos recibos, doble asignación, saldo revisado obsoleto, exceso, moneda/cuenta/deuda, cancelación previa, payload inválido y rollback al fallar el vínculo tras insertar el gasto. La revisión de SQL confirmó que los enlaces preexistentes se comprueban contra los asientos antes de sumar; la suite dirigida cubre los datos coherentes y corrupción simulada mediante fallo en el enlace. Regresiones de altas 44376678920 y transferencias 44376678661 SUCCESS. Writer de código RELEASED epoch 1234.

Quedan lectura de vencimientos, edición de calendario y montaje Android para F05. Próximo bloque funcional presupuestos F06, B1–B5 en `docs/v5/FARO_S4_W01_E_FINANCE_BUDGET_B1_B5_20261118.md`. W2 STOP. V2 productiva OFF.
