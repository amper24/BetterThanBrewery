package dev.moonaticks.betterThanBrewery.station;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FluidTransferTest {
    @Test
    void rejectsOverflowInsteadOfClampingOrDestroyingTheSource() {
        assertFalse(FluidTransfer.fits(0, 2, 3), "a three-unit mug cannot be poured into a two-unit tank");
        assertFalse(FluidTransfer.fits(9, 10, 4), "a bucket cannot pour its four units into one free unit");
        assertFalse(FluidTransfer.fits(10, 10, 1));
        assertFalse(FluidTransfer.fits(0, 10, 0));
        assertFalse(FluidTransfer.fits(11, 10, 1));
        assertTrue(FluidTransfer.fits(6, 10, 4));
        assertTrue(FluidTransfer.fits(0, 2, 2));
    }

    @Test
    void topUpsKeepVolumeAndWeightAgeAndQualityByVolume() {
        FluidTransfer.Mixture mix = FluidTransfer.mix(3, 100, 1000, 1, 20, 0);
        assertEquals(4, mix.units());
        assertEquals(80.0, mix.quality(), 1e-10);
        assertEquals(750, mix.ageTicks());
        assertEquals(0, FluidTransfer.mix(0, 100, 0, 2, 60, 0).ageTicks());
    }
}
