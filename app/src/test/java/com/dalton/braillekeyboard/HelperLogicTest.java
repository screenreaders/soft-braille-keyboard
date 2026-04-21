package com.dalton.braillekeyboard;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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
    public void extractPunctuation_usesSpeechMapForSingleCharacter() {
        Map<String, String> map = new HashMap<String, String>();
        map.put("?", "question mark");
        assertEquals("question mark", SpeechTextUtils.extractPunctuation("?", map));
        assertEquals("ab", SpeechTextUtils.extractPunctuation("ab", map));
        assertNull(SpeechTextUtils.extractPunctuation(null, map));
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

    @Test
    public void padSwipeValueOf_mapsKnownAndUnknownValues() {
        assertEquals(Pad.Swipe.ONE_LEFT, Pad.Swipe.valueOf(1));
        assertEquals(Pad.Swipe.HOLD_SIX_RIGHT, Pad.Swipe.valueOf(229392));
        assertEquals(Pad.Swipe.UNKNOWN, Pad.Swipe.valueOf(999999));
    }

}
