package first.wildfires.soldering;

/**
 * Wire protocol for board edits carried by vanilla's container-button packet.
 *
 * <p><b>Why this is not a single int.</b> The task originally assumed
 * {@code AbstractContainerMenu#clickMenuButton(Player, int)} could carry a packed coordinate. It
 * cannot: in 1.20.1 {@code ServerboundContainerButtonClickPacket} writes the button id with
 * {@code FriendlyByteBuf#writeByte} (verified with {@code javap} on the mapped 1.20.1 jar), so the
 * value on the wire is one <em>signed byte</em> (-128..127). A 12-bit wire pair does not fit.
 *
 * <p><b>What we do instead.</b> One logical operation is sent as a short ordered sequence of
 * ordinary container-button clicks on the same menu:
 *
 * <pre>
 *   control byte  0x80 | action       (8 bits, low nibble = action)
 *   data byte 0   bits 0..6  of payload
 *   data byte 1   bits 7..13 of payload
 * </pre>
 *
 * <p>Data bytes only use 7 bits each, so they never collide with the control flag. The server keeps
 * a tiny per-menu accumulator and executes the action once enough bits arrived. Packets on the same
 * connection are ordered, so the sequence is deterministic. This keeps us on the vanilla button
 * packet: no custom network channel is registered.
 *
 * <p>Payload layouts (least-significant bit first):
 * <pre>
 * PLACE        type(3) | vertical(1)&lt;&lt;3 | flipped(1)&lt;&lt;4 | x(3)&lt;&lt;5 | y(3)&lt;&lt;8     11 bits
 * REMOVE       x(3) | y(3)&lt;&lt;3                                                  6 bits
 * WIRE         x1(3) | y1(3)&lt;&lt;3 | x2(3)&lt;&lt;6 | y2(3)&lt;&lt;9                           12 bits
 * DELETE_WIRE  x1(3) | y1(3)&lt;&lt;3 | x2(3)&lt;&lt;6 | y2(3)&lt;&lt;9                           12 bits
 * COMPLETE     no payload                                                        0 bits
 * CLEAR        no payload                                                        0 bits
 * </pre>
 */
public final class SolderingActions {

    public static final int PLACE = 1;
    public static final int REMOVE = 2;
    public static final int WIRE = 3;
    public static final int DELETE_WIRE = 4;
    public static final int COMPLETE = 5;
    public static final int CLEAR = 6;

    private static final int CONTROL_FLAG = 0x80;
    private static final int ACTION_MASK = 0x0F;
    private static final int DATA_MASK = 0x7F;
    private static final int DATA_BITS = 7;

    private SolderingActions() {
    }

    /** A control byte always has bit 7 set; data bytes always have it clear. */
    public static int control(int action) {
        return CONTROL_FLAG | (action & ACTION_MASK);
    }

    public static boolean isControl(int button) {
        return (button & CONTROL_FLAG) != 0;
    }

    public static int actionOf(int controlByte) {
        return controlByte & ACTION_MASK;
    }

    public static int requiredBits(int action) {
        return switch (action) {
            case PLACE -> 11;
            case REMOVE -> 6;
            case WIRE, DELETE_WIRE -> 12;
            case COMPLETE, CLEAR -> 0;
            default -> -1;
        };
    }

    /** One control byte followed by the 7-bit groups needed to cover {@code bits}. */
    private static int[] sequence(int action, int payload, int bits) {
        if (bits < 0) {
            return new int[0];
        }
        int groups = (bits + DATA_BITS - 1) / DATA_BITS;
        int[] out = new int[groups + 1];
        out[0] = control(action);
        for (int i = 0; i < groups; i++) {
            out[i + 1] = (payload >>> (i * DATA_BITS)) & DATA_MASK;
        }
        return out;
    }

    public static int[] place(int type, int x, int y, int vertical, int flipped) {
        int payload = (type & 7)
                | ((vertical & 1) << 3)
                | ((flipped & 1) << 4)
                | ((x & 7) << 5)
                | ((y & 7) << 8);
        return sequence(PLACE, payload, requiredBits(PLACE));
    }

    public static int[] remove(int x, int y) {
        return sequence(REMOVE, (x & 7) | ((y & 7) << 3), requiredBits(REMOVE));
    }

    public static int[] wire(int x1, int y1, int x2, int y2) {
        int payload = (x1 & 7)
                | ((y1 & 7) << 3)
                | ((x2 & 7) << 6)
                | ((y2 & 7) << 9);
        return sequence(WIRE, payload, requiredBits(WIRE));
    }

    /**
     * Deleting by endpoints rather than by list index keeps the action stable if a state update is
     * in flight when the player clicks the trace; the server looks the wire up by its two holes.
     */
    public static int[] deleteWire(int x1, int y1, int x2, int y2) {
        int payload = (x1 & 7)
                | ((y1 & 7) << 3)
                | ((x2 & 7) << 6)
                | ((y2 & 7) << 9);
        return sequence(DELETE_WIRE, payload, requiredBits(DELETE_WIRE));
    }

    public static int[] complete() {
        return sequence(COMPLETE, 0, requiredBits(COMPLETE));
    }

    public static int[] clear() {
        return sequence(CLEAR, 0, requiredBits(CLEAR));
    }
}
