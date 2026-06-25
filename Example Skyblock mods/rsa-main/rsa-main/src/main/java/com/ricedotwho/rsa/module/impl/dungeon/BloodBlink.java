package com.ricedotwho.rsa.module.impl.dungeon;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsa.component.impl.managers.PacketOrderManager;
import com.ricedotwho.rsa.component.impl.managers.SwapManager;
import com.ricedotwho.rsa.module.impl.other.AutoGfs;
import com.ricedotwho.rsa.packet.sb.BloodClipHelperStartPacket;
import com.ricedotwho.rsa.utils.Util;
import com.ricedotwho.rsm.RSM;
import com.ricedotwho.rsm.component.impl.location.Island;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.component.impl.map.handler.Dungeon;
import com.ricedotwho.rsm.component.impl.map.handler.DungeonInfo;
import com.ricedotwho.rsm.component.impl.map.map.Room;
import com.ricedotwho.rsm.component.impl.map.map.RoomRotation;
import com.ricedotwho.rsm.component.impl.map.map.RoomType;
import com.ricedotwho.rsm.component.impl.map.map.UniqueRoom;
import com.ricedotwho.rsm.component.impl.map.utils.RoomUtils;
import com.ricedotwho.rsm.component.impl.map.utils.ScanUtils;
import com.ricedotwho.rsm.component.impl.task.TaskComponent;
import com.ricedotwho.rsm.data.Keybind;
import com.ricedotwho.rsm.data.Pos;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.client.InputPollEvent;
import com.ricedotwho.rsm.event.impl.client.PacketEvent.Receive;
import com.ricedotwho.rsm.event.impl.game.ServerTickEvent;
import com.ricedotwho.rsm.event.impl.game.ChatEvent.Chat;
import com.ricedotwho.rsm.event.impl.game.ClientTickEvent.Start;
import com.ricedotwho.rsm.event.impl.game.DungeonEvent.RoomLoad;
import com.ricedotwho.rsm.event.impl.world.WorldEvent.Load;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.module.impl.render.ClickGUI;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.KeybindSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.ModeSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.NumberSetting;
import com.ricedotwho.rsm.utils.EtherUtils;
import com.ricedotwho.rsm.utils.ItemUtils;
import java.awt.Point;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.util.PlayerInput;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.network.ClientPlayerEntity;

@ModuleInfo(aliases = "Blood Blink", id = "BloodBlink", category = Category.DUNGEONS)
public class BloodBlink extends Module {
   private static final Pos SLAB_BLOCK_OFFSET_1 = new Pos(-9.5, 82.0, -12.5);
   private static final Pos SLAB_BLOCK_OFFSET_2 = new Pos(-12.5, 83.0, -9.5);
   private static final Pos SLAB_BLOCK_OFFSET_3 = new Pos(-5.5, 82.0, -12.5);
   private static final Pos SLAB_BLOCK_OFFSET_4 = new Pos(6.5, 82.0, -12.5);
   private static final Pos SLAB_BLOCK_OFFSET_5 = new Pos(10.5, 82.0, -12.5);
   private static final Vec3d MIDDLE_MAP_COORDS = new Vec3d(-104.5, 0.0, -104.5);
   private Room targetRoom;
   private Room startRoom;
   private int serverTickTimer = -1;
   private int serverTotalTickTimer = 0;
   private int state = 0;
   private boolean isLower = false;
   private int ticksTilStart = -67;
   public boolean forceNextSneak = false;
   private boolean explored = false;
   private final List<BloodBlink.Entry> roomPriority = new ArrayList<>(5);
   private static List<String> rooms = List.of();
   private final BooleanSetting waitForGround = new BooleanSetting("Wait For Ground", false);
   private final BooleanSetting proxyPearl = new BooleanSetting("Proxy Pearl", false);
   private final BooleanSetting auto = new BooleanSetting("Auto Blink", true);
   private final BooleanSetting africanSlavePingMode = new BooleanSetting("African Slave Ping Mode", false);
   private final NumberSetting deathTickOffset = new NumberSetting("Death Tick Offset", 0.0, 20.0, 0.0, 1.0);
   private final NumberSetting earlyExit = new NumberSetting("Early Exit", 0.0, 20.0, 0.0, 1.0);
   private final NumberSetting exploreExit = new NumberSetting("Explore Exit", 10.0, 40.0, 25.0, 1.0);
   private final NumberSetting bloodLoadTickTime = new NumberSetting("Map Load Tick Time", 5.0, 35.0, 10.0, 1.0);
   private final KeybindSetting cancel = new KeybindSetting("Cancel", new Keybind(-1, false, this::cancel));
   private final ModeSetting mode = new ModeSetting("Mode", "Blood", List.of("Blood", "InstaClear"));
   private final NumberSetting priority = new NumberSetting("Priority", 1.0, 4.0, 1.0, 1.0);

   public BloodBlink() {
      this.registerProperty(
         new Setting[]{
            this.waitForGround,
            this.proxyPearl,
            this.auto,
            this.africanSlavePingMode,
            this.bloodLoadTickTime,
            this.deathTickOffset,
            this.earlyExit,
            this.exploreExit,
            this.mode,
            this.priority,
            this.cancel
         }
      );
      rooms = (List<String>)new Gson()
         .fromJson(
            new InputStreamReader(Objects.requireNonNull(ClickGUI.class.getResourceAsStream("/assets/rsm/room_priority.json"))),
            (new TypeToken<List<String>>() {}).getType()
         );
   }

   public void onEnable() {
      this.resetState();
   }

   public void onDisable() {
      this.resetState();
   }

   @SubscribeEvent
   public void WorldEventLoad(Load event) {
      this.targetRoom = null;
      this.startRoom = null;
      this.serverTickTimer = -1;
      this.serverTotalTickTimer = 0;
      this.ticksTilStart = -67;
      this.resetState();
      this.roomPriority.clear();
      this.state = -1;
   }

   public void resetState() {
      this.state = -1;
      this.isLower = false;
      this.forceNextSneak = false;
      this.explored = false;
   }

   private Pos getSlabBlockOffset() {
      return switch (((BigDecimal)this.priority.getValue()).intValue()) {
         case 1 -> SLAB_BLOCK_OFFSET_1;
         case 2 -> SLAB_BLOCK_OFFSET_3;
         case 3 -> SLAB_BLOCK_OFFSET_4;
         default -> SLAB_BLOCK_OFFSET_5;
      };
   }

   public long encodeIndex(int x, int z) {
      return x | (long)z << 32;
   }

   public long encodeIndex(Point p) {
      return this.encodeIndex(p.x, p.y);
   }

   @SubscribeEvent
   public void onTickStart(Start event) {
      if (Location.getArea() == Island.Dungeon && mc.player != null && !Dungeon.isInBoss()) {
         ClientPlayerEntity player = mc.player;
         if (this.serverTotalTickTimer > 2) {
            switch (this.state) {
               case -1:
                  if (!(Boolean)this.auto.getValue()) {
                     break;
                  }

                  KeyBinding.unpressAll();
                  this.state = 0;
                  // Intentionally fall through to run the initial setup immediately.
               case 0:
                  if (this.targetRoom == null && Dungeon.isStarted()) {
                     RSA.chat("Cannot blood blink while run has started and blood has not been loaded!");
                     this.state = 31;
                  } else if (mc.world != null) {
                     this.forceNextSneak = true;
                     if (!(Boolean)this.waitForGround.getValue() || player.isOnGround()) {
                        this.tryRegisterStartRoomSlabUse(player, 2);
                     }
                  }
               default:
                  break;
               case 4:
                  this.pearl(player.getYaw(), -90.0F, () -> this.state = 5);
                  break;
               case 6:
                  if (this.targetRoom != null) {
                     this.state = 17;
                  } else if (this.serverTickTimer % 40 < ((BigDecimal)this.exploreExit.getValue()).intValue()) {
                     PacketOrderManager.register(PacketOrderManager.STATE.ITEM_USE, () -> {
                        if (SwapManager.swapItem(Items.DIAMOND_SHOVEL)) {
                           float playerYaw = player.getYaw();
                           float[] angles = EtherUtils.getYawAndPitch(MIDDLE_MAP_COORDS.add(0.0, player.getY(), 0.0), false, player, false);
                           float deltaX = (float)(player.getX() - MIDDLE_MAP_COORDS.x);
                           float deltaZ = (float)(player.getZ() - MIDDLE_MAP_COORDS.z);
                           this.aotv0(8, playerYaw, -90.0F);
                           this.aotv0(Math.round(MathHelper.sqrt(deltaX * deltaX + deltaZ * deltaZ) / 12.0F), angles[0], 0.0F);
                           this.explored = true;
                           this.state = 10;
                        }
                     });
                  }
                  break;
               case 11:
                  if (mc.world != null) {
                     this.forceNextSneak = true;
                     if (!(Boolean)this.waitForGround.getValue() || player.isOnGround()) {
                        if (this.ensureValidStartRoom(player)) {
                           if (this.explored) {
                              if (this.targetRoom == null) {
                                 this.findTargetRoom();
                              }

                              if (this.targetRoom == null) {
                                 RSA.chat("Could not find target room!");
                                 this.state = 31;
                                 return;
                              }
                           }

                           this.tryRegisterStartRoomSlabUse(player, 13);
                        }
                     }
                  }
                  break;
               case 15:
                  this.pearl(player.getYaw(), -90.0F, () -> this.state = 16);
                  break;
               case 17:
                  SwapManager.swapItem(Items.DIAMOND_SHOVEL);
                  if (((Boolean)this.africanSlavePingMode.getValue() && this.ticksTilStart != -67 && this.ticksTilStart <= 0 || Dungeon.isStarted())
                     && this.serverTickTimer % 40 < 40 - ((BigDecimal)this.bloodLoadTickTime.getValue()).intValue()) {
                     this.ticksTilStart = -67;
                     PacketOrderManager.register(
                        PacketOrderManager.STATE.ITEM_USE,
                        () -> {
                           if (SwapManager.swapItem(Items.DIAMOND_SHOVEL)) {
                              float playerYaw = player.getYaw();
                              Direction dir = this.getVoidRotation();
                              this.aotv0(4, dir.getPositiveHorizontalDegrees(), 0.0F);
                              this.aotv0(10, playerYaw, 90.0F);
                              Vec3d playerPos = player.getEntityPos().add(fastRotateVec(dir, 0.0, 0.0, -48.0));
                              float deltaX = (float)(this.targetRoom.getX() + 0.5 - playerPos.getX());
                              float deltaZ = (float)(this.targetRoom.getZ() + 0.5 - playerPos.getZ());
                              float[] angles = EtherUtils.getYawAndPitch(deltaX, 0.0, deltaZ);
                              this.aotv0(Math.round(MathHelper.sqrt(deltaX * deltaX + deltaZ * deltaZ) / 12.0F), angles[0], 3.0F);
                              this.aotv0(5, playerYaw, -90.0F);
                              this.state = 29;
                              if (SwapManager.swapItem((Predicate<ItemStack>)(this::isValidEnderPearlStack))) {
                                 if (!SwapManager.sendAirC08(player.getYaw(), -90.0F, true, true)) {
                                    RSA.chat("Pearl failed!");
                                 } else {
                                    this.state = 30;
                                 }
                              }
                           }
                        }
                     );
                  }
                  break;
               case 29:
                  this.pearl(player.getYaw(), -90.0F, () -> this.state = 30);
            }
         }
      }
   }

   private boolean ensureValidStartRoom(ClientPlayerEntity player) {
      if (this.startRoom == null) {
         this.startRoom = ScanUtils.getRoomFromPos(player.getBlockX(), player.getBlockZ());
      }

      return this.startRoom != null
         && this.startRoom.getUniqueRoom() != null
         && this.startRoom.getUniqueRoom().getRotation() != RoomRotation.UNKNOWN;
   }

   private void tryRegisterStartRoomSlabUse(ClientPlayerEntity player, int nextState) {
      if (!this.ensureValidStartRoom(player) || mc.world == null) {
         return;
      }

      PacketOrderManager.register(PacketOrderManager.STATE.ITEM_USE, () -> {
         if (SwapManager.swapItem(Items.DIAMOND_SHOVEL) && player.getLastPlayerInput().sneak() && this.startRoom != null) {
            Pos slab = RoomUtils.getRealPosition(this.getSlabBlockOffset(), this.startRoom);
            Block block = mc.world.getBlockState(slab.asBlockPos()).getBlock();
            if (block == Blocks.AIR) {
               this.isLower = true;
               slab.selfAdd(0.0, -1.0, 0.0);
            }

            float[] angles = EtherUtils.getYawAndPitch(slab.asVec3(), true, player, true);
            SwapManager.sendAirC08(angles[0], angles[1], true, false);
            this.state = nextState;
         }
      });
   }

   private Direction getVoidRotation() {
      int xIndex = (this.startRoom.getX() + 185) / 32;
      int zIndex = (this.startRoom.getZ() + 185) / 32;
      Direction rotation;
      if (xIndex == 0) {
         rotation = Direction.WEST;
      } else if (zIndex == 0) {
         rotation = Direction.NORTH;
      } else if (xIndex > zIndex) {
         rotation = Direction.EAST;
      } else {
         rotation = Direction.SOUTH;
      }

      return rotation;
   }

   private boolean isNormalEnderpearlID(String s) {
      return s.equals("ENDER_PEARL");
   }

   private boolean isValidEnderPearlStack(ItemStack stack) {
      return stack.getItem() == Items.ENDER_PEARL && this.isNormalEnderpearlID(ItemUtils.getID(stack));
   }

   private static Vec3d fastRotateVec(Direction direction, double x, double y, double z) {
      return switch (direction) {
         case NORTH -> new Vec3d(x, y, z);
         case EAST -> new Vec3d(-z, y, x);
         case SOUTH -> new Vec3d(-x, y, -z);
         case WEST -> new Vec3d(z, y, -x);
         default -> Vec3d.ZERO;
      };
   }

   private void aotv0(int count, float yaw, float pitch) {
      for (int i = 0; i < count; i++) {
         SwapManager.sendAirC08(yaw, pitch, true, false);
      }
   }

   public boolean isBlinking() {
      return this.state < 31 && this.state > -1;
   }

   public void doBlink() {
      this.resetState();
      this.state = 0;
      KeyBinding.unpressAll();
   }

   @SubscribeEvent
   public void onPollInputs(InputPollEvent event) {
      if (this.isEnabled() && this.isBlinking() && !Dungeon.isInBoss()) {
         PlayerInput input = event.getClientInput();
         if (input.forward() && input.backward() && input.left() && input.right()) {
            this.cancel();
         } else {
            PlayerInput newInputs = new PlayerInput(false, false, false, false, false, this.forceNextSneak, false);
            this.forceNextSneak = false;
            event.getInput().apply(newInputs);
         }
      }
   }

   private void cancel() {
      this.reset();
      this.state = 31;
      RSA.chat("Cancelling blood blink!");
   }

   private void pearl(float yaw, float pitch, Runnable succeed) {
      PacketOrderManager.register(PacketOrderManager.STATE.ITEM_USE, () -> {
         if (SwapManager.swapItem((Predicate<ItemStack>)(this::isValidEnderPearlStack))) {
            if (SwapManager.sendAirC08(yaw, pitch, true, false)) {
               if (succeed != null) {
                  succeed.run();
               }
            }
         }
      });
   }

   @SubscribeEvent
   public void onLoadRoom(RoomLoad event) {
      if (this.mode.is("Blood") && event.getRoom().getData().type() == RoomType.BLOOD) {
         this.targetRoom = event.getRoom();
         RSA.chat("Found blood at: " + this.targetRoom.getX() + ", " + this.targetRoom.getZ());
      }
   }

   @SubscribeEvent
   public void onChat(Chat event) {
      if (Location.getArea() == Island.Dungeon && MinecraftClient.getInstance().player != null) {
         if (event.getMessage().getString().equals("Starting in 1 second.")) {
            this.ticksTilStart = Math.max(20 - ((BigDecimal)this.earlyExit.getValue()).intValue(), 0);
            AutoGfs.tryGetItem(16, "ENDER_PEARL", true);
         }
      }
   }

   @SubscribeEvent
   public void onReceivePacket(Receive event) {
      if (this.isBlinking() && !Dungeon.isInBoss()) {
         Packet<?> packet = event.getPacket();
         if (packet instanceof WorldTimeUpdateS2CPacket timePacket) {
            long time = timePacket.time();
            this.serverTickTimer = (int)(time + ((BigDecimal)this.deathTickOffset.getValue()).intValue()) % 40;
         }

         if (packet instanceof PlayerPositionLookS2CPacket positionLookPacket) {
            boolean isProxyPearlActive = (Boolean)this.proxyPearl.getValue() && Util.isZero();
            switch (this.state) {
               case 2:
                  if (isProxyPearlActive) {
                     this.sendStartPearling(this.isLower ? 98 : 99);
                     this.state = 5;
                  } else {
                     this.state = 4;
                  }
                  break;
               case 5:
                  if (positionLookPacket.change().position().y <= (this.isLower ? 97.0 : 98.0)) {
                     if (isProxyPearlActive) {
                        return;
                     }

                     this.state = 4;
                  } else {
                     this.state = 6;
                  }
                  break;
               case 10:
                  double y = positionLookPacket.change().position().y;
                  if (y == 76.5 || y == 75.5) {
                     this.state = 11;
                  }
                  break;
               case 13:
                  if (isProxyPearlActive) {
                     this.sendStartPearling(this.isLower ? 98 : 99);
                     this.state = 16;
                  } else {
                     this.state = 15;
                  }
                  break;
               case 16:
                  if (positionLookPacket.change().position().y <= (this.isLower ? 97.0 : 98.0)) {
                     if (isProxyPearlActive) {
                        return;
                     }

                     this.state = 15;
                  } else {
                     this.state = 17;
                  }
                  break;
               case 30:
                  Vec3d pos = positionLookPacket.change().position();
                  boolean isWithinRoomYRange = pos.y >= this.targetRoom.getBottom() - 1 && pos.y <= this.targetRoom.getBottom() + 7;
                  if (this.isInRoom(MathHelper.floor(pos.getX()), MathHelper.floor(pos.getZ()), this.targetRoom) && isWithinRoomYRange) {
                     if (pos.y <= this.targetRoom.getBottom() + 1) {
                        this.state = 29;
                     } else {
                        this.state = 31;
                     }
                  }
            }
         }
      }
   }

   @SubscribeEvent
   public void onServerTick(ServerTickEvent event) {
      this.serverTickTimer++;
      this.serverTotalTickTimer++;
      if (this.ticksTilStart != -67) {
         this.ticksTilStart--;
      }
   }

   private boolean isInRoom(int posX, int posZ, Room room) {
      return MathHelper.abs(room.getX() - posX) < 16 && MathHelper.abs(room.getZ() - posZ) < 16;
   }

   private void findTargetRoom() {
      if (this.mode.is("InstaClear")) {
         DungeonInfo.getUniqueRooms().forEach(room -> {
            if (!room.isOnBloodRush() && rooms.contains(room.getName())) {
               this.addRoom(new BloodBlink.Entry(room, rooms.indexOf(room.getName())));
            }
         });
         if (this.roomPriority.size() < ((BigDecimal)this.priority.getValue()).intValue()) {
            RSA.chat("No room found with priority %s to InstaClear!", this.priority.getValue());
         } else {
            this.targetRoom = this.roomPriority.get(((BigDecimal)this.priority.getValue()).intValue() - 1).room().getMainRoom();
            if (this.targetRoom != null) {
               RSA.chat("Found a room to insta: \"%s\"", this.targetRoom.getData().name());
               if ((Boolean)((ClickGUI)RSM.getModule(ClickGUI.class)).getDevInfo().getValue()) {
                  RSA.chat("InstaClear candidates: %s", this.roomPriority.stream().map(r -> r.room().getName()).toList());
               }
            }
         }
      }
   }

   private void addRoom(BloodBlink.Entry e) {
      if (!e.room().isOnBloodRush() && !this.roomPriority.stream().anyMatch(existingEntry -> existingEntry.room().getName().equals(e.room().getName()))) {
         int i = 0;

         while (i < this.roomPriority.size() && this.roomPriority.get(i).priority() <= e.priority()) {
            i++;
         }

         if (i < 5) {
            this.roomPriority.add(i, e);
            if (this.roomPriority.size() > 5) {
               this.roomPriority.remove(5);
            }
         }
      }
   }

   private void sendStartPearling(int roof) {
      if (mc.getNetworkHandler() != null) {
         if (SwapManager.swapItem("ENDER_PEARL")) {
            TaskComponent.onTick(0L, () -> mc.getNetworkHandler().sendPacket(new CustomPayloadC2SPacket(new BloodClipHelperStartPacket(roof))));
         }
      }
   }

   public Room getTargetRoom() {
      return this.targetRoom;
   }

   public Room getStartRoom() {
      return this.startRoom;
   }

   public int getServerTickTimer() {
      return this.serverTickTimer;
   }

   public int getServerTotalTickTimer() {
      return this.serverTotalTickTimer;
   }

   public int getState() {
      return this.state;
   }

   public boolean isLower() {
      return this.isLower;
   }

   public int getTicksTilStart() {
      return this.ticksTilStart;
   }

   public boolean isForceNextSneak() {
      return this.forceNextSneak;
   }

   public boolean isExplored() {
      return this.explored;
   }

   public List<BloodBlink.Entry> getRoomPriority() {
      return this.roomPriority;
   }

   public BooleanSetting getWaitForGround() {
      return this.waitForGround;
   }

   public BooleanSetting getProxyPearl() {
      return this.proxyPearl;
   }

   public BooleanSetting getAuto() {
      return this.auto;
   }

   public BooleanSetting getAfricanSlavePingMode() {
      return this.africanSlavePingMode;
   }

   public NumberSetting getDeathTickOffset() {
      return this.deathTickOffset;
   }

   public NumberSetting getEarlyExit() {
      return this.earlyExit;
   }

   public NumberSetting getExploreExit() {
      return this.exploreExit;
   }

   public NumberSetting getBloodLoadTickTime() {
      return this.bloodLoadTickTime;
   }

   public KeybindSetting getCancel() {
      return this.cancel;
   }

   public ModeSetting getMode() {
      return this.mode;
   }

   public NumberSetting getPriority() {
      return this.priority;
   }

   record Entry(UniqueRoom room, int priority) {
   }
}
