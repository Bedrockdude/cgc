package com.ricedotwho.rsa.module.impl.player;

import com.ricedotwho.rsm.RSM;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import com.ricedotwho.rsm.utils.ItemUtils;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Block;
import net.minecraft.block.AbstractCauldronBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.SkullBlock;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.client.network.ClientPlayerEntity;

@ModuleInfo(aliases = "Cancel Interact", id = "CancelInteract", category = Category.PLAYER)
public class CancelInteract extends Module {
   private final BooleanSetting abilityOnly = new BooleanSetting("Ability Only", false);
   private static final List<Class<?>> WHITELIST = List.of(LeverBlock.class, SkullBlock.class, AbstractCauldronBlock.class, ChestBlock.class);
   private static final List<TagKey<Block>> WHITELIST_TAGS = List.of(BlockTags.BUTTONS, BlockTags.COPPER_CHESTS);
   private static final List<Class<?>> BLACKLIST = List.of(HopperBlock.class, CraftingTableBlock.class);
   private static final List<TagKey<Block>> BLACKLIST_TAGS = List.of(BlockTags.WALLS, BlockTags.FENCES, BlockTags.DIRT);

   public CancelInteract() {
      this.registerProperty(new Setting[]{this.abilityOnly});
   }

   public static boolean shouldCancelInteract(BlockHitResult hit, ClientPlayerEntity player, ItemStack item) {
      CancelInteract module = (CancelInteract)RSM.getModule(CancelInteract.class);
      if (module.isEnabled() && Location.isInSkyblock()) {
         BlockState state = player.getEntityWorld().getBlockState(hit.getBlockPos());
         if (WHITELIST.stream().anyMatch(c -> c.isInstance(state.getBlock())) || WHITELIST_TAGS.stream().anyMatch(state::isIn)) {
            return false;
         } else {
            return "ENDER_PEARL".equals(ItemUtils.getID(item))
               ? true
               : (!(Boolean)module.getAbilityOnly().getValue() || ItemUtils.isAbilityItem(mc.player.getInventory().getSelectedStack()))
                  && (BLACKLIST_TAGS.stream().anyMatch(state::isIn) || BLACKLIST.stream().anyMatch(c -> c.isInstance(state.getBlock())));
         }
      } else {
         return false;
      }
   }

   public BooleanSetting getAbilityOnly() {
      return this.abilityOnly;
   }
}
