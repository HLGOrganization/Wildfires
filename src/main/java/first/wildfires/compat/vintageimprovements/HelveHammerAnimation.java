package first.wildfires.compat.vintageimprovements;

/** 完整周期：95% 时间抬锤，5% 时间落锤；两段在 -25 度相接。 */
public final class HelveHammerAnimation
{
    public static final int PERIOD = 80;
    private static final float DROP_PHASE = PERIOD * .05F;

    private HelveHammerAnimation() { }

    public static float angle(float remaining)
    {
        float time = Math.max(0, Math.min(PERIOD, remaining));
        return time <= DROP_PHASE ? -25F * time / DROP_PHASE
                : -25F * (PERIOD - time) / (PERIOD - DROP_PHASE);
    }

    /** 插值相位；即使一 tick 跨过峰值，帧间仍能画出抬落过程。 */
    public static float interpolate(float start, float end, float partialTick)
    {
        float partial = Math.max(0, Math.min(1, partialTick));
        return angle(start + (end - start) * partial);
    }
}
