# FARO V5 · W05-A1C0B0 · Corrección de compilación

## B7 — RED_FIX

CI 45538291237 falló en :store:reality:compileKotlin para e76a76625fffbe959b3e816283ea2cc170294b18.
El error apunta a checkSchema: los validadores sobre conexión están en el
objeto interno Household11ReceiptRowsSql. Corregir referencias del esquema
y de sus pruebas. Conservar alcance exacto de tres rutas y seis casos.
Guardia 45538291139 SUCCESS para ese SHA. Sin XML del run fallido.
