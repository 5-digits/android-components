/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.samples.browser

import android.app.Application
import android.content.Intent
import mozilla.components.browser.session.Session
import mozilla.components.feature.addons.update.GlobalAddonDependencyProvider
import mozilla.components.service.glean.Glean
import mozilla.components.support.base.facts.Facts
import mozilla.components.support.base.facts.processor.LogFactProcessor
import mozilla.components.support.base.log.Log
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.base.log.sink.AndroidLogSink
import mozilla.components.support.ktx.android.content.isMainProcess
import mozilla.components.support.webextensions.WebExtensionSupport
import org.mozilla.samples.browser.addons.WebExtensionActionPopupActivity

class SampleApplication : Application() {
    val components by lazy { Components(this) }

    override fun onCreate() {
        super.onCreate()

        Log.addSink(AndroidLogSink())

        if (!isMainProcess()) {
            return
        }

        // IMPORTANT: the following lines initialize the Glean SDK but disable upload
        // of pings. If, for testing purposes, upload is required to be on, change the
        // next line to `uploadEnabled = true`.
        Glean.initialize(applicationContext, uploadEnabled = false)

        Facts.registerProcessor(LogFactProcessor())

        components.engine.warmUp()

        try {
            GlobalAddonDependencyProvider.initialize(
                components.addonManager,
                components.addonUpdater
            )
            WebExtensionSupport.initialize(
                components.engine,
                components.store,
                onNewTabOverride = {
                    _, engineSession, url ->
                        val session = Session(url)
                        components.sessionManager.add(session, true, engineSession)
                        session.id
                },
                onCloseTabOverride = {
                    _, sessionId -> components.tabsUseCases.removeTab(sessionId)
                },
                onSelectTabOverride = {
                    _, sessionId ->
                        val selected = components.sessionManager.findSessionById(sessionId)
                        selected?.let { components.tabsUseCases.selectTab(it) }
                },
                onUpdatePermissionRequest = components.addonUpdater::onUpdatePermissionRequest,
                onActionPopupToggleOverride = {
                    webExtension ->
                        val intent = Intent(this, WebExtensionActionPopupActivity::class.java)
                        intent.putExtra("web_extension_id", webExtension.id)
                        webExtension.getMetadata()?.let {
                            intent.putExtra("web_extension_name", it.name)
                        }
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                }
            )
        } catch (e: UnsupportedOperationException) {
            // Web extension support is only available for engine gecko
            Logger.error("Failed to initialize web extension support", e)
        }
    }
}
