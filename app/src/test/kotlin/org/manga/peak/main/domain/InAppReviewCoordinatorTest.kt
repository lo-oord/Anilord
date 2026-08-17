package org.manga.peak.main.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class InAppReviewCoordinatorTest {

	private val now = TimeUnit.DAYS.toMillis(200)
	private val oldEnough = now - TimeUnit.DAYS.toMillis(3)

	@Test
	fun `eligible after seven launches and three days`() {
		assertTrue(isEligible(launchCount = 7, firstLaunchAt = oldEnough))
	}

	@Test
	fun `not eligible before enough launches or install age`() {
		assertFalse(isEligible(launchCount = 6, firstLaunchAt = oldEnough))
		assertFalse(isEligible(launchCount = 7, firstLaunchAt = now - TimeUnit.DAYS.toMillis(2)))
	}

	@Test
	fun `not eligible twice in same version`() {
		assertFalse(isEligible(launchCount = 7, firstLaunchAt = oldEnough, attemptedVersion = 10))
	}

	@Test
	fun `respects one hundred twenty day cooldown across versions`() {
		assertFalse(
			isEligible(
				launchCount = 7,
				firstLaunchAt = oldEnough,
				lastAttemptAt = now - TimeUnit.DAYS.toMillis(119),
			),
		)
		assertTrue(
			isEligible(
				launchCount = 7,
				firstLaunchAt = oldEnough,
				lastAttemptAt = now - TimeUnit.DAYS.toMillis(120),
			),
		)
	}

	private fun isEligible(
		launchCount: Int,
		firstLaunchAt: Long,
		lastAttemptAt: Long = 0L,
		attemptedVersion: Int = 0,
	): Boolean = InAppReviewCoordinator.isEligible(
		launchCount = launchCount,
		firstLaunchAt = firstLaunchAt,
		lastAttemptAt = lastAttemptAt,
		attemptedVersion = attemptedVersion,
		currentVersion = 10,
		now = now,
	)
}
