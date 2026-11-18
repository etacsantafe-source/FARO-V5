# FARO W01 C0 · Registro atómico de transferencias

## B6

SHA `aef656804ed5f559f19181e9e50b00f407a89783`. El binding del ID oficial finance.transfer.record crea un débito, un crédito y su vínculo en la transacción Reality que escribe el recibo. El owner exige cuentas distintas de la misma moneda y comprueba overflow, apertura conocida y advertencia de saldo aceptada cuando corresponde. El payload binario canónico conserva la intención y los efectos tienen identidades estables.

## B7

PASS acotado a JVM y SQLite ACTIVE9. Guard 44325816939 SUCCESS. CI dirigida 44325818697 SUCCESS, artefacto 14436608119, ZIP `c2af196372de7c9a88c2e8d2cbb7eeafc71ceb692562004426fd31c208f63c82`, XML `5457d06b1cd5d5cb4721aba4c0d46da064eeb111c7b6d483486d4186924dec9c`. Cinco casos ejecutados, cero fallos, errores u omisiones. El caso positivo reabrió ambas partidas y el recibo y obtuvo el mismo recibo al resolver un retry. El caso negativo forzó el fallo del segundo efecto y conservó el ledger anterior sin recibo ni vínculo de la transferencia. También se verificaron moneda, cobertura desconocida, insuficiencia, overflow y formato canónico.

CI automática al preparar el checkpoint. 27/28 SUCCESS, pendiente 44325818560. F04 sigue pendiente de confirmación y montaje en Android DEV. W1 sigue abierto. W2 STOP y V2 productiva OFF.
