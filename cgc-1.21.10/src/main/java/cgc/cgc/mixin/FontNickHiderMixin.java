package cgc.cgc.mixin;

import cgc.cgc.module.impl.render.opsec.NickHider;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Font.class)
public class FontNickHiderMixin {
	@ModifyVariable(method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;", at = @At("HEAD"), argsOnly = true)
	private String cgc$modifyPreparedString(String text) {
		return NickHider.modifyString(text);
	}

	@ModifyVariable(method = "prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;", at = @At("HEAD"), argsOnly = true)
	private FormattedCharSequence cgc$modifyPreparedSequence(FormattedCharSequence text) {
		return NickHider.modifyCharSeq(text);
	}

	@ModifyVariable(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), argsOnly = true)
	private String cgc$modifyWidthString(String text) {
		return NickHider.modifyString(text);
	}

	@ModifyVariable(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("HEAD"), argsOnly = true)
	private FormattedText cgc$modifyWidthComponent(FormattedText text) {
		if (text instanceof Component component) {
			Component modified = NickHider.modifyComponent(component);
			return modified == null ? text : modified;
		}
		return text;
	}

	@ModifyVariable(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), argsOnly = true)
	private FormattedCharSequence cgc$modifyWidthSequence(FormattedCharSequence text) {
		return NickHider.modifyCharSeq(text);
	}
}
