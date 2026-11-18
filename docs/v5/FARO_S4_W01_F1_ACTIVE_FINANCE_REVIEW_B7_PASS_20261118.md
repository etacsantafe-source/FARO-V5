# FARO W01 F1 · Review y Apply de Finanzas

## B6

SHA de implementación `8f283c74d53d8f1ff3dd7cfd4bc093a2a3e05d1d`. La app extiende la revisión aprobada ACTIVE9 a transferencias, declaraciones, pagos, calendarios y presupuesto. Preserva cuenta, asiento, identidad del plan, grant del dueño, AtomicApply y recibo. El texto visible se obtiene del payload y se revalida antes de autorizar. El caso adversarial se corrigió sólo en su preparación sobre SHA `0ad128358157b9411d7eabb7a18296de7acee200`.

## B7

PASS acotado a Workspace y Reality ACTIVE9 en JVM. CI 44383521628 y guard 44383521471 SUCCESS en el SHA corregido. XML 8/8 verificado fuera del workflow, sin fallos, errores ni omisiones. Artefacto 14465255684, ZIP SHA-256 `28bd3f20071284631245da1181f9c12b0ff8ff6fa822eb05014b2135ebaf4924`, XML SHA-256 `9cd6a0c152aabda552dbf36b3f21650818fbc60a119837b0ea434213f73e6671`. Pasaron las regresiones de cuenta y asiento y los casos de transferencias, declaración y pago, presupuesto, rechazo de resumen alterado y payload inválido. El writer de la corrección se verificó RELEASED en epoch 1249. El siguiente bloque debe montar esta ruta en la Activity y verificar navegación, lectura y confirmaciones en el emulador Android API35. W01 global abierto. W2 STOP. V2 productiva OFF.
