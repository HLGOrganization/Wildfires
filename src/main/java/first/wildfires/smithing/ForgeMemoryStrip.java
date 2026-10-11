package first.wildfires.smithing;

import net.dries007.tfc.common.capabilities.forge.ForgeStep;

import java.util.ArrayList;
import java.util.List;

/**
 * 铁砧界面顶上那条记忆带：玩家上次在这个配方上敲出的步骤，五格一字排开，中间那格是接下来要敲的一击。
 *
 * <p>中间那格是不透明的，往左右各两格逐渐变淡，所以一眼就能看出「现在敲这个，敲完往这边走」。带上
 * 显示的是记忆里的一整段，而不是剩下的部分：敲过的那几格仍然留在左边，玩家能从自己已经走到哪儿看出
 * 前后文。走到记忆尽头时中间那格会落到记忆之后 - 那说明这一件已经敲完了，本该出成品了。
 *
 * <p>中间那格还会核对玩家实际敲下去的那一击（{@link #deviated}）：和记忆里对不上的时候界面会给它
 * 换个颜色，好让玩家知道这一件已经偏离了记录，一键回放不会再替他把这一件接着敲完。
 *
 * <p>这里只有摆格子的纯逻辑，画的部分在 {@code first.wildfires.client.smithing.AnvilMemoryStrip}。
 */
public final class ForgeMemoryStrip {

    /** 中间那格两边各显示几格。 */
    public static final int RADIUS = 2;
    /** 一共几格。 */
    public static final int CELLS = RADIUS * 2 + 1;
    /** 手册贴图里一格图标的边长，也是这条记忆带上一格的边长。 */
    public static final int ICON_SIZE = 16;

    /** 从中间往两边的透明度：中间实心，往外第一格 60%，最外面那格 40%。 */
    private static final float[] FADE = {0.40F, 0.60F, 1.0F, 0.60F, 0.40F};

    /**
     * 贴图 {@code textures/gui/forging_step_icons.png} 里 8 个步骤的排列顺序，和 TFC 铁砧上那 8 个
     * 步骤按钮的图标帧是同一套：TFC 的图标帧下标是 {@code (ordinal + 4) % 8}，这里就是把它排成一排。
     */
    public static final List<ForgeStep> ART_ORDER = List.of(
            ForgeStep.PUNCH, ForgeStep.BEND, ForgeStep.UPSET, ForgeStep.SHRINK,
            ForgeStep.HIT_LIGHT, ForgeStep.HIT_MEDIUM, ForgeStep.HIT_HARD, ForgeStep.DRAW);

    private ForgeMemoryStrip() {
    }

    /** 记忆带上的一格：某一步，或者留白；{@code alpha} 是它从中间往外该用的透明度。 */
    public record Cell(ForgeStep step, int index, int offset, float alpha) {

        /** 是不是正中间那格 - 也就是接下来要敲的一击。 */
        public boolean current() {
            return offset == 0;
        }

        /** 这一格有没有步骤可显示。 */
        public boolean isEmpty() {
            return step == null;
        }
    }

    /**
     * 摆出这条记忆带。
     *
     * @param memory 玩家上次敲出的完整步骤
     * @param struck 这一件已经敲过几击，也就是接下来该敲第几击（TFC 记的敲击数就是它）
     */
    public static List<Cell> of(List<ForgeStep> memory, int struck) {
        // 敲过头的时候把中间那格停在记忆的末尾，而不是让整条带子空掉：那样玩家还能看见自己敲到哪儿了。
        int next = clamp(struck, 0, memory.size());
        List<Cell> cells = new ArrayList<>(CELLS);
        for (int offset = -RADIUS; offset <= RADIUS; offset++) {
            int index = next + offset;
            ForgeStep step = index >= 0 && index < memory.size() ? memory.get(index) : null;
            cells.add(new Cell(step, index, offset, FADE[offset + RADIUS]));
        }
        return List.copyOf(cells);
    }

    /**
     * 这一件敲下的最后一击，是不是偏离了记忆里同一位置的那一步。
     *
     * <p>还没有敲过、或者没有记录的时候不算偏离；敲过的次数已经超出记忆的长度也算 - 那说明这一件被敲得
     * 比记录里那次更远，记忆没法再描述它了。
     */
    public static boolean deviated(List<ForgeStep> memory, int struck, ForgeStep last) {
        if (last == null || struck <= 0) {
            return false;
        }
        if (struck > memory.size()) {
            return true;
        }
        return memory.get(struck - 1) != last;
    }

    /** 这串步骤一共把工作值推到多少。一次成功的锻造里，它就是那个配方的目标值。 */
    public static int work(List<ForgeStep> steps) {
        int sum = 0;
        for (ForgeStep step : steps) {
            sum += step.step();
        }
        return sum;
    }

    /** 这一步在贴图里的横坐标，按 {@link #ART_ORDER} 排。 */
    public static int iconU(ForgeStep step) {
        return ART_ORDER.indexOf(step) * ICON_SIZE;
    }

    private static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }
}
