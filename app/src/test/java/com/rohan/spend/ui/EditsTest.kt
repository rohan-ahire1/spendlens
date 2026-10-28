package com.rohan.spend.ui

import com.rohan.spend.data.Cat
import org.junit.Assert.assertEquals
import org.junit.Test

class EditsTest {
    private val empty = Edits(emptyMap(), emptyMap(), emptySet())

    @Test fun undoesAHide() {
        val after = empty.copy(hidden = setOf(7L))
        assertEquals(empty, after.revert(empty, after))
    }

    @Test fun undoesAnUnhide() {
        val before = empty.copy(hidden = setOf(7L))
        assertEquals(before, empty.revert(before, empty))
    }

    @Test fun undoesAlwaysRuleAndPutsBackTheOneOff() {
        // "Do this for all" adds a rule and clears this payment's own category.
        val before = Edits(mapOf("a@x" to Cat.SHOPPING), mapOf(1L to Cat.MOVIES), emptySet())
        val after = Edits(mapOf("a@x" to Cat.SHOPPING, "b@y" to Cat.GROCERIES), emptyMap(), emptySet())
        assertEquals(before, after.revert(before, after))
    }

    @Test fun undoesARuleRemoval() {
        val before = empty.copy(rules = mapOf("a@x" to Cat.TRAVEL))
        assertEquals(before, empty.revert(before, empty))
    }

    @Test fun keepsOtherEditsMadeSince() {
        val before = empty
        val after = empty.copy(txnCats = mapOf(1L to Cat.SALON))
        val now = after.copy(txnCats = mapOf(1L to Cat.SALON, 2L to Cat.FUEL), hidden = setOf(9L))
        assertEquals(empty.copy(txnCats = mapOf(2L to Cat.FUEL), hidden = setOf(9L)), now.revert(before, after))
    }

    @Test fun changedCategoryGoesBackToOldOne() {
        val before = empty.copy(rules = mapOf("a@x" to Cat.SHOPPING))
        val after = empty.copy(rules = mapOf("a@x" to Cat.GROCERIES))
        assertEquals(before, after.revert(before, after))
    }
}
