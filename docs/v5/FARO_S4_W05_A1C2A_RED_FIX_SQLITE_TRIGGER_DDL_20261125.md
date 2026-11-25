# FARO V5 · W05-A1C2A · Canonización de DDL de trigger SQL13

## B7 — RED_FIX

- SHA `95e57e0172428a6f661095f0ad47ffac635ad987`, guardia 45608062931 SUCCESS, CI 45608063047 FAILURE, job 139184024647.
- Artefacto original 15018667110. ZIP SHA-256 `ffc71eff0d18a361d1d566a5aeb8da5c625ca8e2bff3212a95615ae6fce73d54`, XML SHA-256 `66ed2ec52eee43bdfdc7ddaecb130992f723ac0dc743dca44343b85d50676cbe`, contadores `[6, 6, 0, 0]`.
- El trigger de inserción interpola SQL multilínea con menor indentación que la cabecera. Kotlin conserva espacios al inicio de CREATE y SQLite guarda el DDL en sqlite_schema sin esos espacios. Una reproducción con SQLite confirmó que el texto vuelve a coincidir byte por byte al eliminar solamente los espacios iniciales de esa definición. El verificador de SQL13 detectó discrepancia al releer el esquema.
- Canonizar el inicio del trigger de inserción antes de instalarlo. Conservar comparación exacta del esquema, verificación de enlaces, guard de duplicación y rollback. Repetir seis pruebas dirigidas y verificar guardia y XML por SHA.
