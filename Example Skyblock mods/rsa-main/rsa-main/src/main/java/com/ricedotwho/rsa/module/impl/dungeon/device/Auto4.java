package com.ricedotwho.rsa.module.impl.dungeon.device;

import com.ricedotwho.rsa.component.impl.managers.PacketOrderManager;
import com.ricedotwho.rsa.component.impl.managers.SwapManager;
import com.ricedotwho.rsm.component.impl.location.Island;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.component.impl.task.TaskComponent;
import com.ricedotwho.rsm.data.Pos;
import com.ricedotwho.rsm.data.Rotation;
import com.ricedotwho.rsm.event.api.SubscribeEvent;
import com.ricedotwho.rsm.event.impl.world.BlockChangeEvent;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.NumberSetting;
import com.ricedotwho.rsm.utils.ItemUtils;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.item.Items;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.Vec3d;

@ModuleInfo(aliases = "Auto4", id = "Auto4", category = Category.DUNGEONS)
public class Auto4 extends Module {
   private final NumberSetting delay = new NumberSetting("Delay", 0.0, 400.0, 250.0, 10.0);
   private final List<Integer> done = new ArrayList<>();
   private static final List<Pos> blocks = Arrays.asList(
      new Pos(68.0, 130.0, 50.0),
      new Pos(66.0, 130.0, 50.0),
      new Pos(64.0, 130.0, 50.0),
      new Pos(68.0, 128.0, 50.0),
      new Pos(66.0, 128.0, 50.0),
      new Pos(64.0, 128.0, 50.0),
      new Pos(68.0, 126.0, 50.0),
      new Pos(66.0, 126.0, 50.0),
      new Pos(64.0, 126.0, 50.0)
   );
   private long lastShot = 0L;

   public Auto4() {
      this.registerProperty(new Setting[]{this.delay});
   }

   @SubscribeEvent
   public void onBlockChange(BlockChangeEvent event) {
      if (Location.getArea().is(Island.Dungeon) && mc.player != null && this.on4thDev() && this.isHoldingBow()) {
         Pos pos = event.getPos();
         int index = blocks.indexOf(pos);
         if (index != -1) {
            if (event.getNewState().isOf(Blocks.BLUE_TERRACOTTA)) {
               this.done.add(index);
            }

            if (event.getNewState().isOf(Blocks.EMERALD_BLOCK)) {
               long now = System.currentTimeMillis();
               long delay = ((BigDecimal)this.delay.getValue()).longValue() - (now - this.lastShot);
               Rotation rotation = this.calculateAim(event.getPos(), index, "TERMINATOR".equals(ItemUtils.getID(mc.player.getInventory().getSelectedStack())));
               TaskComponent.onMilli(delay, () -> {
                  if (this.isHoldingBow()) {
                     PacketOrderManager.register(PacketOrderManager.STATE.ITEM_USE, () -> {
                        SwapManager.sendAirC08(rotation, false);
                        this.lastShot = System.currentTimeMillis();
                     });
                  }
               });
            }
         }
      }
   }

   private boolean isHoldingBow() {
      return mc.player.getInventory().getSelectedStack().getItem().equals(Items.BOW);
   }

   private boolean on4thDev() {
      Vec3d pos = mc.player.getEntityPos();
      return pos.getX() > 63.0 && pos.getX() < 64.0 && pos.getY() == 127.0 && pos.getZ() > 35.0 && pos.getZ() < 36.0;
   }

   private Rotation calculateAim(Pos pos, int index, boolean term) {
      Pos target = pos.copy();
      if (!term) {
         return Rotation.from(target.add(0.5, 1.0, 0.0).asVec3());
      } else {
         switch (index % 3) {
            case 0:
               target.selfAdd(-0.5, 1.0, 0.0);
               break;
            case 1:
               boolean f1 = this.done.contains(index - 1);
               boolean f2 = this.done.contains(index + 1);
               if (f1 && !f2) {
                  target.selfAdd(-0.5, 1.0, 0.0);
               } else if (f2 && !f1) {
                  target.selfAdd(1.5, 1.0, 0.0);
               } else {
                  target.selfAdd(0.5 + (Math.random() < 0.5 ? -1 : 1), 1.0, 0.0);
               }
               break;
            case 2:
               target.selfAdd(1.5, 1.0, 0.0);
               break;
            default:
               target.selfAdd(0.5, 1.0, 0.0);
         }

         return Rotation.from(target.asVec3());
      }
   }

   public NumberSetting getDelay() {
      return this.delay;
   }

   public List<Integer> getDone() {
      return this.done;
   }

   public long getLastShot() {
      return this.lastShot;
   }
}
