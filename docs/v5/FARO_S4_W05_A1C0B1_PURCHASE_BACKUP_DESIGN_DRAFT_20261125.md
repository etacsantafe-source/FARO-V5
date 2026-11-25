# FARO V5 · W05-A1C0B1 · Respaldo autenticado de compras

## B1 — Alcance y evidencia

FBS2v8 (FaroV5Household11Fbs2Codec) incluye recibos H11R y base Evidence1/2.
A1C0B0 define Reality12 con una tabla separada que registra referencia,
artículo, cantidad, unidad, principal confirmante, hora, procedencia y revisión.
FBS2v8 y el parser SQL11 deben mantenerse exactos. Esta unidad define
el formato portable autenticado. DB capture, replay STAGED y apertura
del writer DEV requieren pruebas posteriores.

## B2 — Comparación y reutilización

Reusar el nested snapshot completo v8, el tamaño máximo 16 MiB y el
FAROBAK2 AES-GCM ya certificado, apoyándose en el patrón acotado H11R.
Mantener la separación compra/recepción/pago de P02 L11/L12 y P03 R11/R12.
La autorización de una compra no procede de la contraseña de un backup.

## B3 — Alternativas y decisión

Anidar bytes FBS2v8 intactos más una sección versionada de compras y digests
SHA-256 internos. La autenticidad pertenece a FAROBAK2; el digest no concede
autoridad. Evitar modificar el formato v8, archivar compras por separado o
insertarlas en Evidence. Autenticar antes de decodificar. Admitir importación
v8 mediante su lector exacto y marcar conjunto de compras vacío; no degradar
un error de autenticación a un fallback. Validar longitudes antes de reservar
memoria, en orden canónico por referencia, con UTF-8 estricto y sin bytes
sobrantes. Mantener un único presupuesto agregado de 16 MiB.

Referencias primarias: https://csrc.nist.gov/pubs/sp/800/38/d/final
y https://www.sqlite.org/lang_transaction.html para el requisito de consumir
una sola vista al integrar la captura SQL.

## B4 — Rutas candidatas y frontera

- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household12Fbs2Codec.kt
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStorePortableBackupCrypto.kt
- v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household12Fbs2CodecTest.kt
- .github/workflows/faro-v5-s4-w05-a1c0b1-purchase-fbs2v9.yml

Propiedad: las líneas están ordenadas por referencia única, campos de texto
acotados, cantidad 1..Int.MAX_VALUE en milésimos, unidad Household válida,
revisión positiva, fecha no negativa y origen manual explícito. Para una
recepción con referencia a una compra conocida, artículo y unidad coinciden y
la suma de cantidades no supera el total de compra. Las recepciones heredadas
pueden carecer de compra vinculada. No promover una compra heredada por ese
solo motivo. Cifrar una única carga y mantener intacta la API v8 existente.

## B5 — Matriz de casos propuesta

Round-trip autenticado con Evidence2, compra y dos recibos parciales.
Round-trip vacío y protocolo explícito de importación v8 con recibos pero
cero compras. Orden, duplicados y campos inválidos. Recepciones que exceden
total o mezclan artículo/unidad. Archivo truncado, payload adulterado,
contraseña equivocada y versión desconocida. Presupuesto de bytes/filas.
CI dirigida con XML exacto. Este borrador todavía requiere guardia de
A1C0B0 B7, verificación de las rutas y permiso READY independiente.

## Integración SQL posterior

El validador actual RealityHousehold11ReceiptRowsPort.checkSchema exige
user_version=11, aunque readRows valida el contenido por separado. El adaptador
Reality12 debe verificar su propia versión y el esquema de las dos tablas
antes de leerlas en la misma transacción. No ampliar el lector v8 para aceptar
SQL12. Para importación v8, reproducir la etapa SQL11 con el camino certificado
y migrar la copia a SQL12 con compras vacías antes de una nueva promoción
DEV. Si se importa v9, cotejar compras y recibos y aceptar solo destino vacío
o snapshot completo idéntico. Ante fallo, descartar o conservar la etapa
inactiva para diagnóstico. Las referencias de compras nuevas deben evitar
colisiones con recibos heredados. Ningún principal ni consentimiento
archivado se rehidrata como permiso de sesión.
