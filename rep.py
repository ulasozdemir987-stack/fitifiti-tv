import re

# SeriesDetailScreen
with open('app/src/main/java/com/fitifiti/tv/ui/screens/SeriesDetailScreen.kt', 'r', encoding='utf-8') as f:
    text = f.read()

text = text.replace(
    '''MetaRow(listOf(s.year ?: info?.releaseDate?.take(4),
                    (info?.genre ?: s.genre)?.split(',', '/', '&')?.take(3)?.joinToString(", ") { it.trim() },
                    if (seasons.isNotEmpty()) (if (seasons.size == 1) " bölüm" else " sezon") else null,
                    // IMDb puanı varsa TMDB puanı tekrarlanmaz (aşağıdaki puan satırında)
                    if (art.vote > 0 && art.votes >= 25 && critics?.imdb == null) "TMDB " else null))''',
    '''MetaRow(listOf(
                    s.year ?: info?.releaseDate?.take(4),
                    (info?.genre ?: s.genre)?.split(',', '/', '&')?.take(3)?.joinToString(", ") { it.trim() },
                    if (seasons.isNotEmpty()) (if (seasons.size == 1) " bölüm" else " sezon") else null,
                    info?.country?.takeIf { it.isNotBlank() },
                    info?.director?.takeIf { it.isNotBlank() },
                    if ((info?.rating ?: 0.0) > 0.0) "IMDb " else null,
                    if (art.vote > 0 && art.votes >= 25 && critics?.imdb == null && (info?.rating ?: 0.0) == 0.0) "TMDB " else null
                ).mapNotNull { it })'''
)

with open('app/src/main/java/com/fitifiti/tv/ui/screens/SeriesDetailScreen.kt', 'w', encoding='utf-8') as f:
    f.write(text)

# MovieDetailScreen
with open('app/src/main/java/com/fitifiti/tv/ui/screens/MovieDetailScreen.kt', 'r', encoding='utf-8') as f:
    text2 = f.read()

text2 = text2.replace(
    '''MetaRow(listOf(m.year ?: info.releaseDate?.take(4),
                    formatDuration(info.durationSecs ?: m.runtimeMin?.times(60)).ifBlank { null },
                    (info.genre ?: m.genre)?.split(',', '/', '&')?.take(3)?.joinToString(", ") { it.trim() },
                    info.age?.takeIf { it.isNotBlank() && it != "0" }?.let { "+" }?.replace("++", "+"),
                    // IMDb puanı varsa TMDB puanı tekrarlanmaz (aşağıdaki puan satırında)
                    if (art.vote > 0 && art.votes >= 25 && critics?.imdb == null) "TMDB " else null))''',
    '''MetaRow(listOf(
                    m.year ?: info.releaseDate?.take(4),
                    formatDuration(info.durationSecs ?: m.runtimeMin?.times(60)).ifBlank { null },
                    (info.genre ?: m.genre)?.split(',', '/', '&')?.take(3)?.joinToString(", ") { it.trim() },
                    info.age?.takeIf { it.isNotBlank() && it != "0" }?.let { "+" }?.replace("++", "+"),
                    info.country?.takeIf { it.isNotBlank() },
                    info.director?.takeIf { it.isNotBlank() },
                    if ((info.rating ?: 0.0) > 0.0) "IMDb " else null,
                    if (art.vote > 0 && art.votes >= 25 && critics?.imdb == null && (info.rating ?: 0.0) == 0.0) "TMDB " else null
                ).mapNotNull { it })'''
)

with open('app/src/main/java/com/fitifiti/tv/ui/screens/MovieDetailScreen.kt', 'w', encoding='utf-8') as f:
    f.write(text2)
