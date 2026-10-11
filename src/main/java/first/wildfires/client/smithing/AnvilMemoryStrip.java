package first.wildfires.client.smithing;

import com.mojang.blaze3d.systems.RenderSystem;
import first.wildfires.Wildfires;
import first.wildfires.smithing.ForgeMemoryStrip;
import net.dries007.tfc.common.capabilities.forge.ForgeStep;
import net.dries007.tfc.common.capabilities.forge.Forging;
import net.dries007.tfc.common.container.AnvilContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 铁砧界面顶上那条记忆带的画法：记录里的一整段步骤，五格一字排开，中间那格是接下来要敲的一击。
 *
 * <p>画在 TFC 面板的上沿之外，不盖住面板里的任何东西 - 面板顶上那条带子（规则提示和 TFC 自己记的
 * 最后三击）在 y = 8..42 那一竖条上，中间列也被占着，所以这里整条挪到面板外面去。摆格子的纯逻辑在
 * {@link ForgeMemoryStrip}，这里只管画。
 *
 * <h2>怎么画</h2>
 * 一格 16 像素，格距 18，整条 88 像素宽，按面板宽度居中。底板是一块半透明的深色，四边各一条一像素的
 * 亮边，好让带子在亮背景和暗背景上都看得清。中间那格下面画一道黄线，标出「接下来敲这个」。
 *
 * <p>中间那格说的那一击，还会在 TFC 面板里对应的那个步骤按钮上沿之上放一个朝下的小箭头：带子说
 * 「敲什么」，箭头说「按哪里」，玩家不用自己在八个按钮里找。按钮位置取 TFC 自己的
 * {@code ForgeStep.buttonX/buttonY}，那是面板内的偏移，和 TFC 摆按钮用的是同一份数，不在这里另抄一份；
 * 箭头贴图自己也带一份模板，那四个定位像素框出按钮的 16x16，贴图底边对齐按钮底边，模板里的箭头就落在
 * 按钮上沿之上。箭头是画上去的贴图而不是控件，所以点它等于点在按钮上，按钮的点击、悬停和提示都不受影响。
 *
 * <p>图标用的是灰色那张贴图，和手册的米色那张分开：带子铺在深色底板上，灰色在深色上看得清；手册是米色
 * 书页，用米色的图标才对得上。
 *
 * <p>越往两边越透明，这是画的时候用 {@code RenderSystem.setShaderColor} 现调的：仓库里所有贴图都是
 * 二值透明，只有靠这个才算得出半透明来。颜色是全局状态，画完必须还原成 (1,1,1,1)，否则后面画的界面
 * 会跟着一起变淡。
 *
 * <h2>什么时候显示</h2>
 * 铁砧上得有选了配方的料，而且这个配方得记得住步骤 - 也就是玩家自己在这个配方上手动敲完过至少一次。
 * 任何一条不成立就整条不画：没记录可看的时候画一条空带子只会让人以为是坏了。
 *
 * <p>中间那格还会跟着玩家实际敲下的那一击变色（{@link ForgeMemoryStrip#deviated}）：对不上记录的时候
 * 染成红的，好让玩家知道这一件已经偏了，一键锻造不会再替他把这一件接着敲完。
 */
public final class AnvilMemoryStrip {

    /** 8 个步骤图标排成一排的贴图，灰色那版：手册用的是同一套图的米色版。 */
    private static final ResourceLocation ICONS = Wildfires.rl("textures/gui/memory_strip_icons.png");
    private static final int SHEET_WIDTH = ForgeMemoryStrip.ICON_SIZE * ForgeMemoryStrip.ART_ORDER.size();

    /** 「下一个」的小箭头，贴图底边对齐步骤按钮的底边。 */
    private static final ResourceLocation NEXT_ARROW = Wildfires.rl("textures/gui/memory_next_arrow.png");
    /**
     * 箭头贴图整幅的尺寸：16x21，本身就是一张模板。
     *
     * <p>左右各两个定位像素（y = 5 与 y = 20）框出中间那块 16x16，就是游戏里步骤按钮的范围；左上角
     * 8x5 是朝下的箭头，横向左右各留四像素。贴图**底边**对齐按钮**底边**画，于是模板里那 5 行箭头正好
     * 落在按钮上沿之上，箭尖紧贴按钮第一行像素。
     */
    private static final int ARROW_SHEET_WIDTH = 16;
    private static final int ARROW_SHEET_HEIGHT = 21;
    /** 箭头在模板里的位置和大小；只画这一块，角上那四个定位像素留在贴图里，不上屏。 */
    private static final int ARROW_U = 4;
    private static final int ARROW_V = 0;
    private static final int ARROW_WIDTH = 8;
    private static final int ARROW_HEIGHT = 5;
    /** TFC 的步骤按钮是 16x16（{@code AnvilStepButton}）。 */
    private static final int BUTTON_SIZE = 16;
    /** 贴图比按钮高，底边对齐之后整幅要往上挪 5 像素（16 - 21）。 */
    private static final int ARROW_SHEET_TOP = BUTTON_SIZE - ARROW_SHEET_HEIGHT;
    private static final int ARROW_X = (BUTTON_SIZE - ARROW_SHEET_WIDTH) / 2 + ARROW_U;
    private static final int ARROW_Y = ARROW_SHEET_TOP + ARROW_V;

    /** 格距，比图标宽两像素，五格之间留出缝来。 */
    private static final int PITCH = ForgeMemoryStrip.ICON_SIZE + 2;
    /** 整条带子的宽度。 */
    private static final int WIDTH = (ForgeMemoryStrip.CELLS - 1) * PITCH + ForgeMemoryStrip.ICON_SIZE;
    /** 面板宽度，带子按它居中，和 {@link PerfectForgeOverlay} 量的是同一张铁砧贴图。 */
    private static final int PANEL_WIDTH = 176;
    /** 带子和面板上沿之间的空隙。 */
    private static final int GAP = 4;
    /** 底板往四边各多出来的一点边。 */
    private static final int PADDING = 3;

    private static final int PLATE = 0xC0101010;
    private static final int BORDER = 0x50FFFFFF;
    /** 中间那格下面那道黄线。 */
    private static final int MARK = 0xFFFFD24A;

    /** 偏离记录时中间那格染的颜色。 */
    private static final float DEVIATED_RED = 1.0F;
    private static final float DEVIATED_GREEN = 0.35F;
    private static final float DEVIATED_BLUE = 0.35F;

    private AnvilMemoryStrip() {
    }

    public static void render(GuiGraphics graphics, AnvilContainer menu, int guiLeft, int guiTop) {
        Forging forging = PerfectForgeOverlay.forging(menu);
        if (forging == null) {
            return;
        }
        ResourceLocation recipe = PerfectForgeOverlay.currentRecipe(forging);
        if (recipe == null) {
            return;
        }
        List<ForgeStep> memory = PerfectForgeClientData.sequence(recipe);
        if (memory.isEmpty()) {
            // 没有记录就没有可看的步骤，与其画一条空带子不如不画。
            return;
        }

        int struck = forging.getSteps().total();
        List<ForgeMemoryStrip.Cell> cells = ForgeMemoryStrip.of(memory, struck);

        int left = guiLeft + (PANEL_WIDTH - WIDTH) / 2;
        // 界面贴着屏幕顶上时上面没有地方了，这时宁可压住面板最上面那几行也不画到屏幕外去。
        int top = Math.max(2, guiTop - GAP - ForgeMemoryStrip.ICON_SIZE);

        // 底板和图标都要现调颜色，所以先统一还原一次，免得接着上一帧留下的颜色画。
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        int plateLeft = left - PADDING;
        int plateRight = left + WIDTH + PADDING;
        int plateTop = top - PADDING;
        int plateBottom = top + ForgeMemoryStrip.ICON_SIZE + PADDING;
        graphics.fill(plateLeft, plateTop, plateRight, plateBottom, PLATE);
        graphics.fill(plateLeft, plateTop, plateRight, plateTop + 1, BORDER);
        graphics.fill(plateLeft, plateBottom - 1, plateRight, plateBottom, BORDER);
        graphics.fill(plateLeft, plateTop, plateLeft + 1, plateBottom, BORDER);
        graphics.fill(plateRight - 1, plateTop, plateRight, plateBottom, BORDER);

        // 半透明必须自己开混合：上面画底板和亮边用的 GuiGraphics#fill 内部是 enableBlend → draw →
        // disableBlend，画完混合是关着的，不重新开的话下面这些 blit 的 alpha 会被整个忽略——表现就是
        // 五格图标一样实、完全不淡（数值一直是对的，只是没生效）。
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        boolean deviated = ForgeMemoryStrip.deviated(memory, struck, forging.getSteps().last());
        int x = left;
        for (ForgeMemoryStrip.Cell cell : cells) {
            if (!cell.isEmpty()) {
                float alpha = cell.alpha();
                if (deviated && cell.current()) {
                    RenderSystem.setShaderColor(DEVIATED_RED, DEVIATED_GREEN, DEVIATED_BLUE, alpha);
                } else {
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
                }
                graphics.blit(ICONS, x, top, ForgeMemoryStrip.iconU(cell.step()), 0,
                        ForgeMemoryStrip.ICON_SIZE, ForgeMemoryStrip.ICON_SIZE,
                        SHEET_WIDTH, ForgeMemoryStrip.ICON_SIZE);
            }
            x += PITCH;
        }
        // 颜色和混合都是全局状态：颜色不还原，这里之后画的所有东西都会带上最后那一格的透明度；
        // 混合不关掉，后面 TFC 自己的绘制会一直被 alpha 混合影响。
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();

        int markLeft = left + ForgeMemoryStrip.RADIUS * PITCH;
        graphics.fill(markLeft, top + ForgeMemoryStrip.ICON_SIZE + 1,
                markLeft + ForgeMemoryStrip.ICON_SIZE, top + ForgeMemoryStrip.ICON_SIZE + 2, MARK);

        // 中间那格说的那一击，贴在对应的步骤按钮上沿之上（贴图底边对齐按钮底边，模板里那四个定位像素
        // 框出按钮本身）。中间那格是空的就说明这一件已经敲完了（或者敲过了记录的长度），那时没有
        // 「下一个」可指，也就没有箭头。箭头只是贴图，输入照旧落到按钮本身上。
        ForgeStep next = cells.get(ForgeMemoryStrip.RADIUS).step();
        if (next != null) {
            graphics.blit(NEXT_ARROW, guiLeft + next.buttonX() + ARROW_X, guiTop + next.buttonY() + ARROW_Y,
                    ARROW_U, ARROW_V, ARROW_WIDTH, ARROW_HEIGHT, ARROW_SHEET_WIDTH, ARROW_SHEET_HEIGHT);
        }
    }
}
