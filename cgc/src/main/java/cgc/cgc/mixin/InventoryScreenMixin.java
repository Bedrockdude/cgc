package cgc.cgc.mixin;

import cgc.cgc.module.impl.general.InventoryButtons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public class InventoryScreenMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
	private void cgc$renderInventoryButtons(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		InventoryButtons.renderInventoryButtons((InventoryScreen) (Object) this, gfx, mouseX, mouseY);
	}
}
