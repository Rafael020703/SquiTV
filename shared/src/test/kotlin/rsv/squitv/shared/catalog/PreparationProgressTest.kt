package rsv.squitv.shared.catalog

import org.junit.Assert.*
import org.junit.Test
import rsv.squitv.domain.model.PreparationSpeed

class PreparationProgressTest {
    @Test fun cachedItemsDoNotCreateAnEstimateAndSamplesMustBeSufficient() {
        val meter = PreparationProgress()
        assertNull(meter.remainingMs(21000, 20000, 0))
        meter.record(5, 1000, 1000)
        assertNull(meter.remainingMs(21000, 20005, 0))
        meter.record(5, 1000, 1000)
        assertEquals(396000L, meter.remainingMs(21000, 20010, 0))
    }
    @Test fun failuresSuspendTheEstimateAndCompletionIsExact() {
        val meter = PreparationProgress()
        repeat(2) { meter.record(5, 1000, 1000) }
        assertNull(meter.remainingMs(20, 10, 1))
        assertNull(meter.remainingMs(0, 0, 0))
        assertEquals(0L, meter.remainingMs(20, 20, 0))
        assertEquals(99, PreparationProgress.percent(21000, 20999))
        assertEquals(100, PreparationProgress.percent(21000, 21000))
        assertEquals(0, PreparationProgress.percent(0, 0))
    }
    @Test fun recentSamplesReplaceOldOnesInsteadOfHoldingAnObsoleteRate() {
        val meter = PreparationProgress()
        meter.record(5, 100000, 1000)
        repeat(20) { meter.record(5, 1000, 1000) }
        assertEquals(4000L, meter.remainingMs(20, 10, 0))
        assertEquals("menos de 1 min", PreparationProgress.duration(15000))
        assertEquals("2 min", PreparationProgress.duration(61000))
    }
    @Test fun failedWaveReducesPressureAndSuccessfulWavesRestoreTheSelectedMode() {
        val policy = PreparationBatchPolicy()
        assertEquals(5, policy.size(PreparationSpeed.FAST))
        assertEquals(10000L, policy.complete(PreparationSpeed.FAST, true))
        assertEquals(1, policy.size(PreparationSpeed.FAST))
        repeat(2) { policy.complete(PreparationSpeed.FAST, false); assertEquals(1, policy.size(PreparationSpeed.FAST)) }
        assertEquals(1000L, policy.complete(PreparationSpeed.FAST, false))
        assertEquals(5, policy.size(PreparationSpeed.FAST))
        assertEquals(2, policy.size(PreparationSpeed.NORMAL))
        assertEquals(1, policy.size(PreparationSpeed.SLOW))
        repeat(10) { assertTrue(policy.complete(PreparationSpeed.FAST, true) <= 80000L) }
    }
}
