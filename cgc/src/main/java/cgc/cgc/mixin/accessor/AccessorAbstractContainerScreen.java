package cgc.cgc.mixin.accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AccessorAbstractContainerScreen {
	@Accessor("hoveredSlot")
	Slot cgc$getHoveredSlot();

	@Accessor("leftPos")
	int cgc$getLeftPos();

	@Accessor("topPos")
	int cgc$getTopPos();
}
