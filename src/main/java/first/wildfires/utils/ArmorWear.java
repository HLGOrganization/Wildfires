package first.wildfires.utils;

import net.minecraft.world.item.ItemStack;

/**
 * 护甲耐久阶段（老大 2026-10-18 定）：剩 ≤50% / ≤25% / ≤10% 时，
 * 该件的各项数值分别乘 0.85 / 0.70 / 0.55（每档 −15%，累计）。
 *
 * <p>护甲值、护甲韧性、TFC 的斩击/穿刺/钝器抗性都用这一份系数，保证三者同步衰减。
 */
public final class ArmorWear {

    /** 耐久剩余比例阈值（从高到低，必须与 {@link #FACTORS} 一一对应） */
    private static final float[] RATIOS = {0.50F, 0.25F, 0.10F};
    /** 对应系数：每档再 −15% */
    private static final float[] FACTORS = {0.85F, 0.70F, 0.55F};

    private ArmorWear() {
    }

    /**
     * @return 1.0（>50%）、0.85（≤50%）、0.70（≤25%）、0.55（≤10%）；
     *         无耐久的物品（maxDamage ≤ 0）恒为 1.0
     */
    public static float factor(ItemStack stack) {
        int max = stack.getMaxDamage();
        if (max <= 0) {
            return 1.0F;
        }
        float ratio = (max - stack.getDamageValue()) / (float) max;
        for (int i = 0; i < RATIOS.length; i++) {
            if (ratio <= RATIOS[i]) {
                return FACTORS[i];
            }
        }
        return 1.0F;
    }
}
