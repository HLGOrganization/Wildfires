package first.wildfires.compat.tfc;

/**
 * Switches for fire spread from TFC's heat sources.
 *
 * <h2>How TFC spreads fire</h2>
 *
 * <p>Every one of them funnels through a single helper, {@code Helpers.fireSpreaderTick(level, pos,
 * random, attempts)}. It walks {@code attempts} steps in random horizontal directions, and at each step
 * writes vanilla fire into the block if it is air with a flammable neighbour. The block the helper is
 * given is the one <em>above</em> the heat source, so what burns is the space in front of the fire, not
 * the fireplace itself.
 *
 * <p>Three blocks call it: {@code FirepitBlock}, {@code CharcoalForgeBlock} and {@code MoltenBlock}.
 * Because the helper takes no argument identifying its caller, an individual source cannot be filtered
 * out from inside it - the call has to be intercepted at each site.
 *
 * <h2>What is switched off here</h2>
 *
 * <p>The campfire and the charcoal forge ignore the vanilla {@code doFireTick} gamerule as far as their
 * own spread is concerned, so they are handled individually. {@code MoltenBlock} is deliberately left
 * alone: molten metal spilling and setting light to its surroundings is a hazard the players expect, and
 * it was not part of this request.
 *
 * <p>Wildfires' own charcoal stove needs no entry here - {@code UnrestrictedCharcoalForgeBlock} already
 * overrides its random tick to do nothing, which removes its spread and its cooling alike.
 */
public final class FireSpreadRules {

    /**
     * Whether TFC's campfire ({@code tfc:firepit}) should stop setting fire to what surrounds it.
     *
     * <p>Its random tick does nothing else, so the whole method is skipped.
     */
    public static final boolean DISABLE_FIREPIT_SPREAD = true;

    /**
     * Whether TFC's charcoal forge ({@code tfc:charcoal_forge}) should stop setting fire to what
     * surrounds it.
     *
     * <p>Only the spread call is skipped. The same method also puts the forge out when its insulation
     * is gone, and a forge that stayed lit inside a wooden building after being unsealed would be worse
     * than the fire it used to start, so that half is left running.
     */
    public static final boolean DISABLE_CHARCOAL_FORGE_SPREAD = true;

    private FireSpreadRules() {
    }
}
