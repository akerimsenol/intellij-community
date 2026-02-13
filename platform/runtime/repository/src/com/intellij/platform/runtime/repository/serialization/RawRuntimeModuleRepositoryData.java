// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.serialization;

import com.intellij.platform.runtime.repository.RuntimeModuleId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Describes raw data read from the module repository JAR. This class is used in code which parses the module repository, if you need to
 * get information about modules in IDE, use {@link com.intellij.platform.runtime.repository.RuntimeModuleRepository} instead.
 */
public final class RawRuntimeModuleRepositoryData {
  private final Map<RuntimeModuleId, RawRuntimeModuleDescriptor> myDescriptors;
  private final Path myBasePath;
  private final RuntimeModuleId myMainPluginModuleId;

  private RawRuntimeModuleRepositoryData(@NotNull Map<RuntimeModuleId, RawRuntimeModuleDescriptor> descriptors,
                                         @NotNull Path basePath,
                                         @Nullable RuntimeModuleId mainPluginModuleId) {
    myDescriptors = descriptors;
    myBasePath = basePath;
    myMainPluginModuleId = mainPluginModuleId;
  }

  /**
   * @deprecated use {@link RuntimeModuleRepositorySerialization#loadFromJar(Path)} or {@link #create(Map, Path, RuntimeModuleId)} instead
   */
  @Deprecated(forRemoval = true)
  @ApiStatus.Internal
  public RawRuntimeModuleRepositoryData(@NotNull Map<String, RawRuntimeModuleDescriptor> descriptors, @NotNull Path basePath, @Nullable String mainPluginModuleId) {
    myDescriptors = new LinkedHashMap<>(descriptors.size());
    for (Map.Entry<String, RawRuntimeModuleDescriptor> entry : descriptors.entrySet()) {
      myDescriptors.put(RuntimeModuleId.raw(entry.getKey()), entry.getValue());
    }
    myBasePath = basePath;
    myMainPluginModuleId = mainPluginModuleId != null ? RuntimeModuleId.raw(mainPluginModuleId) : null;
  }

  public @Nullable RawRuntimeModuleDescriptor findDescriptor(@NotNull RuntimeModuleId id) {
    return myDescriptors.get(id);
  }

  /**
   * @deprecated use {@link #findDescriptor(RuntimeModuleId)} instead
   */
  @Deprecated(forRemoval = true)
  public @Nullable RawRuntimeModuleDescriptor findDescriptor(@NotNull String id) {
    return myDescriptors.get(RuntimeModuleId.raw(id));
  }

  public @NotNull Path getBasePath() {
    return myBasePath;
  }

  public @NotNull Set<RuntimeModuleId> getAllModuleIds() {
    return myDescriptors.keySet();
  }

  /**
   * @deprecated use {@link #getAllModuleIds()} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull Set<String> getAllIds() {
    return myDescriptors.keySet().stream().map(RuntimeModuleId::getStringId).collect(Collectors.toSet());
  }

  /**
   * Returns ID of the main plugin module for an additional module repository describing a custom plugin. 
   * For the main module repository (describing the IDE distribution) it returns {@code null}. 
   */
  public @Nullable String getMainPluginModuleId() {
    return myMainPluginModuleId != null ? myMainPluginModuleId.getStringId() : null;
  }

  /**
   * Use {@link RuntimeModuleRepositorySerialization#loadFromCompactFile} to create an instance in production code.
   */
  @ApiStatus.Internal
  public static @NotNull RawRuntimeModuleRepositoryData create(
    @NotNull Map<RuntimeModuleId, RawRuntimeModuleDescriptor> descriptors,
    @NotNull Path basePath,
    @Nullable RuntimeModuleId mainPluginModuleId
  ) {
    return new RawRuntimeModuleRepositoryData(descriptors, basePath, mainPluginModuleId);
  }
}
