# FARO V5 · W05-A1C0B2A · Compras y recibos Reality12 STAGED

## B7 — PASS acotado

- Código `80f5d08db0dcda9882d3f5e15432ab8650a55e90`, cinco rutas exactas desde el permiso `b15c348dbaa92ee3bd09d428173169e632edaefe`. Guardia del SHA [45545699795](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45545699795) SUCCESS y preflight local/remoto PASS.
- [CI 45545699935](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45545699935) SUCCESS, job 138974666077. El [ZIP original 14991447734](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45545699935/artifacts/14991447734) se descargó y se verificó. Los XML tienen 7 casos únicos de SQL12, 6 de migración y 10 de regresión SQL11, con cero fallos, errores u omitidas.
- SHA-256 ZIP `444f75d53d59103a1c1c752460f79c105bb9c7eea0c87770367570a8439b3c2b`.
- SHA-256 XML de puerto SQL12 `59a4b9ac338d6a10f936d1edb389a7f5259df40970b8208709ce654df15f63cf`.
- SHA-256 XML de migración `797ca4645152ef87559920418ef014e58e2899b1818db6288b0e35a82fdadd72`.
- SHA-256 XML de puerto SQL11 `2b8ab793fdced0162c6dcfdd63544c23d2d28e555b286ebbbdc538c6386e546f`.
- Compra 10 y recibos de 7+3 se capturan en una sola vista SQLite y se restauran juntos en archivo sellado con verificación de versión, esquema exacto y readback. Se cubren rollback antes del commit, replay idéntico, datos históricos, necesidades y finanzas preservadas, rechazo de alteraciones, enlaces y sidecars. El DDL canónico SQL12 permanece byte-exacto.
- A1B2B histórico 45545699835 se disparó por código compartido y falló su preflight antes de ejecutar pruebas. Su suite dirigida SQL11 pasó 10/10 dentro de esta CI. W05-A1C0B0 45545699850 quedó SKIPPED y sus seis regresiones pasaron dentro de la CI dirigida.
- Consume el permiso A1C0B2A. A1C0B2B queda a cargo de admisión STAGED por generación, cinco owners y pin FBS2v9. W05-A1 DEV OPEN, Moto G15 físico NOT_RUN y producción OFF.
