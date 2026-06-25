package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3;

import com.google.common.reflect.TypeToken;
import com.google.gson.GsonBuilder;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.Argument;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.type.LeapArg;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.type.TermArg;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.type.TermCloseArg;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.args.type.TriggerArg;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.recorder.MovementRecorder;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings.BlinkRing;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.rings.Ring;
import com.ricedotwho.rsm.RSM;
import com.ricedotwho.rsm.component.impl.camera.ClientRotationHandler;
import com.ricedotwho.rsm.component.impl.camera.ClientRotationProvider;
import com.ricedotwho.rsm.component.impl.location.Island;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.component.impl.map.handler.Dungeon;
import com.ricedotwho.rsm.data.Keybind;
import com.ricedotwho.rsm.data.MutableInput;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.client.InputPollEvent;
import com.ricedotwho.rsm.event.impl.game.ClientTickEvent.End;
import com.ricedotwho.rsm.event.impl.game.ClientTickEvent.Start;
import com.ricedotwho.rsm.event.impl.game.TerminalEvent.Close;
import com.ricedotwho.rsm.event.impl.game.TerminalEvent.Open;
import com.ricedotwho.rsm.event.impl.render.Render3DEvent.Extract;
import com.ricedotwho.rsm.event.impl.world.WorldEvent.Load;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.group.GroupSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.KeybindSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.NumberSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.SaveSetting;
import com.ricedotwho.rsm.utils.ChatUtils;
import com.ricedotwho.rsm.utils.Utils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;

@ModuleInfo(aliases = "Auto P3", id = "AutoP3", category = Category.DUNGEONS, hasKeybind = true)
public class AutoP3 extends Module implements ClientRotationProvider {
   private static final MutableText PREFIX = Text.empty()
      .append(Text.literal("[").formatted(Formatting.GOLD))
      .append(Text.literal("byebyebalding").formatted(Formatting.DARK_GRAY))
      .append(Text.literal("] ").formatted(Formatting.GOLD))
      .append(Text.empty().formatted(Formatting.WHITE));
   private final BooleanSetting forceSkyblock = new BooleanSetting("Force Skyblock", false);
   private final BooleanSetting yap = new BooleanSetting("Feedback", false);
   private final KeybindSetting triggerBind = new KeybindSetting("Trigger", new Keybind(0, true, this::trigger));
   private static final NumberSetting edgeDist = new NumberSetting("Edge Dist", 0.0, 0.1, 0.001, 0.001);
   private final BooleanSetting depth = new BooleanSetting("Depth", false);
   private final BooleanSetting strafe = new BooleanSetting("45", true);
   private final BooleanSetting freecamBlink = new BooleanSetting("Freecam Blink", false);
   private final GroupSetting<MovementRecorder> movement = new GroupSetting("Movement", new MovementRecorder(this));
   private final SaveSetting<List<Ring>> data = new SaveSetting(
      "Rings",
      "dungeon/ap3",
      "rings.json",
      ArrayList::new,
      (new TypeToken<List<Ring>>() {}).getType(),
      new GsonBuilder().registerTypeHierarchyAdapter(Ring.class, new RingAdapter()).setPrettyPrinting().create(),
      true,
      this::reload,
      null
   );
   private final List<Ring> rings;
   private boolean desync = false;
   private boolean lastDesync = false;
   private final List<Ring> activeRings;
   private final List<Ring> temp = new ArrayList<>();
   private final List<Ring> redoList = new ArrayList<>();

   public AutoP3() {
      this.registerProperty(
         new Setting[]{this.yap, this.triggerBind, edgeDist, this.freecamBlink, this.depth, this.strafe, this.forceSkyblock, this.movement, this.data}
      );
      this.rings = new ArrayList<>();
      this.activeRings = new ArrayList<>(5);
   }

   @SubscribeEvent
   public void onTickEnd(End event) {
      if (!this.desync && this.lastDesync && MinecraftClient.getInstance().player != null) {
         MinecraftClient.getInstance().player.setYaw(ClientRotationHandler.getClientYaw());
         MinecraftClient.getInstance().player.setPitch(ClientRotationHandler.getClientPitch());
      }

      this.lastDesync = this.desync;
   }

   @SubscribeEvent
   public void onWorldLoad(Load event) {
      this.activeRings.clear();
      this.reload();
   }

   public static void load(String config) {
      AutoP3 ap3 = (AutoP3)RSM.getModule(AutoP3.class);
      ap3.getData().setFileName(config);
      ap3.getData().updateFile();
      ap3.getData().load();
      ap3.reload();
   }

   @SubscribeEvent
   public void onPollInputs(InputPollEvent event) {
      if (this.dungeonCheck()) {
         if (!this.activeRings.isEmpty()) {
            MutableInput mutableInput = event.getInput();
            PlayerInput input = event.getClientInput();

            for (int i = 0; i < this.activeRings.size(); i++) {
               Ring r = this.activeRings.get(i);
               boolean bl2 = r.isActive() && r.tick(mutableInput, input, this);
               if (bl2) {
                  r.setInactive();
                  this.activeRings.remove(i--);
               }
            }
         }
      }
   }

   private void reload() {
      this.rings.clear();
      this.rings.addAll((Collection<? extends Ring>)this.data.getValue());
   }

   protected void onDesyncEnable() {
      ClientRotationHandler.registerProvider(this);
      if (MinecraftClient.getInstance().player != null) {
         ClientRotationHandler.setYaw(MinecraftClient.getInstance().player.getYaw());
      }
   }

   @SubscribeEvent
   public void onTick(Start event) {
      if (this.dungeonCheck() && mc.player != null) {
         this.desync = false;
         Vec3d playerPos = mc.player.getEntityPos();
         Vec3d oldPos = mc.player.getLastRenderPos();
         List<Ring> sorted;
         synchronized (this.rings) {
            sorted = this.rings
               .stream()
               .filter(
                  r -> r.updateState(playerPos, oldPos)
                     && (this.activeRings.isEmpty() || this.activeRings.stream().allMatch(active -> r.getPriority() >= active.getPriority()))
               )
               .sorted(Comparator.comparingInt(Ring::getPriority).reversed())
               .toList();
         }

         if (!sorted.isEmpty()) {
            boolean feedback = (Boolean)this.yap.getValue();
            this.activeRings.removeIf(r -> !r.isActive());
            this.temp.clear();
            boolean stop = false;

            for (Ring ring : sorted) {
               this.temp.add(ring);
               if (!ring.checkArg()) {
                  if (ring.isStop()) {
                     stop = true;
                  }

                  ring.setTriggered();
                  ring.setActive();
                  if (feedback) {
                     ring.feedback();
                  }

                  if (!ring.execute()) {
                     break;
                  }
               }
            }

            if (stop) {
               this.activeRings.removeIf(r -> {
                  if (r.shouldStop()) {
                     r.setInactive();
                     return true;
                  } else {
                     return false;
                  }
               });
            }

            this.activeRings.addAll(this.temp.stream().filter(r -> !this.activeRings.contains(r)).toList());
         }
      }
   }

   @SubscribeEvent
   public void onRender(Extract event) {
      if (this.dungeonCheck()) {
         synchronized (this.rings) {
            this.rings.forEach(r -> r.render((Boolean)this.depth.getValue()));
         }
      }
   }

   @SubscribeEvent
   public void onTermOpen(Open event) {
      this.consumeArg(TermArg.class, event);
   }

   @SubscribeEvent
   public void onTermOpen(Close event) {
      if (event.isServer()) {
         this.consumeArg(TermCloseArg.class, true);
      }
   }

   private void trigger() {
      this.consumeArg(TriggerArg.class, true);
      this.consumeArg(TermCloseArg.class, true);
      this.consumeArg(TermArg.class, null);
      this.consumeArg(LeapArg.class, true);
      if (!this.activeRings.isEmpty()) {
         this.activeRings.stream().filter(r -> r instanceof BlinkRing).forEach(r -> ((BlinkRing)r).flushNext());
      }
   }

   private <T> void consumeArg(Class<? extends Argument<T>> clazz, T value) {
      if (mc.player != null) {
         Vec3d playerPos = mc.player.getEntityPos();
         Vec3d oldPos = mc.player.getLastRenderPos();
         this.activeRings.stream().filter(s -> s.isInNode(playerPos, oldPos)).toList().forEach(r -> r.consumeArg(clazz, value));
      }
   }

   private boolean dungeonCheck() {
      return (Boolean)this.forceSkyblock.getValue() || mc.player != null && Location.getArea().is(Island.Dungeon) && Dungeon.isInBoss();
   }

   public void addRing(Ring ring) {
      ring.setTriggered();
      synchronized (this.rings) {
         this.rings.add(ring);
      }

      this.save();
      modMessage(
         "Added %s %s%s", Utils.capitalise(ring.getType().getName()), Formatting.GRAY, ring.getArgManager().getList(ring.getSubManager().getList())
      );
   }

   public boolean insertRing(Ring ring, int index) {
      if (index >= 0 && index <= this.rings.size()) {
         ring.setTriggered();
         synchronized (this.rings) {
            this.rings.add(index, ring);
         }

         this.save();
         return true;
      } else {
         return false;
      }
   }

   public boolean removeIndexed(int index) {
      synchronized (this.rings) {
         if (index < 0 || index >= this.rings.size()) {
            return false;
         }

         this.rings.remove(index);
      }

      this.save();
      return true;
   }

   public void removeNearest(Vec3d pos) {
      synchronized (this.rings) {
         int index = IntStream.range(0, this.rings.size()).boxed().min(Comparator.comparingDouble(i -> this.rings.get(i).getDistanceSq(pos))).orElse(-1);
         if (index >= 0) {
            Ring ring = this.rings.remove(index);
            this.save();
            modMessage("Removed %s", Utils.capitalise(ring.getType().getName()));
            this.data.setValue(List.copyOf(this.rings));
         }
      }
   }

   public void undo() {
      if (this.rings.isEmpty()) {
         modMessage("No Rings!");
      } else {
         Ring ring = this.rings.removeLast();
         this.redoList.add(ring);
         this.save();
         modMessage("Undid %s", Utils.capitalise(ring.getType().getName()));
      }
   }

   public void redo() {
      if (this.redoList.isEmpty()) {
         modMessage("No Rings!");
      } else {
         Ring ring = this.redoList.removeLast();
         this.rings.add(ring);
         this.save();
         modMessage("Redid %s", Utils.capitalise(ring.getType().getName()));
      }
   }

   public void setDesync(boolean bl) {
      if (bl && !this.desync && !this.lastDesync) {
         this.onDesyncEnable();
      }

      this.desync = bl;
   }

   protected boolean getDesync() {
      return this.desync;
   }

   protected boolean getLastDesync() {
      return this.lastDesync;
   }

   public boolean isClientRotationActive() {
      return this.isEnabled() && this.desync;
   }

   public boolean allowClientKeyInputs() {
      return true;
   }

   public void save() {
      this.data.setValue(List.copyOf(this.rings));
      this.data.save();
   }

   public void load() {
      this.data.load();
   }

   public static void modMessage(Object message, Object... objects) {
      ChatUtils.chatClean(PREFIX.copy().append(String.format(message.toString(), objects)));
   }

   public BooleanSetting getForceSkyblock() {
      return this.forceSkyblock;
   }

   public BooleanSetting getYap() {
      return this.yap;
   }

   public KeybindSetting getTriggerBind() {
      return this.triggerBind;
   }

   public BooleanSetting getDepth() {
      return this.depth;
   }

   public BooleanSetting getStrafe() {
      return this.strafe;
   }

   public GroupSetting<MovementRecorder> getMovement() {
      return this.movement;
   }

   public SaveSetting<List<Ring>> getData() {
      return this.data;
   }

   public List<Ring> getRings() {
      return this.rings;
   }

   public List<Ring> getTemp() {
      return this.temp;
   }

   public List<Ring> getRedoList() {
      return this.redoList;
   }

   public static NumberSetting getEdgeDist() {
      return edgeDist;
   }

   public BooleanSetting getFreecamBlink() {
      return this.freecamBlink;
   }

   public List<Ring> getActiveRings() {
      return this.activeRings;
   }
}
