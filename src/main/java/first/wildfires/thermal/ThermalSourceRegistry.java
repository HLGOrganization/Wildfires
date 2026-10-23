package first.wildfires.thermal;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Unified thermal-source definitions keyed by exact blocks, block tags, and optional state properties. */
public final class ThermalSourceRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float UNUSUAL_RADIATION_RANGE = 64.0F;
    private static final Map<Block, ThermalSourceDefinition> THERMAL_SOURCES = new HashMap<>();
    private static final Map<Block, List<StateThermalSourceDefinition>> STATE_THERMAL_SOURCES = new HashMap<>();
    private static volatile List<TagThermalSourceDefinition> TAG_THERMAL_SOURCES = List.of();
    private static volatile Set<Block> REGISTERED_BLOCKS = Set.of();

    static {
        resetToBuiltins();
    }

    private ThermalSourceRegistry() {
    }

    public static synchronized void resetToBuiltins() {
        THERMAL_SOURCES.clear();
        STATE_THERMAL_SOURCES.clear();
        TAG_THERMAL_SOURCES = List.of();
        // 陶瓦热源：当初是为了方便测试热传导加的，正式版不需要（老大 2026-10-23 要求注释掉）
        // register(Blocks.BLACK_TERRACOTTA, new ThermalSourceDefinition(100.0F, 10.0F, 10.0F, 0.5F));
        // register(Blocks.WHITE_TERRACOTTA, new ThermalSourceDefinition(100.0F, 10.0F, 10.0F, 0.5F));
        // register(Blocks.RED_TERRACOTTA, new ThermalSourceDefinition(500.0F, 50.0F, 50.0F, 2.5F));
    }

    public static synchronized void register(Block block, ThermalSourceDefinition definition) {
        warnForUnusualRange(block, definition);
        THERMAL_SOURCES.put(block, definition);
        refreshRegisteredBlocks();
    }

    public static synchronized void registerState(Block block, Map<String, ?> properties,
                                                   ThermalSourceDefinition definition) {
        Objects.requireNonNull(properties, "Thermal source state properties cannot be null");
        Map<Property<?>, Object> expectedProperties = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : properties.entrySet()) {
            String propertyName = Objects.requireNonNull(entry.getKey(), "Thermal source property name cannot be null");
            Property<?> property = block.getStateDefinition().getProperty(propertyName);
            if (property == null) {
                throw new IllegalArgumentException("Block " + block + " has no state property '" + propertyName + "'");
            }
            String requestedValue = String.valueOf(entry.getValue());
            Optional<?> parsedValue = property.getValue(requestedValue);
            if (parsedValue.isEmpty() && entry.getValue() instanceof Number number
                    && Double.isFinite(number.doubleValue())
                    && number.doubleValue() == Math.rint(number.doubleValue())) {
                requestedValue = Long.toString(number.longValue());
                parsedValue = property.getValue(requestedValue);
            }
            if (parsedValue.isEmpty()) {
                String allowed = property.getPossibleValues().stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(", "));
                throw new IllegalArgumentException("Invalid value '" + requestedValue + "' for " + block
                        + " property '" + propertyName + "'; expected one of: " + allowed);
            }
            expectedProperties.put(property, parsedValue.get());
        }
        warnForUnusualRange(block, definition);
        STATE_THERMAL_SOURCES.computeIfAbsent(block, ignored -> new ArrayList<>())
                .add(new StateThermalSourceDefinition(expectedProperties, definition));
        refreshRegisteredBlocks();
    }

    public static synchronized void registerTag(ResourceLocation tagId, ThermalSourceDefinition definition) {
        Objects.requireNonNull(tagId, "Thermal source block tag cannot be null");
        warnForUnusualRange("#" + tagId, definition);
        List<TagThermalSourceDefinition> definitions = new ArrayList<>(TAG_THERMAL_SOURCES);
        definitions.add(new TagThermalSourceDefinition(TagKey.create(Registries.BLOCK, tagId), definition));
        TAG_THERMAL_SOURCES = List.copyOf(definitions);
    }

    public static synchronized void unregister(Block block) {
        THERMAL_SOURCES.remove(block);
        STATE_THERMAL_SOURCES.remove(block);
        refreshRegisteredBlocks();
    }

    public static synchronized void unregisterTag(ResourceLocation tagId) {
        List<TagThermalSourceDefinition> definitions = new ArrayList<>(TAG_THERMAL_SOURCES);
        definitions.removeIf(candidate -> candidate.tag().location().equals(tagId));
        TAG_THERMAL_SOURCES = List.copyOf(definitions);
    }

    @Nullable
    public static synchronized ThermalSourceDefinition getDefinition(BlockState state) {
        List<StateThermalSourceDefinition> stateDefinitions = STATE_THERMAL_SOURCES.get(state.getBlock());
        if (stateDefinitions != null) {
            for (int index = stateDefinitions.size() - 1; index >= 0; index--) {
                StateThermalSourceDefinition candidate = stateDefinitions.get(index);
                if (candidate.matches(state)) {
                    return candidate.definition();
                }
            }
        }
        ThermalSourceDefinition exactDefinition = THERMAL_SOURCES.get(state.getBlock());
        if (exactDefinition != null) {
            return exactDefinition;
        }
        List<TagThermalSourceDefinition> tagDefinitions = TAG_THERMAL_SOURCES;
        for (int index = tagDefinitions.size() - 1; index >= 0; index--) {
            TagThermalSourceDefinition candidate = tagDefinitions.get(index);
            if (state.is(candidate.tag())) {
                return candidate.definition();
            }
        }
        return null;
    }

    public static boolean isThermalSource(BlockState state) {
        return getDefinition(state) != null;
    }

    /** True when the block has any constant or state-conditional source registration. */
    public static boolean isRegisteredBlock(BlockState state) {
        if (REGISTERED_BLOCKS.contains(state.getBlock())) {
            return true;
        }
        for (TagThermalSourceDefinition candidate : TAG_THERMAL_SOURCES) {
            if (state.is(candidate.tag())) {
                return true;
            }
        }
        return false;
    }

    public static synchronized boolean hasStateThermalSource(BlockState state) {
        return STATE_THERMAL_SOURCES.containsKey(state.getBlock());
    }

    public static boolean isActive(Level level, BlockPos position, BlockState state) {
        ResolvedThermalSource source = resolve(level, position, state);
        return source != null && source.active();
    }

    public static float getSurfaceTemperature(Level level, BlockPos position, BlockState state) {
        ResolvedThermalSource source = resolve(level, position, state);
        return source == null || !source.active() ? 0.0F : source.surfaceTemperature();
    }

    /** Resolves one live source exactly once so provider state cannot change between separate queries. */
    @Nullable
    public static ResolvedThermalSource resolve(Level level, BlockPos position, BlockState state) {
        ThermalSourceDefinition definition = getDefinition(state);
        if (definition == null) {
            return null;
        }
        float configured = definition.surfaceTemperature();
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (blockEntity instanceof ThermalSourceStateProvider provider) {
            if (!provider.isThermalSourceActive()) {
                return new ResolvedThermalSource(definition, false, 0.0F, true);
            }
            float provided = provider.getThermalSurfaceTemperature(configured);
            if (!Float.isFinite(provided)) {
                return new ResolvedThermalSource(definition, false, 0.0F, true);
            }
            float limit = Math.abs(configured);
            return new ResolvedThermalSource(definition, true,
                    Math.max(-limit, Math.min(limit, provided)), true);
        }
        return new ResolvedThermalSource(definition, true, configured, false);
    }

    public static float getMaximumNaturalRadiationRange() {
        float maximum = 0.0F;
        synchronized (ThermalSourceRegistry.class) {
            for (ThermalSourceDefinition definition : allDefinitions()) {
                maximum = Math.max(maximum, definition.maximumNaturalRadiationRange());
            }
        }
        return maximum;
    }

    private static List<ThermalSourceDefinition> allDefinitions() {
        List<ThermalSourceDefinition> definitions = new ArrayList<>(THERMAL_SOURCES.values());
        for (List<StateThermalSourceDefinition> stateDefinitions : STATE_THERMAL_SOURCES.values()) {
            for (StateThermalSourceDefinition stateDefinition : stateDefinitions) {
                definitions.add(stateDefinition.definition());
            }
        }
        for (TagThermalSourceDefinition tagDefinition : TAG_THERMAL_SOURCES) {
            definitions.add(tagDefinition.definition());
        }
        return definitions;
    }

    private static void refreshRegisteredBlocks() {
        Set<Block> blocks = new HashSet<>(THERMAL_SOURCES.keySet());
        blocks.addAll(STATE_THERMAL_SOURCES.keySet());
        REGISTERED_BLOCKS = Set.copyOf(blocks);
    }

    private static void warnForUnusualRange(Object source, ThermalSourceDefinition definition) {
        float range = definition.maximumNaturalRadiationRange();
        if (range > UNUSUAL_RADIATION_RANGE) {
            LOGGER.warn("Thermal source {} has an unusually large natural radiation range: {} blocks", source, range);
        }
    }

    private record TagThermalSourceDefinition(TagKey<Block> tag, ThermalSourceDefinition definition) {
    }

    private record StateThermalSourceDefinition(Map<Property<?>, Object> properties,
                                                ThermalSourceDefinition definition) {
        private boolean matches(BlockState state) {
            for (Map.Entry<Property<?>, Object> expected : properties.entrySet()) {
                if (!Objects.equals(state.getValues().get(expected.getKey()), expected.getValue())) {
                    return false;
                }
            }
            return true;
        }
    }

    public record ResolvedThermalSource(ThermalSourceDefinition definition, boolean active,
                                        float surfaceTemperature, boolean dynamic) {

        @Nullable
        public Float radiationTemperature() {
            Float configuredRadiation = definition.radiationTemperature();
            if (configuredRadiation == null || !dynamic || definition.surfaceTemperature() == 0.0F) {
                return configuredRadiation;
            }
            return configuredRadiation * surfaceTemperature / definition.surfaceTemperature();
        }
    }

    public record ThermalSourceDefinition(float surfaceTemperature,
                                          float faceHeatingRate,
                                          @Nullable Float radiationTemperature,
                                          @Nullable Float radiationDecayPerBlock) {
        public ThermalSourceDefinition {
            if (!Float.isFinite(surfaceTemperature)) {
                throw new IllegalArgumentException("Surface temperature must be finite");
            }
            if (!Float.isFinite(faceHeatingRate) || faceHeatingRate < 0.0F) {
                throw new IllegalArgumentException("Face heating rate must be finite and non-negative");
            }
            if (radiationTemperature != null && radiationDecayPerBlock != null
                    && radiationTemperature == 0.0F && radiationDecayPerBlock == 0.0F) {
                radiationTemperature = null;
                radiationDecayPerBlock = null;
            }
            if ((radiationTemperature == null) != (radiationDecayPerBlock == null)) {
                throw new IllegalArgumentException("Radiation temperature and decay must either both be present or both be null");
            }
            if (radiationTemperature != null && !Float.isFinite(radiationTemperature)) {
                throw new IllegalArgumentException("Radiation temperature must be finite when present");
            }
            if (radiationDecayPerBlock != null
                    && (!Float.isFinite(radiationDecayPerBlock) || radiationDecayPerBlock <= 0.0F)) {
                throw new IllegalArgumentException("Radiation decay must be finite and positive when present");
            }
        }

        public boolean radiates() {
            return radiationTemperature != null && radiationDecayPerBlock != null
                    && Math.abs(radiationTemperature) >= 0.1F;
        }

        public boolean heatsAir() {
            return faceHeatingRate > 0.0F;
        }

        public float maximumNaturalRadiationRange() {
            return radiates() ? Math.abs(radiationTemperature) / radiationDecayPerBlock : 0.0F;
        }
    }
}
