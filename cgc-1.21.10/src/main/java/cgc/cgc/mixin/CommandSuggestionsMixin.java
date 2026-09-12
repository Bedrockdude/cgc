package cgc.cgc.mixin;

import cgc.cgc.client.CgcCommandRegistry;
import cgc.cgc.config.CgcSettings;
import cgc.cgc.module.impl.general.PartyNamesTweaks;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.SuggestionContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(CommandSuggestions.class)
public class CommandSuggestionsMixin {
	@Unique
	private static final Pattern CGC_WHITESPACE_PATTERN = Pattern.compile("(\\s+)");

	@Shadow
	@Final
	EditBox input;

	@Shadow
	@Final
	private boolean commandsOnly;

	@Shadow
	private ParseResults<ClientSuggestionProvider> currentParse;

	@Shadow
	boolean keepSuggestions;

	@Shadow
	private CommandSuggestions.SuggestionsList suggestions;

	@Shadow
	@Final
	Minecraft minecraft;

	@Shadow
	@Final
	private final List<FormattedCharSequence> commandUsage = Lists.newArrayList();

	@Shadow
	@Final
	private boolean onlyShowIfCursorPastError;

	@Shadow
	private CompletableFuture<Suggestions> pendingSuggestions;

	@Shadow
	private int commandUsagePosition;

	@Shadow
	private int commandUsageWidth;

	@Shadow
	@Final
	private Screen screen;

	@Shadow
	@Final
	Font font;

	@Shadow
	private boolean allowSuggestions;

	@Unique
	private boolean cgc$customCommand;

	/**
	 * @author CGC
	 * @reason Use CGC's local Brigadier dispatcher for prefixed chat commands so .lc autocompletes like an RSM command.
	 */
	@Overwrite
	public void updateCommandInfo() {
		String string = this.input.getValue();
		if (this.currentParse != null && !this.currentParse.getReader().getString().equals(string)) {
			this.currentParse = null;
		}

		if (!this.keepSuggestions) {
			this.input.setSuggestion(null);
			this.suggestions = null;
		}

		this.commandUsage.clear();
		StringReader reader = new StringReader(string);
		String prefix = CgcSettings.INSTANCE.getCommandPrefix().getValue();
		boolean custom = !prefix.isBlank() && string.startsWith(prefix);
		boolean command = reader.canRead() && (reader.peek() == '/' || custom);

		if (custom) {
			reader.setCursor(prefix.length());
		} else if (command) {
			reader.skip();
		}

		if (this.minecraft.player == null) {
			return;
		}

		this.cgc$customCommand = custom;
		boolean shouldParse = this.commandsOnly || command;
		int cursor = this.input.getCursorPosition();
		if (shouldParse) {
			CommandDispatcher<ClientSuggestionProvider> commandDispatcher = custom
				? CgcCommandRegistry.getDispatcher()
				: this.minecraft.player.connection.getCommands();
			if (this.currentParse == null) {
				this.currentParse = commandDispatcher.parse(reader, this.minecraft.player.connection.getSuggestionsProvider());
			}

			int minCursor = this.onlyShowIfCursorPastError ? reader.getCursor() : 1;
			if (cursor >= minCursor && (this.suggestions == null || !this.keepSuggestions)) {
				this.pendingSuggestions = commandDispatcher.getCompletionSuggestions(this.currentParse, cursor)
					.thenApply(result -> PartyNamesTweaks.augmentSuggestions(string, result));
				this.pendingSuggestions.thenRun(() -> {
					if (this.pendingSuggestions.isDone()) {
						this.cgc$updateUsageInfo();
					}
				});
			}
		} else {
			String beforeCursor = string.substring(0, cursor);
			int lastWordIndex = cgc$getLastWordIndex(beforeCursor);
			Collection<String> collection = this.minecraft.player.connection.getSuggestionsProvider().getCustomTabSuggestions();
			this.pendingSuggestions = SharedSuggestionProvider.suggest(collection, new SuggestionsBuilder(beforeCursor, lastWordIndex));
		}
	}

	@Unique
	private static int cgc$getLastWordIndex(String string) {
		if (Strings.isNullOrEmpty(string)) {
			return 0;
		}

		int index = 0;
		for (Matcher matcher = CGC_WHITESPACE_PATTERN.matcher(string); matcher.find(); index = matcher.end()) {
		}
		return index;
	}

	@Unique
	private void cgc$updateUsageInfo() {
		boolean showParseException = false;
		if (this.input.getCursorPosition() == this.input.getValue().length()) {
			if (this.pendingSuggestions.join().isEmpty() && !this.currentParse.getExceptions().isEmpty()) {
				int literalErrors = 0;

				for (Map.Entry<CommandNode<ClientSuggestionProvider>, CommandSyntaxException> entry : this.currentParse.getExceptions().entrySet()) {
					CommandSyntaxException exception = entry.getValue();
					if (exception.getType() == CommandSyntaxException.BUILT_IN_EXCEPTIONS.literalIncorrect()) {
						literalErrors++;
					} else {
						this.commandUsage.add(cgc$getExceptionMessage(exception));
					}
				}

				if (literalErrors > 0) {
					this.commandUsage.add(cgc$getExceptionMessage(CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(this.currentParse.getReader())));
				}
			} else if (this.currentParse.getReader().canRead()) {
				showParseException = true;
			}
		}

		this.commandUsagePosition = 0;
		this.commandUsageWidth = this.screen.width;
		if (this.commandUsage.isEmpty() && !this.cgc$fillNodeUsage(ChatFormatting.GRAY) && showParseException) {
			this.commandUsage.add(cgc$getExceptionMessage(Commands.getParseException(this.currentParse)));
		}

		this.suggestions = null;
		if (this.allowSuggestions && this.minecraft.options.autoSuggestions().get()) {
			((CommandSuggestions) (Object) this).showSuggestions(false);
		}
	}

	@Unique
	private static FormattedCharSequence cgc$getExceptionMessage(CommandSyntaxException exception) {
		Component component = ComponentUtils.fromMessage(exception.getRawMessage());
		String context = exception.getContext();
		return context == null
			? component.getVisualOrderText()
			: Component.translatable("command.context.parse_error", component, exception.getCursor(), context).getVisualOrderText();
	}

	@Unique
	private boolean cgc$fillNodeUsage(ChatFormatting formatting) {
		List<FormattedCharSequence> lines = Lists.newArrayList();
		Style style = Style.EMPTY.withColor(formatting);
		int width = 0;

		CommandContextBuilder<ClientSuggestionProvider> contextBuilder = this.currentParse.getContext();
		SuggestionContext<ClientSuggestionProvider> suggestionContext = contextBuilder.findSuggestionContext(this.input.getCursorPosition());
		int startPos = suggestionContext.startPos;
		CommandDispatcher<ClientSuggestionProvider> commandDispatcher = this.cgc$customCommand
			? CgcCommandRegistry.getDispatcher()
			: this.minecraft.player.connection.getCommands();
		Map<CommandNode<ClientSuggestionProvider>, String> usage = commandDispatcher.getSmartUsage(suggestionContext.parent, this.minecraft.player.connection.getSuggestionsProvider());

		for (Map.Entry<CommandNode<ClientSuggestionProvider>, String> entry : usage.entrySet()) {
			if (!(entry.getKey() instanceof LiteralCommandNode)) {
				lines.add(FormattedCharSequence.forward(entry.getValue(), style));
				width = Math.max(width, this.font.width(entry.getValue()));
			}
		}

		if (lines.isEmpty()) {
			return false;
		}

		this.commandUsage.addAll(lines);
		this.commandUsagePosition = Mth.clamp(this.input.getScreenX(startPos), 0, this.input.getScreenX(0) + this.input.getInnerWidth() - width);
		this.commandUsageWidth = width;
		return true;
	}
}
