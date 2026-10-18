package com.automatelinux.countdown

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Each test ends by starting a second Navigator on the same settings — that is the app
 * being closed and opened again.
 */
class NavigatorTest {
    private val settings = MapSettings()
    private fun reopen() = Navigator(settings)

    @Test
    fun firstLaunchOpensOnTheList() {
        assertEquals(Route.Home, reopen().current)
    }

    @Test
    fun opensBackInsideTheCountdownYouWereIn() {
        reopen().go(Route.Detail("a"))
        assertEquals(Route.Detail("a"), reopen().current)
    }

    @Test
    fun backFromTheReopenedCountdownGoesToTheListNotOutOfTheApp() {
        reopen().go(Route.Detail("a"))
        val nav = reopen()
        assertTrue(nav.back())
        assertEquals(Route.Home, nav.current)
        assertFalse(nav.back())
    }

    @Test
    fun leavingToTheListIsRememberedToo() {
        val nav = reopen()
        nav.go(Route.Detail("a"))
        nav.back()
        assertEquals(Route.Home, reopen().current)
    }

    @Test
    fun editingInsideACountdownReopensAsThatCountdownNotAsTheHalfTypedEdit() {
        val nav = reopen()
        nav.go(Route.Detail("a"))
        nav.go(Route.Edit("a"))
        assertEquals(Route.Detail("a"), reopen().current)
    }

    @Test
    fun renamingFromACardOnTheListIsNotBeingInACountdown() {
        reopen().go(Route.Edit("a"))
        assertEquals(Route.Home, reopen().current)
    }

    @Test
    fun aNewCountdownIsRememberedOnceItOpens() {
        val nav = reopen()
        nav.go(Route.Edit(null))
        nav.replaceTop(Route.Detail("new"))
        assertEquals(Route.Detail("new"), reopen().current)
    }

    @Test
    fun aDuplicateIsTheCountdownYouAreInAndBackReturnsToTheOriginal() {
        val nav = reopen()
        nav.go(Route.Detail("a"))
        nav.go(Route.Detail("a-copy"))
        assertEquals(Route.Detail("a-copy"), reopen().current)
        nav.back()
        assertEquals(Route.Detail("a"), reopen().current)
    }

    @Test
    fun aDeletedCountdownIsForgotten() {
        val nav = reopen()
        nav.go(Route.Detail("a"))
        nav.go(Route.Edit("a"))
        nav.popToHome()
        assertEquals(Route.Home, reopen().current)
    }
}
