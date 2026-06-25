package com.ricedotwho.rsa.module.impl.dungeon;

import com.ricedotwho.rsm.RSM;
import com.ricedotwho.rsm.component.impl.location.Island;
import com.ricedotwho.rsm.component.impl.location.Location;
import com.ricedotwho.rsm.module.Module;
import com.ricedotwho.rsm.module.api.Category;
import com.ricedotwho.rsm.module.api.ModuleInfo;
import com.ricedotwho.rsm.ui.clickgui.settings.Setting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.BooleanSetting;
import com.ricedotwho.rsm.ui.clickgui.settings.impl.ModeSetting;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.block.Block;
import net.minecraft.block.ButtonBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.WallMountedBlock;
import net.minecraft.util.math.Direction;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.SkullBlock;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BlockFace;

@ModuleInfo(aliases = "Hitboxes", id = "SecretHitboxes", category = Category.DUNGEONS)
public class SecretHitboxes extends Module {
   private final BooleanSetting essence = new BooleanSetting("Essence", false);
   private final ModeSetting buttons = new ModeSetting("Buttons", "Off", List.of("Full", "Flat", "Off"));
   private final BooleanSetting ssButtonsOnly = new BooleanSetting("SS Buttons Only", false);
   private final ModeSetting levers = new ModeSetting("Levers", "Off", List.of("Full", "Half", "1.8", "Off"));
   private final ModeSetting preDevLevers = new ModeSetting("Predev Levers", "Off", List.of("Full", "Half", "1.8", "Off"));
   private static final SecretHitboxes.ShapeData V47_LEVERS = new SecretHitboxes.ShapeData(Block.createCuboidShape(4.0, 0.0, 4.0, 12.0, 10.0, 12.0));
   private static final SecretHitboxes.ShapeData HALF_LEVERS = new SecretHitboxes.ShapeData(Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 10.0, 16.0));
   private static final SecretHitboxes.ShapeData BUTTON;
   private static final SecretHitboxes.ShapeData BUTTON_POWERED;

   public SecretHitboxes() {
      this.registerProperty(new Setting[]{this.essence, this.buttons, this.ssButtonsOnly, this.levers, this.preDevLevers});
   }

   public static VoxelShape getShape(BlockState state, BlockPos pos) {
      SecretHitboxes module = (SecretHitboxes)RSM.getModule(SecretHitboxes.class);
      if (Location.getArea().is(Island.Dungeon) && module != null && module.isEnabled() && mc.world != null) {
         Block block = state.getBlock();
         Objects.requireNonNull(block);

         return switch (block) {
            case SkullBlock ignored when SecretAura.isValidSkull(pos, mc.world) -> module.essence.getValue() ? VoxelShapes.fullCube() : null;
            case LeverBlock ignoredx -> {
               switch (isLamps(pos) ? module.preDevLevers.getValue() : module.levers.getValue()) {
                  case "Full":
                     yield VoxelShapes.fullCube();
                  case "Half":
                     yield HALF_LEVERS.getShape((BlockFace)state.get(WallMountedBlock.FACE), (Direction)state.get(WallMountedBlock.FACING));
                  case "1.8":
                     yield V47_LEVERS.getShape((BlockFace)state.get(WallMountedBlock.FACE), (Direction)state.get(WallMountedBlock.FACING));
                  case null:
                  default:
                     yield null;
               }
            }
            case ButtonBlock ignoredxx -> {
               if (module.buttons.is("Off")) {
                  yield null;
               } else if ((Boolean)module.ssButtonsOnly.getValue() && !isSS(pos)) {
                  yield null;
               } else if (module.buttons.is("Full")) {
                  yield VoxelShapes.fullCube();
               } else {
                  SecretHitboxes.ShapeData data = state.get(ButtonBlock.POWERED) ? BUTTON_POWERED : BUTTON;
                  yield data.getShape((BlockFace)state.get(WallMountedBlock.FACE), (Direction)state.get(WallMountedBlock.FACING));
               }
            }
            default -> null;
         };
      } else {
         return null;
      }
   }

   private static boolean isSS(BlockPos pos) {
      return pos.getX() == 110 && pos.getY() >= 120 && pos.getY() <= 123 && pos.getZ() >= 91 && pos.getZ() <= 95;
   }

   private static boolean isLamps(BlockPos pos) {
      return pos.getX() >= 58 && pos.getX() <= 62 && pos.getY() >= 133 && pos.getY() <= 136 && pos.getZ() == 142;
   }

   public BooleanSetting getEssence() {
      return this.essence;
   }

   public ModeSetting getButtons() {
      return this.buttons;
   }

   public BooleanSetting getSsButtonsOnly() {
      return this.ssButtonsOnly;
   }

   public ModeSetting getLevers() {
      return this.levers;
   }

   public ModeSetting getPreDevLevers() {
      return this.preDevLevers;
   }

   static {
      V47_LEVERS.add(Direction.DOWN, Block.createCuboidShape(4.0, 0.0, 4.0, 12.0, 10.0, 12.0));
      V47_LEVERS.add(Direction.NORTH, Block.createCuboidShape(5.0, 3.0, 10.0, 11.0, 13.0, 16.0));
      V47_LEVERS.add(Direction.SOUTH, Block.createCuboidShape(5.0, 3.0, 0.0, 11.0, 13.0, 6.0));
      V47_LEVERS.add(Direction.EAST, Block.createCuboidShape(0.0, 3.0, 5.0, 6.0, 13.0, 11.0));
      V47_LEVERS.add(Direction.WEST, Block.createCuboidShape(10.0, 3.0, 5.0, 16.0, 13.0, 11.0));
      HALF_LEVERS.add(Direction.DOWN, Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 10.0, 16.0));
      HALF_LEVERS.add(Direction.NORTH, Block.createCuboidShape(0.0, 0.0, 10.0, 16.0, 16.0, 16.0));
      HALF_LEVERS.add(Direction.SOUTH, Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 6.0));
      HALF_LEVERS.add(Direction.EAST, Block.createCuboidShape(0.0, 0.0, 0.0, 6.0, 16.0, 16.0));
      HALF_LEVERS.add(Direction.WEST, Block.createCuboidShape(10.0, 0.0, 0.0, 16.0, 16.0, 16.0));
      double pow = 0.0625;
      BUTTON_POWERED = new SecretHitboxes.ShapeData(
         VoxelShapes.cuboid(0.0, 1.0 - pow, 0.0, 1.0, 1.0, 1.0), VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.0 + pow, 1.0)
      );
      BUTTON_POWERED.add(Direction.EAST, VoxelShapes.cuboid(0.0, 0.0, 0.0, pow, 1.0, 1.0));
      BUTTON_POWERED.add(Direction.WEST, VoxelShapes.cuboid(1.0 - pow, 0.0, 0.0, 1.0, 1.0, 1.0));
      BUTTON_POWERED.add(Direction.SOUTH, VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 1.0, pow));
      BUTTON_POWERED.add(Direction.NORTH, VoxelShapes.cuboid(0.0, 0.0, 1.0 - pow, 1.0, 1.0, 1.0));
      BUTTON_POWERED.add(Direction.UP, VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.0 + pow, 1.0));
      BUTTON_POWERED.add(Direction.DOWN, VoxelShapes.cuboid(0.0, 1.0 - pow, 0.0, 1.0, 1.0, 1.0));
      double unpow = 0.125;
      BUTTON = new SecretHitboxes.ShapeData(
         VoxelShapes.cuboid(0.0, 1.0 - unpow, 0.0, 1.0, 1.0, 1.0), VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.0 + unpow, 1.0)
      );
      BUTTON.add(Direction.EAST, VoxelShapes.cuboid(0.0, 0.0, 0.0, unpow, 1.0, 1.0));
      BUTTON.add(Direction.WEST, VoxelShapes.cuboid(1.0 - unpow, 0.0, 0.0, 1.0, 1.0, 1.0));
      BUTTON.add(Direction.SOUTH, VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 1.0, unpow));
      BUTTON.add(Direction.NORTH, VoxelShapes.cuboid(0.0, 0.0, 1.0 - unpow, 1.0, 1.0, 1.0));
      BUTTON.add(Direction.UP, VoxelShapes.cuboid(0.0, 0.0, 0.0, 1.0, 0.0 + unpow, 1.0));
      BUTTON.add(Direction.DOWN, VoxelShapes.cuboid(0.0, 1.0 - unpow, 0.0, 1.0, 1.0, 1.0));
   }

   private static class ShapeData {
      private final VoxelShape ceil;
      private final VoxelShape floor;
      private final Map<Direction, VoxelShape> directions = new HashMap<>();

      public ShapeData(VoxelShape ceil, VoxelShape floor) {
         this.ceil = ceil;
         this.floor = floor;
      }

      public ShapeData(VoxelShape ceil) {
         this.ceil = ceil;
         this.floor = ceil;
      }

      public void add(Direction dir, VoxelShape shape) {
         this.directions.put(dir, shape);
      }

      public VoxelShape getShape(BlockFace face, Direction direction) {
         return switch (face) {
            case FLOOR -> this.floor;
            case CEILING -> this.ceil;
            default -> (VoxelShape)this.directions.getOrDefault(direction, null);
         };
      }
   }
}
