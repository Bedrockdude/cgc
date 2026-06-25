package com.ricedotwho.rsa.module.impl.dungeon.device;

import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsa.component.impl.managers.PacketOrderManager;
import com.ricedotwho.rsa.component.impl.managers.SwapManager;
import com.ricedotwho.rsm.component.impl.Renderer3D;
import com.ricedotwho.rsm.component.impl.location.Floor;
import com.ricedotwho.rsm.component.impl.location.Island;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.data.Keybind;
import com.ricedotwho.rsm.data.Phase7;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.game.ChatEvent.Chat;
import com.ricedotwho.rsm.event.impl.render.Render3DEvent.Extract;
import com.ricedotwho.rsm.event.impl.render.Render3DEvent.Last;
import com.ricedotwho.rsm.event.impl.world.BlockChangeEvent;
import com.ricedotwho.rsm.event.impl.world.WorldEvent.Load;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.ColourSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.KeybindSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.NumberSetting;
import com.ricedotwho.rsm.utils.DungeonUtils;
import com.ricedotwho.rsm.utils.render.render3d.type.FilledOutlineBox;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;

@ModuleInfo(aliases = "AutoSS", id = "AutoSS", category = Category.DUNGEONS)
public class AutoSS extends Module {
   private static final Vec3d START_BUTTON = new Vec3d(110.875, 121.5, 91.5);
   private static final BlockPos DETECT = new BlockPos(110, 123, 92);
   KeybindSetting resetKey = new KeybindSetting("Reset SS Key", new Keybind(-1, false, null), this::SSR);
   BooleanSetting sendChat = new BooleanSetting("Send SSR Chat Message", true);
   BooleanSetting autoStart = new BooleanSetting("Autostart", true);
   BooleanSetting forceSkyblock = new BooleanSetting("Force Skyblock (Don't keep enabled)", false);
   private final NumberSetting clickDelay = new NumberSetting("Click Delay (MS)", 10.0, 500.0, 200.0, 10.0);
   private final NumberSetting autoStartDelay = new NumberSetting("Autostart Delay (MS)", 10.0, 500.0, 120.0, 10.0);
   private final ColourSetting fillColor = new ColourSetting("Button Fill Color", Colour.GREEN.brighter());
   private final ColourSetting outlineColor = new ColourSetting("Button Outline Color", Colour.GREEN.darker());
   private long lastClickTime = System.currentTimeMillis();
   private boolean next = false;
   private int state = 0;
   private boolean doneFirst = false;
   private boolean doingSS = false;
   private final List<BlockPos> clicks = new ArrayList<>();
   private final List<Vec3d> allButtons = new ArrayList<>();
   private Vec3d clickedButton;

   public AutoSS() {
      this.registerProperty(new Setting[]{this.resetKey, this.sendChat, this.autoStart, this.forceSkyblock, this.clickDelay, this.autoStartDelay});
   }

   private void start() {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         if (!(player.squaredDistanceTo(START_BUTTON) > 25.0)) {
            this.allButtons.clear();
            RSA.chat("Starting SS!");
            this.resetState();
            this.doingSS = true;
            new Thread(() -> {
               try {
                  for (int i = 0; i < 2; i++) {
                     this.reset();
                     this.clickButton(START_BUTTON);
                     Thread.sleep(((BigDecimal)this.autoStartDelay.getValue()).longValue());
                  }

                  this.doingSS = true;
                  this.clickButton(START_BUTTON);
               } catch (Exception var2) {
                  RSA.chat("Error Occurred");
               }
            }).start();
         }
      }
   }

   @SubscribeEvent
   public void onRender(Last event) {
      if (this.areaCheck()) {
         if (System.currentTimeMillis() - this.lastClickTime + 1L >= ((BigDecimal)this.clickDelay.getValue()).longValue()) {
            if (MinecraftClient.getInstance().world != null && MinecraftClient.getInstance().player != null) {
               ClientPlayerEntity player = MinecraftClient.getInstance().player;
               if (!(player.squaredDistanceTo(START_BUTTON) > 25.0)) {
                  if (MinecraftClient.getInstance().world.getBlockState(DETECT).getBlock() == Blocks.STONE_BUTTON && this.doingSS) {
                     if (!this.doneFirst && this.clicks.size() == 3) {
                        this.clicks.removeFirst();
                        this.allButtons.removeFirst();
                     }

                     this.doneFirst = true;
                     if (this.state < this.clicks.size()) {
                        BlockPos next = this.clicks.get(this.state);
                        if (MinecraftClient.getInstance().world.getBlockState(next).getBlock() == Blocks.STONE_BUTTON) {
                           this.clickButton(Vec3d.of(next));
                           this.state++;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean areaCheck() {
      return this.forceSkyblock.getValue()
         ? true
         : Location.getArea().is(Island.Dungeon) && (Location.getFloor() == Floor.F7 || Location.getFloor() == Floor.M7) && DungeonUtils.isPhase(Phase7.P3);
   }

   @SubscribeEvent
   public void onRenderButtons(Extract event) {
      if (this.areaCheck()) {
         if (MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().world != null) {
            ClientWorld level = MinecraftClient.getInstance().world;
            if (System.currentTimeMillis() - this.lastClickTime > ((BigDecimal)this.clickDelay.getValue()).longValue()) {
               this.clickedButton = null;
            }

            if (!(MinecraftClient.getInstance().player.squaredDistanceTo(START_BUTTON) >= 1600.0)) {
               if (this.clickedButton != null) {
                  this.renderButton(level, BlockPos.ofFloored(this.clickedButton), this.fillColor.getValue(), this.outlineColor.getValue());
               }
            }
         }
      }
   }

   private void renderButton(ClientWorld level, BlockPos pos, Colour colorFill, Colour colorOutline) {
      BlockState state = level.getBlockState(pos);
      VoxelShape shape = state.getOutlineShape(level, pos);
      if (!shape.isEmpty()) {
         Renderer3D.addTask(new FilledOutlineBox(shape.getBoundingBox().offset(pos), colorFill, colorOutline, false));
      }
   }

   private void clickButton(Vec3d vec3) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         if (player.squaredDistanceTo(vec3) > 36.0) {
            RSA.chat("Button too far!");
         } else {
            this.lastClickTime = System.currentTimeMillis();
            PacketOrderManager.register(PacketOrderManager.STATE.ITEM_USE, () -> this.clickButton0(vec3));
         }
      }
   }

   private void clickButton0(Vec3d vec3) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         if (player.squaredDistanceTo(vec3) > 36.0) {
            RSA.chat("Button too far!");
         } else {
            this.clickedButton = vec3;
            SwapManager.sendBlockC08(vec3, Direction.WEST, true, false);
         }
      }
   }

   @SubscribeEvent
   public void onWorldLoad(Load event) {
      this.resetState();
   }

   @SubscribeEvent
   public void onChatMessage(Chat event) {
      if (this.areaCheck() && (Boolean)this.autoStart.getValue()) {
         if (MinecraftClient.getInstance().player != null) {
            String msg = event.getMessage().getString();
            if (msg.equals("[BOSS] Goldor: Who dares trespass into my domain?")) {
               this.start();
            }
         }
      }
   }

   @SubscribeEvent
   public void onBlockChange(BlockChangeEvent event) {
      BlockPos pos = event.getBlockPos();
      if (event.getNewState().getBlock() == Blocks.SEA_LANTERN) {
         if (this.areaCheck()) {
            if (pos.getX() == 111 && pos.getY() >= 120 && pos.getY() <= 123 && pos.getZ() >= 92 && pos.getZ() <= 95) {
               BlockPos button = new BlockPos(110, event.getBlockPos().getY(), event.getBlockPos().getZ());
               if (this.clicks.size() == 2 && this.clicks.getFirst().equals(button) && !this.doneFirst) {
                  this.doneFirst = true;
                  this.clicks.removeFirst();
                  this.allButtons.removeFirst();
               }

               if (!this.clicks.contains(button)) {
                  this.state = 0;
                  this.clicks.add(button);
                  this.allButtons.add(Vec3d.of(button));
               }
            }
         }
      }
   }

   public void SSR() {
      if (this.areaCheck()) {
         if ((Boolean)this.sendChat.getValue() && MinecraftClient.getInstance().getNetworkHandler() != null) {
            MinecraftClient.getInstance().getNetworkHandler().sendChatCommand("pc SSRS! blame tps");
         }

         this.start();
      }
   }

   public void resetState() {
      this.allButtons.clear();
      this.clicks.clear();
      this.next = false;
      this.state = 0;
      this.doneFirst = false;
      this.doingSS = false;
   }

   public void onEnable() {
      this.resetState();
   }

   public KeybindSetting getResetKey() {
      return this.resetKey;
   }

   public BooleanSetting getSendChat() {
      return this.sendChat;
   }

   public BooleanSetting getAutoStart() {
      return this.autoStart;
   }

   public BooleanSetting getForceSkyblock() {
      return this.forceSkyblock;
   }

   public NumberSetting getClickDelay() {
      return this.clickDelay;
   }

   public NumberSetting getAutoStartDelay() {
      return this.autoStartDelay;
   }

   public ColourSetting getFillColor() {
      return this.fillColor;
   }

   public ColourSetting getOutlineColor() {
      return this.outlineColor;
   }

   public long getLastClickTime() {
      return this.lastClickTime;
   }

   public boolean isNext() {
      return this.next;
   }

   public int getState() {
      return this.state;
   }

   public boolean isDoneFirst() {
      return this.doneFirst;
   }

   public boolean isDoingSS() {
      return this.doingSS;
   }

   public List<BlockPos> getClicks() {
      return this.clicks;
   }

   public List<Vec3d> getAllButtons() {
      return this.allButtons;
   }

   public Vec3d getClickedButton() {
      return this.clickedButton;
   }
}
