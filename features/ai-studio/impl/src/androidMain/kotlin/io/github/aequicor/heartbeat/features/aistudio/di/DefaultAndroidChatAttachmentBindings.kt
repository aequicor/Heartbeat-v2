package io.github.aequicor.heartbeat.features.aistudio.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aistudio.data.datasource.ChatAttachmentDataSourceImpl
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatAttachmentDataSource

@BindingContainer
@ContributesTo(AppScope::class)
public interface DefaultAndroidChatAttachmentBindings {

    public companion object {

        @Provides
        internal fun provideChatAttachmentDataSource(
            dataSource: ChatAttachmentDataSourceImpl,
        ): ChatAttachmentDataSource = dataSource
    }
}
