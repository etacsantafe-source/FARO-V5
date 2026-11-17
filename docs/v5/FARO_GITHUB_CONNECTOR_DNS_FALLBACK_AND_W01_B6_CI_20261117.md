# FARO V5 — transporte GitHub sin DNS local + checkpoint B6 W01
Fecha AR: 2026-11-17. Documento operacional, no cambia runtime. Unidad `S4_W01_B3B1B1B1B1A_SQL_READ_REVOCATION_OWNER_DEV`.

## Incidencia y solución aplicada
El contenedor de herramientas que ejecuta `git ls-remote https://github.com/etacsantafe-source/FARO-V5.git` devolvió `Could not resolve host: github.com`. `/etc/resolv.conf` allí anunciaba nameserver `168.63.129.16`; una conexión TCP directa a una IP de GitHub también falló. No se confirmó que GitHub estuviera caído. **No editar DNS del usuario ni Termux**, ni prometer que se restauró la red del contenedor: es una restricción de ese entorno.

Acceso alternativo VERIFICADO: el conector GitHub autenticado leyó el HEAD, writer CAS y permisos; escribió los cuatro blobs de código, creó un solo tree/commit y movió la rama con `expected_sha` exacto. El protocolo de GitHub Actions ejecuta `faro-protocol-gate.py --mode preflight` en un worktree de `HEAD^` con las cuatro rutas exactas. Eso constituye preflight oficial remoto y permite correr tests sin clone local.

## Evidencia exacta B6 / PRECI
- Base READY `69fd46d1a00b9395dfdbba488609d67347b7eca7`, B1–B5 hashes y evidencias verificados por lectura remota, permiso EXACT4 `READY`.
- Código commit `80e46887d8b46f7030fbbe4fc9c5bc93a93cb6c9`, unidad `S4_W01_B3B1B1B1B1A_SQL_READ_REVOCATION_OWNER_DEV`; diff EXACT4: port `RealityFinance9TrustedReadPolicyRevocationPort.kt`, `RealityStore.kt`, tests `RealityFinance9TrustedReadPolicyRevocationTest.kt`, workflow `faro-v5-s4-w01-b3b1b1b1b1a-read-revoke-directed.yml`.
- Guard protocolo `44125813602`: COMPLETED SUCCESS. Workflow B6 `44125813960`, preflight `HEAD^` paso SUCCESS; tests SQLite9 dirigidos R01–R08 y 2 suites afectadas ejecutándose al registrar este documento. **No marcar B7 PASS sin CI COMPLETED SUCCESS + XML real.**
- Writer liberado tras B6 epoch1113 y vuelto a adquirir **sólo** para documentar este handoff epoch1114; liberar nuevamente y comprobar su snapshot final.
- Scope: SQLite9 READ `finance.snapshot.read/finance:all`, revocación con epoch idempotente, sin notificación de revocación Android, sin UI productiva, sin saldo aplicado, sin API paga; W2 STOP, V2 productiva OFF.

## Siguiente paso
Esperar sólo el resultado verificable del run `44125813960` de este SHA; si red, leer logs y abrir RED_FIX acotado **mismo unit** sin tocar protocolo anterior. Si green, descargar/verificar logs, XML de 8 tests nuevos y 16 afectados, guard y diff EXACT4, completar B7 PASS acotado en docs y consumir permiso READY a BLOCKED en commit documental aparte. Registrar nuevo checkpoint y writer RELEASED. B3B1B1B1B1B Android notify/lifecycle y G15 después, no incluirlos en B7 de esta unidad.

## Regla reutilizable para siguientes chats
Si el contenedor no resuelve github.com, **preferir GitHub connector + GitHub Actions de SHA exacto**, no bloquear todo FARO por un DNS externo a la app. No afirmar que el script local de preflight corrió; solamente pasó en la Action del código con parent HEAD^, tras validación remota independiente de permiso/hashes.
