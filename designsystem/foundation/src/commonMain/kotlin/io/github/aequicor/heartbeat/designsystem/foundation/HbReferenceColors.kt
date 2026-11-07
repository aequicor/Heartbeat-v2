package io.github.aequicor.heartbeat.designsystem.foundation

/**
 * Reference layer of the token contract (HB-DS-002).
 *
 * The values are named by their place in the brand palette, not by a UI role: a semantic palette
 * resolves a role to exactly one reference value, and a component never reads a reference token
 * directly. Reference tokens are opaque; transparency, when needed, is composed by the consumer.
 */
public object HbReferenceColors {

    public val White: HbColor = HbColor.hex(0xFFFFFF)
    public val Paper: HbColor = HbColor.hex(0xF4F4F5)
    public val Mist: HbColor = HbColor.hex(0xE9E5EF)
    public val Cloud: HbColor = HbColor.hex(0xE3E3E5)
    public val Fog: HbColor = HbColor.hex(0xFFF0ED)
    public val Ink: HbColor = HbColor.hex(0x242426)
    public val InkSoft: HbColor = HbColor.hex(0x66636C)
    public val InkMuted: HbColor = HbColor.hex(0x98949F)
    public val Steel: HbColor = HbColor.hex(0x817B88)
    public val Charcoal: HbColor = HbColor.hex(0x202022)
    public val Graphite: HbColor = HbColor.hex(0x242426)
    public val Slate: HbColor = HbColor.hex(0x28282B)
    public val Basalt: HbColor = HbColor.hex(0x303034)
    public val Coal: HbColor = HbColor.hex(0x2A2A2D)
    public val Snow: HbColor = HbColor.hex(0xEEEEF0)
    public val SnowSoft: HbColor = HbColor.hex(0xB8B4C0)
    public val SnowMuted: HbColor = HbColor.hex(0x8A8790)
    public val SlateLine: HbColor = HbColor.hex(0x424246)
    public val SteelLine: HbColor = HbColor.hex(0x87818F)
    public val Violet: HbColor = HbColor.hex(0x7B6D9A)
    public val VioletInk: HbColor = HbColor.hex(0x6A587C)
    public val Lilac: HbColor = HbColor.hex(0xBAAFD0)
    public val LilacInk: HbColor = HbColor.hex(0xC5B8DC)
    public val Plum: HbColor = HbColor.hex(0x3B3547)
    public val GraphiteAction: HbColor = HbColor.hex(0x353238)
    public val GraphiteActionHover: HbColor = HbColor.hex(0x45404B)
    public val GraphiteActionPressed: HbColor = HbColor.hex(0x29252F)
    public val Pearl: HbColor = HbColor.hex(0xDCD4E8)
    public val PearlHover: HbColor = HbColor.hex(0xE8DFF4)
    public val PearlPressed: HbColor = HbColor.hex(0xC9BED8)
    public val Green: HbColor = HbColor.hex(0x226A46)
    public val GreenWash: HbColor = HbColor.hex(0xE9F6EE)
    public val Amber: HbColor = HbColor.hex(0x805500)
    public val AmberWash: HbColor = HbColor.hex(0xFFF4DC)
    public val Red: HbColor = HbColor.hex(0xB42318)
    public val Blue: HbColor = HbColor.hex(0x245EA4)
    public val BlueWash: HbColor = HbColor.hex(0xEDF4FF)
    public val GreenLight: HbColor = HbColor.hex(0x9AD9B1)
    public val GreenLightWash: HbColor = HbColor.hex(0x223B2C)
    public val AmberLight: HbColor = HbColor.hex(0xF5D18A)
    public val AmberLightWash: HbColor = HbColor.hex(0x40351E)
    public val RedLight: HbColor = HbColor.hex(0xFFB4AB)
    public val RedLightWash: HbColor = HbColor.hex(0x482A2A)
    public val BlueLight: HbColor = HbColor.hex(0xB0CFFF)
    public val BlueLightWash: HbColor = HbColor.hex(0x25344B)
}
