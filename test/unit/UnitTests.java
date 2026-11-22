package com.velthon.launcher;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

/** Checks that run on any computer (no phone needed): church dates and feasts, and the translations. */
public class UnitTests {
    static int fails = 0, passes = 0;
    static void eq(String what, Object got, Object want) {
        if (String.valueOf(got).equals(String.valueOf(want))) { passes++; }
        else { fails++; System.out.println("FAIL " + what + ": got <" + got + "> want <" + want + ">"); }
    }
    static Calendar day(int y, int m, int d) { return new GregorianCalendar(y, m - 1, d); }

    public static void main(String[] a) throws Exception {
        // the Coptic calendar
        eq("coptic date 2026-11-21", Church.copticDate(day(2026, 11, 21), false), "Hathor 12, 1743");
        eq("coptic new year 2026-09-11", Church.copticDate(day(2026, 9, 11), false), "Thout 1, 1743");
        eq("coptic 2027-01-07", Church.copticDate(day(2027, 1, 7), false), "Kiahk 29, 1743");
        int[] k = Church.coptic(day(2026, 11, 21));
        eq("coptic month/day", k[0] + "/" + k[1], "3/12");
        // Easter
        Calendar oe = Church.orthodoxEaster(2026);
        eq("orthodox Easter 2026", (oe.get(Calendar.MONTH) + 1) + "-" + oe.get(Calendar.DAY_OF_MONTH), "4-12");
        Calendar we = Church.westernEaster(2026);
        eq("western Easter 2026", (we.get(Calendar.MONTH) + 1) + "-" + we.get(Calendar.DAY_OF_MONTH), "4-5");
        Calendar oe27 = Church.orthodoxEaster(2027);
        eq("orthodox Easter 2027", (oe27.get(Calendar.MONTH) + 1) + "-" + oe27.get(Calendar.DAY_OF_MONTH), "5-2");
        eq("days between", Church.daysBetween(day(2026, 1, 1), day(2026, 12, 31)), 364);
        // translations: every language answers, unknown words pass through
        for (String[] l : new String[][] {{"fr", "Réglages"}, {"ar", "الإعدادات"}, {"es", "Ajustes"}, {"de", "Einstellungen"}, {"pt", "Definições"}, {"en", "Settings"}, {"it", "Settings"}}) {
            Locale.setDefault(new Locale(l[0]));
            eq("L.t Settings in " + l[0], L.t("Settings"), l[1]);
        }
        Locale.setDefault(new Locale("fr"));
        eq("L.t unknown word", L.t("zzz-not-a-word"), "zzz-not-a-word");
        Locale.setDefault(Locale.ENGLISH);
        System.out.println("unit tests: " + passes + " passed, " + fails + " failed");
        if (fails > 0) System.exit(1);
    }
}
