package com.example.data.model

object CatalogData {
    val sampleStreams = listOf(
        StreamOption(
            resolution = 1080,
            title = "1080P Full HD",
            codec = "h264",
            isDash = false,
            directUrl = "https://archive.org/download/BigBuckBunny_124/Content/big_buck_bunny_720p_surround.mp4",
            sizeBytes = 61_878_609L,
            durationSeconds = 596
        ),
        StreamOption(
            resolution = 720,
            title = "720P HD (Data Saver)",
            codec = "h264",
            isDash = false,
            directUrl = "https://archive.org/download/Tears-of-Steel/tears_of_steel_720p.mp4",
            sizeBytes = 76_435_802L,
            durationSeconds = 734
        ),
        StreamOption(
            resolution = 480,
            title = "480P SD (Ultra Data Saver)",
            codec = "h264",
            isDash = false,
            directUrl = "https://archive.org/download/ElephantsDream/ed_1024_512kb.mp4",
            sizeBytes = 47_065_346L,
            durationSeconds = 653
        )
    )

    val builtInMediaList: List<MediaItem> = listOf(
        MediaItem(
            id = "sub_avengers_endgame",
            title = "Avengers: Endgame",
            coverUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=800&q=80",
            description = "After the devastating events of Infinity War, the universe is in ruins. With the help of remaining allies, the Avengers assemble once more to reverse Thanos' actions.",
            subjectType = 1,
            score = 8.9,
            releaseDate = "2019",
            genre = "Action • Sci-Fi • Adventure",
            duration = 181,
            country = "USA"
        ),
        MediaItem(
            id = "sub_interstellar",
            title = "Interstellar",
            coverUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=800&q=80",
            description = "A team of explorers travel through a wormhole in space in an attempt to ensure humanity's survival as Earth faces catastrophic famine.",
            subjectType = 1,
            score = 8.7,
            releaseDate = "2014",
            genre = "Sci-Fi • Adventure • Drama",
            duration = 169,
            country = "USA"
        ),
        MediaItem(
            id = "sub_spider_verse",
            title = "Spider-Man: Across the Spider-Verse",
            coverUrl = "https://images.unsplash.com/photo-1635805737707-575885ab0820?w=800&q=80",
            description = "Miles Morales catapults across the Multiverse, where he encounters a team of Spider-People charged with protecting its very existence.",
            subjectType = 1,
            score = 8.8,
            releaseDate = "2023",
            genre = "Animation • Action • Sci-Fi",
            duration = 140,
            country = "USA"
        ),
        MediaItem(
            id = "sub_inception",
            title = "Inception",
            coverUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=800&q=80",
            description = "A thief who steals corporate secrets through the use of dream-sharing technology is given the inverse task of planting an idea into the mind of a C.E.O.",
            subjectType = 1,
            score = 8.8,
            releaseDate = "2010",
            genre = "Action • Sci-Fi • Thriller",
            duration = 148,
            country = "USA"
        ),
        MediaItem(
            id = "sub_dune_2",
            title = "Dune: Part Two",
            coverUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=800&q=80",
            description = "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family.",
            subjectType = 1,
            score = 8.6,
            releaseDate = "2024",
            genre = "Action • Adventure • Drama",
            duration = 166,
            country = "USA"
        ),
        MediaItem(
            id = "sub_oppenheimer",
            title = "Oppenheimer",
            coverUrl = "https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?w=800&q=80",
            description = "The story of American scientist J. Robert Oppenheimer and his role in the development of the atomic bomb.",
            subjectType = 1,
            score = 8.9,
            releaseDate = "2023",
            genre = "Biography • Drama • History",
            duration = 180,
            country = "USA"
        ),
        MediaItem(
            id = "sub_stranger_things",
            title = "Stranger Things",
            coverUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=800&q=80",
            description = "When a young boy vanishes, a small town uncovers a mystery involving secret experiments, terrifying supernatural forces and one strange little girl.",
            subjectType = 2,
            score = 8.7,
            releaseDate = "2016-2025",
            genre = "Drama • Fantasy • Horror",
            duration = 50,
            country = "USA"
        ),
        MediaItem(
            id = "sub_breaking_bad",
            title = "Breaking Bad",
            coverUrl = "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=800&q=80",
            description = "A chemistry teacher diagnosed with inoperable lung cancer turns to manufacturing and selling methamphetamine with a former student.",
            subjectType = 2,
            score = 9.5,
            releaseDate = "2008-2013",
            genre = "Crime • Drama • Thriller",
            duration = 49,
            country = "USA"
        ),
        MediaItem(
            id = "sub_the_last_of_us",
            title = "The Last of Us",
            coverUrl = "https://images.unsplash.com/photo-1508739773434-c26b3d09e071?w=800&q=80",
            description = "After a global pandemic destroys civilization, a hardened survivor takes charge of a 14-year-old girl who may be humanity's last hope.",
            subjectType = 2,
            score = 8.8,
            releaseDate = "2023",
            genre = "Action • Adventure • Drama",
            duration = 60,
            country = "USA"
        ),
        MediaItem(
            id = "sub_cyberpunk_edgerunners",
            title = "Cyberpunk: Edgerunners",
            coverUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800&q=80",
            description = "A street kid trying to survive in a technology and body modification-obsessed city of the future stays alive by becoming an edgerunner.",
            subjectType = 2,
            score = 8.4,
            releaseDate = "2022",
            genre = "Animation • Action • Sci-Fi",
            duration = 24,
            country = "Japan"
        ),
        MediaItem(
            id = "sub_attack_on_titan",
            title = "Attack on Titan",
            coverUrl = "https://images.unsplash.com/photo-1563089145-599997674d42?w=800&q=80",
            description = "After his hometown is destroyed and his mother is killed, young Eren Jaeger vows to cleanse the earth of the giant humanoid Titans.",
            subjectType = 2,
            score = 9.1,
            releaseDate = "2013-2023",
            genre = "Animation • Action • Adventure",
            duration = 24,
            country = "Japan"
        ),
        MediaItem(
            id = "sub_demon_slayer",
            title = "Demon Slayer: Kimetsu no Yaiba",
            coverUrl = "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=800&q=80",
            description = "A family is attacked by demons and only two members survive - Tanjiro and his sister Nezuko, who is turning into a demon herself.",
            subjectType = 2,
            score = 8.6,
            releaseDate = "2019-2024",
            genre = "Animation • Action • Fantasy",
            duration = 24,
            country = "Japan"
        )
    )

    val sampleBanners = listOf(
        BannerItem(
            id = "ban_1",
            title = "Spider-Man: Across the Spider-Verse",
            imageUrl = "https://images.unsplash.com/photo-1635805737707-575885ab0820?w=1200&q=80",
            subjectId = "sub_spider_verse",
            subjectType = 1,
            score = 8.8,
            description = "Miles Morales catapults across the Multiverse with Spider-Gwen and a team of Spider-heroes."
        ),
        BannerItem(
            id = "ban_2",
            title = "Dune: Part Two",
            imageUrl = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1200&q=80",
            subjectId = "sub_dune_2",
            subjectType = 1,
            score = 8.6,
            description = "The mythic journey of Paul Atreides as he unites with Chani and the Fremen on Arrakis."
        ),
        BannerItem(
            id = "ban_3",
            title = "Stranger Things",
            imageUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=1200&q=80",
            subjectId = "sub_stranger_things",
            subjectType = 2,
            score = 8.7,
            description = "Uncover secrets, supernatural forces, and the mysteries of the Upside Down."
        )
    )

    fun createFallbackDetail(item: MediaItem): MediaDetail {
        return MediaDetail(
            id = item.id,
            title = item.title,
            description = item.description.ifEmpty { "Experience this cinematic masterpiece in stunning high definition with immersive multi-channel sound." },
            coverUrl = item.coverUrl,
            backdropUrl = item.coverUrl,
            releaseDate = item.releaseDate.ifEmpty { "2024" },
            score = item.score ?: 8.5,
            genre = item.genre.ifEmpty { "Action • Drama" },
            duration = if (item.duration > 0) item.duration else if (item.subjectType == 1) 124 else 45,
            country = item.country.ifEmpty { "USA" },
            subjectType = item.subjectType,
            dubs = listOf(
                DubEdition(subjectId = item.id, language = "English", title = "${item.title} (Original)"),
                DubEdition(subjectId = item.id, language = "Spanish", title = "${item.title} (Español)"),
                DubEdition(subjectId = item.id, language = "French", title = "${item.title} (Français)")
            )
        )
    }

    fun createFallbackSeasons(subjectId: String, title: String): List<SeasonInfo> {
        val s1Episodes = (1..8).map { ep ->
            EpisodeInfo(
                seasonNumber = 1,
                episodeNumber = ep,
                title = "Episode $ep - ${getSampleEpTitle(ep)}",
                coverUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&q=70",
                duration = 45 + ep * 2
            )
        }
        val s2Episodes = (1..8).map { ep ->
            EpisodeInfo(
                seasonNumber = 2,
                episodeNumber = ep,
                title = "Episode $ep - Season 2 Chapter $ep",
                coverUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&q=70",
                duration = 48 + ep
            )
        }
        return listOf(
            SeasonInfo(seasonNumber = 1, episodeCount = s1Episodes.size, episodes = s1Episodes),
            SeasonInfo(seasonNumber = 2, episodeCount = s2Episodes.size, episodes = s2Episodes)
        )
    }

    private fun getSampleEpTitle(ep: Int): String {
        return when (ep) {
            1 -> "The Beginning"
            2 -> "Echoes of the Past"
            3 -> "Into the Shadow"
            4 -> "Turning Tide"
            5 -> "The Gathering Storm"
            6 -> "Shattered Reality"
            7 -> "Point of No Return"
            8 -> "The Final Confrontation"
            else -> "Chapter $ep"
        }
    }
}
