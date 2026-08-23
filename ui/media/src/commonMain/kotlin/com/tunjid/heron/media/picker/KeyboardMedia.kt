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

package com.tunjid.heron.media.picker

import androidx.compose.runtime.Composable
import com.tunjid.heron.data.files.RestrictedFile
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.write
import kotlin.time.Clock

/**
 * An image the software keyboard committed straight into a text field. On Android these come from
 * Gboard's GIF and sticker pickers; the picker is only offered when the focused field advertises
 * that it accepts image content, which [KeyboardMediaReceiver] is responsible for.
 */
data class KeyboardMedia(
    /**
     * The committed bytes, copied into the app's cache. Keyboards hand over a content URI whose
     * read grant is only valid for the lifetime of the input connection, so the bytes are taken
     * eagerly rather than lazily at post time.
     */
    val photo: RestrictedFile.Media.Photo,
    /**
     * The keyboard's own description of the image, when it supplies one. Gboard sends the GIF's
     * description here, which makes it a reasonable starting point for alt text.
     */
    val description: String?,
    /**
     * A publicly reachable URL for the same image, when the keyboard supplies one. Bluesky renders
     * GIFs as external embeds pointing at a URL, never as image blobs — blobs are re-encoded to
     * still JPEG by the CDN — so this is what keeps a posted GIF animated.
     */
    val sourceUrl: String?,
)

/**
 * Advertises every text field within [content] to the software keyboard as able to receive images,
 * and reports what the keyboard commits to [onMediaReceived].
 *
 * A no-op on platforms whose keyboards cannot commit media.
 */
@Composable
expect fun KeyboardMediaReceiver(
    onMediaReceived: (KeyboardMedia) -> Unit,
    content: @Composable () -> Unit,
)

/**
 * Image formats a keyboard may commit that are also postable. Ordering matters: the first of these
 * the committed content declares is the one it is treated as.
 */
internal val KeyboardMediaMimeTypes = listOf(
    "image/gif",
    "image/webp",
    "image/png",
    "image/jpeg",
)

/**
 * Writes keyboard committed [bytes] of [mimeType] to a file in the app's cache and returns it as a
 * photo that can be attached to a post, seeded with the keyboard's own description as [altText].
 * Throws if the write fails; callers log and drop.
 */
internal suspend fun cacheKeyboardMedia(
    bytes: ByteArray,
    mimeType: String,
    altText: String?,
): RestrictedFile.Media.Photo {
    if (!KeyboardMediaDir.exists()) KeyboardMediaDir.createDirectories(mustCreate = false)

    val epoch = Clock.System.now().toEpochMilliseconds()
    val extension = when (mimeType) {
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/png" -> "png"
        else -> "jpg"
    }
    val file = KeyboardMediaDir / "keyboard-$epoch.$extension"
    file.write(bytes)

    return RestrictedFile.photo(
        file = file,
        altText = altText,
    )
}

private val KeyboardMediaDir = FileKit.cacheDir / "keyboard-media"
