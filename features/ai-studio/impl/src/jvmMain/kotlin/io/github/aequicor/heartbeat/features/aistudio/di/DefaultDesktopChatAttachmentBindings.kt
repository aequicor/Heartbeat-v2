package io.github.aequicor.heartbeat.features.aistudio.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import io.github.aequicor.heartbeat.di.AppScope
import io.github.aequicor.heartbeat.features.aistudio.data.datasource.ChatAttachmentDataSourceImpl
import io.github.aequicor.heartbeat.features.aistudio.data.datasource.PromptReferenceDataSourceImpl
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.ChatAttachmentDataSource
import io.github.aequicor.heartbeat.features.aistudio.domain.datasource.PromptReferenceDataSource

@BindingContainer
@ContributesTo(AppScope::class)
public interface DefaultDesktopChatAttachmentBindings {

    public companion object {

        @Provides
        internal fun provideChatAttachmentDataSource(
            dataSource: ChatAttachmentDataSourceImpl,
        ): ChatAttachmentDataSource = dataSource

        @Provides
        internal fun providePromptReferenceDataSource(dataSource: PromptReferenceDataSourceImpl): PromptReferenceDataSource = dataSource
    }
}
