package first.wildfires.compat.tfc;

/**
 * What a lit torch is allowed to light, and what that costs.
 *
 * <h2>Why a torch needs a list at all</h2>
 *
 * <p>TFC's torch already asks to start a fire: {@code TorchItem.useOn} posts a
 * {@link net.dries007.tfc.util.events.StartFireEvent} at {@code FireStrength.WEAK} before falling back to placing
 * the torch as a block. Nothing in TFC answers a weak request, so the request returns false and the torch is
 * placed - which is the behaviour being extended here rather than replaced. Answering it is therefore a matter of
 * deciding, per block, whether this is one of the handful of devices a torch should be able to light.
 *
 * <h2>Why the list is a list of ids rather than of blocks</h2>
 *
 * <p>The targets span two mods, and one of them only exists because this mod adds it. Naming them as registry ids
 * keeps the list readable next to the request it came from, keeps it free of the class loading order that reaching
 * for the block objects would impose, and lets {@code TorchIgnitionRulesSelfTest} pin the exact strings in a plain
 * JVM - a mistyped id is the one mistake in this feature that nothing else would report, because an unknown id
 * simply means a torch that refuses to light.
 *
 * <h2>Why one entry costs nothing</h2>
 *
 * <p>A burnt out torch is not a device to be lit, it is a torch to be relit, and the player is holding the item
 * that is about to become it again. Charging a torch for the exchange would make the repair cost two torches for
 * one torch's worth of light, so it is free. Every other entry consumes one torch, in survival only.
 */
public final class TorchIgnitionRules {

    /**
     * How long a mob burns when it is struck with a lit torch, in seconds.
     *
     * <p>Four seconds is Fire Aspect I, which is the strength a burning brand should carry: long enough to matter
     * in a fight, short enough that a torch is not a better weapon than a sword.
     */
    public static final int MOB_FIRE_SECONDS = 4;

    private TorchIgnitionRules() {
    }

    /**
     * The blocks a lit torch can light, named by registry id.
     */
    public enum Target {

        /** A burnt out torch on the ground. Relit in place, and the only kind that costs nothing. */
        DEAD_TORCH("tfc:dead_torch", true, false),

        /** A burnt out torch on a wall. Relit in place, keeping the face it is mounted on. */
        DEAD_WALL_TORCH("tfc:dead_wall_torch", true, false),

        /** The campfire. Lights only when it has fuel, exactly as TFC's own lighting does. */
        FIREPIT("tfc:firepit", false, true),

        /** The charcoal forge, which is the pit kiln's successor and lights from its own fuel slot. */
        CHARCOAL_FORGE("tfc:charcoal_forge", false, true),

        /**
         * This mod's unrestricted charcoal forge. It is a {@code CharcoalForgeBlock} with the insulation and
         * fire spread rules lifted, so it lights through the same block entity and the same fuel slot.
         */
        UNRESTRICTED_CHARCOAL_FORGE("wildfires:unrestricted_charcoal_forge", false, true),

        /** The blast furnace, which lights once it has been charged with fuel. */
        BLAST_FURNACE("tfc:blast_furnace", false, true),

        /** The bloomery, which lights once its iron and charcoal are in place. */
        BLOOMERY("tfc:bloomery", false, true),

        /** The pit kiln - the straw and log stack that is buried and fired to make charcoal and pottery. */
        PIT_KILN("tfc:pit_kiln", false, true),

        /**
         * The log pile: the covered stack that becomes a burning log pile and is how charcoal is made in bulk.
         * Requiring the pile to hold logs is this mod's own choice - TFC will happily burn an empty one, and a
         * torch spent on it makes no charcoal.
         */
        LOG_PILE("tfc:log_pile", false, true);

        private final String id;
        private final boolean relightsInPlace;
        private final boolean consumesTorch;

        Target(String id, boolean relightsInPlace, boolean consumesTorch) {
            this.id = id;
            this.relightsInPlace = relightsInPlace;
            this.consumesTorch = consumesTorch;
        }

        /** @return the registry id of this target, as it appears in a blockstate. */
        public String id() {
            return id;
        }

        /**
         * @return whether this target is lit by replacing it with a burning torch rather than by lighting a
         *         device. A relight also has to restart the torch's burn counter, because the block is put in
         *         place without going through the block item that would normally start it.
         */
        public boolean relightsInPlace() {
            return relightsInPlace;
        }

        /** @return whether lighting this target spends the torch that was used. */
        public boolean consumesTorch() {
            return consumesTorch;
        }
    }

    /**
     * @param id a block's registry id.
     * @return the target it lights, or {@code null} if a torch has no business lighting it.
     */
    public static Target byId(String id) {
        if (id == null) {
            return null;
        }
        for (Target target : Target.values()) {
            if (target.id.equals(id)) {
                return target;
            }
        }
        return null;
    }
}
