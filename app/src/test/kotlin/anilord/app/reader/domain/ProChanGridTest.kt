package anilord.app.reader.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ProChanGridTest {

	@Test
	fun `vertical pieces are stacked top to bottom`() {
		assertEquals(1 to 4, proChanGridForMode("vertical", 4))
	}

	@Test
	fun `horizontal pieces are placed left to right`() {
		assertEquals(4 to 1, proChanGridForMode("horizontal", 4))
	}

	@Test
	fun `explicit grid keeps rows and columns`() {
		assertEquals(2 to 4, proChanGridForMode("grid_4x2", 8))
	}
}
