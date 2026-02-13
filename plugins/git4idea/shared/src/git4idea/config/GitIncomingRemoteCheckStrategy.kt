// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import git4idea.i18n.GitBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

/**
 * Defines operation to be used to check incoming commits on remote.
 */
enum class GitIncomingRemoteCheckStrategy(
  textKey: @Nls @PropertyKey(resourceBundle = GitBundle.BUNDLE) String,
  descriptionKey: @Nls @PropertyKey(resourceBundle = GitBundle.BUNDLE) String? = null,
) {
  FETCH("settings.git.incoming.change.strategy.text.fetch",
        "settings.git.incoming.change.strategy.description.fetch"),
  LS_REMOTE("settings.git.incoming.change.strategy.text.lsremote",
            "settings.git.incoming.change.strategy.description.lsremote"),
  NONE("settings.git.incoming.change.strategy.text.none");

  val text: @Nls String = GitBundle.message(textKey)
  val description: @Nls String? = descriptionKey?.let(GitBundle::message)
}
