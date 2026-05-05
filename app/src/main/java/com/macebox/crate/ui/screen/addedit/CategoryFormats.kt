package com.macebox.crate.ui.screen.addedit

import com.macebox.crate.domain.model.Category

/**
 * Mirror of crate/src/utils/categoryFormats.js — keep these in sync with the
 * Nextcloud app so the dropdowns and validation match across clients.
 */
object CategoryFormats {
    data class Group(
        val label: String,
        val formats: List<String>,
    )

    val musicGroups: List<Group> = listOf(
        Group("Vinyl", listOf("Vinyl", "7\" Single", "10\"", "12\" Single", "Picture Disc", "Flexi-disc", "Shellac", "Lathe Cut")),
        Group("Tape", listOf("Cassette", "8-Track", "Reel-to-Reel", "DAT", "DCC", "4-Track Cartridge", "Microcassette")),
        Group("Disc", listOf("CD", "SACD", "CD-R", "SHM-CD", "HDCD", "CDV", "Blu-ray Audio", "DVD-Audio", "LaserDisc", "MiniDisc")),
    )

    val filmGroups: List<Group> = listOf(
        Group("Physical", listOf("Blu-ray", "4K UHD", "3D Blu-ray", "DVD", "HD DVD", "VHS", "LaserDisc", "VCD", "Betamax")),
    )

    val bookGroups: List<Group> = listOf(
        Group("Print", listOf("Hardcover", "Paperback", "Mass Market Paperback", "Trade Paperback", "Graphic Novel", "Comic")),
        Group("Audio", listOf("Audiobook CD", "Audiobook Cassette")),
    )

    val gameGroups: List<Group> = listOf(
        Group("Sony", listOf("PS5", "PS4", "PS3", "PS2", "PS1", "PS Vita", "PSP")),
        Group("Microsoft", listOf("Xbox Series X|S", "Xbox One", "Xbox 360", "Xbox")),
        Group(
            "Nintendo",
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
        Group("Sega", listOf("Dreamcast", "Saturn", "Mega Drive / Genesis", "Master System", "Game Gear", "Sega CD", "Sega 32X")),
        Group("Atari", listOf("Atari 2600", "Atari 5200", "Atari 7800", "Atari Lynx", "Jaguar")),
        Group("SNK", listOf("Neo Geo MVS", "Neo Geo AES", "Neo Geo CD", "Neo Geo Pocket Color")),
    )

    val comicGroups: List<Group> = listOf(
        Group("Single Issues", listOf("Single Issue", "Annual", "Special", "One-Shot", "Mini-Series", "Limited Series")),
        Group("Collected", listOf("Trade Paperback", "Hardcover", "Omnibus", "Graphic Novel", "Compendium")),
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
}
