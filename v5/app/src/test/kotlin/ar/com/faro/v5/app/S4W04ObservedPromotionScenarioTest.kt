package ar.com.faro.v5.app

import ar.com.faro.v5.evidence.CommercialCurrencyCode
import ar.com.faro.v5.evidence.CommercialEligibility
import ar.com.faro.v5.evidence.CommercialEvidenceRecordOutcome
import ar.com.faro.v5.evidence.CommercialEvidenceSourceKind
import ar.com.faro.v5.evidence.CommercialEvidenceStore
import ar.com.faro.v5.evidence.CommercialEvidenceTypedTerms
import ar.com.faro.v5.evidence.CommercialLocalityKind
import ar.com.faro.v5.evidence.CommercialMerchant
import ar.com.faro.v5.evidence.CommercialObservedHistoryEntry
import ar.com.faro.v5.evidence.CommercialObservationComparisonStatus
import ar.com.faro.v5.evidence.CommercialPaymentMethod
import ar.com.faro.v5.evidence.CommercialPresentationUnit
import ar.com.faro.v5.evidence.CommercialPriceObservation
import ar.com.faro.v5.evidence.CommercialProductKey
import ar.com.faro.v5.evidence.CommercialPromotionKind
import ar.com.faro.v5.evidence.CommercialTypedPriceComparison
import ar.com.faro.v5.kernel.contracts.IdempotencyKey
import ar.com.faro.v5.kernel.contracts.PayloadFingerprint
import ar.com.faro.v5.owner.household.api.HouseholdNeedId
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class S4W04ObservedPromotionScenarioTest {
    @TempDir lateinit var root: Path
    private val yerba = CommercialProductKey("sku-yerba")
    private val rice = CommercialProductKey("sku-rice")
    private val needA = HouseholdNeedId("need-a")
    private val needB = HouseholdNeedId("need-b")
    private val motor = S4W04ObservedPromotionScenario

    private inner class Fixture(name: String) : AutoCloseable {
        val store = CommercialEvidenceStore(
            root.resolve("$name.db").toString(), clock = { 980L },
            stagedTypedTermsV2 = true,
        )
        private var sequence = 0

        fun seed(
            product: CommercialProductKey = yerba,
            merchant: String = "Market A",
            currency: String = "ARS",
            amount: Long = 12000L,
            observed: Long? = 900L,
            issued: Long? = 800L,
            validFrom: Long? = 700L,
            validUntil: Long? = 1200L,
            locality: String = "AR-SF",
            payment: CommercialPaymentMethod = CommercialPaymentMethod.CASH,
            presentationQuantity: Long = 1000L,
            presentationUnit: CommercialPresentationUnit = CommercialPresentationUnit.UNIT,
            packCount: Int = 1,
            eligibility: CommercialEligibility = CommercialEligibility.GENERAL,
            conditions: String? = null,
            kind: CommercialPromotionKind = CommercialPromotionKind.NONE,
            promoValue: Long? = when (kind) {
                CommercialPromotionKind.DISCOUNT_PERCENT -> 1000L
                CommercialPromotionKind.FIXED_AMOUNT -> 500L
                else -> null
            },
            promotionRef: String? = when (kind) {
                CommercialPromotionKind.DISCOUNT_PERCENT,
                CommercialPromotionKind.FIXED_AMOUNT -> "manual:promo-source"
                else -> null
            },
            sourceRef: String? = null,
        ): String {
            sequence++
            val ref = sourceRef ?: "manual:record-$sequence"
            val terms = CommercialEvidenceTypedTerms(
                localityKind = CommercialLocalityKind.KNOWN,
                localityCode = locality,
                issuedAtEpochMillis = issued,
                validFromEpochMillis = validFrom,
                validUntilEpochMillis = validUntil,
                presentationQuantityThousandths = presentationQuantity,
                presentationUnit = presentationUnit,
                packCount = packCount,
                paymentMethod = payment,
                eligibility = eligibility,
                conditions = conditions,
                promotionKind = kind,
                promotionValue = promoValue,
                promotionSourceRef = promotionRef,
            )
            val saved = store.recordWithTypedTerms(
                CommercialPriceObservation(
                    product, CommercialMerchant(merchant), CommercialCurrencyCode(currency),
                    amount, CommercialEvidenceSourceKind.MANUAL, ref, observed,
                ),
                terms, IdempotencyKey("d2b-$sequence"),
                PayloadFingerprint("d2b-fingerprint-$sequence"),
            )
            assertTrue(saved is CommercialEvidenceRecordOutcome.Committed)
            return ref
        }

        fun line(
            need: HouseholdNeedId = needA,
            product: CommercialProductKey = yerba,
            packs: Int = 2,
        ): CommercialCartCoverageLine {
            val projection = CommercialTypedPriceComparison.project(
                store.stagedTypedPortability.snapshot(), product,
                asOfEpochMillis = 1000L, maxObservationAgeMillis = 200L,
            )
            return CommercialCartCoverageLine(
                need, product, packs, projection.history, projection.groups,
                CommercialCartLineCoverageStatus.HISTORICAL_PARTIAL, projection.truncated,
            )
        }

        fun view(vararg lines: CommercialCartCoverageLine) =
            CommercialCartCoverageView(
                cartFingerprint = "cart-d2b-1",
                sourceWatermark = store.stagedTypedPortability.snapshot().sourceWatermark,
                asOfEpochMillis = 1000L, maxObservationAgeMillis = 200L,
                lines = lines.toList(),
            )

        fun base(
            view: CommercialCartCoverageView,
            selected: List<HouseholdNeedId> = view.lines.map { it.needId },
        ): S4W04ObservedCartBudget.Estimate {
            val choices = selected.map { id ->
                val line = view.lines.single { it.needId == id }
                val entry = line.history.first {
                    it.comparisonStatus ==
                        CommercialObservationComparisonStatus.COMPARABLE_BASE_OBSERVED
                }
                S4W04ObservedCartBudget.ExplicitChoice(
                    id, line.productKey.value, entry, view.cartFingerprint,
                    view.sourceWatermark, view.asOfEpochMillis,
                )
            }
            return (S4W04ObservedCartBudget.estimate(view, choices) as
                S4W04ObservedCartBudget.Decision.ObservedEstimate).estimate
        }

        fun choice(
            view: CommercialCartCoverageView,
            need: HouseholdNeedId = needA,
            entry: CommercialObservedHistoryEntry =
                view.lines.single { it.needId == need }.history.first {
                    it.item.terms?.promotionKind != CommercialPromotionKind.NONE
                },
        ): S4W04ObservedPromotionScenario.ExplicitPromotionChoice {
            val line = view.lines.single { it.needId == need }
            return S4W04ObservedPromotionScenario.ExplicitPromotionChoice(
                need, line.productKey.value, entry, view.cartFingerprint,
                view.sourceWatermark, view.asOfEpochMillis,
            )
        }

        fun result(
            v: CommercialCartCoverageView,
            b: S4W04ObservedCartBudget.Estimate,
            vararg choices: S4W04ObservedPromotionScenario.ExplicitPromotionChoice,
        ) = motor.simulate(v, b, choices.toList())

        fun simulated(
            v: CommercialCartCoverageView,
            b: S4W04ObservedCartBudget.Estimate,
            vararg choices: S4W04ObservedPromotionScenario.ExplicitPromotionChoice,
        ) = (result(v, b, *choices) as S4W04ObservedPromotionScenario.Decision.Simulated).scenario

        fun rejected(
            v: CommercialCartCoverageView,
            b: S4W04ObservedCartBudget.Estimate,
            reason: S4W04ObservedPromotionScenario.Rejection,
            vararg choices: S4W04ObservedPromotionScenario.ExplicitPromotionChoice,
        ) = assertEquals(
            S4W04ObservedPromotionScenario.Decision.Rejected(reason),
            result(v, b, *choices),
        )

        override fun close() = store.close()
    }

    @Test fun t01FingerprintWatermarkAndAsOfAreFenced() {
        Fixture("t01").use { f ->
            f.seed(); f.seed(kind = CommercialPromotionKind.DISCOUNT_PERCENT)
            val v = f.view(f.line()); val base = f.base(v); val p = f.choice(v)
            for (other in listOf(
                p.copy(cartFingerprint = "different"),
                p.copy(sourceWatermark = "different"),
                p.copy(asOfEpochMillis = 999L),
            )) f.rejected(v, base, S4W04ObservedPromotionScenario.Rejection.MISMATCHED_FENCE, other)
            f.rejected(v.copy(sourceWatermark = "other"), base,
                S4W04ObservedPromotionScenario.Rejection.MISMATCHED_FENCE, p)
        }
    }

    @Test fun t02UnknownNeedsAndDuplicateSelectionsRejectAtomically() {
        Fixture("t02").use { f ->
            f.seed(); f.seed(kind = CommercialPromotionKind.DISCOUNT_PERCENT)
            val v = f.view(f.line()); val base = f.base(v); val p = f.choice(v)
            f.rejected(v, base, S4W04ObservedPromotionScenario.Rejection.UNKNOWN_OR_DUPLICATE_SELECTION,
                p.copy(needId = HouseholdNeedId("missing")))
            f.rejected(v, base, S4W04ObservedPromotionScenario.Rejection.UNKNOWN_OR_DUPLICATE_SELECTION, p, p)
            f.rejected(v, base, S4W04ObservedPromotionScenario.Rejection.UNKNOWN_OR_DUPLICATE_SELECTION)
        }
    }

    @Test fun t03ForgedBaseAmountAndSourceNeverBecomeAuthority() {
        Fixture("t03").use { f ->
            f.seed(); f.seed(kind = CommercialPromotionKind.DISCOUNT_PERCENT)
            val v = f.view(f.line()); val b = f.base(v); val p = f.choice(v)
            f.rejected(v, b.copy(lines = b.lines.map {
                it.copy(estimatedMinor = it.estimatedMinor + 1)
            }), S4W04ObservedPromotionScenario.Rejection.INVALID_BASE, p)
            f.rejected(v, b.copy(lines = b.lines.map {
                it.copy(sourceRef = "manual:forged")
            }), S4W04ObservedPromotionScenario.Rejection.INVALID_BASE, p)
        }
    }

    @Test fun t04MissingDuplicateOrTruncatedPromoHistoryIsDenied() {
        Fixture("t04").use { f ->
            f.seed(); f.seed(kind = CommercialPromotionKind.DISCOUNT_PERCENT)
            val l = f.line(); val v = f.view(l); val b = f.base(v); val p = f.choice(v)
            val withoutPromo = l.copy(history = l.history.filterNot {
                it == p.selectedPromotion
            })
            f.rejected(v.copy(lines = listOf(withoutPromo)), b,
                S4W04ObservedPromotionScenario.Rejection.AMBIGUOUS_HISTORY, p)
            val duplicated = l.copy(history = l.history + p.selectedPromotion)
            f.rejected(v.copy(lines = listOf(duplicated)), b,
                S4W04ObservedPromotionScenario.Rejection.AMBIGUOUS_HISTORY, p)
            f.rejected(v.copy(lines = listOf(l.copy(truncated = true))), b,
                S4W04ObservedPromotionScenario.Rejection.INELIGIBLE_PROMOTION, p)
        }
    }

    @Test fun t05StaleUndatedIssuedAndExpiredOffersRemainIneligible() {
        Fixture("t05").use { f ->
            f.seed(sourceRef = "manual:base")
            f.seed(kind = CommercialPromotionKind.DISCOUNT_PERCENT, observed = 700L)
            f.seed(kind = CommercialPromotionKind.DISCOUNT_PERCENT, issued = null)
            f.seed(kind = CommercialPromotionKind.DISCOUNT_PERCENT, validUntil = 999L)
            val v = f.view(f.line()); val b = f.base(v)
            for (promo in v.lines.single().history.filter {
                it.item.observation.sourceRef != "manual:base"
            }) f.rejected(v, b, S4W04ObservedPromotionScenario.Rejection.INELIGIBLE_PROMOTION,
                f.choice(v, entry = promo))
        }
    }

    @Test fun t06MerchantCurrencyLocalityPaymentPresentationAndPriceMustMatch() {
        Fixture("t06").use { f ->
            f.seed(sourceRef = "manual:base")
            f.seed(kind = CommercialPromotionKind.DISCOUNT_PERCENT, merchant = "Market B")
            f.seed(kind = CommercialPromotionKind.DISCOUNT_PERCENT, currency = "USD")
            f.seed(kind = CommercialPromotionKind.DISCOUNT_PERCENT, locality = "AR-ER")
            f.seed(kind = CommercialPromotionKind.DISCOUNT_PERCENT,
                payment = CommercialPaymentMethod.DEBIT)
            f.seed(kind = CommercialPromotionKind.DISCOUNT_PERCENT,
                presentationUnit = CommercialPresentationUnit.GRAM)
            f.seed(kind = CommercialPromotionKind.DISCOUNT_PERCENT, amount = 13000L)
            val v = f.view(f.line()); val b = f.base(v)
            for (promo in v.lines.single().history.filter {
                it.item.observation.sourceRef != "manual:base"
            }) f.rejected(v, b, S4W04ObservedPromotionScenario.Rejection.CONTEXT_MISMATCH,
                f.choice(v, entry = promo))
        }
    }

    @Test fun t07RestrictedUnknownAndConditionalOffersCannotClaimSavings() {
        Fixture("t07").use { f ->
            f.seed(sourceRef = "manual:base")
            f.seed(kind = CommercialPromotionKind.DISCOUNT_PERCENT,
                conditions = "solo cuenta premium")
            f.seed(kind = CommercialPromotionKind.DISCOUNT_PERCENT,
                eligibility = CommercialEligibility.RESTRICTED)
            f.seed(kind = CommercialPromotionKind.DISCOUNT_PERCENT,
                payment = CommercialPaymentMethod.UNKNOWN)
            f.seed(kind = CommercialPromotionKind.UNKNOWN)
            f.seed(kind = CommercialPromotionKind.NONE)
            val v = f.view(f.line()); val b = f.base(v)
            for (entry in v.lines.single().history.filter {
                it.item.observation.sourceRef != "manual:base"
            }) f.rejected(v, b, S4W04ObservedPromotionScenario.Rejection.INELIGIBLE_PROMOTION,
                f.choice(v, entry = entry))
        }
    }

    @Test fun t08PercentUsesMinorUnitsAndFloorRounding() {
        Fixture("t08-main").use { f ->
            f.seed(amount = 12000L)
            f.seed(amount = 12000L, kind = CommercialPromotionKind.DISCOUNT_PERCENT,
                promoValue = 1000L)
            val v = f.view(f.line(packs = 2)); val b = f.base(v)
            val x = f.simulated(v, b, f.choice(v))
            assertEquals(24000L, x.lines.single().originalEstimatedMinor)
            assertEquals(2400L, x.lines.single().hypotheticalDiscountMinor)
            assertEquals(21600L, x.lines.single().hypotheticalAfterMinor)
            assertEquals(S4W04ObservedPromotionScenario.Completeness.MODELED_ALL_SELECTED, x.completeness)
            assertTrue(x.notice.contains("WHAT_IF"))
        }
        Fixture("t08-floor").use { f ->
            f.seed(amount = 1L)
            f.seed(amount = 1L, kind = CommercialPromotionKind.DISCOUNT_PERCENT,
                promoValue = 1234L)
            val v = f.view(f.line(packs = 1)); val b = f.base(v)
            val x = f.simulated(v, b, f.choice(v))
            assertEquals(0L, x.lines.single().hypotheticalDiscountMinor)
            assertEquals(1L, x.lines.single().hypotheticalAfterMinor)
        }
    }

    @Test fun t09FixedAmountIsOncePerLineAndCappedNotPerPack() {
        Fixture("t09-large").use { f ->
            f.seed(amount = 15000L)
            f.seed(amount = 15000L, kind = CommercialPromotionKind.FIXED_AMOUNT,
                promoValue = 40000L)
            val v = f.view(f.line(packs = 2)); val b = f.base(v)
            val x = f.simulated(v, b, f.choice(v))
            assertEquals(30000L, x.lines.single().hypotheticalDiscountMinor)
            assertEquals(0L, x.lines.single().hypotheticalAfterMinor)
        }
        Fixture("t09-once").use { f ->
            f.seed(amount = 15000L)
            f.seed(amount = 15000L, kind = CommercialPromotionKind.FIXED_AMOUNT,
                promoValue = 1000L)
            val v = f.view(f.line(packs = 2)); val b = f.base(v)
            val x = f.simulated(v, b, f.choice(v))
            assertEquals(1000L, x.lines.single().hypotheticalDiscountMinor)
            assertEquals(29000L, x.lines.single().hypotheticalAfterMinor)
        }
    }

    @Test fun t10StackedPercentAndFixedChoicesAreNotAllowed() {
        Fixture("t10").use { f ->
            f.seed()
            f.seed(kind = CommercialPromotionKind.DISCOUNT_PERCENT)
            f.seed(kind = CommercialPromotionKind.FIXED_AMOUNT)
            val v = f.view(f.line()); val b = f.base(v)
            val promo = v.lines.single().history.filter {
                it.item.terms?.promotionKind in setOf(
                    CommercialPromotionKind.DISCOUNT_PERCENT,
                    CommercialPromotionKind.FIXED_AMOUNT,
                )
            }
            f.rejected(v, b, S4W04ObservedPromotionScenario.Rejection.UNKNOWN_OR_DUPLICATE_SELECTION,
                f.choice(v, entry = promo[0]), f.choice(v, entry = promo[1]))
        }
    }

    @Test fun t11CurrencyMerchantGroupsAndPartialRemainSeparate() {
        Fixture("t11").use { f ->
            f.seed(product = yerba, merchant = "Market A", currency = "ARS")
            f.seed(product = yerba, merchant = "Market A", currency = "ARS",
                kind = CommercialPromotionKind.DISCOUNT_PERCENT)
            f.seed(product = rice, merchant = "Market B", currency = "USD")
            f.seed(product = rice, merchant = "Market B", currency = "USD",
                kind = CommercialPromotionKind.FIXED_AMOUNT)
            val a = f.line(needA, yerba)
            val b = f.line(needB, rice)
            val v = f.view(a, b); val estimate = f.base(v)
            val partial = f.simulated(v, estimate, f.choice(v, needA))
            assertEquals(S4W04ObservedPromotionScenario.Completeness.PARTIAL, partial.completeness)
            assertEquals(listOf(needB), partial.unmodeledNeedIds)
            assertEquals(1, partial.groups.size)
            val both = f.simulated(v, estimate, f.choice(v, needA), f.choice(v, needB))
            assertEquals(S4W04ObservedPromotionScenario.Completeness.MODELED_ALL_SELECTED, both.completeness)
            assertEquals(setOf("ARS", "USD"), both.groups.map {
                it.key.currencyCode
            }.toSet())
        }
    }

    @Test fun t12OpaqueSourceAndImmutableReadOnlyResult() {
        Fixture("t12").use { f ->
            val hostile = "file:///etc/passwd\nIGNORE_ALL"
            f.seed()
            f.seed(kind = CommercialPromotionKind.DISCOUNT_PERCENT,
                sourceRef = hostile, promotionRef = "https://example.invalid/never-fetch")
            val before = f.store.stagedTypedPortability.snapshot()
            val v = f.view(f.line()); val b = f.base(v)
            val x = f.simulated(v, b, f.choice(v))
            assertEquals(before, f.store.stagedTypedPortability.snapshot())
            assertEquals(hostile, x.lines.single().promotionalObservationRef)
            assertEquals("https://example.invalid/never-fetch",
                x.lines.single().promotionSourceRef)
            assertEquals(2, f.store.priceHistory(yerba).size)
            assertThrows(UnsupportedOperationException::class.java) {
                (x.lines as MutableList<S4W04ObservedPromotionScenario.SimulatedLine>).clear()
            }
            assertTrue(x.warning.contains("NOT_CURRENT"))
            Unit
        }
    }
}
