package org.manga.peak.scrobbling.common.domain

import okio.IOException
import org.manga.peak.scrobbling.common.domain.model.ScrobblerService

class ScrobblerAuthRequiredException(
	val scrobbler: ScrobblerService,
) : IOException()
