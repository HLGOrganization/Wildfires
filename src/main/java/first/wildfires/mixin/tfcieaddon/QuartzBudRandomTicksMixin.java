package first.wildfires.mixin.tfcieaddon;

import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 让 tfc_ie_addon 的四个石英芽可随机刻（老大 2026-10-20 定）。
 *
 * <h2>为什么必须改注册时的 Properties</h2>
 * 四个方块都是原版 {@code AmethystClusterBlock}，它注册时没有调 {@code Properties#randomTicks()}，
 * 于是 {@code BlockBehaviour#isRandomlyTicking} 为 false，{@code BlockStateBase#initCache()} 把 false
 * 缓存进每个 blockstate，{@code ServerLevel#tickChunk} 里的 {@code blockstate.isRandomlyTicking()}
 * 因此永远为假 —— 这个缓存是在方块构造时（还没注册、拿不到 id）就算好的，事后改不了，
 * 所以只能在 {@code Properties} 传进构造函数之前追加。
 *
 * <h2>注入点</h2>
 * 目标类 {@code com.nmagpie.tfc_ie_addon.common.blocks.Blocks}（tfc_ie_addon 1.3.1）的静态初始化里，
 * 五个石英方块由私有静态 lambda 建出来，四个芽的 lambda 是 {@code lambda$static$3}（quartz_cluster）、
 * {@code $5}（large）、{@code $7}（medium）、{@code $9}（small），每一条都以
 * {@code new AmethystClusterBlock(int, int, Properties)} 结尾（javap 实测，参数 3/4/5/7 与 4/3/3/3）。
 * 这里对每条 lambda 里那次构造的第 3 个参数（index = 2，Properties）追加 {@code randomTicks()}。
 * {@code $5/$7/$9} 是用 {@code Properties.copy(QUARTZ_CLUSTER)} 建的，本来就会继承
 * {@code quartz_cluster} 的那个标志；仍然四条都显式加一次，避免以后 tfc_ie_addon 改注册顺序时漏掉。
 *
 * <h2>为什么不碰原版紫水晶</h2>
 * 注入点只存在于 tfc_ie_addon 的这四个 lambda 里，原版 {@code minecraft:amethyst_cluster} 不在
 * 这条路径上，标志位不会变，行为也不会变。
 *
 * <h2>require = 4</h2>
 * 四条 lambda 各命中一次，共 4 次。tfc_ie_addon 更新后如果 lambda 编号变了，启动时立刻报错，
 * 而不是静默地退回"永远不随机刻"。{@code @Pseudo} 保证 tfc_ie_addon 缺席时整个 mixin 被跳过。
 */
@Pseudo
@Mixin(targets = "com.nmagpie.tfc_ie_addon.common.blocks.Blocks", remap = false)
public abstract class QuartzBudRandomTicksMixin {

    @ModifyArg(
            method = {"lambda$static$3", "lambda$static$5", "lambda$static$7", "lambda$static$9"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/AmethystClusterBlock;<init>(IILnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V",
                    remap = false
            ),
            index = 2,
            require = 4,
            remap = false
    )
    private static BlockBehaviour.Properties wildfires$enableRandomTicks(BlockBehaviour.Properties properties) {
        return properties.randomTicks();
    }
}
