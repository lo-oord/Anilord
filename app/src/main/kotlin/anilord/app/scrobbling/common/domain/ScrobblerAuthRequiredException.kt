package anilord.app.scrobbling.common.domain

import okio.IOException
import anilord.app.scrobbling.common.domain.model.ScrobblerService

class ScrobblerAuthRequiredException(
	val scrobbler: ScrobblerService,
) : IOException()
