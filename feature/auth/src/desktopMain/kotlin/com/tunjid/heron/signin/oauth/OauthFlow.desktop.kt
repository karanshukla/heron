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

package com.tunjid.heron.signin.oauth

import androidx.compose.runtime.Composable
import com.tunjid.heron.data.core.types.GenericUri
import java.awt.Desktop
import java.net.URI

@Composable
actual fun rememberOauthFlowState(onResult: (OauthFlowResult) -> Unit): OauthFlowState =
    LoopbackOauthFlowState

private object LoopbackOauthFlowState : OauthFlowState {

    override val supportsOauth: Boolean = true

    override fun launch(uri: GenericUri) {
        // Auth callback is handled in the data layer via loopback server.
        // This just opens the system browser for the user to authenticate.
        //
        // AWT's Desktop.browse() reaches the browser through GIO/gvfs on Linux. On desktops
        // where that backend isn't loadable, isSupported(BROWSE) is false and browse() throws
        // UnsupportedOperationException, which would otherwise propagate out of the sign in
        // click handler and take down the app. Fall back to the XDG opener in that case, and
        // never throw: failing to open a browser must not crash sign in.
        if (browseWithDesktop(uri = uri)) return
        browseWithXdgOpen(uri = uri)
    }

    private fun browseWithDesktop(
        uri: GenericUri,
    ): Boolean = runCatching {
        if (!Desktop.isDesktopSupported()) return@runCatching false

        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.BROWSE)) return@runCatching false

        desktop.browse(URI(uri.uri))
        true
    }.getOrDefault(false)

    private fun browseWithXdgOpen(
        uri: GenericUri,
    ) {
        runCatching {
            ProcessBuilder(
                XdgOpen,
                uri.uri,
            ).start()
        }
    }
}

private const val XdgOpen = "xdg-open"
