package first.wildfires.compat.tfc;

/**
 * The rule behind the tongs that are never put into an anvil: while sneaking on an anvil, tongs in the
 * clicking hand step aside instead of being inserted, so an item in the other hand is the one that is
 * placed, and a hand with nothing behind it is refused outright.
 *
 * <h2>The click that puts the tongs in</h2>
 *
 * <p>Sneaking on an anvil with an item in hand is TFC's gesture for putting that item into one of the
 * anvil's two slots. A hammer is what welds, and an empty hand takes the anvil's contents out instead.
 * All three are handled by {@code AnvilBlock.interactWithAnvil}, and that method answers with PASS
 * whenever it did not do anything: welding with both slots empty returns PASS, and taking from an anvil
 * that holds nothing returns PASS. Vanilla reads a PASS from one hand as "try the other one", so the very
 * same click is made a second time with the other hand - and the hand a player keeps tongs in while
 * working hot metal is exactly the one that catches that falling click. The tongs are then in the anvil.
 *
 * <p>What that costs is not the tongs. The tongs leaving the hand is what the pack's own heat damage rule
 * notices on its next pass, and with no tongs in either hand a hot stack in the inventory or in the off
 * hand is dropped on the ground and the player is burned for it. A welding session that ends with the
 * tongs in the anvil therefore ends with the player scalded by their own inventory.
 *
 * <h2>Why the tongs step aside rather than the whole click being refused</h2>
 *
 * <p>Refusing the sneak click outright would also refuse putting a workpiece in, which is the other half
 * of why the gesture exists. The tongs are the one item that is never a workpiece, they are held in the
 * hand that is not placing anything, and a player who really wants them in an anvil can still put them
 * there through the anvil screen. So they are the only item this rule takes out of the click.
 *
 * <h2>Why the answer is two different results rather than one</h2>
 *
 * <p>With something in the other hand, the tongs hand is refused with PASS, which is exactly the answer
 * the other hand needs in order to be tried - so a workpiece in the other hand is still placed in a single
 * sneak click, and the tongs are simply never the item that gets chosen. With nothing in the other hand,
 * that same PASS would fall through to TFC's empty-handed take, and taking a hot workpiece into an empty
 * hand is the accident being fixed one hand over. So with nothing in the other hand the click is refused
 * with SUCCESS instead, which spends the click without asking any hand a second time.
 *
 * <h2>What this rule deliberately does not touch</h2>
 *
 * <p>Sneaking with an empty hand still takes from the anvil, because the rule is only ever about a hand
 * that holds tongs. A hammer still welds, because a hammer is never the tongs. A plain click still opens
 * the anvil screen, because the rule is only about a sneak click.
 */
public final class AnvilInsertRules {

    private AnvilInsertRules() {
    }

    /** What should happen to a sneak click on an anvil, as far as the tongs are concerned. */
    public enum Decision {

        /** The click is not this rule's business, so TFC's own handling of it stands untouched. */
        ALLOW,

        /** Refuse the tongs in this hand, and leave the click free to be tried with the other hand. */
        TRY_OTHER_HAND,

        /** Refuse the tongs in this hand and spend the click, so that no hand is asked again. */
        REFUSE
    }

    /**
     * @param sneaking           whether the player is sneaking, which is what TFC's put-in and take-out are
     *                           bound to. A plain click opens the anvil screen instead and is never ours.
     * @param anvilTarget        whether the clicked block is one of TFC's anvils.
     * @param tongsInClickedHand whether the hand that clicked holds tongs. This is the whole point of the
     *                           rule: a click that carries anything else is left alone.
     * @param hammerInClickedHand whether that same hand holds a hammer. A hammer welds rather than being
     *                           put in, and TFC welds before it would ever insert, so a stack that is both
     *                           a hammer and tongs - which no pack ships, but tags are cheap to overlap by
     *                           accident - keeps its weld.
     * @param otherHandEmpty     whether the other hand holds nothing at all, which is what decides whether
     *                           the click has anywhere to fall through to.
     * @return what to do with this click.
     */
    public static Decision decide(boolean sneaking, boolean anvilTarget, boolean tongsInClickedHand,
            boolean hammerInClickedHand, boolean otherHandEmpty) {
        if (!sneaking || !anvilTarget || !tongsInClickedHand || hammerInClickedHand) {
            return Decision.ALLOW;
        }

        return otherHandEmpty ? Decision.REFUSE : Decision.TRY_OTHER_HAND;
    }
}
