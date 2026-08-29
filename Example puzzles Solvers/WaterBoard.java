package com.Bedrock.module.impl.dungeon.safepuzzles;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ricedotwho.rsm.component.impl.Renderer3D;
import com.ricedotwho.rsm.component.impl.location.Island;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.component.impl.map.Map;
import com.ricedotwho.rsm.component.impl.map.handler.Dungeon;
import com.ricedotwho.rsm.component.impl.map.map.Room;
import com.ricedotwho.rsm.component.impl.map.map.RoomRotation;
import com.ricedotwho.rsm.component.impl.map.map.UniqueRoom;
import com.ricedotwho.rsm.component.impl.map.utils.ScanUtils;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.data.Pair;
import com.ricedotwho.rsm.data.Pos;
import com.ricedotwho.rsm.data.Rotation;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.game.ClientTickEvent;
import com.ricedotwho.rsm.event.impl.render.Render3DEvent;
import com.ricedotwho.rsm.event.impl.world.WorldEvent;
import com.ricedotwho.rsm.module.api.SubModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.NumberSetting;
import com.ricedotwho.rsm.utils.ChatUtils;
import com.ricedotwho.rsm.utils.ItemUtils;
import com.ricedotwho.rsm.utils.RotationUtils;
import com.ricedotwho.rsm.utils.render.render3d.type.FilledOutlineBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

@SubModuleInfo(name = "Waterfill", alwaysDisabled = false)
public class Waterfill extends SafePuzzle {
    private static final String SOLUTIONS = "/assets/rsz/dungeons/WaterBoardSolutions.json";
    private static final String POSITIONS = "/assets/rsz/dungeons/Waterboardpositions.json";
    private static final String POSITIONS_ALT = "/assets/rsz/dungeons/Waterboardpositions.Json";

    private static final int FALLBACK_SEARCH_RADIUS_TILES = 3;
    private static final int ROOM_DETECTION_GRACE_TICKS = 600;
    private static final int PATTERN_STABLE_TICKS = 5;
    private static final int STALE_ENTRY_TICKS = 600;
    private static final int AIM_REFRESH_MS = 280;
    private static final int MAX_CLICK_ATTEMPTS = 60;
    private static final long FALL_RESCUE_DELAY_MS = 500L;
    private static final long FALL_RESCUE_COOLDOWN_MS = 1500L;
    private static final long ETHERWARP_LAND_TIMEOUT_MS = 900L;
    private static final long ETHERWARP_RETRY_MS = 250L;
    private static final long LEVER_SLOT_SETTLE_MS = 90L;
    private static final long LEVER_CONFIRM_TIMEOUT_MS = 650L;
    private static final long FLOW_RETARGET_HEAD_START_MS = 8L;
    private static final double FLOW_LINEAR_BLEND = 0.08;

    private static final double CLICK_DISTANCE = 4.05;
    private static final Pos SEA_LANTERN_MIDDLE = new Pos(15, 77, 27);
    private static final Pos TOP_LEFT = new Pos(16, 77, 26);
    private static final Pos TOP_RIGHT = new Pos(14, 77, 26);
    private static final Pos PURPLE_WOOL = new Pos(15, 57, 19);
    private static final Pos FALL_RESCUE_ROOM_POS = new Pos(0.5, 59.0, -0.5);
    private static final List<Block> WOOL_ORDER = List.of(
            Blocks.PURPLE_WOOL,
            Blocks.ORANGE_WOOL,
            Blocks.BLUE_WOOL,
            Blocks.LIME_WOOL,
            Blocks.RED_WOOL
    );

    private final BooleanSetting useEtherwarp = new BooleanSetting("Use Etherwarp", true);
    private final NumberSetting etherwarpSlot = new NumberSetting("Etherwarp Slot", 1.0, 9.0, 2.0, 1.0);
    private final NumberSetting leverClickSlot = new NumberSetting("Lever Click Slot", 1.0, 9.0, 3.0, 1.0);
    private final NumberSetting actionDelay = new NumberSetting("Action Delay (MS)", 0.0, 1000.0, 120.0, 10.0);
    private final NumberSetting lookSpeed = new NumberSetting("Look Speed", 30.0, 720.0, 260.0, 10.0);

    private static java.util.Map<String, java.util.Map<String, java.util.Map<String, List<Double>>>> solutionData;
    private static java.util.Map<String, PositionData> positionData;

    private final List<SolutionEntry> solution = new ArrayList<>();

    private WaterTransform activeTransform;
    private int variant = -1;
    private String subvariant;
    private int pendingVariant = -1;
    private String pendingSubvariant;
    private int pendingPatternTicks;
    private int roomTicks;
    private int openedWaterAt = -1;
    private int waterboardMissTicks;

    private Mode mode = Mode.IDLE;
    private SolutionEntry current;
    private Vec3 aimTarget;
    private long lookStartedAt;
    private long lookDurationMs;
    private long nextActionAt;
    private long nextAimRefreshAt;
    private long lastEtherwarpAt;
    private long etherwarpStartedAt;
    private long leverSlotReadyAt;
    private long leverClickedAt;
    private long fellBelowAt;
    private long lastFallRescueAt;
    private Vec3 etherwarpStartPos;
    private Vec3 etherwarpLandingPoint;
    private int clickAttempts;
    private float lookStartYaw;
    private float lookStartPitch;
    private float lookArcYaw;
    private float lookArcPitch;
    private boolean waitingForLeverState;

    public Waterfill(SafePuzzles module) {
        super(module);
        this.registerProperty(useEtherwarp, etherwarpSlot, leverClickSlot, actionDelay, lookSpeed);
        loadSolutions();
        loadPositions();
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent.Start event) {
        Minecraft client = Minecraft.getInstance();
        if (!getSolver().getValue() || client.player == null || client.level == null) {
            resetState();
            return;
        }

        boolean inWaterBoard = isInWaterBoard();
        if (!inWaterBoard) {
            if (++waterboardMissTicks > ROOM_DETECTION_GRACE_TICKS) {
                resetState();
                return;
            }
            if (!isWaterBoardActive()) {
                return;
            }
        } else {
            waterboardMissTicks = 0;
            detectSolution(client);
        }

        if (handleFallRescue(client)) {
            return;
        }

        roomTicks++;
        tickMode(client);
        if (mode != Mode.IDLE || solution.isEmpty()) {
            return;
        }

        if (skipCompletedSteps(client.level) || solution.isEmpty()) {
            return;
        }

        SolutionEntry next = solution.get(0);
        if (isStale(next)) {
            solution.remove(0);
            return;
        }

        if (System.currentTimeMillis() < nextActionAt && !isDue(next)) {
            return;
        }

        if (!isInClickRange(next, client)) {
            if (shouldEtherwarpTo(next, client)) {
                beginEtherwarp(next, client);
            }
            return;
        }

        if (isDue(next)) {
            beginLeverClick(next, client);
        } else if (shouldPreAim(next, client)) {
            beginLeverPreAim(next, client);
        }
    }

    @SubscribeEvent
    public void onRenderCamera(Render3DEvent.Start event) {
        Minecraft client = Minecraft.getInstance();
        if (!getSolver().getValue() || client.player == null || !isWaterBoardActive()) {
            return;
        }

        if (aimTarget != null) {
            applySmoothLook(client);
        }
    }

    @SubscribeEvent
    public void onRender(Render3DEvent.Extract event) {
        if (!getSolver().getValue() || !isWaterBoardActive() || solution.isEmpty()) {
            return;
        }

        SolutionEntry next = solution.get(0);
        Colour fill = new Colour(60, 200, 120, 55);
        Colour outline = new Colour(60, 240, 150, 180);
        Renderer3D.addTask(new FilledOutlineBox(next.renderBox, fill, outline, false));
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        resetState();
    }

    @Override
    public void reset() {
        resetState();
    }

    private void detectSolution(Minecraft client) {
        WaterTransform transform = getCurrentWaterTransform(client.level);
        if (transform == null) {
            return;
        }

        activeTransform = transform;
        if (subvariant != null) {
            return;
        }

        int detectedVariant = detectVariant(client.level, transform);
        String detectedSubvariant = detectedVariant == -1 ? null : detectSubvariant(client.level, transform);
        if (detectedVariant == -1 || detectedSubvariant == null || detectedSubvariant.length() != 3) {
            clearPendingPattern();
            return;
        }

        if (detectedVariant != pendingVariant || !detectedSubvariant.equals(pendingSubvariant)) {
            pendingVariant = detectedVariant;
            pendingSubvariant = detectedSubvariant;
            pendingPatternTicks = 1;
            return;
        }

        if (++pendingPatternTicks < PATTERN_STABLE_TICKS) {
            return;
        }

        variant = pendingVariant;
        subvariant = pendingSubvariant;
        buildSolution(transform);
    }

    private void buildSolution(WaterTransform transform) {
        java.util.Map<String, java.util.Map<String, List<Double>>> byVariant = solutionData.get(String.valueOf(variant));
        java.util.Map<String, List<Double>> raw = byVariant == null ? null : byVariant.get(subvariant);
        if (raw == null) {
            ChatUtils.chat("Unknown water board variant: " + variant + "/" + subvariant);
            return;
        }

        List<LeverTime> leverTimes = new ArrayList<>();
        for (java.util.Map.Entry<String, List<Double>> entry : raw.entrySet()) {
            Lever lever = Lever.from(entry.getKey());
            for (Double time : entry.getValue()) {
                leverTimes.add(new LeverTime(lever, (int) Math.round(time * 20.0)));
            }
        }
        leverTimes.sort(Comparator.comparingInt((LeverTime lt) -> lt.time).thenComparingInt(lt -> lt.lever.ordinal()));

        solution.clear();
        EnumMap<Lever, Integer> toggleCounts = new EnumMap<>(Lever.class);
        for (LeverTime leverTime : leverTimes) {
            int toggleCount = toggleCounts.merge(leverTime.lever, 1, Integer::sum);
            solution.add(new SolutionEntry(leverTime.lever, leverTime.time, leverTime.time, transform, toggleCount % 2 == 1));
        }
    }

    private void tickMode(Minecraft client) {
        switch (mode) {
            case IDLE -> {
            }
            case PRE_AIM_LEVER -> tickLeverPreAim(client);
            case LOOKING_CLICK -> tickLeverClick(client);
            case LOOKING_ETHERWARP -> tickEtherwarp(client);
            case WAITING_ETHERWARP_LAND -> tickWaitingEtherwarpLand(client);
            case FALL_RESCUE_ETHERWARP -> tickFallRescue(client);
        }
    }

    private boolean handleFallRescue(Minecraft client) {
        if (client.player.getY() >= 59.0) {
            fellBelowAt = 0L;
            return false;
        }

        long now = System.currentTimeMillis();
        if (fellBelowAt == 0L) {
            fellBelowAt = now;
            return mode == Mode.FALL_RESCUE_ETHERWARP;
        }

        if (mode == Mode.FALL_RESCUE_ETHERWARP) {
            tickMode(client);
            return true;
        }

        if (now - fellBelowAt < FALL_RESCUE_DELAY_MS || now - lastFallRescueAt < FALL_RESCUE_COOLDOWN_MS) {
            return false;
        }

        beginFallRescue(client);
        return mode == Mode.FALL_RESCUE_ETHERWARP;
    }

    private void beginFallRescue(Minecraft client) {
        WaterTransform transform = activeTransform != null ? activeTransform : getCurrentWaterTransform(client.level);
        int slot = getEtherwarpSlot(client);
        if (transform == null || slot == -1) {
            return;
        }

        current = null;
        aimTarget = transform.roomRelativeVec(FALL_RESCUE_ROOM_POS);
        mode = Mode.FALL_RESCUE_ETHERWARP;
        client.player.getInventory().setSelectedSlot(slot);
        startLook(client);
    }

    private void beginLeverClick(SolutionEntry entry, Minecraft client) {
        current = entry;
        aimTarget = getAimPoint(client.level, entry.pos);
        mode = Mode.LOOKING_CLICK;
        clickAttempts = 0;
        nextAimRefreshAt = 0L;
        waitingForLeverState = false;
        leverClickedAt = 0L;
        prepareLeverClickSlot(client);
        startLook(client);
    }

    private void beginLeverPreAim(SolutionEntry entry, Minecraft client) {
        current = entry;
        aimTarget = getAimPoint(client.level, entry.pos);
        mode = Mode.PRE_AIM_LEVER;
        clickAttempts = 0;
        nextAimRefreshAt = System.currentTimeMillis() + AIM_REFRESH_MS;
        startLook(client);
    }

    private void tickLeverPreAim(Minecraft client) {
        if (current == null || solution.isEmpty() || solution.get(0) != current) {
            clearAction(client);
            return;
        }

        if (getLookProgress() >= 1.0) {
            long now = System.currentTimeMillis();
            applySmoothLook(client);
            if (isDue(current) && (now >= nextActionAt || isLate(current))) {
                mode = Mode.LOOKING_CLICK;
                clickAttempts = 0;
                nextAimRefreshAt = 0L;
                prepareLeverClickSlot(client);
                tickLeverClick(client);
                return;
            }

            if (now >= nextAimRefreshAt && getLookHit(client, current.pos) == null) {
                aimTarget = getAimPoint(client.level, current.pos);
                nextAimRefreshAt = now + AIM_REFRESH_MS;
                startLook(client, true);
                return;
            }
        }
    }

    private void tickLeverClick(Minecraft client) {
        if (current == null || solution.isEmpty() || solution.get(0) != current) {
            clearAction(client);
            return;
        }

        if (isStepAlreadyComplete(client.level, current)) {
            completeCurrentStep();
            nextActionAt = System.currentTimeMillis() + randomActionDelay();
            clearAction(client);
            return;
        }

        long now = System.currentTimeMillis();
        if (waitingForLeverState) {
            if (now - leverClickedAt < LEVER_CONFIRM_TIMEOUT_MS) {
                return;
            }

            waitingForLeverState = false;
            leverClickedAt = 0L;
            if (++clickAttempts >= MAX_CLICK_ATTEMPTS) {
                if (isStale(current)) {
                    solution.remove(0);
                }
                clearAction(client);
                return;
            }
        }

        if (!isDue(current)) {
            return;
        }

        if (getLookProgress() < 1.0) {
            return;
        }

        applySmoothLook(client);
        if (now < leverSlotReadyAt) {
            return;
        }

        if (rightClickBlock(client, current.pos)) {
            waitingForLeverState = true;
            leverClickedAt = now;
            nextAimRefreshAt = now + LEVER_CONFIRM_TIMEOUT_MS;
            return;
        }

        if (isTooFarForLegitClick(client, current)) {
            if (useEtherwarp.getValue() && getEtherwarpSlot(client) != -1) {
                beginEtherwarp(current, client);
            } else {
                clearAction(client);
            }
            return;
        }

        if (now < nextAimRefreshAt) {
            return;
        }

        if (++clickAttempts >= MAX_CLICK_ATTEMPTS) {
            if (isStale(current)) {
                solution.remove(0);
            }
            clearAction(client);
            return;
        }

        aimTarget = getAimPoint(client.level, current.pos);
        nextAimRefreshAt = now + AIM_REFRESH_MS;
        startLook(client, true);
    }

    private void beginEtherwarp(SolutionEntry entry, Minecraft client) {
        int slot = getEtherwarpSlot(client);
        if (slot == -1) {
            return;
        }

        current = entry;
        etherwarpLandingPoint = getBestEtherwarpPoint(client, entry);
        aimTarget = getEtherwarpAimPoint(client, entry, etherwarpLandingPoint);
        mode = Mode.LOOKING_ETHERWARP;
        etherwarpStartPos = client.player.position();
        etherwarpStartedAt = 0L;
        client.player.getInventory().setSelectedSlot(slot);
        startLook(client);
    }

    private void tickEtherwarp(Minecraft client) {
        if (current == null || aimTarget == null) {
            clearAction(client);
            return;
        }

        if (getLookProgress() < 1.0) {
            return;
        }

        applySmoothLook(client);
        boolean wasSneaking = client.options.keyShift.isDown() || client.player.isShiftKeyDown();
        client.options.keyShift.setDown(true);
        client.player.setShiftKeyDown(true);
        client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
        client.player.swing(InteractionHand.MAIN_HAND);
        client.options.keyShift.setDown(wasSneaking);
        client.player.setShiftKeyDown(wasSneaking);

        lastEtherwarpAt = System.currentTimeMillis();
        nextActionAt = lastEtherwarpAt + randomActionDelay();
        etherwarpStartedAt = lastEtherwarpAt;
        aimTarget = null;
        mode = Mode.WAITING_ETHERWARP_LAND;
    }

    private void tickWaitingEtherwarpLand(Minecraft client) {
        if (current == null || solution.isEmpty() || solution.get(0) != current) {
            clearAction(client);
            return;
        }

        long now = System.currentTimeMillis();
        boolean moved = etherwarpStartPos != null && client.player.position().distanceToSqr(etherwarpStartPos) > 2.25;
        boolean nearWarpSpot = etherwarpLandingPoint != null && client.player.position().distanceToSqr(etherwarpLandingPoint) < 12.25;
        if (moved || nearWarpSpot || now - etherwarpStartedAt >= ETHERWARP_LAND_TIMEOUT_MS) {
            if (!isInClickRange(current, client)) {
                if (now - etherwarpStartedAt < ETHERWARP_LAND_TIMEOUT_MS + ETHERWARP_RETRY_MS) {
                    return;
                }
                beginEtherwarp(current, client);
                return;
            }
            beginLeverPreAim(current, client);
        }
    }

    private void tickFallRescue(Minecraft client) {
        if (aimTarget == null || client.gameMode == null) {
            clearAction(client);
            return;
        }

        if (getLookProgress() < 1.0) {
            return;
        }

        applySmoothLook(client);
        boolean wasSneaking = client.options.keyShift.isDown() || client.player.isShiftKeyDown();
        client.options.keyShift.setDown(true);
        client.player.setShiftKeyDown(true);
        client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
        client.player.swing(InteractionHand.MAIN_HAND);
        client.options.keyShift.setDown(wasSneaking);
        client.player.setShiftKeyDown(wasSneaking);

        lastFallRescueAt = System.currentTimeMillis();
        fellBelowAt = 0L;
        nextActionAt = lastFallRescueAt + randomActionDelay();
        clearAction(client);
    }

    private boolean shouldEtherwarpTo(SolutionEntry entry, Minecraft client) {
        if (!useEtherwarp.getValue()
                || client.player == null
                || client.gameMode == null
                || isInClickRange(entry, client)) {
            return false;
        }

        return getEtherwarpSlot(client) != -1;
    }

    private boolean shouldPreAim(SolutionEntry entry, Minecraft client) {
        return openedWaterAt != -1
                && client.player != null
                && isInClickRange(entry, client)
                && getRemainingTicks(entry) <= Math.max(4, Math.ceil(fixedLookDurationMs() / 50.0) + 2);
    }

    private boolean isDue(SolutionEntry entry) {
        return openedWaterAt == -1 ? entry.readyTime <= 0 : getRemainingTicks(entry) <= 0;
    }

    private boolean isLate(SolutionEntry entry) {
        return openedWaterAt != -1 && getDeadlineRemainingTicks(entry) < -1;
    }

    private int getRemainingTicks(SolutionEntry entry) {
        return entry.readyTime - (roomTicks - openedWaterAt);
    }

    private int getDeadlineRemainingTicks(SolutionEntry entry) {
        return entry.time - (roomTicks - openedWaterAt);
    }

    private boolean isStale(SolutionEntry entry) {
        return openedWaterAt != -1 && getDeadlineRemainingTicks(entry) < -STALE_ENTRY_TICKS;
    }

    private boolean skipCompletedSteps(ClientLevel level) {
        boolean skipped = false;
        while (!solution.isEmpty() && isDue(solution.get(0)) && isStepAlreadyComplete(level, solution.get(0))) {
            completeCurrentStep();
            skipped = true;
        }
        return skipped;
    }

    private boolean isStepAlreadyComplete(ClientLevel level, SolutionEntry entry) {
        Boolean powered = getLeverPowered(level, entry.pos);
        return powered != null && powered == entry.expectedPoweredAfterClick;
    }

    private Boolean getLeverPowered(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof LeverBlock ? state.getValue(LeverBlock.POWERED) : null;
    }

    private void completeCurrentStep() {
        if (solution.isEmpty()) {
            return;
        }

        SolutionEntry entry = solution.remove(0);
        if (entry.lever == Lever.Water && openedWaterAt == -1 && entry.expectedPoweredAfterClick) {
            openedWaterAt = roomTicks;
        }
    }

    private boolean isInClickRange(SolutionEntry entry, Minecraft client) {
        return client.player != null
                && client.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(entry.pos)) <= CLICK_DISTANCE * CLICK_DISTANCE;
    }

    private boolean isTooFarForLegitClick(Minecraft client, SolutionEntry entry) {
        Vec3 point = aimTarget != null ? aimTarget : Vec3.atCenterOf(entry.pos);
        return client.player.getEyePosition().distanceToSqr(point) > (CLICK_DISTANCE - 0.2) * (CLICK_DISTANCE - 0.2);
    }

    private void startLook(Minecraft client) {
        startLook(client, false);
    }

    private void startLook(Minecraft client, boolean flowingRetarget) {
        lookStartedAt = System.currentTimeMillis() - (flowingRetarget ? FLOW_RETARGET_HEAD_START_MS : 0L);
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
        double amount = flowEase(getLookProgress());
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

    private boolean rightClickBlock(Minecraft client, BlockPos block) {
        if (client.gameMode == null || !(client.level.getBlockState(block).getBlock() instanceof LeverBlock)) {
            return false;
        }

        BlockHitResult hit = getLookHit(client, block);
        if (hit == null) {
            hit = getFallbackLeverHit(client, block);
            if (hit == null) {
                return false;
            }
        }

        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
        client.player.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    private BlockHitResult getFallbackLeverHit(Minecraft client, BlockPos block) {
        Vec3 eye = client.player.getEyePosition();
        Vec3 center = getAimPoint(client.level, block);
        double maxDistance = CLICK_DISTANCE + 0.5;
        if (eye.distanceToSqr(center) > maxDistance * maxDistance) {
            return null;
        }

        return new BlockHitResult(center, getNearestDirection(center.subtract(eye)), block, false);
    }

    private Direction getNearestDirection(Vec3 delta) {
        double ax = Math.abs(delta.x);
        double ay = Math.abs(delta.y);
        double az = Math.abs(delta.z);
        if (ay >= ax && ay >= az) {
            return delta.y >= 0.0 ? Direction.UP : Direction.DOWN;
        }
        if (ax >= az) {
            return delta.x >= 0.0 ? Direction.EAST : Direction.WEST;
        }
        return delta.z >= 0.0 ? Direction.SOUTH : Direction.NORTH;
    }

    private BlockHitResult getLookHit(Minecraft client, BlockPos expected) {
        return client.hitResult instanceof BlockHitResult crosshairHit
                && crosshairHit.getType() != HitResult.Type.MISS
                && crosshairHit.getBlockPos().equals(expected)
                ? crosshairHit
                : null;
    }

    private Vec3 getAimPoint(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getShape(level, pos);
        AABB box = shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
        Vec3 center = box.getCenter();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double jitter = 0.055;
        boolean xDepth = box.getXsize() <= box.getYsize() && box.getXsize() <= box.getZsize();
        boolean yDepth = box.getYsize() < box.getXsize() && box.getYsize() <= box.getZsize();
        double x = center.x + (xDepth ? 0.0 : middleOffset(random, Math.min(jitter, box.getXsize() * 0.35)));
        double y = center.y + (yDepth ? 0.0 : middleOffset(random, Math.min(jitter, box.getYsize() * 0.35)));
        double z = center.z + (!xDepth && !yDepth ? 0.0 : middleOffset(random, Math.min(jitter, box.getZsize() * 0.35)));
        return new Vec3(x, y, z);
    }

    private Vec3 getEtherwarpAimPoint(Minecraft client, SolutionEntry entry, Vec3 point) {
        double distanceToLever = horizontalDistance(client.player.position(), Vec3.atCenterOf(entry.pos));
        if (distanceToLever < 7.0) {
            point = point.add(0.0, -0.65, 0.0);
        }
        return point;
    }

    private Vec3 getBestEtherwarpPoint(Minecraft client, SolutionEntry entry) {
        Vec3 base = entry.etherwarpPoint;
        Vec3 best = base;
        double bestScore = scoreEtherwarpPoint(client.player.position(), entry.approachPoint, base);
        for (double ox = -1.0; ox <= 1.0; ox += 1.0) {
            for (double oz = -1.0; oz <= 1.0; oz += 1.0) {
                Vec3 candidate = base.add(ox, 0.0, oz);
                if (!isUsableEtherwarpBlock(client.level, candidate)) {
                    continue;
                }
                double score = scoreEtherwarpPoint(client.player.position(), entry.approachPoint, candidate);
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return best;
    }

    private boolean isUsableEtherwarpBlock(ClientLevel level, Vec3 point) {
        BlockPos floor = BlockPos.containing(point.x, point.y, point.z);
        return !level.getBlockState(floor).isAir()
                && level.getBlockState(floor.above()).isAir()
                && level.getBlockState(floor.above(2)).isAir();
    }

    private double scoreEtherwarpPoint(Vec3 player, Vec3 approach, Vec3 point) {
        return horizontalDistance(player, point) * 0.35 + horizontalDistance(approach, point);
    }

    private int getEtherwarpSlot(Minecraft client) {
        int configured = Mth.clamp((int) Math.round(getDouble(etherwarpSlot)), 1, 9) - 1;
        ItemStack stack = client.player.getInventory().getItem(configured);
        String id = ItemUtils.getID(stack);
        if ("ASPECT_OF_THE_VOID".equals(id) || "ASPECT_OF_THE_END".equals(id)) {
            return configured;
        }

        for (int i = 0; i < 9; i++) {
            stack = client.player.getInventory().getItem(i);
            id = ItemUtils.getID(stack);
            if ("ASPECT_OF_THE_VOID".equals(id) || "ASPECT_OF_THE_END".equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private void prepareLeverClickSlot(Minecraft client) {
        int slot = Mth.clamp((int) Math.round(getDouble(leverClickSlot)), 1, 9) - 1;
        if (client.player.getInventory().getSelectedSlot() != slot) {
            client.player.getInventory().setSelectedSlot(slot);
            leverSlotReadyAt = System.currentTimeMillis() + LEVER_SLOT_SETTLE_MS;
            return;
        }

        leverSlotReadyAt = 0L;
    }

    private boolean isInWaterBoard() {
        if (!Location.getArea().is(Island.Dungeon) || Dungeon.isInBoss()) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return false;
        }

        WaterTransform transform = getCurrentWaterTransform(client.level);
        if (transform == null) {
            return false;
        }

        if (!isConfirmedCurrentWaterBoard(client, transform)) {
            return false;
        }

        activeTransform = transform;
        return true;
    }

    private boolean isConfirmedCurrentWaterBoard(Minecraft client, WaterTransform transform) {
        if (!isPlayerInTransformRoom(client, transform)) {
            return false;
        }

        String roomName = getCurrentRoomName();
        if (roomName != null && roomName.toLowerCase().contains("water")) {
            return true;
        }

        if (!transform.inferred) {
            return true;
        }

        Pair<Integer, Integer> center = ScanUtils.getRoomCenter((int) client.player.position().x(), (int) client.player.position().z());
        return center != null
                && center.getFirst() == transform.x
                && center.getSecond() == transform.z
                && detectVariant(client.level, transform) != -1;
    }

    private boolean isActiveWaterBoardStillCurrent() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null
                && activeTransform != null
                && isPlayerInTransformRoom(client, activeTransform);
    }

    private boolean isPlayerInTransformRoom(Minecraft client, WaterTransform transform) {
        Room current = getCurrentWaterTile();
        if (current != null && current.getUniqueRoom() != null && current.getUniqueRoom().getMainRoom() != null) {
            Room mainRoom = current.getUniqueRoom().getMainRoom();
            return mainRoom.getX() == transform.x && mainRoom.getZ() == transform.z;
        }

        Pair<Integer, Integer> center = ScanUtils.getRoomCenter((int) client.player.position().x(), (int) client.player.position().z());
        return center != null && center.getFirst() == transform.x && center.getSecond() == transform.z;
    }

    private boolean isWaterBoardActive() {
        return waterboardMissTicks <= ROOM_DETECTION_GRACE_TICKS
                && (activeTransform != null || !solution.isEmpty() || mode != Mode.IDLE);
    }

    private String getCurrentRoomName() {
        Room room = getCurrentWaterTile();
        if (room == null) {
            return null;
        }
        if (room.getData() != null && room.getData().name() != null) {
            return room.getData().name();
        }
        return room.getUniqueRoom() == null ? null : room.getUniqueRoom().getName();
    }

    private Room getCurrentWaterTile() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return null;
        }

        Room room = Map.getCurrentRoom();
        if (room != null) {
            return room;
        }
        return ScanUtils.getRoomFromPos((int) client.player.position().x(), (int) client.player.position().z());
    }

    private Room getMainWaterRoom() {
        Room room = getCurrentWaterTile();
        if (room == null || room.getUniqueRoom() == null || room.getUniqueRoom().getMainRoom() == null) {
            return null;
        }
        return room.getUniqueRoom().getMainRoom();
    }

    private WaterTransform getCurrentWaterTransform(ClientLevel level) {
        Room mainRoom = getMainWaterRoom();
        if (mainRoom != null
                && mainRoom.getUniqueRoom() != null
                && mainRoom.getUniqueRoom().getRotation() != RoomRotation.UNKNOWN) {
            WaterTransform roomTransform = new WaterTransform(mainRoom.getUniqueRoom(), false);
            if (detectVariant(level, roomTransform) != -1) {
                return roomTransform;
            }
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return null;
        }

        Pair<Integer, Integer> center = ScanUtils.getRoomCenter((int) client.player.position().x(), (int) client.player.position().z());
        if (center == null) {
            return null;
        }

        for (int dx = -FALLBACK_SEARCH_RADIUS_TILES; dx <= FALLBACK_SEARCH_RADIUS_TILES; dx++) {
            for (int dz = -FALLBACK_SEARCH_RADIUS_TILES; dz <= FALLBACK_SEARCH_RADIUS_TILES; dz++) {
                int originX = center.getFirst() + dx * 32;
                int originZ = center.getSecond() + dz * 32;
                for (RoomRotation rotation : RoomRotation.values()) {
                    if (rotation == RoomRotation.UNKNOWN) {
                        continue;
                    }
                    WaterTransform transform = new WaterTransform(originX, originZ, rotation, true);
                    if (detectVariant(level, transform) != -1) {
                        return transform;
                    }
                }
            }
        }

        return null;
    }

    private int detectVariant(ClientLevel level, WaterTransform transform) {
        int y = level.getBlockState(transform.block(SEA_LANTERN_MIDDLE)).is(Blocks.SEA_LANTERN) ? 77 : 78;
        Block left = getTopBlock(level, transform, TOP_LEFT, y);
        Block right = getTopBlock(level, transform, TOP_RIGHT, y);

        if (left == Blocks.GOLD_BLOCK && right == Blocks.TERRACOTTA) return 0;
        if (left == Blocks.EMERALD_BLOCK && right == Blocks.QUARTZ_BLOCK) return 1;
        if (left == Blocks.QUARTZ_BLOCK && right == Blocks.DIAMOND_BLOCK) return 2;
        if (left == Blocks.GOLD_BLOCK && right == Blocks.QUARTZ_BLOCK) return 3;
        return -1;
    }

    private Block getTopBlock(ClientLevel level, WaterTransform transform, Pos local, int y) {
        BlockPos pos = transform.block(new Pos(local.x, y, local.z));
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.is(Blocks.STONE)) {
            pos = transform.block(new Pos(local.x, y, local.z + 1));
            state = level.getBlockState(pos);
        }
        return state.getBlock();
    }

    private String detectSubvariant(ClientLevel level, WaterTransform transform) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < WOOL_ORDER.size(); i++) {
            BlockPos pos = transform.block(new Pos(PURPLE_WOOL.x, PURPLE_WOOL.y, PURPLE_WOOL.z - i));
            if (level.getBlockState(pos).is(WOOL_ORDER.get(i))) {
                builder.append(i);
            }
        }
        return builder.length() == 3 ? builder.toString() : null;
    }

    private void clearPendingPattern() {
        pendingVariant = -1;
        pendingSubvariant = null;
        pendingPatternTicks = 0;
    }

    private void clearAction(Minecraft client) {
        clearActionOnly();
    }

    private void clearActionOnly() {
        mode = Mode.IDLE;
        current = null;
        aimTarget = null;
        clickAttempts = 0;
        nextAimRefreshAt = 0L;
        etherwarpStartedAt = 0L;
        leverClickedAt = 0L;
        lookStartedAt = 0L;
        lookDurationMs = 0L;
        etherwarpStartPos = null;
        etherwarpLandingPoint = null;
        lookStartYaw = 0.0F;
        lookStartPitch = 0.0F;
        lookArcYaw = 0.0F;
        lookArcPitch = 0.0F;
        waitingForLeverState = false;
    }

    private void resetState() {
        clearAction(Minecraft.getInstance());
        solution.clear();
        activeTransform = null;
        variant = -1;
        subvariant = null;
        clearPendingPattern();
        roomTicks = 0;
        openedWaterAt = -1;
        waterboardMissTicks = 0;
        nextActionAt = 0L;
        lastEtherwarpAt = 0L;
        leverSlotReadyAt = 0L;
        fellBelowAt = 0L;
        lastFallRescueAt = 0L;
    }

    private double horizontalDistance(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private double easeInOut(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private double flowEase(double t) {
        return FLOW_LINEAR_BLEND * t + (1.0 - FLOW_LINEAR_BLEND) * easeInOut(t);
    }

    private double middleOffset(ThreadLocalRandom random, double amount) {
        if (amount <= 0.0) {
            return 0.0;
        }
        return (random.nextDouble(-amount, amount) + random.nextDouble(-amount, amount)) * 0.5;
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

    private static void loadSolutions() {
        if (solutionData != null) {
            return;
        }

        Type type = new TypeToken<HashMap<String, HashMap<String, HashMap<String, List<Double>>>>>() {}.getType();
        try (InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(Waterfill.class.getResourceAsStream(SOLUTIONS)), StandardCharsets.UTF_8)) {
            solutionData = new Gson().fromJson(reader, type);
        } catch (IOException | NullPointerException exception) {
            solutionData = java.util.Map.of();
            ChatUtils.chat("Failed to load WaterBoardSolutions.json");
        }
    }

    private static void loadPositions() {
        if (positionData != null) {
            return;
        }

        Type type = new TypeToken<HashMap<String, PositionData>>() {}.getType();
        try (InputStreamReader reader = openPositionsReader()) {
            positionData = new Gson().fromJson(reader, type);
            if (positionData == null) {
                positionData = java.util.Map.of();
            }
        } catch (IOException | NullPointerException exception) {
            positionData = java.util.Map.of();
        }
    }

    private static InputStreamReader openPositionsReader() {
        java.io.InputStream stream = Waterfill.class.getResourceAsStream(POSITIONS);
        if (stream == null) {
            stream = Waterfill.class.getResourceAsStream(POSITIONS_ALT);
        }
        return new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8);
    }

    private static PositionData getPositionData(Lever lever) {
        if (positionData == null) {
            return null;
        }
        PositionData data = positionData.get(lever.type);
        return data != null ? data : positionData.get(lever.name());
    }

    private record LeverTime(Lever lever, int time) {
    }

    private enum Mode {
        IDLE,
        PRE_AIM_LEVER,
        LOOKING_CLICK,
        LOOKING_ETHERWARP,
        WAITING_ETHERWARP_LAND,
        FALL_RESCUE_ETHERWARP
    }

    private static class SolutionEntry {
        final Lever lever;
        final int readyTime;
        final int time;
        final BlockPos pos;
        final Vec3 approachPoint;
        final Vec3 etherwarpPoint;
        final AABB renderBox;
        final boolean expectedPoweredAfterClick;

        SolutionEntry(Lever lever, int readyTime, int time, WaterTransform transform, boolean expectedPoweredAfterClick) {
            this.lever = lever;
            this.readyTime = readyTime;
            this.time = time;
            this.expectedPoweredAfterClick = expectedPoweredAfterClick;
            PositionData data = getPositionData(lever);
            this.pos = transform.block(new Pos(lever.x, lever.y, lever.z));
            this.approachPoint = transform.vec(lever.approachLocal());
            this.etherwarpPoint = data != null && data.etherwarp != null
                    ? transform.vec(data.etherwarp.toPos())
                    : transform.vec(lever.etherwarpLocal());

            Vec3 p1 = transform.vec(new Pos(lever.x1, lever.y1, lever.z1));
            Vec3 p2 = transform.vec(new Pos(lever.x2, lever.y1 + lever.height, lever.z2));
            this.renderBox = new AABB(
                    Math.min(p1.x, p2.x),
                    Math.min(p1.y, p2.y),
                    Math.min(p1.z, p2.z),
                    Math.max(p1.x, p2.x),
                    Math.max(p1.y, p2.y),
                    Math.max(p1.z, p2.z)
            );
        }
    }

    private static class PositionData {
        PositionVec etherwarp;
    }

    private static class PositionVec {
        double x;
        double y;
        double z;

        Pos toPos() {
            return new Pos(x, y, z);
        }
    }

    private static class WaterTransform {
        final int x;
        final int z;
        final RoomRotation rotation;
        final UniqueRoom room;
        final boolean inferred;

        WaterTransform(int x, int z, RoomRotation rotation, boolean inferred) {
            this.x = x;
            this.z = z;
            this.rotation = rotation;
            this.room = null;
            this.inferred = inferred;
        }

        WaterTransform(UniqueRoom room, boolean inferred) {
            this.x = room.getMainRoom().getX();
            this.z = room.getMainRoom().getZ();
            this.rotation = room.getRotation();
            this.room = room;
            this.inferred = inferred;
        }

        BlockPos block(Pos local) {
            Vec3 real = vec(local);
            return BlockPos.containing(real.x, real.y, real.z);
        }

        Vec3 vec(Pos local) {
            if (room != null) {
                return room.getMainRoom().getRealPositionFixed(local).asVec3();
            }

            double realX = local.x() - 15.0;
            double realZ = local.z() - 15.0;
            switch (rotation) {
                case TOPRIGHT -> {
                    double oldX = realX;
                    realX = -realZ;
                    realZ = oldX;
                }
                case BOTRIGHT -> {
                    realX = -realX;
                    realZ = -realZ;
                }
                case BOTLEFT -> {
                    double oldX = realX;
                    realX = realZ;
                    realZ = -oldX;
                }
                case TOPLEFT, UNKNOWN -> {
                }
            }
            return new Vec3(realX + x, local.y(), realZ + z);
        }

        Vec3 roomRelativeVec(Pos local) {
            if (room != null) {
                return room.getMainRoom().getRealPositionFixed(local).asVec3();
            }

            double realX = local.x();
            double realZ = local.z();
            switch (rotation) {
                case TOPRIGHT -> {
                    double oldX = realX;
                    realX = -realZ;
                    realZ = oldX;
                }
                case BOTRIGHT -> {
                    realX = -realX;
                    realZ = -realZ;
                }
                case BOTLEFT -> {
                    double oldX = realX;
                    realX = realZ;
                    realZ = -oldX;
                }
                case TOPLEFT, UNKNOWN -> {
                }
            }
            return new Vec3(realX + x, local.y(), realZ + z);
        }

        @Override
        public String toString() {
            return rotation + "@" + x + "," + z + (inferred ? " inferred" : " room");
        }
    }

    private enum Lever {
        Quartz("quartz_block", 20, 61, 20, 20.625, 20.3125, 21.0, 20.6875, 61.25, 0.5),
        Gold("gold_block", 20, 61, 15, 20.625, 15.3125, 21.0, 15.6875, 61.25, 0.5),
        Coal("coal_block", 20, 61, 10, 20.625, 10.3125, 21.0, 10.6875, 61.25, 0.5),
        Diamond("diamond_block", 10, 61, 20, 10.0, 20.3125, 10.375, 20.6875, 61.25, 0.5),
        Emerald("emerald_block", 10, 61, 15, 10.0, 15.3125, 10.375, 15.6875, 61.25, 0.5),
        Terracotta("hardened_clay", 10, 61, 10, 10.0, 10.3125, 10.375, 10.6875, 61.25, 0.5),
        Water("water", 15, 60, 5, 15.25, 5.3125, 15.75, 5.6875, 60.0, 0.375);

        final String type;
        final int x;
        final int y;
        final int z;
        final double x1;
        final double z1;
        final double x2;
        final double z2;
        final double y1;
        final double height;

        Lever(String type, int x, int y, int z, double x1, double z1, double x2, double z2, double y1, double height) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.x1 = x1;
            this.z1 = z1;
            this.x2 = x2;
            this.z2 = z2;
            this.y1 = y1;
            this.height = height;
        }

        Pos approachLocal() {
            if (this == Water) {
                return new Pos(15.5, 59.0, 7.35);
            }
            double sideOffset = z >= 20 ? -0.85 : 0.85;
            return x > 15
                    ? new Pos(17.55, 60.0, z + 0.5 + sideOffset)
                    : new Pos(12.45, 60.0, z + 0.5 + sideOffset);
        }

        Pos etherwarpLocal() {
            if (this == Water) {
                return new Pos(15.5, 59.0, 6.5);
            }
            return x > 15
                    ? new Pos(19.5, 60.0, z + 0.5)
                    : new Pos(11.5, 60.0, z + 0.5);
        }

        static Lever from(String type) {
            for (Lever lever : values()) {
                if (lever.type.equals(type)) {
                    return lever;
                }
            }
            throw new IllegalArgumentException("Unknown lever type: " + type);
        }
    }
}
