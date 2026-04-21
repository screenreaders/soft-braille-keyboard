package com.dalton.braillekeyboard;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Locale;

public class HelperLogicTest {
    @Test
    public void stringDifference_returnsSuffixFromFirstDifference() {
        assertEquals("cd", BrailleImeCompositionUtils.stringDifference("ab", "abcd"));
        assertEquals("word", BrailleImeCompositionUtils.stringDifference("Word", "word"));
    }

    @Test
    public void capitalise_respectsAutoCapsAndCapsMode() {
        assertEquals("Hello", BrailleImeCompositionUtils.capitalise("hello", true, 1));
        assertEquals("hello", BrailleImeCompositionUtils.capitalise("hello", false, 1));
        assertEquals("hello", BrailleImeCompositionUtils.capitalise("hello", true, 0));
    }

    @Test
    public void maskPassword_preservesLength() {
        assertEquals("****", SpeechTextUtils.maskPassword("test"));
        assertEquals("", SpeechTextUtils.maskPassword(null));
    }

    @Test
    public void getBestEnd_prefersSeparatorInsideWindow() {
        String text = "one two three four";
        assertEquals(7, SpeechTextUtils.getBestEnd(text, 0, 10));
        assertEquals(text.length(), SpeechTextUtils.getBestEnd(text, 0, text.length()));
    }

    @Test
    public void matchRank_prefersLanguageThenCountryThenVariant() {
        Locale base = Locale.forLanguageTag("pl-PL");
        assertEquals(3, BrailleParserTableUtils.matchRank(Locale.forLanguageTag("pl-PL"), base));
        assertEquals(1, BrailleParserTableUtils.matchRank(Locale.forLanguageTag("pl"), base));
        assertEquals(0, BrailleParserTableUtils.matchRank(Locale.forLanguageTag("en-US"), base));
    }

    @Test
    public void buildPaddedCells_wrapsWithZeros() {
        Byte[] cells = new Byte[] { 1, 2, null, 4 };
        assertArrayEquals(new byte[] { 0, 1, 2, 0, 4, 0 },
                BrailleParserTableUtils.buildPaddedCells(cells));
    }

}
