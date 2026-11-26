# FARO V5 · W05-A2B0B1 · FBS2v11 tipado y cifrado

Unidad: `S4_W05_A2B0B1_AUTH_FBS2V11_RESERVATIONS_CODEC_DEV`. Base c4d1901549005e7fdb990eb5b83aba62099eb01c. Estado **PREAUDITADO PARA GUARDIA; aún no READY ni B6/B7**. Evidencia B1–B5 de diseño, NO son resultados de tests. V2 productiva, stock real, pagos, Auto/Noche OFF. Moto G15 NOT_RUN.

## B1 — Evidencia

Entrada: A2B0A B7 PASS. FBS2v10 autentica cinco stores, compras y compensaciones; SQL14 STAGED B7 7/7 creó tabla de eventos vacía. Falta respaldar ledger RESERVE/RELEASE dentro de FAROBAK2 sin cambiar v10.

## B2 — V4.4 y V5

Aprovechar FaroV5Household13Fbs2Codec + FaroV5FiveStorePortableBackupCrypto, recibos y modelos Household14. V4.4 separaba comprado/recibido/reservado/consumido sin copiar su DB.

Matriz V4.4: `docs/v5/FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md`; reutilización: `docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md`. No copiar legado ni repetir B7 previo.

## B3 — Soluciones externas

ADAPTAR SQLite STRICT https://www.sqlite.org/stricttables.html, OWASP Crypto Storage https://cheatsheetseries.owasp.org/cheatsheets/Cryptographic_Storage_Cheat_Sheet.html; REFERENCIA Grocy https://github.com/grocy/grocy/blob/master/grocy.openapi.json, servidor ajeno y sin código reutilizado. DESCARTAR nuevo cifrado y contador reserved mutable: riesgo de compatibilidad y pérdidas causales.

Sin nuevas dependencias, servicios ni APIs pagas. Ante límites reales, retener control local y pruebas adversariales; los proyectos externos son patrones/referencia, no permisos.

## B4 — Diseño y límites

Nuevo formato canónico FBS2v11: sección base v10 **byte-exacta**, sección eventos de reserva, longitud+SHA-256 por sección y límite total heredado. Evento tipado eventId,intentKey,type,reservationRef,stockItemRef,unit,quantityThousandths,principal,recordedAtMs,revision. Determinismo, UTF-8 estricto, orden único por revision, control de cantidades Long y releases parciales; validar reserved neto por SKU+unidad+principal no mayor a físico neto recibido y compensado. Crypto FAROBAK2 ya existente, sin tocar v10 ni conceder permisos con decode.

**Rutas exactas a autorizar tras guardia documental:**

1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household14Fbs2Codec.kt`
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStorePortableBackupCrypto.kt`
3. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household14Fbs2CodecTest.kt`
4. `.github/workflows/faro-v5-s4-w05-a2b0b1-fbs2v11-codec.yml`

No main, V4.4, ni activación de stock productivo. Toda ruta adicional obliga a rehacer solo el delta del permiso.

## B5 — Pruebas negativas propuestas

- T01 FBS2v11 ida/vuelta con compra10, recibos7+3, Undo7, reserva2/liberación1; base v10 idéntica.
- T02 Rechazar exceso sobre físico3, release ajena, duplicados y sobreliberación acumulada.
- T03 Rechazar digests/truncado/UTF8 inválido, 8193 eventos, colisión de revisiones y overflow.
- T04 Contraseña/ciphertext incorrectos bloquean antes de exponer datos; decode no escribe.
- T05 Regresión dirigida v10/v9 y XML original exacto.

Exigir guardia 7 bloques documental SUCCESS, evidencia blob SHA, permiso READY individual y preflight de **cada ruta** antes de B6; código `FARO-UNIT:S4_W05_A2B0B1_AUTH_FBS2V11_RESERVATIONS_CODEC_DEV` en línea propia, CI del SHA real, XML JUnit original tests>0 y failures=errors=skipped=0, B7 independiente, permiso consumido, writer CAS RELEASED. Para sucesores hay que reconfirmar HEAD y contrato real después del predecesor. Nunca declarar B7 desde este documento.
