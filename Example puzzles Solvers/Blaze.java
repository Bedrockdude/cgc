package com.Bedrock.module.impl.dungeon.safepuzzles;

import com.ricedotwho.rsm.component.impl.Renderer3D;
import com.ricedotwho.rsm.component.impl.location.Island;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.component.impl.map.Map;
import com.ricedotwho.rsm.component.impl.map.handler.Dungeon;
import com.ricedotwho.rsm.component.impl.map.map.Room;
import com.ricedotwho.rsm.component.impl.map.utils.ScanUtils;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.data.Keybind;
import com.ricedotwho.rsm.data.Pos;
import com.ricedotwho.rsm.data.Rotation;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.game.ClientTickEvent;
import com.ricedotwho.rsm.event.impl.render.Render3DEvent;
import com.ricedotwho.rsm.event.impl.world.WorldEvent;
import com.ricedotwho.rsm.module.api.SubModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.KeybindSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.NumberSetting;
import com.ricedotwho.rsm.utils.ChatUtils;
import com.ricedotwho.rsm.utils.ItemUtils;
import com.ricedotwho.rsm.utils.RotationUtils;
import com.ricedotwho.rsm.utils.render.render3d.type.FilledOutlineBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SubModuleInfo(name = "Blaze", alwaysDisabled = false)
public class Blaze extends SafePuzzle {
    private static final Pos ROOM_MIDDLE = new Pos(0.5, 0.0, 0.5);
    private static final Pos HIGH_TO_LOW_STAND = new Pos(-6.5, 47, 1.5);
    private static final Pos LOW_TO_HIGH_STAND = new Pos(-6.5, 97, 0.5);
    private static final Pattern BLAZE_HP = Pattern.compile(".*Blaze [\\d,]+/([\\d,]+).*");
    private static final double ARRIVE_DISTANCE_SQ = 0.18;
    private static final double INSIDE_ROOM_LIMIT = 16.25;
    private static final long SLOT_SETTLE_MS = 90L;
    private static final long EDGE_CROUCH_AFTER_STOP_MS = 150L;
    private static final long EDGE_UNSHIFT_SETTLE_MS = 150L;
    private static final long EDGE_STOP_MAX_WAIT_MS = 1200L;
    private static final double EDGE_STOP_SPEED_SQ = 0.0004;
    private static final int ROOM_SEARCH_RADIUS_TILES = 2;
    private static final Pos BLAZE_SCAN_MIN = new Pos(-18.0, 20.0, -18.0);
    private static final Pos BLAZE_SCAN_MAX = new Pos(18.0, 125.0, 18.0);
    // Bottom-to-top physical blaze slots.
    private static final AimPoint[] LOW_TO_HIGH_AIM_POINTS = {
            new AimPoint(1.0, 79.0, -2.0),
            new AimPoint(-1.0, 81.5, 0.0),
            new AimPoint(1.0, 87.0, 2.0),
            new AimPoint(3.0, 91.0, 0.0),
            new AimPoint(1.0, 95.0, -2.0),
            new AimPoint(-1.0, 99.0, 0.0),
            new AimPoint(1.0, 103.0, 2.0),
            new AimPoint(3.0, 107.0, 0.0),
            new AimPoint(1.0, 111.0, -2.0),
            new AimPoint(-1.0, 116.5, 0.0)
    };
    private static final AimPoint[] HIGH_TO_LOW_AIM_POINTS = {
            new AimPoint(1.0, 29.0, -2.0),
            new AimPoint(-1.0, 32.0, 0.0),
            new AimPoint(1.0, 37.0, 2.0),
            new AimPoint(3.0, 41.0, 0.0),
            new AimPoint(1.0, 45.0, -2.0),
            new AimPoint(-1.0, 49.0, 0.0),
            new AimPoint(1.0, 53.0, 2.0),
            new AimPoint(3.0, 57.0, 0.0),
            new AimPoint(1.0, 61.0, -2.0),
            new AimPoint(-1.0, 66.5, 0.0)
    };

    private final BooleanSetting autoPosition = new BooleanSetting("Auto Position", true);
    private final BooleanSetting debug = new BooleanSetting("Debug", true);
    private final NumberSetting bowSlot = new NumberSetting("Bow Slot", 1.0, 9.0, 1.0, 1.0);
    private final NumberSetting lookTime = new NumberSetting("Look Time (MS)", 10.0, 1000.0, 220.0, 10.0);
    private final NumberSetting edgeWalkTime = new NumberSetting("Edge Walk Time (MS)", 50.0, 2000.0, 250.0, 25.0);
    private final KeybindSetting startKey = new KeybindSetting("Start Key", new Keybind(GLFW.GLFW_KEY_UNKNOWN, false, this::requestManualStart));
    private final KeybindSetting captureAimKey = new KeybindSetting("Capture Aim Key", new Keybind(GLFW.GLFW_KEY_UNKNOWN, false, this::captureAim));

    private final List<BlazeSlot> blazeSlots = new ArrayList<>();
    private final List<AimPoint> shotQueue = new ArrayList<>();

    private Mode mode = Mode.IDLE;
    private Variant variant;
    private Room activeRoom;
    private Vec3 standPoint;
    private Vec3 aimTarget;
    private long lookStartedAt;
    private long edgeWalkUntil;
    private long edgeCrouchUntil;
    private long edgeUnshiftUntil;
    private long edgeStopWaitStartedAt;
    private long nextShootAt;
    private long nextTargetAt;
    private long slotReadyAt;
    private long lastStatusAt;
    private float lookStartYaw;
    private float lookStartPitch;
    private int shotIndex;
    private int shotsFiredAtTarget;
    private boolean shotTargetActive;
    private boolean captureHeld;
    private boolean forwardOwned;
    private boolean sneakOwned;
    private boolean startHeld;
    private boolean manualStartRequested;

    public Blaze(SafePuzzles module) {
        super(module);
        this.registerProperty(autoPosition, debug, bowSlot, lookTime, edgeWalkTime, startKey, captureAimKey);
    }

    @Override
    protected void onEnable() {
        startKey.getValue().register();
        captureAimKey.getValue().register();
        status("Blaze: enabled");
    }

    @Override
    protected void onDisable() {
        startKey.getValue().unregister();
        captureAimKey.getValue().unregister();
        releaseInputs(Minecraft.getInstance());
        resetState();
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent.Start event) {
        Minecraft client = Minecraft.getInstance();
        if (!getSolver().getValue() || client.player == null || client.level == null) {
            releaseInputs(client);
            resetState();
            return;
        }

        tickStartKey();
        tickCaptureKey();

        Room room = manualStartRequested ? getMainCurrentRoom() : getMainBlazeRoom();
        if (room == null && isSequenceActive()) {
            room = activeRoom;
        }
        if (room == null) {
            releaseInputs(client);
            debugStatus(client, "waiting");
            resetState();
            return;
        }

        Variant detected = detectVariant(client, room);
        if (manualStartRequested) {
            manualStartRequested = false;
            status("Blaze: manual start in " + roomDebugName(room) + " as " + detected.name());
        }
        if (detected != variant) {
            releaseInputs(client);
            resetState();
            variant = detected;
        }

        activeRoom = room;
        standPoint = getStandPoint(room, variant);
        scanBlazes(client, room);
        if (!autoPosition.getValue() || standPoint == null) {
            releaseMovement(client);
            return;
        }

        debugStatus(client, "running " + variant.name() + " " + mode.name());
        tickPositioning(client, room);
    }

    @SubscribeEvent
    public void onRenderCamera(Render3DEvent.Start event) {
        Minecraft client = Minecraft.getInstance();
        if (!getSolver().getValue() || client.player == null || (!isInBlazeRoom() && !isSequenceActive()) || aimTarget == null) {
            return;
        }

        applyLook(client);
    }

    @SubscribeEvent
    public void onRender(Render3DEvent.Extract event) {
        if (!getSolver().getValue() || !isInBlazeRoom()) {
            return;
        }

        renderStandPoint();
        renderTargets();
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        releaseInputs(Minecraft.getInstance());
        resetState();
    }

    @Override
    public void reset() {
        releaseInputs(Minecraft.getInstance());
        resetState();
    }

    private void tickPositioning(Minecraft client, Room room) {
        if (mode == Mode.IDLE) {
            if (horizontalDistanceSqr(client.player.position(), standPoint) <= ARRIVE_DISTANCE_SQ) {
                beginEdgeLook(client, room);
            }
        }

        long now = System.currentTimeMillis();
        switch (mode) {
            case EDGE_LOOK -> {
                if (getLookProgress() < 1.0) {
                    setForward(client, false, false);
                    setSneak(client, false, false);
                    return;
                }

                edgeWalkUntil = 0L;
                aimTarget = null;
                mode = Mode.EDGE_WALK;
            }
            case EDGE_WALK -> {
                setSneak(client, true, true);
                setForward(client, true, true);
                if (edgeWalkUntil == 0L) {
                    edgeWalkUntil = now + getLong(edgeWalkTime);
                    return;
                }

                if (now >= edgeWalkUntil) {
                    setForward(client, false, true);
                    setSneak(client, true, true);
                    edgeStopWaitStartedAt = now;
                    mode = Mode.EDGE_STOP_WAIT;
                    return;
                }

            }
            case EDGE_STOP_WAIT -> {
                setForward(client, false, true);
                setSneak(client, true, true);
                if (hasStoppedWalking(client, now)) {
                    edgeCrouchUntil = now + EDGE_CROUCH_AFTER_STOP_MS;
                    mode = Mode.EDGE_CROUCH_SETTLE;
                    return;
                }
            }
            case EDGE_CROUCH_SETTLE -> {
                setForward(client, false, true);
                setSneak(client, true, true);
                if (now >= edgeCrouchUntil) {
                    setForward(client, false, true);
                    setSneak(client, false, true);
                    edgeUnshiftUntil = now + EDGE_UNSHIFT_SETTLE_MS;
                    mode = Mode.EDGE_UNSHIFT_SETTLE;
                    return;
                }
            }
            case EDGE_UNSHIFT_SETTLE -> {
                setForward(client, false, true);
                setSneak(client, false, true);
                if (now >= edgeUnshiftUntil) {
                    releaseMovement(client);
                    mode = Mode.READY;
                }
            }
            case READY -> {
                releaseMovement(client);
                beginShotSequence(client, room);
            }
            case SHOOT_LOOK -> tickShootLook(client);
            case NEXT_TARGET_DELAY -> tickNextTargetDelay(client, room);
            case SHOOT_SECOND_DELAY -> tickShootSecond(client);
            case DONE -> {
            }
            case IDLE -> {
            }
        }
    }

    private void beginEdgeLook(Minecraft client, Room room) {
        activeRoom = room;
        setForward(client, false, false);
        setSneak(client, false, false);
        mode = Mode.EDGE_LOOK;

        Pos local = getStandLocal(variant);
        double dx = ROOM_MIDDLE.x - local.x;
        double dz = ROOM_MIDDLE.z - local.z;
        Pos lookLocal = Math.abs(dx) >= Math.abs(dz)
                ? new Pos(local.x + Math.signum(dx) * 4.0, local.y, local.z)
                : new Pos(local.x, local.y, local.z + Math.signum(dz) * 4.0);
        Vec3 lookPoint = roomRelativeVec(room, lookLocal);
        aimTarget = new Vec3(lookPoint.x, client.player.getEyePosition().y, lookPoint.z);
        startLook(client);
    }

    private void beginShotSequence(Minecraft client, Room room) {
        int slot = getBowSlot(client);
        if (slot == -1) {
            status("Blaze: no bow found");
            mode = Mode.DONE;
            return;
        }
        int expectedTargets = getAimPoints().length;
        if (blazeSlots.size() < expectedTargets) {
            status("Blaze: waiting for HP targets " + blazeSlots.size() + "/" + expectedTargets);
            return;
        }

        prepareSlot(client, slot);
        if (!buildShotQueue(room)) {
            status("Blaze: waiting for stable target mapping " + shotQueue.size() + "/" + expectedTargets);
            return;
        }

        shotIndex = 0;
        beginShotLook(client, room);
    }

    private void beginShotLook(Minecraft client, Room room) {
        if (shotIndex >= shotQueue.size()) {
            shotTargetActive = false;
            aimTarget = null;
            mode = Mode.DONE;
            return;
        }

        AimPoint shot = shotQueue.get(shotIndex);
        aimTarget = roomVec(room, shot.pos());
        shotTargetActive = true;
        shotsFiredAtTarget = 0;
        startLook(client);
        nextShootAt = System.currentTimeMillis() + randomShootDelay();
        mode = Mode.SHOOT_LOOK;
    }

    private void tickShootLook(Minecraft client) {
        if (!shotTargetActive || client.gameMode == null) {
            mode = Mode.DONE;
            return;
        }

        applyLook(client);
        long now = System.currentTimeMillis();
        if (getLookProgress() < 1.0 || now < slotReadyAt || now < nextShootAt) {
            return;
        }

        shootBow(client);
        shotsFiredAtTarget++;
        nextShootAt = now + randomShootDelay();
        mode = Mode.SHOOT_SECOND_DELAY;
    }

    private void tickShootSecond(Minecraft client) {
        if (!shotTargetActive || client.gameMode == null) {
            mode = Mode.DONE;
            return;
        }

        applyLook(client);
        long now = System.currentTimeMillis();
        if (now < nextShootAt) {
            return;
        }

        shootBow(client);
        shotsFiredAtTarget++;
        shotIndex++;
        if (shotIndex >= shotQueue.size()) {
            shotTargetActive = false;
            aimTarget = null;
            mode = Mode.DONE;
            return;
        }

        nextTargetAt = now + randomNextTargetDelay();
        mode = Mode.NEXT_TARGET_DELAY;
    }

    private void tickNextTargetDelay(Minecraft client, Room room) {
        if (System.currentTimeMillis() < nextTargetAt) {
            return;
        }

        beginShotLook(client, room);
    }

    private void shootBow(Minecraft client) {
        client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
        client.player.swing(InteractionHand.MAIN_HAND);
    }

    private boolean buildShotQueue(Room room) {
        shotQueue.clear();
        AimPoint[] aimPoints = getAimPoints();
        boolean[] usedSlots = new boolean[aimPoints.length];
        for (BlazeSlot target : blazeSlots) {
            int slot = target.slot();
            if (slot < 0 || slot >= aimPoints.length || usedSlots[slot]) {
                shotQueue.clear();
                return false;
            }
            usedSlots[slot] = true;
            shotQueue.add(aimPoints[slot]);
        }
        return shotQueue.size() == aimPoints.length;
    }

    private AimPoint[] getAimPoints() {
        return variant == Variant.HIGH_TO_LOW ? HIGH_TO_LOW_AIM_POINTS : LOW_TO_HIGH_AIM_POINTS;
    }

    private Vec3 getStandPoint(Room room, Variant variant) {
        return roomRelativeVec(room, getStandLocal(variant));
    }

    private Pos getStandLocal(Variant variant) {
        return variant.stand;
    }

    private void scanBlazes(Minecraft client, Room room) {
        blazeSlots.clear();

        AABB scanBox = blazeScanBox(room);
        List<ArmorStand> stands = client.level.getEntitiesOfClass(ArmorStand.class, scanBox, stand -> stand.getCustomName() != null);
        List<BlazeLabel> labels = new ArrayList<>();
        for (ArmorStand stand : stands) {
            Matcher matcher = BLAZE_HP.matcher(stand.getCustomName().getString());
            if (!matcher.matches()) {
                continue;
            }

            int hp = Integer.parseInt(matcher.group(1).replace(",", ""));
            Pos relative = room.getRelativePosition(new Pos(stand.position()));
            if (relative != null) {
                labels.add(new BlazeLabel(stand, hp, relative.y));
            }
        }

        labels.sort(Comparator.comparingDouble(BlazeLabel::relativeY));
        int count = Math.min(labels.size(), getAimPoints().length);
        for (int i = 0; i < count; i++) {
            BlazeLabel label = labels.get(i);
            blazeSlots.add(new BlazeSlot(i, label.hp(), label.stand()));
        }

        blazeSlots.sort(Comparator.comparingInt(BlazeSlot::hp));
        if (variant == Variant.HIGH_TO_LOW) {
            blazeSlots.sort(Comparator.comparingInt(BlazeSlot::hp).reversed());
        }
    }

    private Variant detectVariant(Minecraft client, Room room) {
        Variant namedVariant = variantFromRoomName(room);
        if (namedVariant != null) {
            return namedVariant;
        }

        ClientLevel level = client.level;
        for (int x = -2; x <= 2; x++) {
            for (int z = -3; z <= 1; z++) {
                BlockPos pos = room.getRealPositionFixed(new Pos(x, 118.0, z)).asBlockPos();
                if (level.getBlockState(pos).is(Blocks.COBBLESTONE)) {
                    return Variant.LOW_TO_HIGH;
                }
            }
        }

        return client.player.getY() > 80.0 ? Variant.LOW_TO_HIGH : Variant.HIGH_TO_LOW;
    }

    private void captureAim() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || !isInBlazeRoom()) {
            return;
        }

        Room room = getMainBlazeRoom();
        if (room != null) {
            scanBlazes(client, room);
        }
        HitResult hit = client.hitResult;
        Vec3 eye = client.player.getEyePosition();
        Vec3 look = client.player.getViewVector(1.0F);
        Vec3 point = hit != null && hit.getType() != HitResult.Type.MISS ? hit.getLocation() : eye.add(look.scale(80.0));
        Pos relative = room == null ? new Pos(point) : room.getRelativePosition(new Pos(point));
        BlazeSlot target = closestSlotToRay(eye, look);
        String slot = target == null ? "unknown" : String.valueOf(target.slot() + 1);
        String text = String.format(
                "Blaze aim: variant=%s, slot=%s, aim={x=%.3f,y=%.3f,z=%.3f}, world={x=%.3f,y=%.3f,z=%.3f}, yaw=%.2f, pitch=%.2f",
                variant == null ? "unknown" : variant.name(),
                slot,
                relative.x, relative.y, relative.z,
                point.x, point.y, point.z,
                client.player.getYRot(), client.player.getXRot()
        );
        client.keyboardHandler.setClipboard(text);
        ChatUtils.chat(text);
    }

    private BlazeSlot closestSlotToRay(Vec3 eye, Vec3 look) {
        BlazeSlot best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 normalized = look.normalize();
        for (BlazeSlot target : blazeSlots) {
            Vec3 center = target.stand().position();
            Vec3 eyeToTarget = center.subtract(eye);
            double alongRay = eyeToTarget.dot(normalized);
            if (alongRay < 0.0) {
                continue;
            }

            double distanceToRay = eyeToTarget.subtract(normalized.scale(alongRay)).lengthSqr();
            double score = distanceToRay + alongRay * 0.002;
            if (score < bestScore) {
                bestScore = score;
                best = target;
            }
        }
        return best != null && bestScore < 16.0 ? best : null;
    }

    private void tickCaptureKey() {
        Keybind keybind = captureAimKey.getValue();
        if (keybind == null || !keybind.isActive()) {
            captureHeld = false;
            return;
        }

        if (!captureHeld) {
            captureAim();
            captureHeld = true;
        }
    }

    private void tickStartKey() {
        Keybind keybind = startKey.getValue();
        if (keybind == null || !keybind.isActive()) {
            startHeld = false;
            return;
        }

        if (!startHeld) {
            requestManualStart();
            startHeld = true;
        }
    }

    private void requestManualStart() {
        manualStartRequested = true;
        status("Blaze: manual start requested");
    }

    private void renderStandPoint() {
        if (standPoint == null) {
            return;
        }

        AABB box = new AABB(
                standPoint.x - 0.5,
                standPoint.y,
                standPoint.z - 0.5,
                standPoint.x + 0.5,
                standPoint.y + 0.08,
                standPoint.z + 0.5
        );
        Renderer3D.addTask(new FilledOutlineBox(box, new Colour(80, 220, 255, 65), new Colour(80, 220, 255, 200), false));
    }

    private void renderTargets() {
        for (int i = 0; i < Math.min(3, blazeSlots.size()); i++) {
            BlazeSlot target = blazeSlots.get(i);
            Colour fill = switch (i) {
                case 0 -> new Colour(0, 255, 90, 70);
                case 1 -> new Colour(255, 170, 0, 60);
                default -> new Colour(255, 70, 70, 55);
            };
            Colour outline = switch (i) {
                case 0 -> new Colour(0, 255, 90, 220);
                case 1 -> new Colour(255, 170, 0, 210);
                default -> new Colour(255, 70, 70, 200);
            };
            Renderer3D.addTask(new FilledOutlineBox(target.stand().getBoundingBox(), fill, outline, false));
        }
    }

    private void startLook(Minecraft client) {
        lookStartedAt = System.currentTimeMillis();
        lookStartYaw = client.player.getYRot();
        lookStartPitch = client.player.getXRot();
    }

    private void applyLook(Minecraft client) {
        if (aimTarget == null || client.player == null) {
            return;
        }

        Rotation wanted = Rotation.from(aimTarget);
        double amount = easeInOut(getLookProgress());
        float nextYaw = lookStartYaw + RotationUtils.wrapAngleTo180(wanted.getYaw() - lookStartYaw) * (float) amount;
        float nextPitch = lookStartPitch + RotationUtils.wrapAngleTo180(wanted.getPitch() - lookStartPitch) * (float) amount;
        client.player.setYRot(nextYaw);
        client.player.setXRot(Mth.clamp(nextPitch, -90.0F, 90.0F));
        client.player.yHeadRot = nextYaw;
    }

    private double getLookProgress() {
        long duration = getLong(lookTime);
        return duration <= 0L
                ? 1.0
                : Mth.clamp((double) (System.currentTimeMillis() - lookStartedAt) / duration, 0.0, 1.0);
    }

    private boolean isInBlazeRoom() {
        if (!isDungeonClear()) {
            return false;
        }

        return getCurrentBlazeTile() != null;
    }

    private Room getCurrentBlazeTile() {
        Minecraft client = Minecraft.getInstance();
        Room room = Map.getCurrentRoom();
        if (isBlazeLikeRoom(room) && isPlayerInsideRoom(room)) {
            return room;
        }

        if (client.player == null) {
            return null;
        }

        room = ScanUtils.getRoomFromPos((int) client.player.position().x(), (int) client.player.position().z());
        if (isBlazeLikeRoom(room) && isPlayerInsideRoom(room)) {
            return room;
        }

        return null;
    }

    private Room getMainBlazeRoom() {
        Room room = getCurrentBlazeTile();
        if (room == null) {
            return null;
        }
        if (room.getUniqueRoom() != null && room.getUniqueRoom().getMainRoom() != null) {
            return room.getUniqueRoom().getMainRoom();
        }
        return room;
    }

    private Room getMainCurrentRoom() {
        Minecraft client = Minecraft.getInstance();
        Room room = Map.getCurrentRoom();
        if (room == null && client.player != null) {
            room = ScanUtils.getRoomFromPos((int) client.player.position().x(), (int) client.player.position().z());
        }
        if (room == null) {
            return null;
        }
        if (room.getUniqueRoom() != null && room.getUniqueRoom().getMainRoom() != null) {
            return room.getUniqueRoom().getMainRoom();
        }
        return room;
    }

    private boolean isBlazeRoom(Room room) {
        if (room == null) {
            return false;
        }
        String name = normalizeRoomName(getRoomName(room));
        return "blaze".equals(name)
                || name.contains("lower")
                || name.contains("higher");
    }

    private Variant variantFromRoomName(Room room) {
        String name = normalizeRoomName(getRoomName(room));
        if (name.contains("lower")) {
            return Variant.HIGH_TO_LOW;
        }
        if (name.contains("higher")) {
            return Variant.LOW_TO_HIGH;
        }
        return null;
    }

    private String normalizeRoomName(String name) {
        return name == null ? "" : name.toLowerCase().replaceAll("[^a-z]", "");
    }

    private String getRoomName(Room room) {
        if (room == null) {
            return null;
        }
        if (room.getData() != null && room.getData().name() != null) {
            return room.getData().name();
        }
        return room.getUniqueRoom() == null ? null : room.getUniqueRoom().getName();
    }

    private boolean isBlazeLikeRoom(Room room) {
        return room != null && (isBlazeRoom(room) || hasBlazeHpLabels(room));
    }

    private boolean isPlayerInsideRoom(Room room) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return false;
        }

        Room main = room.getUniqueRoom() != null && room.getUniqueRoom().getMainRoom() != null
                ? room.getUniqueRoom().getMainRoom()
                : room;
        Pos relative = main.getRelativePositionFixed(new Pos(client.player.position()));
        return Math.abs(relative.x) <= INSIDE_ROOM_LIMIT && Math.abs(relative.z) <= INSIDE_ROOM_LIMIT;
    }

    private boolean isSequenceActive() {
        return mode != Mode.IDLE && mode != Mode.DONE && activeRoom != null;
    }

    private Room findNearbyBlazeRoom(Minecraft client) {
        com.ricedotwho.rsm.data.Pair<Integer, Integer> center = ScanUtils.getRoomCenter((int) client.player.position().x(), (int) client.player.position().z());
        if (center == null) {
            return null;
        }

        for (int dx = -ROOM_SEARCH_RADIUS_TILES; dx <= ROOM_SEARCH_RADIUS_TILES; dx++) {
            for (int dz = -ROOM_SEARCH_RADIUS_TILES; dz <= ROOM_SEARCH_RADIUS_TILES; dz++) {
                Room room = ScanUtils.getRoomFromPos(center.getFirst() + dx * 32, center.getSecond() + dz * 32);
                if (isBlazeLikeRoom(room)) {
                    return room;
                }
            }
        }
        return null;
    }

    private boolean isDungeonClear() {
        return Location.getArea().is(Island.Dungeon) && !Dungeon.isInBoss();
    }

    private boolean hasBlazeHpLabels(Room room) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return false;
        }

        AABB scanBox = blazeScanBox(room);
        List<ArmorStand> stands = client.level.getEntitiesOfClass(ArmorStand.class, scanBox, stand -> stand.getCustomName() != null);
        for (ArmorStand stand : stands) {
            if (BLAZE_HP.matcher(stand.getCustomName().getString()).matches()) {
                return true;
            }
        }
        return false;
    }

    private Vec3 roomVec(Room room, Pos local) {
        return room.getRealPositionFixed(local).asVec3();
    }

    private AABB blazeScanBox(Room room) {
        return new AABB(roomVec(room, BLAZE_SCAN_MIN), roomVec(room, BLAZE_SCAN_MAX)).inflate(2.0);
    }

    private Vec3 roomRelativeVec(Room room, Pos local) {
        return room.getRealPosition(local).asVec3();
    }

    private double horizontalDistanceSqr(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    private void setForward(Minecraft client, boolean down, boolean owned) {
        if (down || owned || forwardOwned) {
            client.options.keyUp.setDown(down);
        }
        forwardOwned = owned;
    }

    private void setSneak(Minecraft client, boolean down, boolean owned) {
        if (down || owned || sneakOwned) {
            client.options.keyShift.setDown(down);
            if (client.player != null) {
                client.player.setShiftKeyDown(down);
            }
        }
        sneakOwned = owned;
    }

    private void releaseMovement(Minecraft client) {
        if (client == null) {
            return;
        }
        setForward(client, false, false);
        setSneak(client, false, false);
    }

    private void releaseInputs(Minecraft client) {
        releaseMovement(client);
    }

    private void prepareSlot(Minecraft client, int slot) {
        if (client.player.getInventory().getSelectedSlot() != slot) {
            client.player.getInventory().setSelectedSlot(slot);
            slotReadyAt = System.currentTimeMillis() + SLOT_SETTLE_MS;
            return;
        }

        slotReadyAt = 0L;
    }

    private void prepareBowSlot(Minecraft client) {
        int slot = getBowSlot(client);
        if (slot != -1) {
            prepareSlot(client, slot);
        }
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

    private void resetState() {
        mode = Mode.IDLE;
        variant = null;
        activeRoom = null;
        standPoint = null;
        aimTarget = null;
        lookStartedAt = 0L;
        edgeWalkUntil = 0L;
        edgeCrouchUntil = 0L;
        edgeUnshiftUntil = 0L;
        edgeStopWaitStartedAt = 0L;
        nextShootAt = 0L;
        nextTargetAt = 0L;
        slotReadyAt = 0L;
        lookStartYaw = 0.0F;
        lookStartPitch = 0.0F;
        shotIndex = 0;
        shotsFiredAtTarget = 0;
        shotTargetActive = false;
        shotQueue.clear();
        captureHeld = false;
        startHeld = false;
        manualStartRequested = false;
        forwardOwned = false;
        sneakOwned = false;
        blazeSlots.clear();
    }

    private void status(String message) {
        long now = System.currentTimeMillis();
        if (now - lastStatusAt < 1500L) {
            return;
        }

        lastStatusAt = now;
        ChatUtils.chat(message);
    }

    private void debugStatus(Minecraft client, String state) {
        if (!debug.getValue()) {
            return;
        }

        Room mapRoom = Map.getCurrentRoom();
        Room posRoom = client.player == null ? null : ScanUtils.getRoomFromPos((int) client.player.position().x(), (int) client.player.position().z());
        Room nearby = client.player == null ? null : findNearbyBlazeRoom(client);
        status("Blaze debug: " + state
                + " area=" + Location.getArea()
                + " boss=" + Dungeon.isInBoss()
                + " map=" + roomDebugName(mapRoom)
                + " pos=" + roomDebugName(posRoom)
                + " nearby=" + roomDebugName(nearby)
                + " y=" + (client.player == null ? "?" : String.format("%.1f", client.player.getY())));
    }

    private String roomDebugName(Room room) {
        if (room == null) {
            return "null";
        }
        String dataName = room.getData() == null ? "null" : room.getData().name();
        String uniqueName = room.getUniqueRoom() == null ? "null" : room.getUniqueRoom().getName();
        return dataName + "/" + uniqueName;
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

    private boolean hasStoppedWalking(Minecraft client, long now) {
        if (client.player == null) {
            return true;
        }

        Vec3 velocity = client.player.getDeltaMovement();
        double horizontalSpeedSq = velocity.x * velocity.x + velocity.z * velocity.z;
        return horizontalSpeedSq <= EDGE_STOP_SPEED_SQ
                || now - edgeStopWaitStartedAt >= EDGE_STOP_MAX_WAIT_MS;
    }

    private long randomShootDelay() {
        return ThreadLocalRandom.current().nextLong(80L, 101L);
    }

    private long randomNextTargetDelay() {
        return ThreadLocalRandom.current().nextLong(80L, 101L);
    }

    private enum Mode {
        IDLE,
        EDGE_LOOK,
        EDGE_WALK,
        EDGE_STOP_WAIT,
        EDGE_CROUCH_SETTLE,
        EDGE_UNSHIFT_SETTLE,
        READY,
        SHOOT_LOOK,
        SHOOT_SECOND_DELAY,
        NEXT_TARGET_DELAY,
        DONE
    }

    private enum Variant {
        HIGH_TO_LOW(HIGH_TO_LOW_STAND),
        LOW_TO_HIGH(LOW_TO_HIGH_STAND);

        final Pos stand;

        Variant(Pos stand) {
            this.stand = stand;
        }
    }

    private record BlazeLabel(ArmorStand stand, int hp, double relativeY) {
    }

    private record BlazeSlot(int slot, int hp, ArmorStand stand) {
    }

    private record AimPoint(double x, double y, double z) {
        Pos pos() {
            return new Pos(x, y, z);
        }
    }
}
