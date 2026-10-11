package first.wildfires.mixin.tfc;

import net.dries007.tfc.common.recipes.BarrelRecipe;
import net.dries007.tfc.common.recipes.inventory.BarrelInventory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把「凝乳」那三条桶配方吃掉的奶酪布还回来。
 *
 * <h2>问题</h2>
 * 桶配方只有一个物品产出位，也没有「保留输入物」这种字段，所以数据包层面
 * 既做不到「额外多产出一块奶酪布」，也做不到「不消耗奶酪布」：
 *   · {@code tfc:copy_input} 修饰器的语义是「产出换成输入物本身」（TFC 拿它做盐水腌制、
 *     染色皮革、降温、葡萄发酵），不是「除产出外再附赠输入物」——两个东西要同时给出来，
 *     一个产出位装不下；
 *   · 于是 {@code firmalife:barrel/{milk_curd,goat_milk_curd,yak_milk_curd}} 里那块布
 *     只能被吃掉（Firmalife 自己的 {@code barrel/cream.json} 之所以能返还布，是因为它的
 *     产物是奶油这种流体，物品位空着，正好把布放回去）。
 *
 * <h2>做法</h2>
 * {@code BarrelRecipe#assembleOutputs(BarrelInventory)} 是唯一把配方产出塞进桶的地方
 * （封盖配方 {@code SealedBarrelRecipe} 没有覆盖它，所以瞬时配方和封盖配方都走这里），
 * 而 {@code BarrelInventory} 自带的 {@code insertItemWithOverflow} 正是 TFC 自己往桶里
 * 塞物品用的入口：物品槽空着就放进去，被占着就进桶的 excess 列表，
 * 由 {@code BarrelBlockEntity#serverTick} 弹到桶边。在方法 TAIL 补一块布即可。
 *
 * <h2>判定规则</h2>
 * 只在「输入需要奶酪布、产出的主物品却不是奶酪布」时补一块，因此：
 *   · 三条凝乳配方 → 补回一块布（布被吃掉了）；
 *   · {@code barrel/cream} → 产物就是布，跳过，否则会凭空多给一块；
 *   · 将来新加的同类配方自动适用，不用维护配方 id 白名单。
 * 奶酪布按物品 id 现查（{@code firmalife:cheesecloth}），Firmalife 缺席时静默跳过，
 * 这样补丁不构成对它的编译依赖。
 */
@Mixin(value = BarrelRecipe.class, remap = false)
public class BarrelRecipeMixin {

    private static final ResourceLocation WILDFIRES$CHEESECLOTH = new ResourceLocation("firmalife", "cheesecloth");

    @Inject(method = "assembleOutputs", at = @At("TAIL"))
    private void wildfires$returnConsumedCheesecloth(BarrelInventory inventory, CallbackInfo ci) {
        Item cheesecloth = ForgeRegistries.ITEMS.getValue(WILDFIRES$CHEESECLOTH);
        if (cheesecloth == null) {
            return;
        }

        BarrelRecipe recipe = (BarrelRecipe) (Object) this;

        // 输入里没有奶酪布 → 这条配方和布无关（绝大多数桶配方）
        if (recipe.getInputItem() == null || !recipe.getInputItem().test(new ItemStack(cheesecloth))) {
            return;
        }

        // 产物本身就是奶酪布 → 布并没有被吃掉（cream），不能补
        if (recipe.getOutputItem() != null && recipe.getOutputItem().getEmptyStack().is(cheesecloth)) {
            return;
        }

        inventory.insertItemWithOverflow(new ItemStack(cheesecloth));
    }
}
