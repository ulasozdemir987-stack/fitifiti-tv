package com.fitifiti.tv

import com.fitifiti.tv.data.catalog.Catalog
import com.fitifiti.tv.data.xtream.Category
import com.fitifiti.tv.data.xtream.Movie
import com.fitifiti.tv.data.xtream.Series
import com.fitifiti.tv.data.xtream.Variant
import com.fitifiti.tv.domain.*
import com.fitifiti.tv.ui.components.channelMonogram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Siteden taşınan metin/katalog mantığı. Her işlevi farklı girdilerle çalıştırır: Java'nın regex sözdizimi
 * JavaScript'ten farklı, hatalı bir kalıp ancak çalışınca (TV'de, ana sayfa açılırken) patlıyordu.
 */
class DomainTest {
    private val titles = listOf(
        "Ucuz Roman - Pulp Fiction (1994)", "Upgrade - Yükseltme", "GIBI (2021)", "Loki [4K HDR]", "Dune: Part Two - Çöl Gezegeni 2 |4K HDR]",
        "Yolda Kaldım [TR SUB]", "LEGO ONE PIECE", "UNABOMBER", "Esaretin Bedeli【1994】", "", "   ", "-", "[TR DUBLAJ]", "Halef - 2. Bölüm",
    )

    @Test fun formatFunctions() {
        for (t in titles) {
            displayTitle(t); splitTitle(t); cardTitle(t); fixShouting(t); foldTr(t); hasTr(t)
            episodeName("$t - S01E02 - Ad", t); episodeName(t, "Halef", "2")
            normalizeTitleKey(t); variantLabel(t, "4K FİLMLER"); variantLabel(t, "TR DUBLAJ")
            titleCaseTr(t); cleanProgrammeTitle(t); programmeHeadline(t); cleanChannelName(t); channelMonogram(t)
        }
        assertEquals("Ucuz Roman", cardTitle("Ucuz Roman - Pulp Fiction (1994)"))
        assertEquals("Esaretin Bedeli", displayTitle("Esaretin Bedeli【1994】"))
        listOf("01:53:34", "6814", "113 min", "45 dk", "", null, "abc").forEach { formatDuration(it); formatDuration(it, "minutes") }
    }

    @Test fun liveFormat() {
        val m = parseMatch("SUPER LIG (26-27) 2. HAFTA TRABZONSPOR - BASAKSEHIR - BANT -")
        assertTrue(m != null)
        listOf("UK: SPORT", "Haber", "", "NBA: LAKERS - CELTICS").forEach { parseMatch(it); programmeHeadline(it) }
    }

    @Test fun categories() {
        listOf("NETFLIX DİZİLERİ", "DiSNEY PLUS DiZiLERi", "KORKU & PSiKOLOJiK", "FR: Séries", "AKSIYON [DE]", "B*** CONNECT", "4K FİLMLER", "", "IMDB TOP 250")
            .forEach { categoryStyle(it); categoryLabel(it); categoryRank(it) }
        assertEquals("Netflix", categoryStyle("NETFLIX DİZİLERİ").label)
    }

    @Test fun dedupeAndSearchAndRanking() {
        val movies = titles.mapIndexed { i, t -> Movie(i + 1, t.ifBlank { "Film $i" }, year = "2021", genre = "Aksiyon, Dram", rating = 7.0 + i % 3, added = i.toLong()) } +
            Movie(100, "Ucuz Roman [4K]", year = "2021", ext = "mkv")
        val items = movies.map { DedupeItem(it, it.name, it.year ?: "", it.rating, Variant(it.id, it.ext, variantLabel(it.name).ifEmpty { "Standart" })) }
        val kept = dedupe(items) { m, v -> m.copy(variants = v) }
        assertTrue(kept.size < movies.size)
        val series = listOf(Series(1, "Halef", year = "2024", genre = "Dram"), Series(2, "Gibi", year = "2021", genre = "Komedi"))
        val idx = SearchIndex(kept, series)
        listOf("ucuz", "gıbı", "halef", "lokı", "x", "").forEach { idx.search(it) }
        assertTrue(idx.search("ucuz roman").movies.isNotEmpty())
        val r = Ranking(Catalog(kept, series, emptyList(), listOf(Category("1", "Aksiyon")), emptyList(), emptyList(), 1))
        r.genreRows("movie"); r.genreRows("series"); r.featuredMovies; r.topSeries
    }
}
