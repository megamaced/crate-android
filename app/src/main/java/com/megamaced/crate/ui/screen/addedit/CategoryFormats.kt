package com.megamaced.crate.ui.screen.addedit

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.megamaced.crate.R
import com.megamaced.crate.domain.model.Category
import com.megamaced.crate.util.UiText

/**
 * Mirror of crate/src/utils/categoryFormats.js — keep these in sync with the
 * Nextcloud app so the dropdowns and validation match across clients.
 *
 * The format values themselves are what the server stores, so they are literals
 * either way. What the user sees is the group headings and [labelRes].
 */
object CategoryFormats {
    data class Group(
        @param:StringRes val labelRes: Int,
        val formats: List<String>,
    )

    val musicGroups: List<Group> = listOf(
        Group(
            R.string.format_group_vinyl,
            listOf("Vinyl", "7\" Single", "10\"", "12\" Single", "Picture Disc", "Flexi-disc", "Shellac", "Lathe Cut"),
        ),
        Group(
            R.string.format_group_tape,
            listOf("Cassette", "8-Track", "Reel-to-Reel", "DAT", "DCC", "4-Track Cartridge", "Microcassette"),
        ),
        Group(
            R.string.format_group_disc,
            listOf("CD", "SACD", "CD-R", "SHM-CD", "HDCD", "Blu-ray Audio", "DVD-Audio", "MiniDisc"),
        ),
    )

    val filmGroups: List<Group> = listOf(
        Group(
            R.string.format_group_physical,
            listOf("Blu-ray", "4K UHD", "3D Blu-ray", "DVD", "HD DVD", "VHS", "LaserDisc", "VCD", "Betamax"),
        ),
    )

    val bookGroups: List<Group> = listOf(
        Group(
            R.string.format_group_print,
            listOf("Hardcover", "Paperback", "Mass Market Paperback", "Trade Paperback", "Graphic Novel", "Comic"),
        ),
        Group(R.string.format_group_audio, listOf("Audiobook CD", "Audiobook Cassette")),
    )

    val gameGroups: List<Group> = listOf(
        Group(R.string.format_group_sony, listOf("PS5", "PS4", "PS3", "PS2", "PS1", "PS Vita", "PSP")),
        Group(R.string.format_group_microsoft, listOf("Xbox Series X|S", "Xbox One", "Xbox 360", "Xbox")),
        Group(
            R.string.format_group_nintendo,
            listOf(
                "Switch 2",
                "Switch",
                "Wii U",
                "Wii",
                "GameCube",
                "N64",
                "SNES",
                "NES",
                "3DS",
                "DS",
                "Game Boy Advance",
                "Game Boy Color",
                "Game Boy",
                "Virtual Boy",
            ),
        ),
        Group(
            R.string.format_group_sega,
            listOf("Dreamcast", "Saturn", "Mega Drive / Genesis", "Master System", "Game Gear", "Sega CD", "Sega 32X"),
        ),
        Group(R.string.format_group_atari, listOf("Atari 2600", "Atari 5200", "Atari 7800", "Atari Lynx", "Jaguar")),
        Group(R.string.format_group_snk, listOf("Neo Geo MVS", "Neo Geo AES", "Neo Geo CD", "Neo Geo Pocket Color")),
    )

    val comicGroups: List<Group> = listOf(
        Group(
            R.string.format_group_single_issues,
            listOf("Single Issue", "Annual", "Special", "One-Shot", "Mini-Series", "Limited Series"),
        ),
        Group(R.string.format_group_collected, listOf("Trade Paperback", "Hardcover", "Omnibus", "Graphic Novel", "Compendium")),
    )

    fun groupsFor(category: Category): List<Group> =
        when (category) {
            Category.Music -> musicGroups
            Category.Films -> filmGroups
            Category.Books -> bookGroups
            Category.Games -> gameGroups
            Category.Comics -> comicGroups
        }

    fun forCategory(category: Category): List<String> = groupsFor(category).flatMap { it.formats }

    fun isValid(
        category: Category,
        value: String,
    ): Boolean {
        if (value.isBlank()) return false
        return forCategory(category).any { it.equals(value.trim(), ignoreCase = true) }
    }

    /**
     * The display name of a stored format. The value stored and sent to the
     * server is always the English name; only formats named with ordinary words
     * translate. A platform, model or disc-standard name (PS5, VHS, SACD…) is a
     * product name, and a custom format is the user's own text, so those show
     * as stored. Mirror of formatLabel() in the web app.
     */
    @StringRes
    fun labelRes(format: String): Int? =
        when (format) {
            "Vinyl" -> R.string.format_name_vinyl
            "7\" Single" -> R.string.format_name_7_inch_single
            "12\" Single" -> R.string.format_name_12_inch_single
            "Picture Disc" -> R.string.format_name_picture_disc
            "Flexi-disc" -> R.string.format_name_flexi_disc
            "Shellac" -> R.string.format_name_shellac
            "Lathe Cut" -> R.string.format_name_lathe_cut
            "Cassette" -> R.string.format_name_cassette
            "Reel-to-Reel" -> R.string.format_name_reel_to_reel
            "4-Track Cartridge" -> R.string.format_name_4_track_cartridge
            "Microcassette" -> R.string.format_name_microcassette
            "Hardcover" -> R.string.format_name_hardcover
            "Paperback" -> R.string.format_name_paperback
            "Mass Market Paperback" -> R.string.format_name_mass_market_paperback
            "Trade Paperback" -> R.string.format_name_trade_paperback
            "Graphic Novel" -> R.string.format_name_graphic_novel
            "Comic" -> R.string.format_name_comic
            "Audiobook CD" -> R.string.format_name_audiobook_cd
            "Audiobook Cassette" -> R.string.format_name_audiobook_cassette
            "Single Issue" -> R.string.format_name_single_issue
            "Annual" -> R.string.format_name_annual
            "Special" -> R.string.format_name_special
            "One-Shot" -> R.string.format_name_one_shot
            "Mini-Series" -> R.string.format_name_mini_series
            "Limited Series" -> R.string.format_name_limited_series
            "Omnibus" -> R.string.format_name_omnibus
            "Compendium" -> R.string.format_name_compendium
            else -> null
        }

    /** [labelRes] as text for a ViewModel, or the stored value when it has none. */
    fun label(format: String): UiText = labelRes(format)?.let { UiText.Res(it) } ?: UiText.Raw(format)

    /** [labelRes] looked up with [getString], or the stored value when it has none. */
    fun label(
        format: String,
        getString: (Int) -> String,
    ): String = labelRes(format)?.let(getString) ?: format
}

/** [CategoryFormats.labelRes] resolved, or the stored value when it has none. */
@Composable
@ReadOnlyComposable
fun formatLabel(format: String): String = CategoryFormats.labelRes(format)?.let { stringResource(it) } ?: format
