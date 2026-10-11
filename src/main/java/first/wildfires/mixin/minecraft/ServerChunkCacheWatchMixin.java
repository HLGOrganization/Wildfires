package first.wildfires.mixin.minecraft;

import first.wildfires.compat.minecraft.MapChunkLoadConfig;
import first.wildfires.compat.minecraft.SynchronousChunkLoadWatcher;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * <h2>服务端线程上"等区块"这件事，至少要留下名字</h2>
 * 野火已经挡住了自己这一侧会强制读区块的地方（见
 * {@link first.wildfires.mixin.minecraft.MapItemUpdateGuardMixin}），但别的模组、甚至原版自己，
 * 照样可能在服务端线程上同步要一个还没送到的区块。那种等待住在
 * {@code ServerChunkCache#getChunk} → {@code ServerChunkCache$MainThreadExecutor#managedBlock}，
 * 一旦太久，服务器就只剩一条看门狗崩溃报告，看不出是谁在等哪个区块。
 * <p>
 * 这道注入把那一次等待计时：等待真的发生了（{@code isDone} 还没真）才取时间，
 * 等完把耗时交给 {@link SynchronousChunkLoadWatcher}，超过阈值就记一条带调用栈的日志。
 * <b>它只报告，不改变行为</b>——后面那句 {@code join} 仍然需要这个区块，
 * 在这里拒绝等待只会把阻塞挪到 {@code join} 上（那里没有任务泵，等不到任何东西），
 * 抛异常则会把调用方直接打死：都不如让等待照原样结束，然后把"谁在等、等了多久、等的是哪一块"写清楚。
 * <p>
 * <b>为什么参数表这么写：</b>被重定向的调用 {@code mainThreadProcessor.managedBlock(isDone)} 是
 * 实例调用，所以处理器的第一个参数是接收者。接收者在字节码里的类型是
 * {@code ServerChunkCache$MainThreadExecutor}，它是包私有的、也没自己声明 {@code managedBlock}
 * （那是从 {@code BlockableEventLoop} 继承来的），而 Mixin 的强制转换规则要求处理器参数写成一个
 * <b>公开的父类型</b>并带上 {@code @Coerce}：这里就用 {@code BlockableEventLoop<Runnable>}，
 * 正是 {@code managedBlock} 的声明处。后面四个参数按 {@code @Redirect} 的老规矩接上被注入方法
 * （{@code ServerChunkCache#getChunk}）自己的参数，用来把日志写到具体区块上。
 * @see first.wildfires.compat.minecraft.SynchronousChunkLoadWatcher
 */
@Mixin(ServerChunkCache.class)
public class ServerChunkCacheWatchMixin {

    /**
     * 等区块的地方。等待完成之后才计时上报，所以这里多花的时间不会加进正在被观察的那次等待里。
     * <p>
     * {@code require = 0}：这是诊断用的观察者，注入点找不到（比如以后原版改了这段实现）时应该安静地
     * 不生效，而不是让游戏起不来。
     */
    @Redirect(
            method = "getChunk(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerChunkCache$MainThreadExecutor;managedBlock(Ljava/util/function/BooleanSupplier;)V"
            ),
            require = 0
    )
    private void wildfires$timeTheWaitForTheChunk(@Coerce BlockableEventLoop<Runnable> executor,
            BooleanSupplier isDone, int x, int z, ChunkStatus status, boolean requireChunk) {
        if (isDone.getAsBoolean() || !MapChunkLoadConfig.watcherEnabled()) {
            executor.managedBlock(isDone);
            return;
        }
        long started = System.nanoTime();
        executor.managedBlock(isDone);
        SynchronousChunkLoadWatcher.report(x, z, status, requireChunk, System.nanoTime() - started);
    }
}
