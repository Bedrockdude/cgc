package com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto;

import com.ricedotwho.rsa.component.impl.TickFreeze;
import com.ricedotwho.rsa.module.impl.dungeon.boss.p3.terminals.auto.terminals.TerminalRenderer;
import com.ricedotwho.rsm.component.impl.Terminals;
import com.ricedotwho.rsm.data.Colour;
import com.ricedotwho.rsm.data.TerminalType;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.client.InputPollEvent;
import com.ricedotwho.rsm.event.impl.render.Render2DEvent;
import com.ricedotwho.rsm.module.SubModule;
import com.ricedotwho.rsm.module.api.SubModuleInfo;
import com.ricedotwho.rsm.module.impl.dungeon.boss.p3.terminal.TerminalSolver;
import com.ricedotwho.rsm.module.impl.dungeon.boss.p3.terminal.types.Melody;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.ColourSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.DragSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.ModeSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.NumberSetting;
import com.ricedotwho.rsm.utils.Utils;
import com.ricedotwho.rsm.utils.render.render2d.NVGUtils;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import net.minecraft.util.PlayerInput;
import net.minecraft.client.MinecraftClient;
import org.joml.Vector2d;

@SubModuleInfo(name = "InvWalk", alwaysDisabled = false)
public class InvWalk extends SubModule<AutoTerms> {
   private final ModeSetting style = new ModeSetting("Style", "Items", Arrays.asList("Solver", "Items"));
   private static final BooleanSetting useOverrides = new BooleanSetting("Use Overrides", true);
   private final BooleanSetting renderTitles = new BooleanSetting("Render title thing", true);
   private final BooleanSetting titleMCFont = new BooleanSetting("Title MC Font", true);
   private final BooleanSetting renderClicksLeft = new BooleanSetting("Render clicks left", true);
   private final BooleanSetting clicksMCFont = new BooleanSetting("Clicks MC Font", true);
   private final ColourSetting titleColour = new ColourSetting("Title Colour", new Colour(96, 31, 158));
   private final ColourSetting remainingColour = new ColourSetting("Remaining Colour", new Colour(96, 31, 158));
   private final ColourSetting clicksColour = new ColourSetting("Clicks Colour", new Colour(0, 191, 0));
   private final BooleanSetting textShadow = new BooleanSetting("Text Shadow", false);
   private final ModeSetting moveDelayMode = new ModeSetting("Mode Delay", "Freeze", List.of("Stop Inputs", "Freeze"));
   private final NumberSetting melodyMoveDelay = new NumberSetting("Melody Move Delay", 0.0, 500.0, 300.0, 50.0);
   private final DragSetting termTitle = new DragSetting("Term Title", new Vector2d(10.0, 10.0), new Vector2d(150.0, 15.0));
   private final DragSetting clicksText = new DragSetting("Clicks Text", new Vector2d(10.0, 10.0), new Vector2d(150.0, 15.0));
   private final DragSetting gui = new DragSetting("Visualiser Gui", new Vector2d(551.0, 330.0), new Vector2d(144.0, 80.0));
   private final TerminalRenderer terminalRenderer;
   public int melodyMoveCounter = 0;
   private long lastMelodyClick = 0L;

   public InvWalk(AutoTerms module) {
      super(module);
      this.registerProperty(
         new Setting[]{
            this.style,
            useOverrides,
            this.renderTitles,
            this.titleMCFont,
            this.renderClicksLeft,
            this.clicksMCFont,
            this.titleColour,
            this.remainingColour,
            this.clicksColour,
            this.textShadow,
            this.moveDelayMode,
            this.melodyMoveDelay,
            this.termTitle,
            this.clicksText,
            this.gui
         }
      );
      this.terminalRenderer = new TerminalRenderer();
   }

   public void reset() {
      this.melodyMoveCounter = 0;
   }

   @SubscribeEvent
   public void onRenderGui(Render2DEvent event) {
      try {
         if (((AutoTerms)this.module).isInTerm()) {
            int slots = Utils.getGuiSlotCount(((AutoTerms)this.module).getTerminalContainer().getType());
            if ((Boolean)this.renderClicksLeft.getValue() && Terminals.getCurrent() != null) {
               String remainingText = "Clicks remaining: ";
               String clicks = Terminals.getCurrent() instanceof Melody mel
                  ? mel.getProgress() + "/4"
                  : String.valueOf(Terminals.getCurrent().getSolution().size());
               if ((Boolean)this.clicksMCFont.getValue()) {
                  this.clicksText.renderScaledGFX(event.getGfx(), () -> {
                     event.getGfx().drawTextWithShadow(mc.textRenderer, remainingText, 0, 0, this.remainingColour.getValue().getRGB());
                     event.getGfx().drawTextWithShadow(mc.textRenderer, clicks, mc.textRenderer.getWidth(remainingText), 0, this.clicksColour.getValue().getRGB());
                  }, 150.0F, 15.0F);
               } else {
                  this.clicksText
                     .renderScaled(
                        event.getGfx(),
                        () -> {
                           NVGUtils.drawText(
                              remainingText, 0.0F, 0.0F, 14.0F, this.remainingColour.getValue(), (Boolean)this.textShadow.getValue(), NVGUtils.PRODUCT_SANS
                           );
                           NVGUtils.drawText(
                              clicks,
                              NVGUtils.getTextWidth(remainingText, 14.0F, NVGUtils.PRODUCT_SANS),
                              0.0F,
                              14.0F,
                              this.clicksColour.getValue(),
                              (Boolean)this.textShadow.getValue(),
                              NVGUtils.PRODUCT_SANS
                           );
                        },
                        150.0F,
                        15.0F
                     );
               }
            }

            if ((Boolean)this.renderTitles.getValue() && Terminals.getCurrent() != null) {
               String termText = "In " + Utils.capitalise(Terminals.getCurrent().getType().name().replace("_", " ").toLowerCase());
               if (Terminals.getCurrent().getType().equals(TerminalType.MELODY)) {
                  int moveDelay = ((BigDecimal)this.melodyMoveDelay.getValue()).intValue();
                  long now = System.currentTimeMillis();
                  if (this.lastMelodyClick + moveDelay > now) {
                     termText = termText + " " + (this.lastMelodyClick - now + moveDelay) + "ms";
                  }
               }

               String finalTermText = termText;
               if ((Boolean)this.titleMCFont.getValue()) {
                  this.termTitle
                     .renderScaledGFX(
                        event.getGfx(),
                        () -> event.getGfx().drawTextWithShadow(mc.textRenderer, finalTermText, 0, 0, this.titleColour.getValue().getRGB()),
                        150.0F,
                        15.0F
                     );
               } else {
                  this.termTitle
                     .renderScaled(
                        event.getGfx(),
                        () -> NVGUtils.drawText(
                           finalTermText, 0.0F, 0.0F, 14.0F, this.titleColour.getValue(), (Boolean)this.textShadow.getValue(), NVGUtils.PRODUCT_SANS
                        ),
                        150.0F,
                        15.0F
                     );
               }
            }

            if (this.style.is("Items")) {
               float width = 144.0F;
               float height = (float)(Math.floor(slots / 9.0F) * 16.0);
               this.gui
                  .renderScaledGFX(
                     event.getGfx(), () -> this.terminalRenderer.renderItems(event.getGfx(), ((AutoTerms)this.module).getTerminal()), width, height
                  );
            } else {
               float gap = 32.0F + ((BigDecimal)TerminalSolver.getGap().getValue()).floatValue();
               this.gui.renderScaled(event.getGfx(), () -> this.terminalRenderer.renderSolver(gap), 9.0F * gap, slots / 9.0F * gap);
            }
         }
      } catch (Exception exception) {
         throw new RuntimeException(exception);
      }
   }

   @SubscribeEvent
   public void onPollInput(InputPollEvent event) {
      if (this.melodyMoveCounter >= 1) {
         if (MinecraftClient.getInstance().currentScreen == null && !((AutoTerms)this.module).isInTerm()) {
            this.melodyMoveCounter = 0;
         } else {
            PlayerInput oldInputs = event.getClientInput();
            PlayerInput newInputs = new PlayerInput(false, false, false, false, false, oldInputs.sneak(), false);
            event.getInput().apply(newInputs);
            this.melodyMoveCounter--;
         }
      }
   }

   public void onMelodyClick() {
      this.lastMelodyClick = System.currentTimeMillis();
      if (this.moveDelayMode.is("Freeze")) {
         TickFreeze.freeze(((BigDecimal)this.melodyMoveDelay.getValue()).longValue(), true);
      } else {
         this.melodyMoveCounter = ((BigDecimal)this.melodyMoveDelay.getValue()).intValue() / 50;
      }
   }

   public ModeSetting getStyle() {
      return this.style;
   }

   public BooleanSetting getRenderTitles() {
      return this.renderTitles;
   }

   public BooleanSetting getTitleMCFont() {
      return this.titleMCFont;
   }

   public BooleanSetting getRenderClicksLeft() {
      return this.renderClicksLeft;
   }

   public BooleanSetting getClicksMCFont() {
      return this.clicksMCFont;
   }

   public ColourSetting getTitleColour() {
      return this.titleColour;
   }

   public ColourSetting getRemainingColour() {
      return this.remainingColour;
   }

   public ColourSetting getClicksColour() {
      return this.clicksColour;
   }

   public BooleanSetting getTextShadow() {
      return this.textShadow;
   }

   public ModeSetting getMoveDelayMode() {
      return this.moveDelayMode;
   }

   public NumberSetting getMelodyMoveDelay() {
      return this.melodyMoveDelay;
   }

   public DragSetting getTermTitle() {
      return this.termTitle;
   }

   public DragSetting getClicksText() {
      return this.clicksText;
   }

   public DragSetting getGui() {
      return this.gui;
   }

   public TerminalRenderer getTerminalRenderer() {
      return this.terminalRenderer;
   }

   public int getMelodyMoveCounter() {
      return this.melodyMoveCounter;
   }

   public long getLastMelodyClick() {
      return this.lastMelodyClick;
   }

   public static BooleanSetting getUseOverrides() {
      return useOverrides;
   }
}
