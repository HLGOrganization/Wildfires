package first.wildfires.compat.tfc;

/**
 * The rule behind smoldering heat sources: an unlit device that has not finished cooling lights again by
 * itself as soon as it holds fuel, so a campfire or a forge whose flame went out can be fed back to life
 * without a firestarter.
 *
 * <h2>Why "still above zero" is a real window and not a euphemism for "still lit"</h2>
 *
 * <p>TFC cools a device towards its target temperature one step at a time and lands on the target exactly.
 * {@code HeatCapability.adjustTempTowards} returns {@code Math.max(temp - delta, target)}, and the target of
 * a device whose fire is out is nothing at all - the fuel temperature is zero, rain only lowers the target
 * and is clamped at zero. So a firepit that went out at 600 degrees is cold exactly 600 ticks later and not
 * one tick sooner, and the window in between is measured in real seconds: about half a minute for a firepit,
 * about two minutes for a charcoal forge, and twice both while a bellows is working the device, because
 * air halves the cooling rate.
 *
 * <h2>Why the test is {@code > 0} rather than a threshold</h2>
 *
 * <p>The rule that was asked for is "before the temperature reaches zero", so that is what is written here.
 * Any other number would be a second figure that nothing in the game shows the player, and the boundary a
 * player can actually see - the device has gone cold, and fuel stays fuel - is the one used.
 *
 * <h2>Why a device is allowed to refuse</h2>
 *
 * <p>TFC zeroes a charcoal forge's {@code HEAT} property, and the forge then extinguishes itself, whenever
 * the forge stops passing its multiblock check: the block above is neither air nor a crucible, the chimney
 * cannot see the sky, or the five blocks around it are no longer insulation. Relighting one of those would
 * burn a piece of charcoal per attempt on a flame TFC puts out again on the next random tick. The earthen
 * oven is exempt because it is built as a standalone device - it leaves its own heat alone on a neighbour
 * change and never spreads fire - so the block itself is the whole structure it needs.
 */
public final class SmolderingRules {

    private SmolderingRules() {
    }

    /**
     * @param lit              whether the device is already showing a flame: {@code LIT} on a firepit, a
     *                         non-zero {@code HEAT} on a charcoal forge.
     * @param temperature      the device's own temperature, in degrees. Zero means it has finished cooling.
     * @param deviceCanStayLit whether TFC would still let this device burn where it stands.
     * @return whether this tick should relight the device from the fuel it is holding.
     */
    public static boolean shouldRelight(boolean lit, float temperature, boolean deviceCanStayLit) {
        return !lit && deviceCanStayLit && temperature > 0f;
    }
}
