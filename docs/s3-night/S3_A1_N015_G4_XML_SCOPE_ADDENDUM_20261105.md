# FARO V5 — N015 storage RED_FIX G4 XML addendum · 05/11/2026

## B5 RED_FIX — expectativa exacta ampliada, sin relajación

La preauditoría `S3_A1_N015_SCHEMA7_G4_B7_RED_FIX_20261105.md` añade **un** JUnit de migración real 6→7 a los ocho métodos existentes de `RealityDomainOwnersT2Test`; el workflow `.github/workflows/faro-v5-g4-reality-domains-directed.yml` aún comprueba exactamente `(8,0,0,0)` y daría un falso rojo con 9/9. B6 autorizado a cambiar sólo esos dos literales del gate (8→9, PASS 8/8→9/9), sin tocar otras suites ni omitir errores/skips. Prueba de DROP COLUMN en base temporal, no operación sobre datos de usuario. B7 exige `G4_REALITY_DOMAIN_XML_PASS 9/9` y el resto de regresiones verdes; N015 Reality 3/3+FBS1 4/4+FBS2 7/7 ya PASS exacto en #42106829637 y no se repiten si no cambia código productivo.
