package com.cloudjex.jexplayer.data.update

data class GithubRelease(
    val version: String,
    val notes: String,
    val htmlUrl: String,
    val isPreRelease: Boolean
)

/** SemVer suficiente para las versiones que publica JexPlayer. */
data class AppVersion private constructor(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<Identifier>
) : Comparable<AppVersion> {
    data class Identifier(val value: String) {
        val numeric: Int? get() = value.toIntOrNull()?.takeIf { it >= 0 }
    }

    override fun compareTo(other: AppVersion): Int {
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)
            .takeIf { it != 0 }?.let { return it }
        if (preRelease.isEmpty() && other.preRelease.isNotEmpty()) return 1
        if (preRelease.isNotEmpty() && other.preRelease.isEmpty()) return -1
        for (index in 0 until maxOf(preRelease.size, other.preRelease.size)) {
            val left = preRelease.getOrNull(index) ?: return -1
            val right = other.preRelease.getOrNull(index) ?: return 1
            if (left.numeric != null && right.numeric != null) {
                if (left.numeric != right.numeric) return left.numeric!!.compareTo(right.numeric!!)
            } else if (left.numeric != null) return -1
            else if (right.numeric != null) return 1
            else if (left.value != right.value) return left.value.compareTo(right.value)
        }
        return 0
    }

    companion object {
        fun parse(raw: String): AppVersion? {
            val normalized = raw.trim().removePrefix("v").removePrefix("V")
            val parts = normalized.split('-', limit = 2)
            val numbers = parts[0].split('.')
            if (numbers.size !in 1..3 || numbers.any { it.toIntOrNull()?.let { n -> n >= 0 } != true }) return null
            val pre = parts.getOrNull(1)?.split('.') ?: emptyList()
            if (pre.any { it.isEmpty() || it.toIntOrNull()?.let { n -> n >= 0 } == false }) return null
            return AppVersion(
                numbers.getOrNull(0)!!.toInt(), numbers.getOrNull(1)?.toInt() ?: 0,
                numbers.getOrNull(2)?.toInt() ?: 0, pre.map(::Identifier)
            )
        }
    }
}

fun latestRelease(releases: List<GithubRelease>, installed: String): GithubRelease? {
    val installedVersion = AppVersion.parse(installed) ?: return null
    return releases.mapNotNull { release ->
        AppVersion.parse(release.version)?.let { version -> release to version }
    }.filter { (_, version) -> version > installedVersion }
        .maxByOrNull { (_, version) -> version }
        ?.first
}
