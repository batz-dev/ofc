package com.example.data.model

data class MediaItem(
    val id: String,
    val title: String,
    val coverUrl: String,
    val description: String = "",
    val subjectType: Int = 1, // 1 = Movie, 2 = TV Series, 5 = Anime
    val score: Double? = null,
    val releaseDate: String = "",
    val genre: String = "",
    val duration: Int = 0,
    val country: String = ""
)

data class BannerItem(
    val id: String,
    val title: String,
    val imageUrl: String,
    val subjectId: String,
    val subjectType: Int = 1,
    val score: Double? = null,
    val description: String = ""
)

data class HomeSection(
    val title: String,
    val type: String,
    val items: List<MediaItem>
)

data class HomeData(
    val banners: List<BannerItem>,
    val sections: List<HomeSection>
)

data class DubEdition(
    val subjectId: String,
    val language: String,
    val title: String
)

data class MediaDetail(
    val id: String,
    val title: String,
    val description: String,
    val coverUrl: String,
    val backdropUrl: String,
    val releaseDate: String,
    val score: Double?,
    val genre: String,
    val duration: Int,
    val country: String,
    val subjectType: Int,
    val dubs: List<DubEdition> = emptyList()
)

data class EpisodeInfo(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val coverUrl: String = "",
    val duration: Int = 0
)

data class SeasonInfo(
    val seasonNumber: Int,
    val episodeCount: Int,
    val episodes: List<EpisodeInfo>
)

data class StreamOption(
    val resolution: Int, // 1080, 720, 480, etc.
    val title: String, // "1080P", "720P"
    val codec: String, // "hevc", "h264"
    val isDash: Boolean,
    val mpdUrl: String = "",
    val signCookie: String = "",
    val directUrl: String = "",
    val sizeBytes: Long = 0,
    val durationSeconds: Int = 0
)
