# FARO V5 — W04-D0 · Carrito comercial efímero — B7 PASS

Fecha local 2026-11-20. Unidad `S4_W04_D0_CART_DRAFT_DEV`. Permiso READY exact3 en `cc582f2536300c4a00d1d72112c8a1a7c42092d6`, guardia `44853071766` SUCCESS. Preflight local y remoto PASS.

## B7

**PASS_SCOPED_DEV.** Código `7ba9c5c97dda3b8e228680954b997d9bc5d46c05`, con las tres rutas del permiso. El carrito conserva la necesidad seleccionada y el número explícito de packs como campos separados. Reutiliza C0A para verificar el estado, la revisión y el fence de cada candidato. Rechaza el carrito completo si una línea queda sin autorización, está vencida, repite una necesidad o incumple los límites. Las líneas son inmutables y el fingerprint canónico es estable ante cambios en el orden de entrada.

- Guardia del código `44853703753` SUCCESS, job `136690784861`.
- CI dirigida `44853703822` SUCCESS, job `136690784731`, `:app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W04CommercialCartDraftTest`.
- Artefacto `14686911469` descargado en B7 y vinculado por GitHub Actions al SHA exacto. El XML contiene 12 casos distintos, con cero fallos, errores u omisiones. Los casos cubren T01–T12 de la preauditoría.
- SHA-256 ZIP `e36ef7c51abedac90c3f740faaca297876c5d2292c1203fb3606b4255c4fab09`.
- SHA-256 XML `dde6333f31afa98a7afe840c1626f5dbb92a2770e43e27310cf85ad3bf5f0bd4`.

Alcance JVM DEV. D0 es efímero y de solo lectura. La selección de packs no calcula precios, conversiones ni stock. D1–D4 siguen fuera del permiso. Android y G15 quedan NOT_RUN. V2 productiva OFF. Este checkpoint consume READY y prevé liberar el writer del epoch 1489 tras publicar.
