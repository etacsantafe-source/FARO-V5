# FARO W01 B3B2 · Apertura confirmada Finance9

## B6

SHA `b324911839c7d1893d637fea9c80e614bff0584c`. El grant de alta F02 exige una apertura declarada y la relaciona con un receipt en la misma transacción. El owner comprueba la apertura física y conserva el cero explícito. Ante discordancia se revierten cuenta, vínculo, grant y receipt. La lectura ACTIVE9 usa un snapshot WAL con admisión antes y después. Conserva desconocidos heredados incluso si existe una cifra física anterior a la confirmación.

## B7

PASS acotado a Finance9 DEV, JVM con SQLite real. Guard 44322715581 SUCCESS. XML verificados de forma independiente:

- Reality 6/6, run 44322715808, artefacto 14436006664, ZIP `2016bf3289a09ea38fd8fbc06f516b577bcb10e5ba7acb71644b77464776ba8d`, XML `c7e71cabb57ac64a8f6c0502c75418d7e63acd49e8bffa90b39a8ab62558a874`.
- Workspace y Reality 5/5, run 44322715824, artefacto 14435842940, ZIP `cf961a14fb6ed4f7b8a2eebb4c87b9cc4dcf7797293d0ce8b5818860c723b45e`, XML `c8966a9c0b93422e4008138ae0355aa8a6c37859bb233511177846cfc901a248`.

Los casos verificaron apertura cero declarada, 100 declarados en Review, preservación de heredado desconocido, persistencia al reabrir, denegación de lectura sin admisión y rollback cuando 49 difiere de 50. En el rollback el mismo grant pudo confirmar el importe correcto en el intento original. Check local SQLite de la sentencia 4/4.

Workflows automáticos en este SHA al preparar el checkpoint: 30/30 SUCCESS. Sigue pendiente la integración Android F02/F03. Se prepara C0, el núcleo de F04 que puede avanzar con CI JVM. W2 STOP y V2 productiva OFF.
