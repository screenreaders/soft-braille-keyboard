package com.dalton.braillekeyboard;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.googlecode.eyesfree.braille.translate.TableInfo;
import com.googlecode.eyesfree.braille.translate.TranslationResult;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class BrailleParserInstrumentedTest {

    @Test
    public void translatorLoadsAndExposesTables() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger readyStatus = new AtomicInteger(
                BrailleParser.STATUS_PREPARING);
        final AtomicReference<BrailleParser> parserRef =
                new AtomicReference<BrailleParser>();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                new Runnable() {
                    @Override
                    public void run() {
                        parserRef.set(new BrailleParser(context,
                                new BrailleParser.BrailleParserListener() {
                                    @Override
                                    public void onTranslatorReady(int status) {
                                        readyStatus.set(status);
                                        latch.countDown();
                                    }
                                }));
                    }
                });

        assertTrue("Translator service did not become ready in time",
                latch.await(20, TimeUnit.SECONDS));

        final BrailleParser parser = parserRef.get();
        assertNotNull("BrailleParser instance was not created", parser);
        assertTrue("Unexpected translator status: " + readyStatus.get(),
                readyStatus.get() == BrailleParser.STATUS_OK
                        || readyStatus.get() == BrailleParser.STATUS_TABLE_ERROR);

        final AtomicReference<List<TableInfo>> literaryRef =
                new AtomicReference<List<TableInfo>>();
        final AtomicReference<List<TableInfo>> computerRef =
                new AtomicReference<List<TableInfo>>();
        final AtomicReference<TableInfo> currentTableRef =
                new AtomicReference<TableInfo>();
        final AtomicReference<TranslationResult> translationRef =
                new AtomicReference<TranslationResult>();

        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                new Runnable() {
                    @Override
                    public void run() {
                        literaryRef.set(
                                parser.getTables(BrailleParser.BrailleType.LITERARY));
                        computerRef.set(
                                parser.getTables(BrailleParser.BrailleType.COMPUTER));
                        currentTableRef.set(parser.getTable(context));
                        translationRef.set(parser.translateText(
                                context, "abc", 3));
                    }
                });

        List<TableInfo> literary = literaryRef.get();
        List<TableInfo> computer = computerRef.get();
        assertNotNull("Literary table list is null", literary);
        assertNotNull("Computer table list is null", computer);
        assertFalse("No literary tables were loaded", literary.isEmpty());
        assertFalse("No computer tables were loaded", computer.isEmpty());

        TableInfo currentTable = currentTableRef.get();
        assertNotNull("Current default table is null", currentTable);

        TranslationResult translation = translationRef.get();
        assertNotNull("Forward translation returned null", translation);
        assertNotNull("Forward translation cells are null", translation.getCells());
        assertTrue("Forward translation produced no cells",
                translation.getCells().length > 0);

        if (Locale.getDefault().getLanguage().equals("pl")) {
            assertTrue("Default Polish literary table should be selected on Polish locale",
                    currentTable.getId().startsWith("pl-"));
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                new Runnable() {
                    @Override
                    public void run() {
                        parser.destroy();
                    }
                });
    }
}
