// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncContributor

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.plugins.gradle.model.ExternalSourceSet
import org.jetbrains.plugins.gradle.model.GradleLightBuild
import org.jetbrains.plugins.gradle.model.GradleLightProject
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext

interface GradleSourceRootSyncContributorExtension {
  suspend fun configureSourceSetModules(
    context: ProjectResolverContext,
    storage: MutableEntityStorage,
    buildModel: GradleLightBuild,
    projectModel: GradleLightProject,
    moduleEntity: ModuleEntityBuilder,
    sourceSet: ExternalSourceSet
  )

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<GradleSourceRootSyncContributorExtension> = ExtensionPointName.create("org.jetbrains.plugins.gradle.sourceRootSyncContributor")
  }
}
