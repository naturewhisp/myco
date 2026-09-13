package github.naturewhisp.myco.core

import kotlin.test.Test
import kotlin.test.assertEquals

class MycoCoreInfoTest {
    @Test
    fun exposesStableVersion() {
        assertEquals("0.1.0", MycoCoreInfo().version())
    }
}
