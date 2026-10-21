package first.wildfires.soldering;

import com.sighs.apricityui.screen.ApricityContainerMenu;
import com.sighs.apricityui.screen.ApricityContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 洞洞板电路编辑器的 Screen。
 *
 * <p><b>刻意和原版熔炉一样：什么点击都不覆写。</b>
 * 拿物品/放下/右键放一个/Shift 快移/取产物，全部交给
 * {@code AbstractContainerScreen} 的默认实现（AUI 的 {@code ApricityContainerScreen} 就是这么用的）。
 *
 * <p>2026-10-20 的教训：之前为了绕开 AUI 的槽命中测试，自己写了 {@code mouseClicked} 发
 * {@code handleInventoryMouseClick}，结果绕过 {@code skipNextRelease} 导致"松开又补发一次"
 * （右键放两个、按住才动）。而绕过的理由（页面 meta {@code aui-mouse-events=intercept} 吃掉点击）
 * 已经从页面删掉了 —— 那条路本来就不必走。
 *
 * <p>这里只保留构造器；界面内容全部由 {@code apricity/screens/soldering.html} 决定。
 */
public final class SolderingScreen extends ApricityContainerScreen {

    public SolderingScreen(ApricityContainerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }
}
