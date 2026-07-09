package cgc.cgc.module.impl.dungeon.autoc

import cgc.cgc.module.impl.dungeon.autoc.nodes.BreakNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.BonzoNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.CommandNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.CrouchNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.EdgeNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.EtherwarpNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.InteractNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.JumpNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.LeapNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.LookNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.RecordNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.StopNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.StrafeNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.UseNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.WalkNode
import cgc.cgc.module.impl.dungeon.autoc.nodes.WarpNode
import net.minecraft.client.player.LocalPlayer
import java.util.Locale

enum class AutoCNodeType(
	val commandName: String,
	val usage: String,
	private val factory: (LocalPlayer, String) -> AutoCNode?
) {
	LOOK("look", "/ac add look", { player, _ -> LookNode.supply(player) }),
	WALK("walk", "/ac add walk", WalkNode::supply),
	STRAFE("strafe", "/ac add strafe <W|A|S|D>", StrafeNode::supply),
	ETHERWARP("etherwarp", "/ac add etherwarp", EtherwarpNode::supply),
	WARP("warp", "/ac add warp", WarpNode::supply),
	INTERACT("interact", "/ac add interact <true|false>", InteractNode::supply),
	USE("use", "/ac add use", UseNode::supply),
	BONZO("bonzo", "/ac add bonzo", BonzoNode::supply),
	CROUCH("crouch", "/ac add crouch <seconds>", CrouchNode::supply),
	COMMAND("command", "/ac add command <command>", CommandNode::supply),
	JUMP("jump", "/ac add jump", JumpNode::supply),
	EDGE("edge", "/ac add edge", EdgeNode::supply),
	LEAP("leap", "/ac add leap <A|Archer|M|Mage|B|Berserk|T|Tank|H|Healer>", LeapNode::supply),
	STOP("stop", "/ac add stop [n<node>...]", StopNode::supply),
	RECORD("record", "/ac add record <seconds>", RecordNode::supply),
	BREAK("break", "/ac add break <true|false> <seconds>", BreakNode::supply);

	fun supply(player: LocalPlayer, args: String): AutoCNode? =
		factory(player, args)

	companion object {
		fun byName(name: String): AutoCNodeType? =
			entries.firstOrNull { it.commandName.equals(name, ignoreCase = true) || it.name.equals(name, ignoreCase = true) }

		fun commandNames(): Array<String> =
			entries.map { it.commandName.lowercase(Locale.ROOT) }.toTypedArray()
	}
}
