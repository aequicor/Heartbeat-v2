package io.github.aequicor.heartbeat.features.acp.data

import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.EmbeddedResourceResource
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionEvent
import io.github.aequicor.heartbeat.features.aiengine.api.session.SessionEvent.PromptFile
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

internal fun SessionEvent.SendPrompt.toAcpPromptContent(imagePrompts: Boolean): List<ContentBlock> = buildList {
    val descriptions = promptFiles.mapIndexedNotNull { index, file ->
        val resource = file as? PromptFile.Resource ?: return@mapIndexedNotNull null
        val description = resource.description?.takeIf(String::isNotBlank) ?: return@mapIndexedNotNull null
        mapOf("position" to attachments.size + index + 1, "filename" to resource.name, "description" to description)
    }
    val prompt = if (descriptions.isEmpty()) {
        text
    } else {
        val options = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            indent = 2
            indicatorIndent = 2
            indentWithIndicator = true
        }
        val yaml = Yaml(options).dump(mapOf("attachments" to descriptions)).removeSuffix("\n")
        if (text.isBlank()) "---\n$yaml" else "$text\n\n---\n$yaml"
    }
    if (prompt.isNotBlank()) add(ContentBlock.Text(prompt))
    attachments.forEach { add(ContentBlock.Text(it)) }
    promptFiles.forEach { add(it.toAcpBlock(imagePrompts)) }
}

private fun PromptFile.toAcpBlock(imagePrompts: Boolean): ContentBlock = when (this) {
    is PromptFile.Text -> ContentBlock.Text(content)
    is PromptFile.Image -> ContentBlock.Image(base64, mimeType)
    is PromptFile.Resource -> {
        val payload = content
        val type = mimeType
        if (imagePrompts && payload is PromptFile.Resource.Content.Binary && type?.startsWith("image/", ignoreCase = true) == true) {
            ContentBlock.Image(payload.base64, type)
        } else {
            ContentBlock.Resource(
                when (payload) {
                    is PromptFile.Resource.Content.Text ->
                        EmbeddedResourceResource.TextResourceContents(text = payload.value, uri = uri, mimeType = type)

                    is PromptFile.Resource.Content.Binary ->
                        EmbeddedResourceResource.BlobResourceContents(blob = payload.base64, uri = uri, mimeType = type)
                },
            )
        }
    }
    is PromptFile.ResourceLink -> ContentBlock.ResourceLink(
        name = name,
        uri = uri,
        title = title,
        mimeType = mimeType,
        size = sizeBytes,
        description = description,
    )
}
