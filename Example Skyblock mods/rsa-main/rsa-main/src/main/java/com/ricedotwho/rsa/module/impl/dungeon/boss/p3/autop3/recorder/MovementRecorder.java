package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.recorder;

import com.google.common.reflect.TypeToken;
import com.google.gson.GsonBuilder;
import com.ricedotwho.rsa.component.impl.managers.PacketOrderManager;
import com.ricedotwho.rsa.component.impl.managers.SwapManager;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.autop3.AutoP3;
import com.ricedotwho.rsm.data.Keybind;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.client.InputPollEvent;
import com.ricedotwho.rsm.event.impl.client.PacketEvent.Send;
import com.ricedotwho.rsm.event.impl.world.WorldEvent.Load;
import com.ricedotwho.rsm.module.SubModule;
import com.ricedotwho.rsm.module.api.SubModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.ButtonSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.KeybindSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.SaveSetting;
import com.ricedotwho.rsm.utils.ItemUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.client.util.InputUtil;

@SubModuleInfo(name = "Movement")
public class MovementRecorder extends SubModule<AutoP3> {
   private final KeybindSetting recordKey = new KeybindSetting("Record", new Keybind(InputUtil.UNKNOWN_KEY, this::toggleRecording));
   private final ButtonSetting prune = new ButtonSetting("Prune Inputs", "Prune", this::prune);
   private static final SaveSetting<List<MovementRecorder.PlayerInput>> data = new SaveSetting(
      "Route",
      "dungeon/recorder",
      "inputs.json",
      ArrayList::new,
      (new TypeToken<List<MovementRecorder.PlayerInput>>() {}).getType(),
      new GsonBuilder().registerTypeHierarchyAdapter(MovementRecorder.PlayerInput.class, new PlayerInputAdapter()).setPrettyPrinting().create(),
      true,
      null,
      null
   );
   private static MovementRecorder.State state = MovementRecorder.State.IDLE;
   private static final List<MovementRecorder.PlayerInput> recorded = new ArrayList<>();
   private static int playIndex = 0;

   public MovementRecorder(AutoP3 module) {
      super(module);
      this.registerProperty(new Setting[]{this.recordKey, this.prune, data});
   }

   public void reset() {
      playIndex = 0;
      recorded.clear();
      state = MovementRecorder.State.IDLE;
   }

   @SubscribeEvent
   public void onWorldLoad(Load event) {
      this.reset();
   }

   private void toggleRecording() {
      switch (state) {
         case RECORDING:
            state = MovementRecorder.State.IDLE;
            data.setValue(recorded);
            data.save();
            AutoP3.modMessage("Saved %s tick recording to \"%s.%s\"", recorded.size(), data.getFileName(), data.getExt());
         case PAUSED:
         default:
            break;
         case PLAYING:
            AutoP3.modMessage("Cannot record while playing!");
            break;
         case IDLE:
            recorded.clear();
            state = MovementRecorder.State.RECORDING;
            AutoP3.modMessage("Started recording!");
      }
   }

   private void prune() {
      List<MovementRecorder.PlayerInput> inputs = (List<MovementRecorder.PlayerInput>)data.getValue();

      int changed;
      for (changed = 0; inputs.size() > 1 && inputs.get(0).equals(inputs.get(1)); changed++) {
         inputs.remove(1);
      }

      while (inputs.size() > 1 && inputs.getLast().equals(inputs.get(inputs.size() - 2))) {
         inputs.removeLast();
         changed++;
      }

      data.save();
      AutoP3.modMessage("Removed %s ticks", changed);
   }

   @SubscribeEvent
   public void onPacket(Send event) {
      if (state == MovementRecorder.State.RECORDING && mc.player != null && event.getPacket() instanceof PlayerInteractItemC2SPacket packet && !recorded.isEmpty()) {
         ItemStack held = mc.player.getStackInHand(packet.getHand());
         String itemId = ItemUtils.getID(held);
         if (!itemId.isBlank() && (packet.getYaw() != 0.0 || packet.getPitch() != 0.0)) {
            MovementRecorder.PlayerInput last = recorded.getLast();
            last.using = true;
            last.useItem = new MovementRecorder.UseItem(itemId, packet.getYaw(), packet.getPitch());
         }
      }
   }

   @SubscribeEvent
   public void record(InputPollEvent event) {
      if (mc.player != null) {
         net.minecraft.util.PlayerInput in = event.getClientInput();
         if (state == MovementRecorder.State.RECORDING) {
            MovementRecorder.PlayerInput next = new MovementRecorder.PlayerInput(
               mc.gameRenderer.getCamera().getCameraYaw(), mc.gameRenderer.getCamera().getPitch(), in
            );
            recorded.add(next);
         } else if (state == MovementRecorder.State.PLAYING) {
            if (in.forward() || in.backward() || in.left() || in.right() || in.sneak()) {
               AutoP3.modMessage("Cancelling movement");
               this.reset();
               return;
            }

            List<MovementRecorder.PlayerInput> inputs = (List<MovementRecorder.PlayerInput>)data.getValue();
            if (playIndex >= inputs.size()) {
               state = MovementRecorder.State.IDLE;
               AutoP3.modMessage("Done playing %s", data.getFileName());
               return;
            }

            MovementRecorder.PlayerInput next = inputs.get(playIndex);
            event.getInput().apply(next.input());
            mc.player.setYaw(next.yaw);
            mc.player.setPitch(next.pitch);
            if (next.using && next.useItem != null && SwapManager.swapItem(next.useItem.item)) {
               PacketOrderManager.register(
                  PacketOrderManager.STATE.ITEM_USE, () -> SwapManager.sendAirC08(next.useItem.yaw, next.useItem.pitch, SwapManager.isDesynced(), false)
               );
            }

            playIndex++;
         }
      }
   }

   public static void resumeRecording() {
      if (state == MovementRecorder.State.PAUSED) {
         state = MovementRecorder.State.PLAYING;
      }

      if (data.getValue() != null && playIndex < ((List)data.getValue()).size() && ((List)data.getValue()).get(playIndex) != null) {
         MovementRecorder.PlayerInput next = (MovementRecorder.PlayerInput)((List)data.getValue()).get(playIndex);
         mc.player.setYaw(next.yaw);
         mc.player.setPitch(next.pitch);
      }
   }

   public static void pauseRecording() {
      if (state == MovementRecorder.State.PLAYING) {
         state = MovementRecorder.State.PAUSED;
      }
   }

   public static void playRecording(String name) {
      if (state != MovementRecorder.State.IDLE && state != MovementRecorder.State.PAUSED) {
         AutoP3.modMessage("Cannot start playing while not idle! State: %s", state);
      } else {
         playIndex = 0;
         data.setFileName(name);
         data.updateFile();
         data.load();
         if (((List)data.getValue()).isEmpty()) {
            AutoP3.modMessage("Cannot play empty recording!");
         } else {
            state = MovementRecorder.State.PLAYING;
         }
      }
   }

   public static SaveSetting<List<MovementRecorder.PlayerInput>> getData() {
      return data;
   }

   public static class PlayerInput {
      public final float yaw;
      public final float pitch;
      public boolean using = false;
      public MovementRecorder.UseItem useItem = null;
      public final boolean forward;
      public final boolean back;
      public final boolean left;
      public final boolean right;
      public final boolean jump;
      public final boolean sneak;
      public final boolean sprint;

      public PlayerInput(float yaw, float pitch, net.minecraft.util.PlayerInput in) {
         this(yaw, pitch, in.forward(), in.backward(), in.left(), in.right(), in.jump(), in.sneak(), in.sprint());
      }

      public net.minecraft.util.PlayerInput input() {
         return new net.minecraft.util.PlayerInput(this.forward, this.back, this.left, this.right, this.jump, this.sneak, this.sprint);
      }

      public boolean equals(MovementRecorder.PlayerInput other) {
         return this.yaw == other.yaw
            && this.pitch == other.pitch
            && this.forward == other.forward
            && this.back == other.back
            && this.left == other.left
            && this.right == other.right
            && this.jump == other.jump
            && this.sneak == other.sneak
            && this.sprint == other.sprint;
      }

      public PlayerInput(
         float yaw,
         float pitch,
         boolean using,
         MovementRecorder.UseItem useItem,
         boolean forward,
         boolean back,
         boolean left,
         boolean right,
         boolean jump,
         boolean sneak,
         boolean sprint
      ) {
         this.yaw = yaw;
         this.pitch = pitch;
         this.using = using;
         this.useItem = useItem;
         this.forward = forward;
         this.back = back;
         this.left = left;
         this.right = right;
         this.jump = jump;
         this.sneak = sneak;
         this.sprint = sprint;
      }

      public PlayerInput(float yaw, float pitch, boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sneak, boolean sprint) {
         this.yaw = yaw;
         this.pitch = pitch;
         this.forward = forward;
         this.back = back;
         this.left = left;
         this.right = right;
         this.jump = jump;
         this.sneak = sneak;
         this.sprint = sprint;
      }
   }

   private static enum State {
      RECORDING,
      PAUSED,
      PLAYING,
      IDLE;
   }

   public static class UseItem {
      public String item;
      public float yaw;
      public float pitch;

      public UseItem(String item, float yaw, float pitch) {
         this.item = item;
         this.yaw = yaw;
         this.pitch = pitch;
      }
   }
}
