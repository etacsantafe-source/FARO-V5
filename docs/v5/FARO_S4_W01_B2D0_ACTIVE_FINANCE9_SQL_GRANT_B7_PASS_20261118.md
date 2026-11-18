# FARO W01 B2D0 · Autorización SQL activa Finance9

## B6

Implementación `2e6d2b23da350f24468bcda7cf2874e942bfd20c`. Reality ACTIVE9 emite política y grant en una sola transacción y consume el grant dentro del Apply que escribe el efecto y el receipt. El puerto está limitado a F02/F03 y requiere la admisión del owner. El puente STAGED conserva su contrato. El workflow D1B1C1 conserva todas sus pruebas y verifica el permiso del SHA que lo activa.

La CI inicial 44316229474 falló primero por HTTP 429 de Maven Central y luego ejecutó cuatro casos verdes y uno con un fixture incompatible con la unicidad de fingerprint. `a0913a63c2c7123322d323e9b0a1cabe93eaab23` corrige sólo el fixture. El guard documental 44317444930 marcó READY consumido en el padre. 44317684326 y 44317861933 verificaron consumo y renovación exacta.

## B7

PASS acotado a SQLite real en JVM en `a0913a63c2c7123322d323e9b0a1cabe93eaab23`. CI 44318145242 SUCCESS; artefacto 14431934924, ZIP `e7cdaca8d051ee586bac649aab019f764314cf1f0c9dafee20b1eb9890b29782`, XML `678ad676ef1222048e41c03c7dcbd4f2fcd3f42682f3601f49a49977117dda20`. Se verificaron 5 casos ejecutados, cero fallos, errores u omisiones.

- Una cuenta y un ingreso produjeron dos receipts enlazados a sus grants. Se reabrió Reality y se verificaron efectos y receipts.
- Se rechazó el segundo uso del grant original. Una emisión repetida no anuló la autorización original.
- Se denegaron schemas legacy y STAGED, operación fuera de alcance, pérdida de admisión, fingerprint distinto y vencimiento.
- SHA original: 27 de los restantes 27 workflows SUCCESS. SHA con el fixture corregido: 44318145012 guard, 44318144965 receipt History, 44318145038 G4 Reality y 44318145242 dirigido, todos SUCCESS.

W01 sigue abierto. B2D1 conecta Review y Apply; la confirmación Android y la verificación de saldos de apertura tienen sus deltas pendientes. W2 STOP, V2 productiva OFF. Las pruebas de este bloque se ejecutaron sin ADB.
