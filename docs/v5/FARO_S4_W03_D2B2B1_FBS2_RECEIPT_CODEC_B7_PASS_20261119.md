# FARO V5 — W03-D2B2B1 · B7 del codec FBS2 con recibos

Unidad S4_W03_D2B2B1_FBS2_RECEIPT_CODEC_DEV. Código 71c174a7e8f534fe946d071c6c67bc28d6069850. Alcance DEV del formato semántico. W03 y D2B2B siguen OPEN.

## B1–B5
Evidencia en docs/v5/FARO_S4_W03_D2B2B1_FBS2_RECEIPT_CODEC_B1_B5_20261119.md, SHA b92ce5e8e05a013875ea55a4c1650021a7fbac0b, guardia 44599758048 SUCCESS. El primer commit de permiso 7f302cf812a65d2b5940e2880c7b5cb942634153 falló en 44600031589 por faltar las marcas B6 y B7. Corrección documental en aaef584452374aa5e1e0cfde4ebe308ee620f6ec, guardia 44600186279 SUCCESS y preflight local exacto de tres rutas. No se programó contra el permiso defectuoso.

## B6
FBS2v5 conserva el bloque semántico Finance9 completo y una sección obligatoria de recibos de Avisos, aun vacía. Verifica tamaños, SHA-256 por sección, EOF, orden/identidad, referencia a Notice local, revisión y fingerprint. El lector legacy rechaza v5. El cambio no abre SQLite ni monta el formato en el exportador, restaurador o host.

CI 44600744259, job 135831148610 SUCCESS, guardia del SHA 44600744026 SUCCESS. El ZIP de artefacto 14566254903 se descargó y su SHA-256 adf22b02df20638ca2c5da3ba4921d0d60f5a6099a3a48de98a27fe7d2bf2ad2 coincidió con Actions. El XML se verificó de forma independiente: 5 tests, 0 failures, 0 errors, 0 skipped. Cobertura: round-trip con recibos reales y cinco stores, ledger vacío explícito y rechazo legacy, corrupción y truncamiento, duplicados/huérfanos/fingerprint/revisiones, y base Finance9 obligatoria.

## B7
PASS acotado al codec DEV. No certifica captura/restore autenticados, ni modificación del schema ACTIVE, cutover, host, UI o cierre global. READY consumido. Estado B7 final sujeto a guardia de este checkpoint y confirmación del writer RELEASED.

## Siguiente
D2B2B2: snapshot combinado y restore transaccional Auxiliary4 STAGED con cercos de puntero, archivo autenticado y verificación de los cinco owners. B1–B5 y READY propios. El backup actual descarta Class D delivery_intent, pero los flujos route, resolve y click hacen JOIN con esa tabla. La recuperación debe reconstruir únicamente la intención determinística SUPPRESSED_POLICY de Avisos locales bajo la autoridad de Auxiliary, sin crear otra Notice ni sobrescribir su revisión. No restaurar efectos OS. Tratar reanudación tras fallar antes y después de subir a schema4, pin del archivo exacto y no sobreescritura de datos ajenos. D2B2B3: cambio de generación en DEV con journal y recuperación, preflight ACTIVE4 estricto y host de Avisos con recibos bajo grants. Luego cierre D, Android E y certificación G. F opcional diferido. V2 productiva OFF y G15 NOT_RUN.
