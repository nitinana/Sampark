package com.sampark

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidContactsRepositoryTest {

    @Test
    fun `sim account type is excluded`() {
        assertTrue(isExcludedAccountType("com.android.sim"))
    }

    @Test
    fun `whatsapp account type is excluded`() {
        assertTrue(isExcludedAccountType("com.whatsapp"))
    }

    @Test
    fun `google account type is not excluded`() {
        assertFalse(isExcludedAccountType("com.google"))
    }

    @Test
    fun `null account type is not excluded`() {
        assertFalse(isExcludedAccountType(null))
    }
}
