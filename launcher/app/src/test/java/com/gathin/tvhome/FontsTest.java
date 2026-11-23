package com.gathin.tvhome;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** The font key: parsing, the TV's choice over the theme, which resource draws what, tracking, and that every font file exists. */
public class FontsTest {
    @Test public void aFontIsOutfitUnlessItSaysInter() {
        assertEquals("outfit", Fonts.font(null));
        assertEquals("outfit", Fonts.font("outfit"));
        assertEquals("outfit", Fonts.font("comic"));
        assertEquals("inter", Fonts.font(" Inter "));
    }

    @Test public void theTvsChoiceBeatsTheThemeAndMinusOneFollowsIt() {
        assertEquals("inter", Fonts.chosen(Fonts.CHOICE_INTER, "outfit"));
        assertEquals("outfit", Fonts.chosen(Fonts.CHOICE_OUTFIT, "inter"));
        assertEquals("inter", Fonts.chosen(-1, "inter"));
        assertEquals("outfit", Fonts.chosen(-1, null));
        assertEquals("outfit", Fonts.chosen(7, "outfit"));
        assertEquals(Fonts.CHOICE_INTER, Fonts.choiceOf("INTER"));
        assertEquals(Fonts.CHOICE_OUTFIT, Fonts.choiceOf("x"));
    }

    @Test public void outfitKeepsEveryResourceAndInterMapsEachWeight() {
        int[] outfit = {R.font.outfit, R.font.outfit_regular, R.font.outfit_light, R.font.outfit_extralight, R.font.outfit_medium};
        for (int id : outfit) assertEquals(id, Fonts.resource("outfit", id));
        assertEquals(R.font.inter_light, Fonts.resource("inter", R.font.outfit_light));
        assertEquals(R.font.inter_light, Fonts.resource("inter", R.font.outfit_extralight));
        assertEquals(R.font.inter_regular, Fonts.resource("inter", R.font.outfit_regular));
        assertEquals(R.font.inter_regular, Fonts.resource("inter", R.font.outfit));
        assertEquals(R.font.inter_medium, Fonts.resource("inter", R.font.outfit_medium));
        assertEquals(0, Fonts.resource("inter", 0));   // a system family (values-vi) is left alone
        assertNotEquals(R.font.inter_light, R.font.inter_medium);
    }

    @Test public void outfitTrackingIsUntouchedAndInterOpensLessAndTightensTitles() {
        assertEquals(0.12f, Fonts.tracking("outfit", 0.12f, 14), 0f);
        assertEquals(-0.04f, Fonts.tracking("outfit", -0.04f, 52), 0f);
        assertEquals(0.08f, Fonts.tracking("inter", 0.12f, 14), 1e-6f);
        assertEquals(0.147f, Fonts.tracking("inter", 0.22f, 12), 1e-3f);
        assertEquals(-0.02f, Fonts.tracking("inter", -0.04f, 52), 0f);
        assertEquals(-0.02f, Fonts.tracking("inter", -0.02f, 48), 0f);
        assertEquals(-0.01f, Fonts.tracking("inter", -0.01f, 34), 0f);
        assertEquals(0.04f, Fonts.tracking("inter", 0.04f, 14), 0f);
        assertEquals(0.12f, Fonts.captionTracking("outfit"), 0f);
        assertEquals(0.08f, Fonts.captionTracking("inter"), 1e-6f);
    }

    @Test public void clocksGetTabularFiguresOnlyInInter() {
        assertEquals("tnum", Fonts.clockFeatures("inter"));
        assertEquals(null, Fonts.clockFeatures("outfit"));
    }

    @Test public void everyFontFileAFontFamilyNamesExists() throws Exception {
        File dir = new File("src/main/res/font");
        File[] files = dir.listFiles();
        assertTrue("run from the app module", files != null);
        Pattern ref = Pattern.compile("@font/([a-z0-9_]+)");
        int checked = 0;
        for (File f : files) {
            if (!f.getName().endsWith(".xml")) continue;
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            Matcher m = ref.matcher(text);
            while (m.find()) {
                checked++;
                assertTrue(f.getName() + " names @font/" + m.group(1),
                        new File(dir, m.group(1) + ".ttf").isFile() || new File(dir, m.group(1) + ".xml").isFile());
            }
        }
        assertTrue(checked >= 7);
        for (String name : new String[]{"inter_light.xml", "inter_regular.xml", "inter_medium.xml"})
            assertTrue(name, new File(dir, name).isFile());
    }

    @Test public void homeFontKeyIsForgottenByFollowTheme() {
        assertTrue(java.util.Arrays.asList(Config.HORIZON_KEYS).contains(Config.HOME_FONT_KEY));
    }
}
