package cgc.cgc.mixin;

import cgc.cgc.client.CgcCommandRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
	@Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
	private void cgc$handlePrefixedCommand(String message, boolean addToRecentChat, CallbackInfo ci) {
		String normalized = ((ChatScreen) (Object) this).normalizeChatMessage(message);
		if (!CgcCommandRegistry.tryExecutePrefixed(normalized)) {
			return;
		}

		if (addToRecentChat) {
			Minecraft.getInstance().gui.getChat().addRecentChat(normalized);
		}
		ci.cancel();
	}
}
