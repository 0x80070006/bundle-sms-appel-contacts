package com.android.messaging.ui

import com.android.messaging.testutil.androidManifestDocument
import com.android.messaging.testutil.elementsByTagName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

internal class LauncherAliasManifestTest {

    private val launcherAliasName = ".ui.conversationlist.ConversationListActivity"
    private val hostName = ".ui.MainActivity"
    private val mainAction = "android.intent.action.MAIN"
    private val launcherCategory = "android.intent.category.LAUNCHER"

    private val manifest: Document by lazy { androidManifestDocument() }

    @Test
    fun launcherAlias_keepsPublicName_andTargetsHost() {
        val matches = aliases().filter { element ->
            element.getAttribute("android:name") == launcherAliasName
        }

        assertEquals("Expected a single alias named $launcherAliasName", 1, matches.size)

        val alias = matches.single()
        assertEquals(hostName, alias.getAttribute("android:targetActivity"))
        assertEquals("true", alias.getAttribute("android:exported"))
        assertTrue(
            "Alias $launcherAliasName declares no MAIN + LAUNCHER intent filter",
            alias.declaresLauncherIntentFilter(),
        )
    }

    @Test
    fun host_isNotExported_andDeclaresNoIntentFilter() {
        val matches = activities().filter { element ->
            element.getAttribute("android:name") == hostName
        }

        assertEquals("Expected a single activity named $hostName", 1, matches.size)

        val host = matches.single()
        assertEquals("false", host.getAttribute("android:exported"))
        assertEquals(0, host.elementsByTagName(tagName = "intent-filter").size)
    }

    @Test
    fun manifest_declaresSingleLauncherEntry() {
        // Bundle: the icon colour aliases (bundle.launcher.*, chosen in Appearance) also declare
        // a launcher filter, but ship disabled: only the one the user picks is turned on, in
        // place of the default. So at install there is still a single launcher entry.
        val (colourAliases, others) = (aliases() + activities())
            .filter { element -> element.declaresLauncherIntentFilter() }
            .partition { element -> element.getAttribute("android:name").startsWith("bundle.launcher.") }

        assertEquals(listOf(launcherAliasName), others.map { it.getAttribute("android:name") })
        colourAliases.forEach { alias ->
            assertEquals(
                "Icon colour alias ${alias.getAttribute("android:name")} must ship disabled",
                "false",
                alias.getAttribute("android:enabled"),
            )
        }
    }

    private fun Element.declaresLauncherIntentFilter(): Boolean {
        return elementsByTagName(tagName = "intent-filter").any { filter ->
            mainAction in filter.childElementValues(tagName = "action") &&
                launcherCategory in filter.childElementValues(tagName = "category")
        }
    }

    private fun Element.childElementValues(tagName: String): Set<String> {
        return elementsByTagName(tagName = tagName)
            .map { element -> element.getAttribute("android:name") }
            .toSet()
    }

    private fun aliases(): List<Element> {
        return manifest.elementsByTagName(tagName = "activity-alias")
    }

    private fun activities(): List<Element> {
        return manifest.elementsByTagName(tagName = "activity")
    }
}
