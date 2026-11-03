package com.example.bilibox.bili

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val code: Int = -1,
    val message: String = "",
    val ttl: Int = 1,
    val data: T? = null,
)

@Serializable
data class WbiImg(
    @SerialName("img_url") val imgUrl: String = "",
    @SerialName("sub_url") val subUrl: String = "",
)

@Serializable
data class NavData(
    @SerialName("isLogin") val isLogin: Boolean = false,
    @SerialName("wbi_img") val wbiImg: WbiImg? = null,
)

@Serializable
data class BuvidData(
    @SerialName("b_3") val b3: String = "",
    @SerialName("b_4") val b4: String = "",
)

@Serializable
data class VideoInfo(
    val bvid: String = "",
    val aid: Long = 0,
    val cid: Long = 0,
    val title: String = "",
    val pic: String = "",
    val desc: String = "",
    val duration: Int = 0,
    val pubdate: Long = 0,
    val owner: Owner = Owner(),
    val pages: List<Page> = emptyList(),
    val stat: Stat? = null,
)

@Serializable
data class Owner(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
)

@Serializable
data class Page(
    val cid: Long = 0,
    val page: Int = 0,
    val part: String = "",
    val duration: Int = 0,
)

@Serializable
data class Stat(
    val view: Long = 0,
    val danmaku: Long = 0,
    val reply: Long = 0,
    val like: Long = 0,
    val coin: Long = 0,
    val favorite: Long = 0,
    val share: Long = 0,
)

@Serializable
data class UserCardData(
    val card: UserCard = UserCard(),
    @SerialName("archive_count") val archiveCount: Long = 0,
)

@Serializable
data class UserCard(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
    val fans: Long = 0,
)

@Serializable
data class PlayData(
    val quality: Int = 0,
    val format: String = "",
    val timelength: Long = 0,
    @SerialName("accept_quality") val acceptQuality: List<Int> = emptyList(),
    @SerialName("accept_description") val acceptDescription: List<String> = emptyList(),
    val dash: Dash? = null,
    val durl: List<Durl> = emptyList(),
)

@Serializable
data class Dash(
    val duration: Int = 0,
    val video: List<DashVideo> = emptyList(),
    val audio: List<DashAudio> = emptyList(),
    val dolby: Dolby? = null,
    val flac: Flac? = null,
)

@Serializable
data class DashVideo(
    val id: Int = 0,
    @SerialName("baseUrl") val baseUrl: String = "",
    @SerialName("base_url") val baseUrlLegacy: String = "",
    @SerialName("backupUrl") val backupUrl: List<String>? = null,
    @SerialName("backup_url") val backupUrlLegacy: List<String>? = null,
    val bandwidth: Long = 0,
    val codecid: Int = 0,
    val codecs: String = "",
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("frameRate") val frameRate: String = "",
) {
    val url: String get() = baseUrl.ifEmpty { baseUrlLegacy }
    val backups: List<String> get() = backupUrl ?: backupUrlLegacy ?: emptyList()
}

@Serializable
data class DashAudio(
    val id: Int = 0,
    @SerialName("baseUrl") val baseUrl: String = "",
    @SerialName("base_url") val baseUrlLegacy: String = "",
    @SerialName("backupUrl") val backupUrl: List<String>? = null,
    @SerialName("backup_url") val backupUrlLegacy: List<String>? = null,
    val bandwidth: Long = 0,
    val codecid: Int = 0,
    val codecs: String = "",
) {
    val url: String get() = baseUrl.ifEmpty { baseUrlLegacy }
    val backups: List<String> get() = backupUrl ?: backupUrlLegacy ?: emptyList()
}

@Serializable
data class Dolby(val audio: List<DashAudio>? = null)

@Serializable
data class Flac(val audio: DashAudio? = null)

@Serializable
data class Durl(
    val order: Int = 0,
    val length: Long = 0,
    val size: Long = 0,
    val url: String = "",
    @SerialName("backup_url") val backupUrl: List<String>? = null,
)

data class VideoSearchItem(
    val title: String = "",
    val author: String = "",
    val play: String = "",
    val commentCount: Long = 0,
    val publishTime: Long = 0,
    val duration: String = "",
    val bvid: String = "",
    val pic: String = "",
)

data class VideoSearchPage(
    val items: List<VideoSearchItem> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = false,
    val total: Int = 0,
)
