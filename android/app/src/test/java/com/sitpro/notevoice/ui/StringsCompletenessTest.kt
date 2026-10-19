package com.sitpro.notevoice.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

/** Every English string must have a Vietnamese version and vice versa (CLAUDE.md: Ngôn ngữ). */
class StringsCompletenessTest {
    private fun names(path: String): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val root = doc.documentElement
        return listOf("string", "plurals", "string-array").flatMap { tag ->
            val nodes = root.getElementsByTagName(tag)
            (0 until nodes.length).map { nodes.item(it) as Element }
                .filter { it.getAttribute("translatable") != "false" } // names and language labels
                .map { it.getAttribute("name") }
        }.toSet()
    }

    @Test
    fun englishAndVietnameseHaveTheSameKeys() {
        val en = names("src/main/res/values/strings.xml")
        val vi = names("src/main/res/values-vi/strings.xml")
        assertEquals("missing in values-vi", emptySet<String>(), en - vi)
        assertEquals("missing in values (English)", emptySet<String>(), vi - en)
    }
}
