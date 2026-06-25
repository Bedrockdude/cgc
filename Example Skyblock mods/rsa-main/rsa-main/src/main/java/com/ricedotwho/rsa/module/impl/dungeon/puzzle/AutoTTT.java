package com.ricedotwho.rsa.module.impl.dungeon.puzzle;

import com.ricedotwho.rsa.component.impl.managers.PacketOrderManager;
import com.ricedotwho.rsa.component.impl.managers.SwapManager;
import com.ricedotwho.rsm.module.api.SubModuleInfo;
import com.ricedotwho.rsm.module.impl.dungeon.puzzle.TicTacToe;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.NumberSetting;
import com.ricedotwho.rsm.utils.RotationUtils;
import java.math.BigDecimal;
import net.minecraft.block.ButtonBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.entity.EntityPose;

@SubModuleInfo(name = "Tic Tac Toe", alwaysDisabled = false)
public class AutoTTT extends TicTacToe {
   private final BooleanSetting auto = new BooleanSetting("Auto", false);
   private final NumberSetting range = new NumberSetting("Range", 1.0, 6.0, 4.5, 0.1);
   private final NumberSetting cooldown = new NumberSetting("Cooldown", 100.0, 1000.0, 500.0, 25.0);
   private long nextClick = 0L;

   public AutoTTT(Puzzles puzzles) {
      super(puzzles);
      this.registerProperty(new Setting[]{this.auto, this.range, this.cooldown});
   }

   public void reset() {
      super.reset();
      this.nextClick = 0L;
   }

   protected void postSolve() {
      if (this.getBestMove() != null && (Boolean)this.auto.getValue() && System.currentTimeMillis() >= this.nextClick) {
         Vec3d eyePos = mc.player
            .getEntityPos()
            .add(0.0, mc.player.getLastPlayerInput().sneak() ? 1.54F : MinecraftClient.getInstance().player.getEyeHeight(EntityPose.STANDING), 0.0);
         BlockPos best = this.getBestMove();
         double dist = eyePos.squaredDistanceTo(best.getX(), best.getY(), best.getZ());
         double range = ((BigDecimal)this.getRange().getValue()).doubleValue();
         if (!(dist > range * range)) {
            this.clickButton(best, eyePos);
         }
      }
   }

   private void clickButton(BlockPos pos, Vec3d eyePos) {
      BlockState blockState = mc.world.getBlockState(pos);
      if (blockState.getBlock() instanceof ButtonBlock) {
         Box blockAABB = blockState.getOutlineShape(mc.world, pos).getBoundingBox();
         Vec3d center = new Vec3d(
            (blockAABB.minX + blockAABB.maxX) * 0.5 + pos.getX(),
            (blockAABB.minY + blockAABB.maxY) * 0.5 + pos.getY(),
            (blockAABB.minZ + blockAABB.maxZ) * 0.5 + pos.getZ()
         );
         BlockHitResult result = RotationUtils.collisionRayTrace(pos, blockAABB, eyePos, center);
         if (result != null) {
            PacketOrderManager.register(
               PacketOrderManager.STATE.ITEM_USE,
               () -> SwapManager.sendBlockC08(result.getPos(), result.getSide(), !mc.player.getLastPlayerInput().sneak(), true)
            );
            this.nextClick = System.currentTimeMillis() + ((BigDecimal)this.cooldown.getValue()).longValue();
         }
      }
   }

   public BooleanSetting getAuto() {
      return this.auto;
   }

   public NumberSetting getRange() {
      return this.range;
   }

   public NumberSetting getCooldown() {
      return this.cooldown;
   }

   public long getNextClick() {
      return this.nextClick;
   }
}
