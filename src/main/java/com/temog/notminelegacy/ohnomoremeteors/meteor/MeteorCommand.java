package com.temog.notminelegacy.ohnomoremeteors.meteor;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.temog.notminelegacy.ohnomoremeteors.Config;
import com.temog.notminelegacy.ohnomoremeteors.OhNoMoreMeteors;
import me.emafire003.dev.ohmymeteors.entities.MeteorProjectileEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Test command for summoning meteors of a chosen variant (operator level 2):
 * <pre>
 *   /ohnomoremeteors meteor &lt;cindrite|rimerite|veyrite|plain|random&gt; [size] [height] [pos] [angle] [north|east|south|west|random]
 * Without a size the meteor gets a random size (impactFormula.randomizeMeteors) or the default size 8.
 * </pre>
 * The meteor is a normal Oh My, Meteors! meteor, spawned {@code height} blocks above {@code pos} (default: the
 * command position) and falling straight down. {@code angle} is the entry angle in degrees from straight down
 * (-80 to 80, default 0). The direction is the compass way the meteor travels (default random); a negative angle
 * travels the opposite way. The meteor still lands on {@code pos}; it starts {@code height * tan(angle)} blocks to the side
 * (OMM meteors fly in a straight line, so the angle holds all the way down). For a named variant the claim is written before the meteor joins
 * the world, then the same size and speed tuning as a natural claim is applied, so it behaves like a rolled
 * meteor of that kind. {@code plain} writes a claim no add-on owns, so it always stays a plain OMM meteor;
 * {@code random} writes nothing and lets the normal roll decide.
 */
@Mod.EventBusSubscriber(modid = OhNoMoreMeteors.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MeteorCommand {

    private static final ResourceLocation METEOR_TYPE_ID = new ResourceLocation("ohmymeteors", "meteor_projectile");
    /** Claim value that no add-on reads as its own: the meteor stays plain and nobody else claims it. */
    public static final String PLAIN_CLAIM = "ohnomoremeteors:plain";
    public static final int DEFAULT_SIZE = 8;
    public static final int DEFAULT_HEIGHT = 60;
    /** Downward start speed in blocks per tick (OMM's own spawns use roughly 1 - 2). */
    private static final double FALL_SPEED = 1.0D;
    /** Steepest entry angle from straight down, in degrees, either way. */
    public static final float MAX_ANGLE = 80.0F;
    /** Keeps an angled meteor's far start chunk ticking until OMM's own chunk loading takes over (10 s). */
    private static final TicketType<ChunkPos> START_TICKET =
            TicketType.create("ohnomoremeteors_meteor_start", java.util.Comparator.comparingLong(ChunkPos::toLong), 200);

    private MeteorCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> meteor = Commands.literal("meteor");
        for (String kind : new String[]{"cindrite", "rimerite", "veyrite", "plain", "random"}) {
            meteor.then(Commands.literal(kind)
                    .executes(c -> run(c, kind, null, DEFAULT_HEIGHT, null))
                    .then(Commands.argument("size", IntegerArgumentType.integer(1, 50))
                            .executes(c -> run(c, kind, IntegerArgumentType.getInteger(c, "size"), DEFAULT_HEIGHT, null))
                            .then(Commands.argument("height", IntegerArgumentType.integer(5, 320))
                                    .executes(c -> run(c, kind, IntegerArgumentType.getInteger(c, "size"),
                                            IntegerArgumentType.getInteger(c, "height"), null))
                                    .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                            .executes(c -> run(c, kind, IntegerArgumentType.getInteger(c, "size"),
                                                    IntegerArgumentType.getInteger(c, "height"),
                                                    BlockPosArgument.getLoadedBlockPos(c, "pos")))
                                            .then(angleArgument(kind))))));
        }
        dispatcher.register(Commands.literal(OhNoMoreMeteors.MODID)
                .requires(s -> s.hasPermission(2))
                .then(meteor));
    }

    /** [angle] then an optional travel direction: north / east / south / west (fixed) or random (the default). */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Float> angleArgument(String kind) {
        var angle = Commands.argument("angle", FloatArgumentType.floatArg(-MAX_ANGLE, MAX_ANGLE))
                .executes(c -> runAngled(c, kind, null));
        angle.then(Commands.literal("random").executes(c -> runAngled(c, kind, null)));
        String[] names = {"south", "west", "north", "east"};
        for (int i = 0; i < names.length; i++) {
            float yaw = i * 90.0F;
            angle.then(Commands.literal(names[i]).executes(c -> runAngled(c, kind, yaw)));
        }
        return angle;
    }

    private static int runAngled(CommandContext<CommandSourceStack> c, String kind, @Nullable Float yaw) throws CommandSyntaxException {
        return run(c, kind, IntegerArgumentType.getInteger(c, "size"), IntegerArgumentType.getInteger(c, "height"),
                BlockPosArgument.getLoadedBlockPos(c, "pos"), FloatArgumentType.getFloat(c, "angle"), yaw);
    }

    private static int run(CommandContext<CommandSourceStack> c, String kind, @Nullable Integer size, int height, @Nullable BlockPos pos)
            throws CommandSyntaxException {
        return run(c, kind, size, height, pos, 0.0F, null);
    }

    /** @param yaw travel heading in Minecraft yaw degrees (south 0, west 90, north 180, east 270); null = random */
    private static int run(CommandContext<CommandSourceStack> c, String kind, @Nullable Integer size, int height, @Nullable BlockPos pos,
                           float angle, @Nullable Float yaw) throws CommandSyntaxException {
        CommandSourceStack source = c.getSource();
        MeteorVariant variant = switch (kind) {
            case "cindrite" -> MeteorVariant.CINDRITE;
            case "rimerite" -> MeteorVariant.RIMERITE;
            case "veyrite" -> MeteorVariant.VEYRITE;
            default -> null;
        };
        if (variant != null && !variant.isEnabled()) {
            source.sendFailure(Component.translatable("commands.ohnomoremeteors.meteor.disabled", kind));
            return 0;
        }
        Vec3 target = pos != null ? Vec3.atBottomCenterOf(pos) : source.getPosition();
        MeteorProjectileEntity meteor = spawn(source.getLevel(), target, size, height, variant, "plain".equals(kind),
                angle, yaw != null ? yaw : source.getLevel().getRandom().nextFloat() * 360.0F);
        if (meteor == null) {
            source.sendFailure(Component.translatable("commands.ohnomoremeteors.meteor.failed"));
            return 0;
        }
        BlockPos at = meteor.blockPosition();
        source.sendSuccess(() -> Component.translatable("commands.ohnomoremeteors.meteor.spawned",
                kind, meteor.getSize(), at.getX(), at.getY(), at.getZ()), true);
        return 1;
    }

    /** Same as the full form, with an exact size (no random size change). */
    @Nullable
    public static MeteorProjectileEntity spawn(ServerLevel level, Vec3 target, int size, int height,
                                               @Nullable MeteorVariant variant, boolean plain) {
        return spawn(level, target, Integer.valueOf(size), height, variant, plain);
    }

    /**
     * Spawns one OMM meteor {@code height} blocks above {@code target}, falling straight down.
     * Size: an explicit size is used exactly; null means DEFAULT_SIZE, or a random size between commandRandomSizeMin
     * and commandRandomSizeMax when impactFormula.randomizeMeteors is on. The fall speed gets the random speed roll
     * whenever randomizeMeteors is on (the command has no speed argument).
     *
     * @param variant the variant to force, or null for none
     * @param plain   when variant is null: true writes the plain claim (never a variant), false leaves the normal roll
     * @return the meteor, or null if OMM's meteor type is missing or the spawn failed
     */
    @Nullable
    public static MeteorProjectileEntity spawn(ServerLevel level, Vec3 target, @Nullable Integer size, int height,
                                               @Nullable MeteorVariant variant, boolean plain) {
        return spawn(level, target, size, height, variant, plain, 0.0F, 0.0F);
    }

    /**
     * As above, with an entry angle: {@code angle} degrees from straight down (clamped to -80..80), tilted along the
     * horizontal heading {@code yaw} (Minecraft yaw, degrees). The meteor starts {@code height} blocks above the
     * target and {@code height * tan(angle)} blocks back along the heading, so it still lands on the target.
     */
    @Nullable
    public static MeteorProjectileEntity spawn(ServerLevel level, Vec3 target, @Nullable Integer size, int height,
                                               @Nullable MeteorVariant variant, boolean plain, float angle, float yaw) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(METEOR_TYPE_ID);
        if (type == null) {
            return null;
        }
        Entity entity = type.create(level);
        if (!(entity instanceof MeteorProjectileEntity meteor)) {
            return null;
        }
        double y = Math.min(target.y + height, level.getMaxBuildHeight() - 2);
        double tilt = Math.toRadians(Mth.clamp(angle, -MAX_ANGLE, MAX_ANGLE));
        // Unit heading on the ground for this yaw (Minecraft: yaw 0 faces +Z, 90 faces -X).
        double hx = -Math.sin(Math.toRadians(yaw));
        double hz = Math.cos(Math.toRadians(yaw));
        double back = (y - target.y) * Math.tan(tilt);
        meteor.moveTo(target.x - hx * back, y, target.z - hz * back, 0.0F, 90.0F);
        if (Math.abs(back) > 1.0E-3D) {
            ChunkPos start = new ChunkPos(meteor.blockPosition());
            level.getChunkSource().addRegionTicket(START_TICKET, start, 3, start);
        }
        boolean random = Config.RANDOMIZE_METEORS.get();
        int finalSize;
        if (size != null) {
            finalSize = size;
        } else if (random) {
            int a = Config.COMMAND_RANDOM_SIZE_MIN.get();
            int b = Config.COMMAND_RANDOM_SIZE_MAX.get();
            finalSize = Math.min(a, b) + level.getRandom().nextInt(Math.abs(b - a) + 1);
        } else {
            finalSize = DEFAULT_SIZE;
        }
        meteor.setSize(finalSize);
        double horizontal = Math.sin(tilt) * FALL_SPEED;
        meteor.setDeltaMovement(hx * horizontal, -Math.cos(tilt) * FALL_SPEED, hz * horizontal);
        // The command decides the randomness itself, so the join-time randomizer leaves this meteor alone.
        meteor.getPersistentData().putBoolean(ImpactFormula.RANDOMIZED_KEY, true);
        if (random) {
            meteor.getPersistentData().putDouble(ImpactFormula.SPEED_ROLL_KEY, ImpactFormula.rollSpeed(level.getRandom()));
        }
        if (variant != null) {
            variant.writeTo(meteor);
            MeteorEvents.applyClaimSize(meteor, variant);
            MeteorEvents.applySpeed(meteor, variant);
        } else if (plain) {
            meteor.getPersistentData().putString(MeteorVariant.NBT_KEY, PLAIN_CLAIM);
        }
        return level.addFreshEntity(meteor) ? meteor : null;
    }
}
