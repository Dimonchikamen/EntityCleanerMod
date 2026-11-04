package ru.lagsweep;

/**
 * Статический фасад к активному экземпляру SweepEvents — используется командой /lagsweep.
 */
public final class SweepController {

    private SweepController() {}

    public static void requestImmediateSweep() {
        SweepEvents ev = SweepEvents.instance;
        if (ev != null) ev.forceSweepNextTick();
    }

    public static void restartCycle() {
        SweepEvents ev = SweepEvents.instance;
        if (ev != null) ev.restartCyclePublic();
    }

    public static long ticksUntilSweep() {
        SweepEvents ev = SweepEvents.instance;
        return ev != null ? ev.ticksUntilSweepValue() : 0L;
    }

    public static int[] whitelistStats() {
        SweepEvents ev = SweepEvents.instance;
        return ev != null ? ev.whitelistStatsValue() : new int[]{0, 0, 0};
    }
}
