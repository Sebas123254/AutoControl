package com.sierracode.autocontrol.core.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NumberParserTest {
    private val d = 0.0001

    @Test fun usFormatWithGrouping() { assertEquals(1250.50, NumberParser.parse("1,250.50")!!, d) }
    @Test fun euFormatWithGrouping() { assertEquals(1250.50, NumberParser.parse("1.250,50")!!, d) }
    @Test fun commaAsDecimal()        { assertEquals(17.5,    NumberParser.parse("17,5")!!, d) }
    @Test fun dotAsDecimal()          { assertEquals(17.5,    NumberParser.parse("17.5")!!, d) }
    @Test fun integer()               { assertEquals(1250.0,  NumberParser.parse("1250")!!, d) }
    @Test fun repeatedGroupingDots()  { assertEquals(1_250_500.0, NumberParser.parse("1.250.500")!!, d) }
    @Test fun ignoresSpaces()         { assertEquals(1250.50, NumberParser.parse(" 1,250.50 ")!!, d) }

    @Test fun emptyIsNull() {
        assertNull(NumberParser.parse(""))
        assertNull(NumberParser.parse("   "))
        assertNull(NumberParser.parse(null))
    }

    @Test fun textIsNull() {
        assertNull(NumberParser.parse("abc"))
        assertNull(NumberParser.parse("12a"))
    }
}