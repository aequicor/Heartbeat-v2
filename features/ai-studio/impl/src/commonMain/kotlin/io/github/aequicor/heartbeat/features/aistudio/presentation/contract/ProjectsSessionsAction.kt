package io.github.aequicor.heartbeat.features.aistudio.presentation.contract

public sealed interface ProjectsSessionsAction {

    public sealed interface Panel : ProjectsSessionsAction {
        public data object Load : Panel
        public data object ExpandPanel : Panel
        public data object CollapsePanel : Panel
        public data object ExpandSearch : Panel
        public data object CollapseSearch : Panel
        public data class Search(val query: String) : Panel
        public data object LoadMoreProjects : Panel
    }

    public sealed interface Project : ProjectsSessionsAction {
        public data class Rename(val id: String, val title: String) : Project
        public data class Delete(val id: String) : Project
        public data class SetExpanded(val id: String, val isExpanded: Boolean) : Project
        public data class LoadMoreSessions(val projectId: String) : Project
    }

    public sealed interface Session : ProjectsSessionsAction {
        public data class Select(val id: String) : Session
        public data class Rename(val id: String, val title: String) : Session
        public data class Archive(val id: String) : Session
        public data class Delete(val id: String) : Session
        public data class SetExpanded(val id: String, val isExpanded: Boolean) : Session
        public data class LoadMoreChildren(val parentId: String) : Session
    }
}
