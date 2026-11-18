# FARO W01 B2D0 · RED_FIX del caso de consumo repetido

## B1

SHA 2e6d2b23da350f24468bcda7cf2874e942bfd20c. Guard 44316229388 SUCCESS. Run 44316229474 intento 1 bloqueado por Maven Central HTTP 429 antes de tests. El intento 2 compiló y ejecutó 5 casos, 4 PASS y 1 error de fixture. Artefacto 14433036975, ZIP SHA-256 3b6c0d99dc1bdb965ca55cb158d5d6c3d5b2f1c7ff433fdab4fef4ea59aa6e5b. XML: `duplicateIntentDoesNotInvalidateTheOriginalGrant` intentó insertar un segundo `apply_attempt` con el mismo `plan_fingerprint`. La restricción UNIQUE de Reality lo bloqueó antes de invocar el gate.

## B2

Conservar la identidad estable de Apply y los receipts previstos por P03 R17 y la verificación de idempotencia de L17. El cambio acota el fixture a la conducta real del V5.

## B3

Reusar documentación de SQLite sobre restricciones UNIQUE, que confirma que el fingerprint repetido debe ser rechazado por la tabla. https://www.sqlite.org/lang_createtable.html#uniqueconst. Conservar la evidencia sobre el alcance de identificadores de reintento de Synapse en la auditoría B2D0. La alternativa de debilitar la garantía UNIQUE para permitir el fixture comprometería la idempotencia de FARO.

## B4

Una ruta: `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9ActiveDevGrantPortTest.kt`. Mantener un único intento con el fingerprint, emitir el grant, rechazar una emisión duplicada y consumirlo dos veces contra el mismo recibo dentro de una transacción real. El primer consumo debe aceptar y el segundo denegar. Confirmar el receipt y exigir un único efecto.

## B5

Mantener la comprobación de que un intento duplicado al emitir no invalida el grant original. El gate se prueba contra el recibo real de ese intento. Preservar el oráculo de cinco casos y CI dirigida sin ADB.

## B7

RED_FIX B2D0 para el SHA 2e6d2b23da350f24468bcda7cf2874e942bfd20c. La reparación se limita al caso descrito. Falta CI y verificación de su nuevo XML para emitir PASS.
