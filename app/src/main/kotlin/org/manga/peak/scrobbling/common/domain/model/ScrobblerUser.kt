package org.manga.peak.scrobbling.common.domain.model

data class ScrobblerUser(
	val id: Long,
	val nickname: String,
	val avatar: String?,
	val service: ScrobblerService,
)
