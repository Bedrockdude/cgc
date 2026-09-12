package cgc.cgc.mixin;

import cgc.cgc.module.impl.fixies.CapitalLetterCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EditBox.class)
public class EditBoxMixin {
	@Inject(method = "charTyped", at = @At("TAIL"))
	private void cgc$normalizeChatCommandAfterChar(CharacterEvent input, CallbackInfoReturnable<Boolean> cir) {
		cgc$normalizeChatCommand();
	}

	@Inject(method = "keyPressed", at = @At("TAIL"))
	private void cgc$normalizeChatCommandAfterKey(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
		cgc$normalizeChatCommand();
	}

	private void cgc$normalizeChatCommand() {
		if (!(Minecraft.getInstance().screen instanceof ChatScreen)) {
			return;
		}

		EditBox editBox = (EditBox) (Object) this;
		String value = editBox.getValue();
		String fixed = CapitalLetterCommands.normalizeInput(value);
		if (fixed.equals(value)) {
			return;
		}

		int cursor = editBox.getCursorPosition();
		editBox.setValue(fixed);
		editBox.setCursorPosition(Math.min(cursor, fixed.length()));
	}
}
