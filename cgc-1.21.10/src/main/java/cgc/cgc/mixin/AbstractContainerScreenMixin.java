package cgc.cgc.mixin;

import cgc.cgc.module.impl.dungeon.TerminalSolver;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class AbstractContainerScreenMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void cgc$renderTerminalSolverOverlay(GuiGraphicsExtractor gfx, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
		TerminalSolver.renderTerminalOverlay((AbstractContainerScreen<?>) (Object) this, gfx);
	}
}
