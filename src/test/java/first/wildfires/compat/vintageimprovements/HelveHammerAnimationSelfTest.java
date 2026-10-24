package first.wildfires.compat.vintageimprovements;

public final class HelveHammerAnimationSelfTest
{
    private static void equal(float expected, float actual)
    {
        if (Math.abs(expected - actual) > .0001F)
            throw new AssertionError(expected + " != " + actual);
    }

    public static void main(String[] args)
    {
        equal(0, HelveHammerAnimation.angle(80));
        equal(-12.5F, HelveHammerAnimation.angle(42));
        equal(-25, HelveHammerAnimation.angle(4));
        equal(-12.5F, HelveHammerAnimation.angle(2));
        equal(0, HelveHammerAnimation.angle(0));
        equal(0, HelveHammerAnimation.angle(-16));
        equal(-25, HelveHammerAnimation.interpolate(16, 0, .75F));
        equal(-25, HelveHammerAnimation.interpolate(8, 0, .5F));
        equal(HelveHammerAnimation.angle(0), HelveHammerAnimation.angle(80));
        if (Math.abs(HelveHammerAnimation.angle(4.0001F) - HelveHammerAnimation.angle(3.9999F)) > .001F)
            throw new AssertionError("impact discontinuity");
        for (int rate : new int[]{1, 2, 4, 6, 8, 16})
        {
            float peak = 0;
            for (int timer = 80; timer > 0; timer -= rate)
                for (int frame = 0; frame <= 160; frame++)
                {
                    float angle = HelveHammerAnimation.interpolate(timer, Math.max(0, timer-rate), frame/160F);
                    if (!Float.isFinite(angle) || angle > 0 || angle < -25.001F)
                        throw new AssertionError("invalid angle");
                    peak = Math.min(peak, angle);
                }
            if (peak > -24.9F) throw new AssertionError("skipped impact at rate " + rate);
        }
        System.out.println("Helve animation: endpoints, impact continuity, wrap and 6 processing rates passed");
    }
}
