package cgc.cgc.runtime

import cgc.cgc.mixin.accessor.RenderTypeAccessor
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.LayeringTransform
import net.minecraft.client.renderer.rendertype.OutputTarget
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import java.util.Optional

object CgcRenderTypes {
	val linesThroughWalls: RenderType = RenderTypeAccessor.`cgc$create`(
		"cgc_lines_through_walls",
		RenderSetup.builder(throughWallsPipeline("pipeline/cgc_lines_through_walls", RenderPipelines.LINES_TRANSLUCENT))
			.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
			.setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
			.createRenderSetup()
	)

	val filledBoxThroughWalls: RenderType = RenderTypeAccessor.`cgc$create`(
		"cgc_filled_box_through_walls",
		RenderSetup.builder(throughWallsPipeline("pipeline/cgc_filled_box_through_walls", RenderPipelines.DEBUG_FILLED_BOX))
			.sortOnUpload()
			.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
			.createRenderSetup()
	)

	private fun throughWallsPipeline(name: String, base: RenderPipeline): RenderPipeline {
		val baseSnippet = RenderPipeline.Snippet(
			Optional.of(base.vertexShader),
			Optional.of(base.fragmentShader),
			Optional.of(base.shaderDefines),
			Optional.of(base.samplers),
			Optional.of(base.uniforms),
			Optional.of(base.colorTargetState),
			Optional.ofNullable(base.depthStencilState),
			Optional.of(base.polygonMode),
			Optional.of(base.isCull),
			Optional.of(base.vertexFormat),
			Optional.of(base.vertexFormatMode)
		)
		return RenderPipeline.builder(baseSnippet)
			.withLocation(name)
			.withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, false))
			.build()
	}
}
