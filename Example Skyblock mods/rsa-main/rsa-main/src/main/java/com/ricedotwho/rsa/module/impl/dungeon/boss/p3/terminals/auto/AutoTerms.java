package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto;

import com.google.common.collect.Lists;
import com.google.common.primitives.Shorts;
import com.google.common.primitives.SignedBytes;
import com.ricedotwho.rsa.RSA;
import com.ricedotwho.rsa.event.impl.RawTickEvent;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.terminals.ClickedSlotsTracker;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.terminals.Colors;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.terminals.Melody;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.terminals.Solution;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.terminals.SolutionClick;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.terminals.StartsWith;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.terminals.Terminal;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.terminals.TerminalState;
import com.ricedotwho.rsm.RSM;
import com.ricedotwho.rsm.event.api.EventPriority;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.client.InputPollEvent;
import com.ricedotwho.rsm.event.impl.client.PacketEvent.Receive;
import com.ricedotwho.rsm.event.impl.client.PacketEvent.Send;
import com.ricedotwho.rsm.event.impl.game.ClientTickEvent.Start;
import com.ricedotwho.rsm.event.impl.render.Render3DEvent.Last;
import com.ricedotwho.rsm.event.impl.world.WorldEvent.Load;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.group.GroupSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.MultiBoolSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.NumberSetting;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.math.BigDecimal;
import java.util.List;
import net.minecraft.util.PlayerInput;
import net.minecraft.screen.sync.ItemStackHash;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.SetCursorItemS2CPacket;

@ModuleInfo(aliases = "AutoTerms", id = "AutoTerms", category = Category.DUNGEONS)
public class AutoTerms extends Module {
   private long lastClickTime = 0L;
   private boolean clickedWindow = false;
   private boolean firstClick = true;
   private Terminal terminal;
   private ScreenHandler terminalContainer;
   private final ClickedSlotsTracker clickedSlotsTracker;
   private TerminalState predictedState = null;
   private final NumberSetting firstClickDelay = new NumberSetting("First Click Delay", 200.0, 600.0, 400.0, 5.0);
   private final NumberSetting delay = new NumberSetting("Delay", 100.0, 250.0, 150.0, 5.0);
   private final NumberSetting breakThreshold = new NumberSetting("Break Threshold", 200.0, 800.0, 500.0, 10.0);
   private static final MultiBoolSetting terminals = new MultiBoolSetting(
      "Terminals",
      List.of("Colours", "Melody", "Numbers", "Red Green", "Rubix", "Starts With"),
      List.of("Colours", "Melody", "Numbers", "Red Green", "Rubix", "Starts With")
   );
   private final BooleanSetting melodySkip = new BooleanSetting("Melody Skip", true);
   private final BooleanSetting melodySkipFirst = new BooleanSetting("Don't Skip First", true);
   private final GroupSetting<InvWalk> invWalkGroup = new GroupSetting("Invwalk", new InvWalk(this));

   public AutoTerms() {
      this.clickedSlotsTracker = new ClickedSlotsTracker();
      this.registerProperty(
         new Setting[]{terminals, this.firstClickDelay, this.delay, this.breakThreshold, this.melodySkip, this.melodySkipFirst, this.invWalkGroup}
      );
   }

   @SubscribeEvent
   public void onLoadWorld(Load event) {
      this.close();
   }

   @SubscribeEvent
   public void render(Last event) {
      if (this.isInTerm() && !(this.terminal instanceof Melody)) {
         if (this.terminal.shouldSolve() && !this.terminal.isSolved()) {
            this.terminal.solve();
         }

         if (this.terminal.isSolved()) {
            if (this.predictedState != null) {
               TerminalState newState = this.terminal.getCurrentState();
               if (!this.predictedState.matches(newState)) {
                  this.firstClick = true;
                  this.lastClickTime = System.currentTimeMillis();
                  this.clickedSlotsTracker.clear();
               }

               this.predictedState = null;
            }

            if (this.terminal.isEnabled()) {
               if (!this.firstClick || System.currentTimeMillis() - this.lastClickTime >= ((BigDecimal)this.firstClickDelay.getValue()).longValue()) {
                  if (System.currentTimeMillis() - this.lastClickTime >= ((BigDecimal)this.delay.getValue()).longValue()) {
                     if (System.currentTimeMillis() - this.lastClickTime > ((BigDecimal)this.breakThreshold.getValue()).longValue()) {
                        this.clickedWindow = false;
                     }

                     if (this.isInTerm() && !this.clickedWindow) {
                        if (this.terminal.isSolved()) {
                           Solution solution = this.terminal.getSolution();
                           if (solution.getLength() >= 1) {
                              this.sendWindowClick(solution.getNext());
                              this.lastClickTime = System.currentTimeMillis();
                              this.clickedWindow = true;
                              this.firstClick = false;
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void sendWindowClick(int windowID, SolutionClick click, PlayerEntity player, ScreenHandler abstractContainerMenu) {
      if (windowID != abstractContainerMenu.syncId) {
         RSA.chat("Window ID mismatch!");
      } else {
         ClientPlayNetworkHandler connection = MinecraftClient.getInstance().getNetworkHandler();
         if (connection != null) {
            DefaultedList<Slot> nonNullList = abstractContainerMenu.slots;
            int l = nonNullList.size();
            List<ItemStack> list = Lists.newArrayListWithCapacity(l);

            for (Slot slot : nonNullList) {
               list.add(slot.getStack().copy());
            }

            abstractContainerMenu.onSlotClick(click.index(), click.button(), click.type(), player);
            Int2ObjectMap<ItemStackHash> int2ObjectMap = new Int2ObjectOpenHashMap();

            for (int m = 0; m < l; m++) {
               ItemStack itemStack = list.get(m);
               ItemStack itemStack2 = ((Slot)nonNullList.get(m)).getStack();
               if (!ItemStack.areEqual(itemStack, itemStack2)) {
                  int2ObjectMap.put(m, ItemStackHash.fromItemStack(itemStack2, connection.getComponentHasher()));
               }
            }

            ItemStackHash hashedStack = ItemStackHash.fromItemStack(abstractContainerMenu.getCursorStack(), connection.getComponentHasher());
            connection.sendPacket(
               new ClickSlotC2SPacket(
                  windowID,
                  abstractContainerMenu.getRevision(),
                  Shorts.checkedCast(click.index()),
                  SignedBytes.checkedCast(click.button()),
                  click.type(),
                  int2ObjectMap,
                  hashedStack
               )
            );
         }
      }
   }

   public void sendWindowClick(SolutionClick click) {
      if (MinecraftClient.getInstance().player != null) {
         if (this.isInTerm() && click.index() >= 0 && click.index() < this.terminal.getType().getSlotCount()) {
            if (this.terminal instanceof StartsWith || this.terminal instanceof Colors) {
               this.clickedSlotsTracker.clickSlot(this.terminalContainer.getSlot(click.index()));
            }

            sendWindowClick(this.terminal.getWindowID(), click, MinecraftClient.getInstance().player, this.terminalContainer);
         }
      }
   }

   @SubscribeEvent
   public void onPollInput(InputPollEvent event) {
      if (((InvWalk)this.invWalkGroup.getValue()).melodyMoveCounter >= 1) {
         if (MinecraftClient.getInstance().currentScreen == null && !this.isInTerm()) {
            ((InvWalk)this.invWalkGroup.getValue()).melodyMoveCounter = 0;
         } else {
            PlayerInput oldInputs = event.getClientInput();
            PlayerInput newInputs = new PlayerInput(false, false, false, false, false, oldInputs.sneak(), false);
            event.getInput().apply(newInputs);
            ((InvWalk)this.invWalkGroup.getValue()).melodyMoveCounter--;
         }
      }
   }

   @SubscribeEvent
   public void onTick(Start event) {
      if (!this.isInTerm()) {
         this.firstClick = true;
         this.clickedSlotsTracker.clear();
         this.lastClickTime = System.currentTimeMillis();
      }
   }

   @SubscribeEvent
   public void onRawTick(RawTickEvent event) {
      if (this.isInTerm() && this.terminal instanceof Melody melody && melody.isEnabled() && melody.onTickStart(this)) {
         ((InvWalk)this.invWalkGroup.getValue()).onMelodyClick();
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public void onReceivePacket(Receive event) {
      if (event.getPacket() instanceof OpenScreenS2CPacket packet) {
         if (packet.getSyncId() >= 1 && packet.getSyncId() <= 100) {
            if (MinecraftClient.getInstance().player != null) {
               TerminalState predictionState = new TerminalState(null, 0);
               if (this.terminal != null && this.terminal.isSolved()) {
                  predictionState = this.terminal.getNextState();
               }

               this.terminalContainer = packet.getScreenHandlerType().create(packet.getSyncId(), MinecraftClient.getInstance().player.getInventory());
               this.terminal = Terminal.fromPacket(packet, this.terminalContainer);
               if (this.terminal == null) {
                  this.terminalContainer = null;
               } else {
                  this.predictedState = predictionState;
                  this.clickedWindow = false;
                  ((InvWalk)this.invWalkGroup.getValue()).getTerminalRenderer().newWindow(this.terminalContainer);
                  if (((InvWalk)this.invWalkGroup.getValue()).isEnabled()) {
                     event.setCancelled(true);
                  }
               }
            }
         }
      } else if (this.isInTerm() && event.getPacket() instanceof ScreenHandlerSlotUpdateS2CPacket packetx) {
         if (packetx.getSyncId() != 0 && packetx.getSyncId() == this.terminalContainer.syncId) {
            this.terminalContainer.setStackInSlot(packetx.getSlot(), packetx.getRevision(), packetx.getStack());
            this.terminal.loadSlot(packetx);
            if (((InvWalk)this.invWalkGroup.getValue()).isEnabled()) {
               event.setCancelled(true);
            }
         }
      } else if (this.isInTerm() && event.getPacket() instanceof CloseScreenS2CPacket packetxx) {
         if (packetxx.getSyncId() != this.terminalContainer.syncId) {
            RSA.chat("Container ID mismatch on close!");
         }

         this.close();
         if (((InvWalk)this.invWalkGroup.getValue()).isEnabled()) {
            event.setCancelled(true);
         }
      } else if (this.isInTerm() && event.getPacket() instanceof SetCursorItemS2CPacket packetxx) {
         if (((InvWalk)this.invWalkGroup.getValue()).isEnabled()) {
            event.setCancelled(true);
         }
      } else if (this.isInTerm() && event.getPacket() instanceof InventoryS2CPacket packetxx) {
         if (packetxx.syncId() != 0 && ((InvWalk)this.invWalkGroup.getValue()).isEnabled()) {
            event.setCancelled(true);
         }
      }
   }

   private void close() {
      this.terminal = null;
      ((InvWalk)this.invWalkGroup.getValue()).getTerminalRenderer().close();
      this.terminalContainer = null;
      this.predictedState = null;
      this.firstClick = true;
      this.lastClickTime = System.currentTimeMillis();
      this.clickedSlotsTracker.clear();
   }

   @SubscribeEvent
   public void onSendPacket(Send event) {
      if (this.isInTerm() && event.getPacket() instanceof CloseHandledScreenC2SPacket packet) {
         this.close();
      }
   }

   public static boolean isInTerminal() {
      return ((AutoTerms)RSM.getModule(AutoTerms.class)).isInTerm();
   }

   public boolean isInTerm() {
      return this.terminal != null && this.terminalContainer != null;
   }

   public long getLastClickTime() {
      return this.lastClickTime;
   }

   public boolean isClickedWindow() {
      return this.clickedWindow;
   }

   public boolean isFirstClick() {
      return this.firstClick;
   }

   public Terminal getTerminal() {
      return this.terminal;
   }

   public ScreenHandler getTerminalContainer() {
      return this.terminalContainer;
   }

   public ClickedSlotsTracker getClickedSlotsTracker() {
      return this.clickedSlotsTracker;
   }

   public TerminalState getPredictedState() {
      return this.predictedState;
   }

   public NumberSetting getFirstClickDelay() {
      return this.firstClickDelay;
   }

   public NumberSetting getDelay() {
      return this.delay;
   }

   public NumberSetting getBreakThreshold() {
      return this.breakThreshold;
   }

   public BooleanSetting getMelodySkip() {
      return this.melodySkip;
   }

   public BooleanSetting getMelodySkipFirst() {
      return this.melodySkipFirst;
   }

   public GroupSetting<InvWalk> getInvWalkGroup() {
      return this.invWalkGroup;
   }

   public static MultiBoolSetting getTerminals() {
      return terminals;
   }
}
