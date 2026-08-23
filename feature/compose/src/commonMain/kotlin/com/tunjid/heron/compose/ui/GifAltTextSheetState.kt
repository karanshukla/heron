/*
 *    Copyright 2026 Adetunji Dahunsi
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

package com.tunjid.heron.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.tunjid.heron.data.core.models.ExternalEmbed
import com.tunjid.heron.data.core.models.gifAltText
import com.tunjid.heron.images.AsyncImage
import com.tunjid.heron.images.ImageArgs
import com.tunjid.heron.ui.sheets.BottomSheetScope
import com.tunjid.heron.ui.sheets.BottomSheetScope.Companion.ModalBottomSheet
import com.tunjid.heron.ui.sheets.BottomSheetScope.Companion.rememberBottomSheetState
import com.tunjid.heron.ui.sheets.BottomSheetState

/**
 * Alt text for a GIF, which — unlike uploaded media — is posted as an external card, and so
 * carries its description on the embed rather than on a file.
 */
@Stable
class GifAltTextSheetState(
    scope: BottomSheetScope,
) : BottomSheetState(scope) {
    internal var embed by mutableStateOf<ExternalEmbed?>(null)

    override fun onHidden() {
        embed = null
    }

    fun editAltText(
        embed: ExternalEmbed,
    ) {
        this.embed = embed
        show()
    }

    companion object {
        @Composable
        fun rememberGifAltTextSheetState(
            onAltTextUpdated: (String) -> Unit,
        ): GifAltTextSheetState {
            val state = rememberBottomSheetState {
                GifAltTextSheetState(
                    scope = it,
                )
            }

            GifAltTextBottomSheet(
                state = state,
                onAltTextUpdated = onAltTextUpdated,
            )

            return state
        }
    }
}

@Composable
private fun GifAltTextBottomSheet(
    state: GifAltTextSheetState,
    onAltTextUpdated: (String) -> Unit,
) {
    val embed = state.embed
    if (embed != null) state.ModalBottomSheet {
        AltTextSheetContent(
            initialAltText = embed.gifAltText,
            preview = {
                AsyncImage(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = AltTextPreviewHeight)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    args = remember(embed.uri) {
                        ImageArgs(
                            url = embed.uri.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            shape = MediaUploadItemShape,
                        )
                    },
                )
            },
            onSave = { altText ->
                onAltTextUpdated(altText)
                state.hide()
            },
        )
    }
}
