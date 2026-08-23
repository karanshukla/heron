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

import android.content.ContentResolver
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputContentInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import com.tunjid.heron.data.logging.logcat
import com.tunjid.heron.data.logging.loggableText
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Declares support for `InputConnection#commitContent` on the text fields inside [content], which
 * is what makes Gboard offer its GIF and sticker pickers.
 *
 * The text field itself is untouched: rather than requiring the
 * [androidx.compose.foundation.text.input.TextFieldState] overload of `BasicTextField` — the only
 * one `Modifier.contentReceiver` supports — the platform text input session is intercepted, the
 * [EditorInfo] the field publishes is amended with the image MIME types the app can post, and the
 * [InputConnection] is wrapped to catch what the keyboard commits.
 */
@Composable
actual fun KeyboardMediaReceiver(
    onMediaReceived: (KeyboardMedia) -> Unit,
    content: @Composable () -> Unit,
) {
    val contentResolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    val currentOnMediaReceived by rememberUpdatedState(onMediaReceived)

    val interceptor = remember(
        contentResolver,
        scope,
    ) {
        PlatformTextInputInterceptor { request, nextHandler ->
            nextHandler.startInputMethod(
                PlatformTextInputMethodRequest { editorInfo ->
                    val connection = request.createInputConnection(editorInfo)
                    editorInfo.acceptKeyboardMedia()
                    KeyboardMediaInputConnection(
                        target = connection,
                        scope = scope,
                        contentResolver = contentResolver,
                        onMediaReceived = { currentOnMediaReceived(it) },
                    )
                },
            )
        }
    }

    InterceptPlatformTextInput(
        interceptor = interceptor,
        content = content,
    )
}

/**
 * Adds the postable image MIME types to whatever the text field already declared, leaving the
 * field's own declarations in place.
 */
private fun EditorInfo.acceptKeyboardMedia() {
    val declared = contentMimeTypes?.toList().orEmpty()
    contentMimeTypes = (declared + KeyboardMediaMimeTypes)
        .distinct()
        .toTypedArray()
}

private class KeyboardMediaInputConnection(
    target: InputConnection,
    private val scope: CoroutineScope,
    private val contentResolver: ContentResolver,
    private val onMediaReceived: (KeyboardMedia) -> Unit,
) : InputConnectionWrapper(
    target,
    false,
) {

    override fun commitContent(
        inputContentInfo: InputContentInfo,
        flags: Int,
        opts: Bundle?,
    ): Boolean {
        val mimeType = KeyboardMediaMimeTypes.firstOrNull(inputContentInfo.description::hasMimeType)
            ?: return super.commitContent(inputContentInfo, flags, opts)

        val description = inputContentInfo.description
            .label
            ?.toString()
            ?.takeIf(String::isNotBlank)

        // The keyboard's content URI is only readable while the grant it hands over is held, so it
        // is taken now and released once the bytes have been copied out.
        val holdsGrant =
            (flags and InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0
        if (holdsGrant) try {
            inputContentInfo.requestPermission()
        } catch (e: Exception) {
            logcat { "Keyboard media permission denied: ${e.loggableText()}" }
            return false
        }

        scope.launch {
            try {
                val photo = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(inputContentInfo.contentUri)
                        ?.use(InputStream::readBytes)
                        ?.let { bytes ->
                            cacheKeyboardMedia(
                                bytes = bytes,
                                mimeType = mimeType,
                                altText = description,
                            )
                        }
                }
                if (photo != null) onMediaReceived(
                    KeyboardMedia(
                        photo = photo,
                        description = description,
                        sourceUrl = inputContentInfo.linkUri
                            ?.takeIf { it.scheme == "http" || it.scheme == "https" }
                            ?.toString(),
                    ),
                )
            } catch (e: Exception) {
                logcat { "Error reading keyboard media: ${e.loggableText()}" }
            } finally {
                if (holdsGrant) inputContentInfo.releasePermission()
            }
        }

        return true
    }
}
