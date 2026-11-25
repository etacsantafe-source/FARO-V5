# FARO V5 · W05-A1C2D · Restauración autenticada FBS2v10 de cinco almacenes SQL13

## B7 — PASS acotado

- Código certificado: `31bc42b5ed882d721ad9cf569965a113858fdac1`, ocho rutas funcionales exactas del permiso `S4_W05_A1C2D_SQL13_FBS2V10_FIVE_STORE_REPLAY_DEV`. El diff fue cotejado contra las ocho rutas autorizadas. `main` y V4.4 intactas.
- [Guardia 45617021480](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45617021480): `SUCCESS` sobre el SHA del código. Preflight del permiso del padre, validación del alcance y protocolo de siete bloques PASS.
- [CI dirigida 45617021774](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45617021774): `SUCCESS`, job `139214933897`. Compilación Gradle + prueba SQL13 v10 8/8, regresión v9 8/8 y base v8 8/8; `24/24`, sin fallos, errores ni omitidas. El paso de CI comprobó los tres XML JUnit originales antes de aceptar el resultado.
- [Artefacto original 15023566071](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45617021774/artifacts/15023566071) `w05-a1c2d-sql13-five-store-replay-xml` publicado y no expirado. No declarar hash ZIP/XML adicional sin verificación propia de bytes.
- Reutiliza el replay base v8 y la importación v9 por versión explícita. El replay v10 autentica el ciphertext original, fija identidad y puntero, migra SQL9→10→11→12→13 solo en generación STAGED inactiva con host cerrado, conserva base histórica, Finance9, Necesidades y Evidence2, aplica compras/recepciones/compensaciones en una transacción Reality13 y verifica semántica de los cinco almacenes tras reapertura.
- La suite prueba Evidence1/Evidence2, replay idempotente del mismo archivo, reemplazo de ciphertext denegado, v9 importado sin compensaciones, contraseña incorrecta, corrupción, interrupción previa a commit, revocación/ABA y divergencia de owner. Las pruebas no constituyen activación ni certificación de stock físico.
- **Pendiente fuera de esta unidad:** SQL13 cutover/ACTIVE, N-016 Undo compensatorio ejecutable con autorización, recuperación real PREPARED/COMMITTED/UNKNOWN a nivel de host, Android DEV y A1C4 aceptación integral. Moto G15 físico `NOT_RUN`. V2 productiva, Apply productivo, stock real, pagos, Auto/Noche `OFF`.
- Permiso A1C2D consumido a `BLOCKED`; siguiente unidad precisa B1–B5 delta, guardia y READY individual. Writer debe quedar `RELEASED` y el puntero reconciliado antes del próximo código.
