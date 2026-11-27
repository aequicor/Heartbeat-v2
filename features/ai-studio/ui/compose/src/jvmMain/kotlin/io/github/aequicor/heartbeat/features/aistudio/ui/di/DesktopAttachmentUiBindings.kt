package io.github.aequicor.heartbeat.features.aistudio.ui.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AttachmentFolderOpener
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AttachmentFolderOpenerImpl
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AttachmentSourceInspector
import io.github.aequicor.heartbeat.features.aistudio.ui.components.AttachmentSourceInspectorImpl

@BindingContainer
@ContributesTo(AppScope::class)
public interface DesktopAttachmentUiBindings {

    public companion object {

        @Provides
        internal fun provideAttachmentFolderOpener(impl: AttachmentFolderOpenerImpl): AttachmentFolderOpener = impl

        @Provides
        internal fun provideAttachmentSourceInspector(impl: AttachmentSourceInspectorImpl): AttachmentSourceInspector = impl
    }
}
