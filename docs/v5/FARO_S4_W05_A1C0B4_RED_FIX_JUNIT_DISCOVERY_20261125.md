# FARO V5 · W05-A1C0B4 · Descubrimiento del caso de revisión y puntero ABA

## B7 — RED_FIX

- SHA `5323a040dc2f6a48c37107d0171ed5ff2b97b315`. Guardia 45601527290 SUCCESS. CI 45601527663 FAILURE en el control XML posterior a Gradle.
- Artefacto original 15015394156, ZIP SHA-256 `5d19b1d898264b891bc002c8786fc10bc7c86077ce1f7945693a6dc1eeecd9d3`. XML SHA-256 `4752e98d640326bed6403dc37388b7a5879e4a85b49bc7aab5704ccec1e53e67`, siete casos aprobados, cero fallos, errores u omitidas.
- El caso `staleEpochAndFullPointerAbaRejectAlreadyApprovedPlan` no fue descubierto. Su cuerpo de expresión infiere el tipo de la última aserción JUnit y deja una firma JVM con retorno. Declarar `Unit` en ese único método, conservar las aserciones y repetir las ocho pruebas y la guardia por SHA con XML original.
