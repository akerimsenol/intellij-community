// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.modules.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationEntityBuilder
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationEntityId
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationHash

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ScriptCompilationConfigurationEntityImpl(private val dataSource: ScriptCompilationConfigurationEntityData) :
    ScriptCompilationConfigurationEntity, WorkspaceEntityBase(dataSource) {

    private companion object {

        private val connections = listOf<ConnectionId>()

    }

    override val symbolicId: ScriptCompilationConfigurationEntityId = super.symbolicId

    override val data: ByteArray
        get() {
            readField("data")
            return dataSource.data
        }
    override val hash: ScriptCompilationConfigurationHash
        get() {
            readField("hash")
            return dataSource.hash
        }
    override val tag: Int
        get() {
            readField("tag")
            return dataSource.tag
        }

    override val entitySource: EntitySource
        get() {
            readField("entitySource")
            return dataSource.entitySource
        }

    override fun connectionIdList(): List<ConnectionId> {
        return connections
    }


    internal class Builder(result: ScriptCompilationConfigurationEntityData?) :
        ModifiableWorkspaceEntityBase<ScriptCompilationConfigurationEntity, ScriptCompilationConfigurationEntityData>(result),
        ScriptCompilationConfigurationEntityBuilder {
        internal constructor() : this(ScriptCompilationConfigurationEntityData())

        override fun applyToBuilder(builder: MutableEntityStorage) {
            if (this.diff != null) {
                if (existsInBuilder(builder)) {
                    this.diff = builder
                    return
                } else {
                    error("Entity ScriptCompilationConfigurationEntity is already created in a different builder")
                }
            }
            this.diff = builder
            addToBuilder()
            this.id = getEntityData().createEntityId()
// After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
// Builder may switch to snapshot at any moment and lock entity data to modification
            this.currentEntityData = null
// Process linked entities that are connected without a builder
            processLinkedEntities(builder)
            checkInitialization() // TODO uncomment and check failed tests
        }

        private fun checkInitialization() {
            val _diff = diff
            if (!getEntityData().isEntitySourceInitialized()) {
                error("Field WorkspaceEntity#entitySource should be initialized")
            }
            if (!getEntityData().isDataInitialized()) {
                error("Field ScriptCompilationConfigurationEntity#data should be initialized")
            }
            if (!getEntityData().isHashInitialized()) {
                error("Field ScriptCompilationConfigurationEntity#hash should be initialized")
            }
        }

        override fun connectionIdList(): List<ConnectionId> {
            return connections
        }

        // Relabeling code, move information from dataSource to this builder
        override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
            dataSource as ScriptCompilationConfigurationEntity
            if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
            if (this.data != dataSource.data) this.data = dataSource.data
            if (this.hash != dataSource.hash) this.hash = dataSource.hash
            if (this.tag != dataSource.tag) this.tag = dataSource.tag
            updateChildToParentReferences(parents)
        }


        override var entitySource: EntitySource
            get() = getEntityData().entitySource
            set(value) {
                checkModificationAllowed()
                getEntityData(true).entitySource = value
                changedProperty.add("entitySource")

            }
        override var data: ByteArray
            get() = getEntityData().data
            set(value) {
                checkModificationAllowed()
                getEntityData(true).data = value
                changedProperty.add("data")

            }
        override var hash: ScriptCompilationConfigurationHash
            get() = getEntityData().hash
            set(value) {
                checkModificationAllowed()
                getEntityData(true).hash = value
                changedProperty.add("hash")

            }
        override var tag: Int
            get() = getEntityData().tag
            set(value) {
                checkModificationAllowed()
                getEntityData(true).tag = value
                changedProperty.add("tag")
            }

        override fun getEntityClass(): Class<ScriptCompilationConfigurationEntity> = ScriptCompilationConfigurationEntity::class.java
    }

}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ScriptCompilationConfigurationEntityData : WorkspaceEntityData<ScriptCompilationConfigurationEntity>() {
    lateinit var data: ByteArray
    lateinit var hash: ScriptCompilationConfigurationHash
    var tag: Int = 0

    internal fun isDataInitialized(): Boolean = ::data.isInitialized
    internal fun isHashInitialized(): Boolean = ::hash.isInitialized


    override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntityBuilder<ScriptCompilationConfigurationEntity> {
        val modifiable = ScriptCompilationConfigurationEntityImpl.Builder(null)
        modifiable.diff = diff
        modifiable.id = createEntityId()
        return modifiable
    }

    @OptIn(EntityStorageInstrumentationApi::class)
    override fun createEntity(snapshot: EntityStorageInstrumentation): ScriptCompilationConfigurationEntity {
        val entityId = createEntityId()
        return snapshot.initializeEntity(entityId) {
            val entity = ScriptCompilationConfigurationEntityImpl(this)
            entity.snapshot = snapshot
            entity.id = entityId
            entity
        }
    }

    override fun getMetadata(): EntityMetadata {
        return MetadataStorageImpl.getMetadataByTypeFqn("org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationEntity") as EntityMetadata
    }

    override fun getEntityInterface(): Class<out WorkspaceEntity> {
        return ScriptCompilationConfigurationEntity::class.java
    }

    override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
        return ScriptCompilationConfigurationEntity(data, hash, tag, entitySource)
    }

    override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
        val res = mutableListOf<Class<out WorkspaceEntity>>()
        return res
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (this.javaClass != other.javaClass) return false
        other as ScriptCompilationConfigurationEntityData
        if (this.entitySource != other.entitySource) return false
        if (this.data != other.data) return false
        if (this.hash != other.hash) return false
        if (this.tag != other.tag) return false
        return true
    }

    override fun equalsIgnoringEntitySource(other: Any?): Boolean {
        if (other == null) return false
        if (this.javaClass != other.javaClass) return false
        other as ScriptCompilationConfigurationEntityData
        if (this.data != other.data) return false
        if (this.hash != other.hash) return false
        if (this.tag != other.tag) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entitySource.hashCode()
        result = 31 * result + data.hashCode()
        result = 31 * result + hash.hashCode()
        result = 31 * result + tag.hashCode()
        return result
    }

    override fun hashCodeIgnoringEntitySource(): Int {
        var result = javaClass.hashCode()
        result = 31 * result + data.hashCode()
        result = 31 * result + hash.hashCode()
        result = 31 * result + tag.hashCode()
        return result
    }
}
