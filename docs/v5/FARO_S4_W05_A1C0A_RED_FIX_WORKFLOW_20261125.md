# FARO V5 · W05-A1C0A · Corrección de CI

## B7 — RED_FIX

- SHA `d16458174bb92c403dfa59239c90745a513a09c7`. Guardia 45534681275 SUCCESS. CI 45534679102 FAILURE en validación YAML (línea 12): GitHub rechazó la clave `contentts`. El job JVM no se ejecutó y no produjo XML.
- Corrección de alcance exacto: usar `contents: read` y comprobar en CI el permiso del commit padre para la única ruta del RED_FIX. Conservar el contrato, las ocho pruebas y el objetivo XML 8/8.
- A1C0A queda pendiente de CI y B7. Stock productivo, V2 productiva, pagos y Auto/Noche OFF.
