package com.noorsalah.app

/** Names and generated Android resources for the bundled, openly licensed adhan recordings. */
object AdhanAudioCatalog {
    const val MADINAH = "أذان المدينة – المسجد النبوي"
    const val MALMO_FAJR = "أذان الفجر – مسجد مالمو"

    val bundledChoices = listOf(MADINAH, MALMO_FAJR)

    fun isBundled(selectedAudio: String): Boolean =
        bundledChoices.any { selectedAudio == it }

    fun resourceId(selectedAudio: String): Int? = when (selectedAudio) {
        MADINAH -> R.raw.adhan_madinah
        MALMO_FAJR -> R.raw.adhan_malmo_fajr
        else -> null
    }
}
