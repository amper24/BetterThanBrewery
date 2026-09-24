package dev.moonaticks.betterThanBrewery.station;

/** Exact, all-or-nothing liquid transfers. Values are measured in configured container units. */
public final class FluidTransfer {
    private FluidTransfer() { }

    public record Mixture(int units, double quality, int ageTicks) { }

    public static boolean fits(int current, int capacity, int incoming) {
        return capacity > 0 && current >= 0 && current <= capacity && incoming > 0 && incoming <= capacity - current;
    }

    /** Combining the same fluid cannot turn a young/poor batch into an old/perfect one for free. */
    public static Mixture mix(int current, double currentQuality, int currentAge,
                              int incoming, double incomingQuality, int incomingAge) {
        int total = Math.addExact(current, incoming);
        double quality = (current * currentQuality + incoming * incomingQuality) / total;
        long age = Math.round((current * (double) currentAge + incoming * (double) incomingAge) / total);
        return new Mixture(total, quality, (int) Math.max(0, Math.min(Integer.MAX_VALUE, age)));
    }
}
