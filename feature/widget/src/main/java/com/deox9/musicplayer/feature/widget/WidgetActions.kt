// SPDX-License-Identifier: GPL-3.0-or-later
package com.deox9.musicplayer.feature.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback

private val CommandKey = ActionParameters.Key<String>("command")

/** Binds one [WidgetCommand] to a control. */
fun commandAction(command: WidgetCommand): Action =
    actionRunCallback<WidgetCommandAction>(actionParametersOf(CommandKey to command.name))

/**
 * Runs a transport command off the widget.
 *
 * The command travels as its enum name rather than an ordinal: ordinals are positional,
 * and a widget pinned to a home screen outlives the install that created it, so a
 * reordered enum would silently turn every pinned Next button into a Previous.
 */
class WidgetCommandAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val name = parameters[CommandKey] ?: return
        val command = WidgetCommand.entries.firstOrNull { it.name == name } ?: return
        sendWidgetCommand(context, command)
    }
}

/**
 * Opens the app.
 *
 * The launch intent is resolved from the package manager rather than naming the
 * activity: this module does not depend on `:app`, and hard-coding the class name
 * would be a string that compiles fine and fails at the tap.
 */
class OpenAppAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
