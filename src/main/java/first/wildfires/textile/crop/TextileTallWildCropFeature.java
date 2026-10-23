package first.wildfires.textile.crop;

import com.mojang.serialization.Codec;
import net.dries007.tfc.world.feature.BlockConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

/**
 * 双高野生作物的世界生成 feature。
 *
 * <p>从 textile-2.2.3 的 {@code TextileTallWildCropFeature} 移植。原实现只有一句：
 * <pre>
 * public boolean place(FeaturePlaceContext&lt;BlockConfig&lt;TextileWildDoubleCropBlock&gt;&gt; ctx) {
 *     ctx.config().block().placeTwoHalves(ctx.level(), ctx.origin(), 2);
 *     return true;
 * }
 * </pre>
 *
 * <p>注册类型 ID 必须沿用 {@code textile:tall_wild_crop}，
 * 因为 kubejs/data/textile/worldgen/configured_feature/plant/wild_crop/*.json 里写的就是它。
 */
public class TextileTallWildCropFeature extends Feature<BlockConfig<TextileWildDoubleCropBlock>>
{
    public static final Codec<BlockConfig<TextileWildDoubleCropBlock>> CODEC =
            BlockConfig.codec(block -> (TextileWildDoubleCropBlock) block, "block");

    public TextileTallWildCropFeature(Codec<BlockConfig<TextileWildDoubleCropBlock>> codec)
    {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<BlockConfig<TextileWildDoubleCropBlock>> context)
    {
        context.config().block().placeTwoHalves(context.level(), context.origin(), 2);
        return true;
    }
}