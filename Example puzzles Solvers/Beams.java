package com.Bedrock.module.impl.dungeon.safepuzzles;

import com.ricedotwho.rsm.component.impl.Renderer3D;
import com.ricedotwho.rsm.component.impl.location.Island;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.component.impl.map.Map;
import com.ricedotwho.rsm.component.impl.map.handler.Dungeon;
import com.ricedotwho.rsm.component.impl.map.map.Room;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.data.Pos;
import com.ricedotwho.rsm.data.Rotation;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.game.ClientTickEvent;
import com.ricedotwho.rsm.event.impl.render.Render3DEvent;
import com.ricedotwho.rsm.event.impl.world.BlockChangeEvent;
import com.ricedotwho.rsm.event.impl.world.WorldEvent;
import com.ricedotwho.rsm.module.api.SubModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.NumberSetting;
import com.ricedotwho.rsm.utils.ItemUtils;
import com.ricedotwho.rsm.utils.RotationUtils;
import com.ricedotwho.rsm.utils.render.render3d.type.FilledOutlineBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@SubModuleInfo(name = "Beams", alwaysDisabled = false)
public class Beams extends SafePuzzle {
    private static final Pos START_PLATFORM = new Pos(0.5, 75.0, 0.5);
    private static final double PLATFORM_RADIUS_SQ = 1.21;
    private static final double PLATFORM_Y_TOLERANCE = 2.25;
    private static final long SLOT_SETTLE_MS = 90L;
    private static final long RUN_TIMEOUT_MS = 12000L;

    private static final BeamData[] SOLUTIONS = {
            new BeamData(15, 74, 15, 15, 84, 13),
            new BeamData(15, 78, 3, 15, 76, 27),
            new BeamData(5, 76, 24, 24, 77, 7),
            new BeamData(2, 75, 16, 27, 78, 14),
            new BeamData(4, 72, 8, 25, 79, 21),
            new BeamData(4, 75, 9, 25, 76, 23),
            new BeamData(22, 80, 22, 4, 72, 8),
            new BeamData(3, 76, 18, 26, 78, 12),
            new BeamData(9, 81, 20, 26, 70, 7),
            new BeamData(18, 81, 21, 9, 69, 3),
            new BeamData(18, 82, 8, 10, 69, 27),
            new BeamData(25, 76, 23, 6, 74, 5),
            new BeamData(6, 74, 5, 25, 76, 23),
            new BeamData(26, 70, 7, 9, 81, 20)
    };

    private final NumberSetting bowSlot = new NumberSetting("Bow Slot", 1.0, 9.0, 1.0, 1.0);
    private final NumberSetting actionDelay = new NumberSetting("Action Delay (MS)", 0.0, 1000.0, 120.0, 10.0);
    private final NumberSetting lookSpeed = new NumberSetting("Look Speed", 30.0, 720.0, 260.0, 10.0);

    private final List<BeamPair> detectedPairs = new ArrayList<>();
    private final List<BeamPair> completedPairs = new ArrayList<>();
    private final List<BlockPos> shotQueue = new ArrayList<>();

    private Mode mode = Mode.IDLE;
    private BeamPair currentPair;
    private BlockPos currentTarget;
    private Vec3 aimTarget;
    private int shotIndex;
    private long runStartedAt;
    private long lookStartedAt;
    private long lookDurationMs;
    private long nextActionAt;
    private long slotReadyAt;
    private float lookStartYaw;
    private float lookStartPitch;
    private float lookArcYaw;
    private float lookArcPitch;

    public Beams(SafePuzzles module) {
        super(module);
        this.registerProperty(bowSlot, actionDelay, lookSpeed);
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent.Start event) {
        Minecraft client = Minecraft.getInstance();
        if (!getSolver().getValue() || client.player == null || client.level == null || !isInCreeperBeams()) {
            resetState();
            return;
        }

        if (mode != Mode.IDLE && System.currentTimeMillis() - runStartedAt > RUN_TIMEOUT_MS) {
            resetState();
            return;
        }

        scanPairs(client.level);

        if (mode == Mode.IDLE) {
            if (isOnPlatform(client, START_PLATFORM)) {
                beginRun(client);
            }
            return;
        }

        if (System.currentTimeMillis() < nextActionAt) {
            return;
        }

        switch (mode) {
            case SHOOT_QUEUE -> tickShoot(client);
            case IDLE -> {
            }
        }
    }

    @SubscribeEvent
    public void onRenderCamera(Render3DEvent.Start event) {
        Minecraft client = Minecraft.getInstance();
        if (!getSolver().getValue() || client.player == null || !isInCreeperBeams()) {
            return;
        }

        if (aimTarget != null) {
            applySmoothLook(client);
        }
    }

    @SubscribeEvent
    public void onRender(Render3DEvent.Extract event) {
        if (!getSolver().getValue() || !isInCreeperBeams()) {
            return;
        }

        BeamPair pair = currentPair != null ? currentPair : detectedPairs.isEmpty() ? null : detectedPairs.getFirst();
        if (pair == null) {
            return;
        }

        Renderer3D.addTask(new FilledOutlineBox(new AABB(pair.first()), new Colour(60, 220, 255, 45), new Colour(60, 220, 255, 180), false));
        Renderer3D.addTask(new FilledOutlineBox(new AABB(pair.second()), new Colour(120, 255, 120, 45), new Colour(120, 255, 120, 180), false));
    }

    @SubscribeEvent
    public void onBlockChange(BlockChangeEvent event) {
        if (!getSolver().getValue() || !isInCreeperBeams() || !event.getNewState().is(Blocks.PRISMARINE)) {
            return;
        }

        for (BeamPair pair : detectedPairs) {
            if (pair.contains(event.getBlockPos()) && !pair.matches(currentPair) && !isCompleted(pair)) {
                completedPairs.add(pair);
                return;
            }
        }
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        resetState();
    }

    @Override
    public void reset() {
        resetState();
    }

    private void beginRun(Minecraft client) {
        BeamPair next = getNextPair();
        if (next == null) {
            return;
        }

        runStartedAt = System.currentTimeMillis();
        buildShotQueue();
        if (shotQueue.isEmpty()) {
            resetState();
            return;
        }

        currentPair = pairForTarget(shotQueue.getFirst());
        shotIndex = 0;
        beginShoot(client, shotQueue.getFirst(), Mode.SHOOT_QUEUE);
    }

    private void tickShoot(Minecraft client) {
        if (currentTarget == null || aimTarget == null || client.gameMode == null) {
            resetState();
            return;
        }

        if (getLookProgress() < 1.0) {
            return;
        }

        applySmoothLook(client);
        if (System.currentTimeMillis() < slotReadyAt) {
            return;
        }

        shootBow(client);
        nextActionAt = System.currentTimeMillis() + randomActionDelay();
        shotIndex++;

        if (shotIndex >= shotQueue.size()) {
            resetState();
            return;
        }

        BlockPos next = shotQueue.get(shotIndex);
        currentPair = pairForTarget(next);
        beginShoot(client, next, Mode.SHOOT_QUEUE);
    }

    private void beginShoot(Minecraft client, BlockPos target, Mode nextMode) {
        int slot = getBowSlot(client);
        if (slot == -1) {
            resetState();
            return;
        }

        mode = nextMode;
        currentTarget = target;
        aimTarget = Vec3.atCenterOf(target);
        prepareSlot(client, slot);
        startLook(client);
    }

    private void shootBow(Minecraft client) {
        client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
        client.player.swing(InteractionHand.MAIN_HAND);
    }

    private void buildShotQueue() {
        shotQueue.clear();
        for (BeamPair pair : detectedPairs) {
            if (isCompleted(pair)) {
                continue;
            }
            addTarget(pair.first());
            addTarget(pair.second());
        }
    }

    private void addTarget(BlockPos target) {
        if (!shotQueue.contains(target)) {
            shotQueue.add(target);
        }
    }

    private BeamPair pairForTarget(BlockPos target) {
        for (BeamPair pair : detectedPairs) {
            if (pair.contains(target)) {
                return pair;
            }
        }
        return currentPair;
    }

    private void scanPairs(ClientLevel level) {
        Room room = getMainRoom();
        if (room == null) {
            detectedPairs.clear();
            return;
        }

        List<BeamPair> found = new ArrayList<>();
        for (BeamData solution : SOLUTIONS) {
            BeamPair pair = new BeamPair(componentBlock(room, solution.x1, solution.y1, solution.z1), componentBlock(room, solution.x2, solution.y2, solution.z2));
            if (!level.getBlockState(pair.first()).is(Blocks.SEA_LANTERN) || !level.getBlockState(pair.second()).is(Blocks.SEA_LANTERN)) {
                continue;
            }

            if (isCompleted(pair) || found.stream().anyMatch(existing -> existing.overlaps(pair))) {
                continue;
            }

            found.add(pair);
            if (found.size() >= 4) {
                break;
            }
        }

        detectedPairs.clear();
        detectedPairs.addAll(found);
    }

    private BeamPair getNextPair() {
        for (BeamPair pair : detectedPairs) {
            if (!isCompleted(pair)) {
                return pair;
            }
        }
        return null;
    }

    private boolean isCompleted(BeamPair candidate) {
        return completedPairs.stream().anyMatch(pair -> pair.overlaps(candidate));
    }

    private boolean isOnPlatform(Minecraft client, Pos local) {
        Vec3 platform = roomVec(local);
        if (platform == null) {
            return false;
        }

        Vec3 player = client.player.position();
        double dx = player.x - platform.x;
        double dz = player.z - platform.z;
        return dx * dx + dz * dz <= PLATFORM_RADIUS_SQ && Math.abs(player.y - platform.y) <= PLATFORM_Y_TOLERANCE;
    }

    private Vec3 roomVec(Pos local) {
        Room room = getMainRoom();
        return room == null ? null : room.getRealPositionFixed(local).asVec3();
    }

    private BlockPos componentBlock(Room room, int x, int y, int z) {
        return room.getRealPositionFixed(new Pos(x - 15.0, y, z - 15.0)).asBlockPos();
    }

    private Room getMainRoom() {
        return Map.getCurrentRoom() == null || Map.getCurrentRoom().getUniqueRoom() == null
                ? null
                : Map.getCurrentRoom().getUniqueRoom().getMainRoom();
    }

    private boolean isInCreeperBeams() {
        return Location.getArea().is(Island.Dungeon)
                && !Dungeon.isInBoss()
                && Map.getCurrentRoom() != null
                && Map.getCurrentRoom().getUniqueRoom() != null
                && "Creeper Beams".equals(Map.getCurrentRoom().getUniqueRoom().getName());
    }

    private void startLook(Minecraft client) {
        lookStartedAt = System.currentTimeMillis();
        lookStartYaw = client.player.getYRot();
        lookStartPitch = client.player.getXRot();

        Rotation wanted = Rotation.from(aimTarget);
        float yawDelta = RotationUtils.wrapAngleTo180(wanted.getYaw() - lookStartYaw);
        float pitchDelta = RotationUtils.wrapAngleTo180(wanted.getPitch() - lookStartPitch);
        double angle = Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);
        lookDurationMs = fixedLookDurationMs();
        calculateLookArc(yawDelta, pitchDelta, angle);
    }

    private long fixedLookDurationMs() {
        double speed = Math.max(1.0, getDouble(lookSpeed));
        return Mth.clamp((long) Math.round(100000.0 / speed), 90L, 1800L);
    }

    private void applySmoothLook(Minecraft client) {
        if (aimTarget == null || client.player == null) {
            return;
        }

        Rotation wanted = Rotation.from(aimTarget);
        double amount = easeInOut(getLookProgress());
        double arcAmount = Math.sin(Math.PI * amount);
        float nextYaw = lookStartYaw
                + RotationUtils.wrapAngleTo180(wanted.getYaw() - lookStartYaw) * (float) amount
                + lookArcYaw * (float) arcAmount;
        float nextPitch = lookStartPitch
                + RotationUtils.wrapAngleTo180(wanted.getPitch() - lookStartPitch) * (float) amount
                + lookArcPitch * (float) arcAmount;
        client.player.setYRot(nextYaw);
        client.player.setXRot(Mth.clamp(nextPitch, -90.0F, 90.0F));
        client.player.yHeadRot = nextYaw;
    }

    private void calculateLookArc(float yawDelta, float pitchDelta, double angle) {
        if (angle < 0.5) {
            lookArcYaw = 0.0F;
            lookArcPitch = 0.0F;
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double arc = Math.min(0.22, Math.max(0.04, angle * 0.012)) * (random.nextBoolean() ? 1.0 : -1.0);
        lookArcYaw = (float) (-pitchDelta / angle * arc);
        lookArcPitch = (float) (yawDelta / angle * arc);
    }

    private double getLookProgress() {
        return lookDurationMs <= 0L
                ? 1.0
                : Mth.clamp((double) (System.currentTimeMillis() - lookStartedAt) / lookDurationMs, 0.0, 1.0);
    }

    private void prepareSlot(Minecraft client, int slot) {
        if (client.player.getInventory().getSelectedSlot() != slot) {
            client.player.getInventory().setSelectedSlot(slot);
            slotReadyAt = System.currentTimeMillis() + SLOT_SETTLE_MS;
            return;
        }

        slotReadyAt = 0L;
    }

    private int getBowSlot(Minecraft client) {
        int configured = Mth.clamp((int) Math.round(getDouble(bowSlot)), 1, 9) - 1;
        if (isBow(client.player.getInventory().getItem(configured))) {
            return configured;
        }

        for (int i = 0; i < 9; i++) {
            if (isBow(client.player.getInventory().getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    private boolean isBow(ItemStack stack) {
        String id = ItemUtils.getID(stack);
        return stack.getItem() instanceof BowItem
                || id.contains("BOW")
                || "TERMINATOR".equals(id)
                || "JUJU_SHORTBOW".equals(id);
    }

    private long randomActionDelay() {
        long base = getLong(actionDelay);
        if (base <= 0L) {
            return 0L;
        }

        long min = Math.max(0L, Math.round(base * 0.55));
        long max = Math.max(min + 1L, Math.round(base * 1.45));
        return ThreadLocalRandom.current().nextLong(min, max + 1L);
    }

    private long getLong(NumberSetting setting) {
        Object value = setting.getValue();
        return value instanceof BigDecimal bigDecimal ? bigDecimal.longValue() : Long.parseLong(String.valueOf(value));
    }

    private double getDouble(NumberSetting setting) {
        Object value = setting.getValue();
        return value instanceof BigDecimal bigDecimal ? bigDecimal.doubleValue() : Double.parseDouble(String.valueOf(value));
    }

    private double easeInOut(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private void resetState() {
        mode = Mode.IDLE;
        currentPair = null;
        currentTarget = null;
        aimTarget = null;
        detectedPairs.clear();
        completedPairs.clear();
        shotQueue.clear();
        shotIndex = 0;
        runStartedAt = 0L;
        lookStartedAt = 0L;
        lookDurationMs = 0L;
        nextActionAt = 0L;
        slotReadyAt = 0L;
        lookStartYaw = 0.0F;
        lookStartPitch = 0.0F;
        lookArcYaw = 0.0F;
        lookArcPitch = 0.0F;
    }

    private enum Mode {
        IDLE,
        SHOOT_QUEUE
    }

    private record BeamData(int x1, int y1, int z1, int x2, int y2, int z2) {
    }

    private record BeamPair(BlockPos first, BlockPos second) {
        boolean contains(BlockPos pos) {
            return first.equals(pos) || second.equals(pos);
        }

        boolean overlaps(BeamPair other) {
            return other != null && (contains(other.first) || contains(other.second));
        }

        boolean matches(BeamPair other) {
            return other != null
                    && ((first.equals(other.first) && second.equals(other.second))
                    || (first.equals(other.second) && second.equals(other.first)));
        }
    }
}
