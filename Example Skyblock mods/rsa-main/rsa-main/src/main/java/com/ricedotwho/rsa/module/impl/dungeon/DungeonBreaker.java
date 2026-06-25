package com.ricedotwho.rsa.module.impl.dungeon;

import com.ricedotwho.rsm.RSM;
import com.ricedotwho.rsm.component.impl.location.Island;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.utils.ItemUtils;
import java.util.Arrays;
import java.util.List;
import net.minecraft.block.BushBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.RedstoneTorchBlock;
import net.minecraft.block.SkullBlock;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.block.CauldronBlock;
import net.minecraft.registry.tag.TagKey;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@ModuleInfo(aliases = "ZPDB", id = "DungeonBreaker", category = Category.DUNGEONS)
public class DungeonBreaker extends Module {
   private static final List<Block> BLACKLIST = Arrays.asList(
      Blocks.BARRIER,
      Blocks.COMMAND_BLOCK,
      Blocks.IRON_BLOCK,
      Blocks.BEDROCK,
      Blocks.PISTON,
      Blocks.PISTON_HEAD,
      Blocks.MOVING_PISTON,
      Blocks.STICKY_PISTON,
      Blocks.TNT,
      Blocks.END_PORTAL,
      Blocks.END_PORTAL_FRAME,
      Blocks.END_GATEWAY,
      Blocks.NETHER_PORTAL,
      Blocks.CHEST,
      Blocks.ENDER_CHEST,
      Blocks.TRAPPED_CHEST
   );
   private static final List<TagKey<Block>> TAGS = List.of(BlockTags.BUTTONS, BlockTags.COPPER_CHESTS);
   private static final List<Class<?>> CLASSES = List.of(
      LeverBlock.class, RedstoneTorchBlock.class, BushBlock.class, CauldronBlock.class, SkullBlock.class, ChestBlock.class, HopperBlock.class, BlockWithEntity.class
   );
   private static int maxCharges = 20;
   private static int charges = 20;

   public void reset() {
      charges = 20;
   }

   public static void handleDigSpeed(BlockState state, ItemStack held, CallbackInfoReturnable<Float> cir) {
      if (Location.getArea().is(Island.Dungeon)
         && "DUNGEONBREAKER".equals(ItemUtils.getID(held))
         && ((DungeonBreaker)RSM.getModule(DungeonBreaker.class)).isEnabled()) {
         if (canInstantMine(state)) {
            cir.setReturnValue(1500.0F);
         } else {
            cir.setReturnValue(0.0F);
         }
      }
   }

   public static boolean canInstantMine(BlockState state) {
      return !BLACKLIST.contains(state.getBlock())
         && TAGS.stream().noneMatch(state::isIn)
         && CLASSES.stream().noneMatch(c -> c.isInstance(state.getBlock()));
   }
}
