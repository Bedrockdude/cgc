package cgc.cgc.mixin;

import cgc.cgc.client.CgcCommandRegistry;
import cgc.cgc.module.impl.fixies.CapitalLetterCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
	@Shadow
	protected EditBox input;

	@Inject(method = "keyPressed", at = @At("TAIL"))
	private void cgc$normalizeAfterKey(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
		cgc$normalizeInputBox();
	}

	@Inject(method = "insertText", at = @At("TAIL"))
	private void cgc$normalizeAfterInsertText(String text, boolean overwrite, CallbackInfo ci) {
		cgc$normalizeInputBox();
	}

	@Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
	private void cgc$handlePrefixedCommand(String message, boolean addToRecentChat, CallbackInfo ci) {
		String normalized = ((ChatScreen) (Object) this).normalizeChatMessage(message);
		String fixed = CapitalLetterCommands.normalizeInput(normalized);
		if (!fixed.equals(normalized)) {
			if (CgcCommandRegistry.tryExecutePrefixed(fixed)) {
				if (addToRecentChat) {
					Minecraft.getInstance().gui.getChat().addRecentChat(fixed);
				}
			} else {
				cgc$sendChatInput(fixed, addToRecentChat);
			}
			ci.cancel();
			return;
		}

		if (!CgcCommandRegistry.tryExecutePrefixed(fixed)) {
			return;
		}

		if (addToRecentChat) {
			Minecraft.getInstance().gui.getChat().addRecentChat(fixed);
		}
		ci.cancel();
	}

	private void cgc$normalizeInputBox() {
		String value = this.input.getValue();
		String fixed = CapitalLetterCommands.normalizeInput(value);
		if (fixed.equals(value)) {
			return;
		}

		int cursor = this.input.getCursorPosition();
		this.input.setValue(fixed);
		this.input.setCursorPosition(Math.min(cursor, fixed.length()));
	}

	private void cgc$sendChatInput(String message, boolean addToRecentChat) {
		Minecraft minecraft = Minecraft.getInstance();
		if (message.isEmpty() || minecraft.player == null) {
			return;
		}
		if (addToRecentChat) {
			minecraft.gui.getChat().addRecentChat(message);
		}

		if (message.startsWith("/")) {
			minecraft.player.connection.sendCommand(message.substring(1));
		} else {
			minecraft.player.connection.sendChat(message);
		}
	}
}
