package io.github.aequicor.heartbeat.designsystem.foundation

/** One scalable type role: size and line height in logical units, weight as an OpenType value. */
public data class HbTypeStyle(
    public val size: Int,
    public val lineHeight: Int,
    public val weight: Int,
)

/** Type roles of the interface. Containers grow with the resolved scale instead of clipping. */
public data class HbTypeScale(
    public val pageTitle: HbTypeStyle,
    public val sectionTitle: HbTypeStyle,
    public val paneTitle: HbTypeStyle,
    public val readingBody: HbTypeStyle,
    public val uiLabel: HbTypeStyle,
    public val caption: HbTypeStyle,
    public val code: HbTypeStyle,
    public val metadata: HbTypeStyle,
)

/** Weights allowed by the contract; a decorative weight is not introduced for basic controls. */
public object HbTypeWeights {
    public const val Regular: Int = 400
    public const val Medium: Int = 500
    public const val SemiBold: Int = 600
}

/** Type scales of the two host classes. Density does not change the scale: the host font does. */
public object HbTypeScales {
    public val Desktop: HbTypeScale = HbTypeScale(
        pageTitle = HbTypeStyle(size = 24, lineHeight = 32, weight = HbTypeWeights.SemiBold),
        sectionTitle = HbTypeStyle(size = 18, lineHeight = 24, weight = HbTypeWeights.SemiBold),
        paneTitle = HbTypeStyle(size = 14, lineHeight = 20, weight = HbTypeWeights.SemiBold),
        readingBody = HbTypeStyle(size = 15, lineHeight = 24, weight = HbTypeWeights.Regular),
        uiLabel = HbTypeStyle(size = 13, lineHeight = 18, weight = HbTypeWeights.Medium),
        caption = HbTypeStyle(size = 12, lineHeight = 16, weight = HbTypeWeights.Regular),
        code = HbTypeStyle(size = 13, lineHeight = 20, weight = HbTypeWeights.Regular),
        metadata = HbTypeStyle(size = 12, lineHeight = 16, weight = HbTypeWeights.Regular),
    )

    public val Touch: HbTypeScale = HbTypeScale(
        pageTitle = HbTypeStyle(size = 24, lineHeight = 32, weight = HbTypeWeights.SemiBold),
        sectionTitle = HbTypeStyle(size = 20, lineHeight = 28, weight = HbTypeWeights.SemiBold),
        paneTitle = HbTypeStyle(size = 16, lineHeight = 22, weight = HbTypeWeights.SemiBold),
        readingBody = HbTypeStyle(size = 16, lineHeight = 26, weight = HbTypeWeights.Regular),
        uiLabel = HbTypeStyle(size = 14, lineHeight = 20, weight = HbTypeWeights.Medium),
        caption = HbTypeStyle(size = 13, lineHeight = 18, weight = HbTypeWeights.Regular),
        code = HbTypeStyle(size = 14, lineHeight = 22, weight = HbTypeWeights.Regular),
        metadata = HbTypeStyle(size = 13, lineHeight = 18, weight = HbTypeWeights.Regular),
    )
}
