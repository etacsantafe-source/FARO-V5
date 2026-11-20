# FARO V5 · W04-B1C1 · Review canónico de necesidades

Fecha local 2026-11-20. Unidad `S4_W04_B1C1_CANONICAL_NEEDS_REVIEW_DEV`. Complementa B1–B5 de `FARO_S4_W04_B1B_B1D_NEEDS_OPERATIONS_B1_B5_20261120.md`.

## B1 — Entrada y criterios
El commit de persistencia `51124c0e31915419075e8307d62962b61be90c23` define el snapshot Household y cuatro cambios tipados con revisión de lista y fila. El catálogo generado ya enumera las cuatro operaciones sin necesidad de editarlo. Construir una propuesta determinista desde un snapshot admitido y un objetivo humano. El objeto congelado incluirá generación y secuencia, revisión esperada, identidad interna y estado anterior/posterior. Render en español con nombre, cantidad, unidad, estado, fecha y prioridad. El request de alta recibe identidad generada por el host a partir de la identidad local de intento.

## B2 — Comparación y reutilización
Conservar el patrón de V4.4 por nombre sin importar el runtime ni su escala en micros. Adaptar la separación propuesta/Apply usada en `S4W03MatterReviewHost`: Review genera payload canónico y Apply deberá comprobarlo después. Reutilizar `CanonicalPayload` y tipos Household existentes. Cantidad almacenada en milésimos desde todos los puntos de entrada. No introducir escritura de Workspace en esta unidad.

## B3 — Alternativas sustentadas
Mantener la investigación externa de la preauditoría. Grocy es referencia de interacción por artículo. La especificación oficial de normalización Unicode, https://unicode.org/reports/tr15/, respalda una normalización explícita sólo para comparación de nombres; conservar la grafía almacenada. El payload incluye la entrada tipada exacta y se congela mediante la huella compartida de FARO. Rechazar construir Apply desde texto interpolado después de la confirmación o elegir automáticamente un match ambiguo. El binario usa una versión y campos ordenados para evitar equivalencias accidentales; verificar un decode canónico con reencode exacto.

## B4 — Delta y rutas exactas
Nombres de existentes resueltos con coincidencia normalizada única. Independizar la huella del texto localizado, congelar identidad y revisiones. Conservar preimagen explícita del snapshot en el payload para evaluación de Undo cuando se incorpore Workspace. C2 deberá almacenar su envelope mediante los puertos del kernel y revalidar el snapshot. C1 no importa Workspace ni Reality.

- `v5/owner/household/src/main/kotlin/ar/com/faro/v5/owner/household/api/HouseholdNeedReview.kt`
- `v5/owner/household/src/test/kotlin/ar/com/faro/v5/owner/household/api/HouseholdNeedReviewTest.kt`
- `.github/workflows/faro-v5-s4-w04-b1c1-needs-review.yml`

## B5 — Negativas y criterios
Verificar cuatro operaciones y texto antes/después legible, mismo payload/huella para la misma entrada, invalidación ante generación/revisión o fila cambiada, rechazo de duplicados por nombre, ausencia de snapshot, texto malformado, formato no canónico y fechas/cantidades inválidas. Sin scope SQL ni efecto material. Validar fingerprint y preimagen antes de volver elegible una propuesta. Decisiones de autorización material quedan para C2. Terminal B7 de cada unidad según su propia CI y XML.
