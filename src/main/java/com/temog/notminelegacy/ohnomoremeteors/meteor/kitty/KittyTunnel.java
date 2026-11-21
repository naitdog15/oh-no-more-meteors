package com.temog.notminelegacy.ohnomoremeteors.meteor.kitty;

import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import me.emafire003.dev.ohmymeteors.OhMyMeteors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The hardness-budget tunnel of a Komit Kitty: the kitty digs down along its flight line, spending a budget on
 * every block, and stops when the budget is used up. The blast and the capsule happen at the end of the tunnel.
 *
 * Cost of one cell (checked in this order, see {@link #cost}):
 *  1. outside the build height, unbreakable (bedrock, barrier), a block entity, or tagged
 *     ohmymeteors:meteor_explosion_safe: STOP;
 *  2. a fluid cell with no collision (water, lava, water holding kelp): the fluid cost, the cell is not cleared;
 *  3. free: air, an empty collision shape (plants, fire), leaves, the snow layer block: 0;
 *  4. any other block: hardness (or its override) plus the cost base.
 *
 * The world checks (the cell must be entity-ticking and allowed by the claim guard) live in {@link #dig}, so the
 * cost function stays pure on (state, getter, position).
 *
 * Kitties are size 3 or less, so OMM's own size/2 dig-in never runs for them and this tunnel is the only one.
 */
public final class KittyTunnel {

    /** Cost value for a cell the kitty can never enter. */
    public static final double STOP = -1.0D;

    /** Tolerance for comparing a cost with the budget (float hardness values add up with tiny errors). */
    private static final double EPS = 1.0E-9D;

    private static final int KIND_STOP = 0;
    private static final int KIND_FLUID = 1;
    private static final int KIND_FREE = 2;
    private static final int KIND_SOLID = 3;

    /** One warning per game run for a bad override entry. */
    private static volatile boolean loggedBadOverride;

    private KittyTunnel() {
    }

    /**
     * The result of one dig: the cell the kitty ended in (where the blast and the capsule go), how many solid
     * cells were cleared, and whether the kitty reached solid ground (false for a mid-air hit or a landing that
     * only passed free cells).
     */
    public record Result(BlockPos end, int cleared, boolean grounded) {
        /** No digging: the kitty explodes where it is (used when meteor griefing is off). */
        public static Result inPlace(Vec3 at) {
            return new Result(BlockPos.containing(at), 0, false);
        }
    }

    // ---- cost ----

    /** What kind of cell this is: STOP, fluid, free or solid. Pure on its arguments. */
    private static int kind(BlockState s, BlockGetter g, BlockPos p) {
        if (g.isOutsideBuildHeight(p)) {
            return KIND_STOP;
        }
        if (s.getDestroySpeed(g, p) < 0.0F || s.hasBlockEntity() || s.is(OhMyMeteors.METEOR_EXPLOSION_SAFE)) {
            return KIND_STOP;
        }
        boolean noCollision = s.getCollisionShape(g, p).isEmpty();
        // Fluids come before the empty-shape test, or water would come out free.
        if (!s.getFluidState().isEmpty() && noCollision) {
            return KIND_FLUID;
        }
        // The snow layer block is listed on its own: layers 2 to 8 have a collision shape.
        if (s.isAir() || noCollision || s.is(BlockTags.LEAVES) || s.is(Blocks.SNOW)) {
            return KIND_FREE;
        }
        return KIND_SOLID;
    }

    /** Budget cost of one cell: {@link #STOP}, 0 for free cells, the fluid cost, or hardness plus the base cost. */
    public static double cost(BlockState s, BlockGetter g, BlockPos p) {
        return costOf(kind(s, g, p), s, g, p);
    }

    private static double costOf(int kind, BlockState s, BlockGetter g, BlockPos p) {
        return switch (kind) {
            case KIND_STOP -> STOP;
            case KIND_FLUID -> Config.KITTY_TUNNEL_FLUID_COST.get();
            case KIND_FREE -> 0.0D;
            default -> {
                double hardness = overrideOf(s);
                if (Double.isNaN(hardness)) {
                    hardness = s.getDestroySpeed(g, p);
                }
                yield hardness + Config.KITTY_TUNNEL_COST_BASE.get();
            }
        };
    }

    /**
     * How many cells of this block in a row the kitty can pass with a full budget: the same repeated
     * subtraction the dig uses. STOP gives 0. A free cell (cost 0) is capped at the tunnel's maximum length.
     */
    public static int cellsThrough(BlockState s, BlockGetter g, BlockPos p) {
        double cost = cost(s, g, p);
        if (cost == STOP) {
            return 0;
        }
        int cap = Config.KITTY_TUNNEL_MAX_LENGTH.get();
        if (cost <= 0.0D) {
            return cap;
        }
        double budget = Config.KITTY_TUNNEL_BUDGET.get();
        int cells = 0;
        while (cells < cap && cost <= budget + EPS) {
            budget -= cost;
            cells++;
        }
        return cells;
    }

    // ---- hardness overrides ----

    /** The parsed override list, remembered together with the config list object it came from. */
    private record Overrides(List<? extends String> source, Map<Block, Double> blocks, List<TagOverride> tags) {
    }

    private record TagOverride(TagKey<Block> tag, double value) {
    }

    /** Rebuilt only when the config hands out a different list object (it does so after a config reload). */
    private static volatile Overrides overrides;

    private static Overrides overrides() {
        List<? extends String> raw = Config.KITTY_TUNNEL_COST_OVERRIDES.get();
        Overrides cached = overrides;
        if (cached == null || cached.source() != raw) {
            cached = parseOverrides(raw);
            overrides = cached;
        }
        return cached;
    }

    /** The hardness override for this block, or NaN when none is listed. */
    private static double overrideOf(BlockState s) {
        Overrides o = overrides();
        if (o.blocks().isEmpty() && o.tags().isEmpty()) {
            return Double.NaN;
        }
        Double byId = o.blocks().get(s.getBlock());
        if (byId != null) {
            return byId;
        }
        for (TagOverride t : o.tags()) {
            if (s.is(t.tag())) {
                return t.value();
            }
        }
        return Double.NaN;
    }

    /** Parses "id=value" and "#tag=value" entries. Bad entries are skipped with one warning. */
    private static Overrides parseOverrides(List<? extends String> raw) {
        Map<Block, Double> blocks = new HashMap<>();
        List<TagOverride> tags = new ArrayList<>();
        if (raw != null) {
            for (String entry : raw) {
                if (!parseOneOverride(entry, blocks, tags) && !loggedBadOverride) {
                    loggedBadOverride = true;
                    OhNoMoreMeteors.LOGGER.warn("[ohnomoremeteors] Komit Kitty tunnelCostOverrides: skipping the entry \"{}\" (use id=value or #tag=value).", entry);
                }
            }
        }
        return new Overrides(raw, blocks, tags);
    }

    private static boolean parseOneOverride(String entry, Map<Block, Double> blocks, List<TagOverride> tags) {
        try {
            if (entry == null) {
                return false;
            }
            String text = entry.trim();
            int eq = text.lastIndexOf('=');
            if (eq <= 0) {
                return false;
            }
            double value = Double.parseDouble(text.substring(eq + 1).trim());
            if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0D) {
                return false;
            }
            String name = text.substring(0, eq).trim();
            boolean isTag = name.startsWith("#");
            ResourceLocation id = ResourceLocation.tryParse(isTag ? name.substring(1) : name);
            if (id == null) {
                return false;
            }
            if (isTag) {
                tags.add(new TagOverride(TagKey.create(Registries.BLOCK, id), value));
                return true;
            }
            if (!ForgeRegistries.BLOCKS.containsKey(id)) {
                return false;
            }
            blocks.put(ForgeRegistries.BLOCKS.getValue(id), value);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    // ---- the dig ----

    /** One cell the kitty may look at: inside build height, entity-ticking (never loads a chunk) and allowed by the guard. */
    private static boolean mayEnter(ServerLevel level, BlockPos c, KittyClaimGuard guard) {
        return !level.isOutsideBuildHeight(c) && level.isPositionEntityTicking(c) && guard.allows(c);
    }

    /** As {@link #dig(ServerLevel, Vec3, Vec3, double, KittyClaimGuard)} with a fresh claim guard (for tests). */
    public static Result dig(ServerLevel level, Vec3 from, Vec3 dir, double speed) {
        return dig(level, from, dir, speed, KittyClaimGuard.of(level));
    }

    /**
     * Digs along dir from the point where the kitty hit, in 0.5-block steps, visiting each new cell once. One
     * budget (the tunnelBudget config) is shared by both phases.
     *
     * Phase A, the approach: free cells and fluid cells are passed, at most ceil(speed) + 3 of them. Fluid cells
     * spend the fluid cost here too, so a kitty that dived through a lake has less left for the bed. If no solid
     * cell turns up (or the budget runs out inside a fluid) the result has grounded = false: blast only.
     *
     * Phase B, the tunnel: a cell that cannot be entered (STOP, not entity-ticking, refused by the claim guard)
     * or costs more than the budget ends the tunnel; otherwise its cost is paid and a solid cell becomes air with
     * no drops. The tunnel ends after tunnelMaxLength cells, and also right after a solid cell is followed by
     * air (a cave: the kitty breaks through and stops there).
     *
     * The end cell is the last cell entered. If the very first solid cell cannot be afforded it is the last
     * approach cell (the kitty splats on it).
     */
    public static Result dig(ServerLevel level, Vec3 from, Vec3 dir, double speed, KittyClaimGuard guard) {
        double budget = Config.KITTY_TUNNEL_BUDGET.get();
        double fluidCost = Config.KITTY_TUNNEL_FLUID_COST.get();
        int maxLength = Config.KITTY_TUNNEL_MAX_LENGTH.get();
        int approachLimit = (int) Math.ceil(speed) + 3;
        int maxSteps = 4 * (approachLimit + maxLength + 4);
        double boreR = Config.KITTY_TUNNEL_BORE_RADIUS.get();
        double boreSq = boreR * boreR;
        // The channel is smashed after the dig, so it can never look like a cave to the centre line.
        List<BlockPos> path = new ArrayList<>();

        BlockPos last = BlockPos.containing(from);
        BlockPos previous = null;
        int approach = 0;
        int visitedInTunnel = 0;
        int cleared = 0;
        boolean tunnelling = false;
        boolean afterSolid = false;

        for (int i = 0; i <= maxSteps; i++) {
            BlockPos c = BlockPos.containing(from.add(dir.scale(0.5D * i)));
            if (c.equals(previous)) {
                continue;
            }
            previous = c;

            BlockState s = null;
            int kind = KIND_STOP;
            // The entity-ticking test comes first: reading the state of an unloaded cell could load its chunk.
            if (mayEnter(level, c, guard)) {
                s = level.getBlockState(c);
                kind = kind(s, level, c);
            }

            if (!tunnelling) {
                if (kind == KIND_STOP) {
                    return new Result(last, 0, false);
                }
                if (kind == KIND_FREE || kind == KIND_FLUID) {
                    last = c;
                    if (kind == KIND_FLUID) {
                        budget -= fluidCost;
                        if (budget <= 0.0D) {
                            return new Result(last, 0, false); // the water used up the budget: blast only
                        }
                    }
                    if (++approach > approachLimit) {
                        return new Result(last, 0, false);
                    }
                    continue;
                }
                tunnelling = true; // first solid cell: handled by the tunnel rules below
            }

            if (kind == KIND_STOP || visitedInTunnel >= maxLength) {
                break;
            }
            visitedInTunnel++;
            double cost = costOf(kind, s, level, c);
            if (cost > budget + EPS) {
                break;
            }
            budget -= cost;
            last = c;
            if (kind == KIND_SOLID) {
                level.setBlock(c, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                cleared++;
                path.add(c);
                afterSolid = true;
                continue;
            }
            if (kind == KIND_FREE && afterSolid && s.isAir()) {
                break; // broke through into a cave
            }
            afterSolid = false;
        }
        for (BlockPos c : path) {
            bore(level, c, boreSq, guard);
        }
        return new Result(last, cleared, tunnelling);
    }

    /**
     * Smashes the channel around one tunnel cell (user ruling 2026-11-21: the path is destroyed, not just burrowed):
     * every cell whose centre is within the bore radius of this cell's centre, if it may be entered and the tunnel
     * could dig it (solid or a plant, at most the full budget; never STOP blocks, fluids or harder-than-budget blocks).
     * No budget is spent and nothing drops. With the default 1.25 that is the 18 face and edge neighbours.
     */
    private static void bore(ServerLevel level, BlockPos c, double boreSq, KittyClaimGuard guard) {
        if (boreSq < 1.0D) {
            return;
        }
        int r = (int) Math.floor(Math.sqrt(boreSq));
        double full = Config.KITTY_TUNNEL_BUDGET.get() + EPS;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    int d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 == 0 || d2 > boreSq) {
                        continue;
                    }
                    p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    if (!mayEnter(level, p, guard)) {
                        continue;
                    }
                    BlockState s = level.getBlockState(p);
                    if (s.isAir()) {
                        continue;
                    }
                    int kind = kind(s, level, p);
                    if (kind == KIND_STOP || kind == KIND_FLUID) {
                        continue;
                    }
                    if (kind == KIND_SOLID && costOf(kind, s, level, p) > full) {
                        continue;
                    }
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    // ---- the capsule rule ----

    /**
     * The capsule's rule for a cell it may overwrite: inside build height, chunk loaded, allowed by the claim
     * guard, and a cost the tunnel could pay (not STOP and at most the full budget). That covers bedrock,
     * unbreakable blocks, block entities and explosion-safe blocks, and keeps every block the kitty could not
     * have dug into (obsidian and harder). Air, fluids, plants, dirt, stone, deepslate and ores are replaceable.
     */
    public static boolean replaceable(ServerLevel level, BlockPos p, KittyClaimGuard guard) {
        if (level.isOutsideBuildHeight(p) || !level.hasChunkAt(p) || !guard.allows(p)) {
            return false;
        }
        double cost = cost(level.getBlockState(p), level, p);
        return cost != STOP && cost <= Config.KITTY_TUNNEL_BUDGET.get() + EPS;
    }
}
