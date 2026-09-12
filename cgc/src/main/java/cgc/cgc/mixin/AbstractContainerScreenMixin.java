package cgc.cgc.mixin;

import cgc.cgc.module.impl.dungeon.TerminalSolver;
import cgc.cgc.module.impl.general.InventoryButtons;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void cgc$renderTerminalSolverOverlay(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		TerminalSolver.renderTerminalOverlay((AbstractContainerScreen<?>) (Object) this, gfx);
		InventoryButtons.renderInventoryButtons((AbstractContainerScreen<?>) (Object) this, gfx, mouseX, mouseY);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void cgc$handleInventoryButtonClick(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
		if (InventoryButtons.handleMouseClicked((AbstractContainerScreen<?>) (Object) this, click, doubled)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
	private void cgc$handleInventoryButtonDrag(MouseButtonEvent click, double dragX, double dragY, CallbackInfoReturnable<Boolean> cir) {
		if (InventoryButtons.handleMouseDragged((AbstractContainerScreen<?>) (Object) this, click)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
	private void cgc$handleInventoryButtonRelease(MouseButtonEvent click, CallbackInfoReturnable<Boolean> cir) {
		if (InventoryButtons.handleMouseReleased((AbstractContainerScreen<?>) (Object) this, click)) {
			cir.setReturnValue(true);
		}
	}
}
