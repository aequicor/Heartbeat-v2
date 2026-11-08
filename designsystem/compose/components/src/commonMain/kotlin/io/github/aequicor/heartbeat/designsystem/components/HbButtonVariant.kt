package io.github.aequicor.heartbeat.designsystem.components

/**
 * Emphasis of an action (HB-DS-015).
 *
 * [HbButtonVariant.Primary] is the dominant local action and uses the documented action pair;
 * [HbButtonVariant.Secondary] is a regular action on a quiet surface with a control border;
 * [HbButtonVariant.Ghost] is a low-emphasis action without a container; [HbButtonVariant.Destructive]
 * marks an irreversible action and is the only variant that uses the error fill. The variants share
 * geometry, busy behavior and focus treatment, so a screen does not have to fake a hierarchy with
 * the primary style.
 */
enum class HbButtonVariant {
    Primary,
    Secondary,
    Ghost,
    Destructive,
}
