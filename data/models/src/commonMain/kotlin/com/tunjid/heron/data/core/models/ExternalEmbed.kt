/*
 *    Copyright 2024 Adetunji Dahunsi
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.tunjid.heron.data.core.models

import com.tunjid.heron.data.core.types.GenericUri
import com.tunjid.heron.data.core.types.ImageUri
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ExternalEmbed(
    val uri: GenericUri,
    val title: String,
    val description: String,
    val thumb: ImageUri?,
    val readingTime: Long? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
) : Embed

/**
 * Whether this embed is a GIF.
 *
 * `app.bsky.embed.external` has no GIF variant: clients post a GIF as an external card whose
 * [uri] points straight at the animated file, and read that intent back off the URL. Blobs are no
 * help here — the image CDN re-encodes them to a still frame — so the URL is the GIF.
 */
val ExternalEmbed.isGif: Boolean
    get() = uri.uri
        .substringBefore('?')
        .endsWith(GifFormat, ignoreCase = true)

/**
 * The alt text a GIF embed carries in its [description], stripped of the prefix that marks whether
 * an author wrote it or it was defaulted from the GIF's own title.
 */
val ExternalEmbed.gifAltText: String
    get() = when {
        description.startsWith(UserAltTextPrefix) ->
            description.removePrefix(UserAltTextPrefix)
        description.startsWith(DefaultAltTextPrefix) ->
            description.removePrefix(DefaultAltTextPrefix)
        else -> description
    }

/**
 * The [ExternalEmbed.description] to write for a GIF, in the shape the Bluesky app writes and
 * parses: author supplied alt text is prefixed `Alt: `, and a description defaulted from the GIF's
 * own [title] is prefixed `ALT: `, so readers can tell the two apart.
 */
fun gifDescription(
    title: String,
    altText: String?,
): String = when (val alt = altText?.trim()) {
    null, "" -> DefaultAltTextPrefix + title
    else -> UserAltTextPrefix + alt
}

private const val GifFormat = ".gif"
private const val UserAltTextPrefix = "Alt: "
private const val DefaultAltTextPrefix = "ALT: "
