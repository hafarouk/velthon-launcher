package com.velthon.launcher;

import android.content.Context;

import java.util.Calendar;
import java.util.GregorianCalendar;

/**
 * Church calendar, as on the Velthon TV: Coptic Orthodox (Coptic date, Orthodox Easter) or Catholic
 * (Gregorian dates, Western Easter). Today's feast, the next one, and the readings for feast days.
 */
final class Church {
    private Church() { }

    static final class Feast {
        final String id, en, ar; final int days;
        Feast(String id, String en, String ar, int days) { this.id = id; this.en = en; this.ar = ar; this.days = days; }
    }

    /** "orthodox", "catholic" or "off". */
    static String calendar(Context c) { return U.prefs(c).getString("church", "orthodox"); }

    private static final String[][] NAMES = {
            {"nativity", "Nativity", "عيد الميلاد"},
            {"theophany", "Theophany", "عيد الغطاس"},
            {"annunciation", "Annunciation", "عيد البشارة"},
            {"lent", "Great Lent begins", "بداية الصوم الكبير"},
            {"palm", "Palm Sunday", "أحد الشعانين"},
            {"thursday", "Covenant Thursday", "خميس العهد"},
            {"cross", "Good Friday", "الجمعة العظيمة"},
            {"resurrection", "Feast of the Resurrection", "عيد القيامة"},
            {"ascension", "Ascension", "عيد الصعود"},
            {"pentecost", "Pentecost", "عيد العنصرة"},
            // Coptic Orthodox feasts and fasts across the year
            {"jonah", "Jonah's Fast begins", "بداية صوم يونان"},
            {"jonah_end", "Jonah's Passover", "فصح يونان"},
            {"lazarus", "Lazarus Saturday", "سبت لعازر"},
            {"bright_sat", "Bright Saturday", "سبت الفرح"},
            {"thomas", "Thomas Sunday", "أحد توما"},
            {"apostles_fast", "Apostles' Fast begins", "بداية صوم الرسل"},
            {"circumcision", "Feast of the Circumcision", "عيد الختان"},
            {"cana", "Wedding at Cana", "عرس قانا الجليل"},
            {"temple", "Entry into the Temple", "دخول المسيح الهيكل"},
            {"egypt", "Entry of the Lord into Egypt", "دخول المسيح أرض مصر"},
            {"apostles", "Feast of the Apostles", "عيد الرسل"},
            {"mary_fast", "St. Mary's Fast begins", "بداية صوم العذراء"},
            {"transfiguration", "Transfiguration", "عيد التجلي"},
            {"assumption", "Assumption of St. Mary", "صعود جسد العذراء"},
            {"nayrouz", "Nayrouz, Coptic New Year", "عيد النيروز"},
            {"holycross", "Feast of the Cross", "عيد الصليب"},
            {"nativity_fast", "Nativity Fast begins", "بداية صوم الميلاد"},
            {"kiahk", "Month of Kiahk begins", "بداية شهر كيهك"},
            {"baptist", "Martyrdom of St. John the Baptist", "استشهاد القديس يوحنا المعمدان"},
            {"beasts", "The Four Incorporeal Beasts", "الأربعة الحيوانات غير المتجسدين"},
            {"michael", "Feast of Archangel Michael", "عيد رئيس الملائكة ميخائيل"},
            {"mina", "Martyrdom of St. Mina", "استشهاد مار مينا العجايبي"},
            {"priests", "The 24 Priests", "الأربعة والعشرون قسيسًا"},
            {"mary_temple", "Presentation of St. Mary into the Temple", "دخول السيدة العذراء الهيكل"},
            {"gabriel", "Archangel Gabriel", "رئيس الملائكة غبريال"},
            {"mary_dormition", "Dormition of St. Mary", "نياحة السيدة العذراء"},
            {"antony", "Departure of St. Antony the Great", "نياحة الأنبا أنطونيوس"},
            {"george", "Martyrdom of St. George", "استشهاد مار جرجس"},
            {"mark", "Martyrdom of St. Mark the Apostle", "استشهاد مار مرقس الرسول"},
            {"mary_nativity", "Nativity of St. Mary", "ميلاد السيدة العذراء"},
            {"raphael", "Archangel Raphael", "رئيس الملائكة رافائيل"},
            {"m_michael", "Monthly commemoration of Archangel Michael", "تذكار رئيس الملائكة ميخائيل"},
            {"m_mary", "Monthly commemoration of St. Mary", "تذكار السيدة العذراء"},
            // Catholic
            {"ash", "Ash Wednesday", "أربعاء الرماد"},
            {"presentation", "Presentation of the Lord", "تقدمة الرب للهيكل"},
            {"assumption_c", "Assumption of Mary", "انتقال العذراء"},
            {"allsaints", "All Saints", "عيد جميع القديسين"},
            {"immaculate", "Immaculate Conception", "الحبل بلا دنس"},
            {"advent", "Advent begins", "بداية زمن المجيء"},
    };
    private static Feast feast(String id, int days) {
        for (String[] n : NAMES) if (n[0].equals(id)) return new Feast(id, n[1], n[2], days);
        return null;
    }

    /** The feast of a given day (calendar of {@code orthodox}), or null. */
    private static String feastOn(Calendar day, boolean orthodox) {
        int year = day.get(Calendar.YEAR);
        int diff = daysBetween(orthodox ? orthodoxEaster(year) : westernEaster(year), day);
        int m = day.get(Calendar.MONTH) + 1, d = day.get(Calendar.DAY_OF_MONTH);
        if (diff == 0) return "resurrection";
        if (diff == -7) return "palm";
        if (diff == -3) return "thursday";
        if (diff == -2) return "cross";
        if (diff == 39) return "ascension";
        if (diff == 49) return "pentecost";
        if (orthodox) {
            if (diff == -69) return "jonah";
            if (diff == -66) return "jonah_end";
            if (diff == -55) return "lent";
            if (diff == -8) return "lazarus";
            if (diff == -1) return "bright_sat";
            if (diff == 7) return "thomas";
            if (diff == 50) return "apostles_fast";
            if (m == 1 && d == 7) return "nativity";
            if (m == 1 && d == 14) return "circumcision";
            if (m == 1 && d == 19) return "theophany";
            if (m == 1 && d == 21) return "cana";
            if (m == 8 && d == 7) return "mary_fast";
            if (m == 11 && d == 25) return "nativity_fast";
            // the rest follow the Coptic calendar itself (as in the Synaxarium)
            int[] cd = coptic(day);
            int cm = cd[0], cdd = cd[1];          // month 1 = Thout ... 13 = the little month
            if (cm == 1 && cdd == 1) return "nayrouz";
            if (cm == 1 && cdd == 2) return "baptist";
            if (cm == 1 && cdd == 17) return "holycross";
            if (cm == 3 && cdd == 8) return "beasts";
            if (cm == 3 && cdd == 12) return "michael";
            if (cm == 3 && cdd == 15) return "mina";
            if (cm == 3 && cdd == 24) return "priests";
            if (cm == 4 && cdd == 1) return "kiahk";
            if (cm == 4 && cdd == 3) return "mary_temple";
            if (cm == 4 && cdd == 22) return "gabriel";
            if (cm == 5 && cdd == 21) return "mary_dormition";
            if (cm == 5 && cdd == 22) return "antony";
            if (cm == 6 && cdd == 8) return "temple";
            if (cm == 7 && cdd == 10) return "holycross";
            if (cm == 7 && cdd == 29) return "annunciation";
            if (cm == 8 && cdd == 23) return "george";
            if (cm == 8 && cdd == 30) return "mark";
            if (cm == 9 && cdd == 1) return "mary_nativity";
            if (cm == 9 && cdd == 24) return "egypt";
            if (cm == 10 && cdd == 12) return "michael";
            if (cm == 11 && cdd == 5) return "apostles";
            if (cm == 12 && cdd == 13) return "transfiguration";
            if (cm == 12 && cdd == 16) return "assumption";
            if (cm == 13 && cdd == 3) return "raphael";
        } else {
            if (diff == -46) return "ash";
            if (m == 12 && d == 25) return "nativity";
            if (m == 1 && d == 6) return "theophany";
            if (m == 2 && d == 2) return "presentation";
            if (m == 3 && d == 25) return "annunciation";
            if (m == 8 && d == 6) return "transfiguration";
            if (m == 8 && d == 15) return "assumption_c";
            if (m == 9 && d == 14) return "holycross";
            if (m == 11 && d == 1) return "allsaints";
            if (m == 12 && d == 8) return "immaculate";
            Calendar xmas = new GregorianCalendar(year, Calendar.DECEMBER, 25);
            int back = (xmas.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY);
            if (back == 0) back = 7;
            if (daysBetween(day, xmas) == back + 21) return "advent";
        }
        return null;
    }

    /** Today's feast (Christmas and Easter last two days, as on the TV), or null. */
    static Feast today(Context c) {
        String cal = calendar(c);
        if (cal.equals("off")) return null;
        boolean orthodox = cal.equals("orthodox");
        Calendar t = Calendar.getInstance();
        String id = feastOn(t, orthodox);
        if (id == null) {
            Calendar y = (Calendar) t.clone(); y.add(Calendar.DAY_OF_MONTH, -1);
            String yid = feastOn(y, orthodox);
            if ("nativity".equals(yid) || "resurrection".equals(yid) || "theophany".equals(yid) || "pentecost".equals(yid)) id = yid;
        }
        return id == null ? null : feast(id, 0);
    }

    /** The next feast after today, with the number of days until it. */
    static Feast next(Context c) {
        String cal = calendar(c);
        if (cal.equals("off")) return null;
        boolean orthodox = cal.equals("orthodox");
        Calendar d = Calendar.getInstance();
        for (int i = 1; i <= 400; i++) {
            d.add(Calendar.DAY_OF_MONTH, 1);
            String id = feastOn(d, orthodox);
            if (id != null) return feast(id, i);
        }
        return null;
    }

    /** The coming feasts (today first, if it is one), up to {@code n}, within the next year. */
    static java.util.ArrayList<Feast> upcoming(Context c, int n) {
        java.util.ArrayList<Feast> l = new java.util.ArrayList<>();
        String cal = calendar(c);
        if (cal.equals("off")) return l;
        boolean orthodox = cal.equals("orthodox");
        Calendar d = Calendar.getInstance();
        for (int i = 0; i <= 370 && l.size() < n; i++) {
            if (i > 0) d.add(Calendar.DAY_OF_MONTH, 1);
            String id = feastOn(d, orthodox);
            if (id != null) { Feast f = feast(id, i); if (f != null) l.add(f); }
        }
        return l;
    }

    /** In Lent (between its first day and Palm Sunday)? */
    static boolean lent(Context c) {
        String cal = calendar(c);
        if (cal.equals("off")) return false;
        Calendar t = Calendar.getInstance();
        int diff = daysBetween(cal.equals("orthodox") ? orthodoxEaster(t.get(Calendar.YEAR)) : westernEaster(t.get(Calendar.YEAR)), t);
        return diff >= (cal.equals("orthodox") ? -55 : -46) && diff < -7;
    }

    /** The church season or fast we are in today, {en, ar}, or null. */
    static String[] season(Context c) {
        String cal = calendar(c);
        if (cal.equals("off")) return null;
        boolean orthodox = cal.equals("orthodox");
        Calendar t = Calendar.getInstance();
        int year = t.get(Calendar.YEAR);
        int diff = daysBetween(orthodox ? orthodoxEaster(year) : westernEaster(year), t);
        int m = t.get(Calendar.MONTH) + 1, d = t.get(Calendar.DAY_OF_MONTH);
        if (diff >= -7 && diff <= -1) return new String[] {"Holy Week", "أسبوع الآلام"};
        if (orthodox) {
            if (diff >= -69 && diff <= -67) return new String[] {"Jonah's Fast", "صوم يونان"};
            if (diff >= -55 && diff < -7) return new String[] {"Great Lent", "الصوم الكبير"};
            if (diff >= 0 && diff <= 49) return new String[] {"The Holy Fifty Days", "الخماسين المقدسة"};
            Calendar apostles = new GregorianCalendar(year, Calendar.JULY, 12);
            if (diff >= 50 && daysBetween(t, apostles) > 0) return new String[] {"Apostles' Fast", "صوم الرسل"};
            if (m == 8 && d >= 7 && d <= 21) return new String[] {"St. Mary's Fast", "صوم العذراء"};
            if ((m == 11 && d >= 25) || m == 12 || (m == 1 && d <= 6)) return new String[] {"Nativity Fast", "صوم الميلاد"};
        } else {
            if (diff >= -46 && diff < -7) return new String[] {"Lent", "الصوم الكبير"};
            if (diff >= 0 && diff <= 49) return new String[] {"Eastertide", "الزمن الفصحي"};
        }
        return null;
    }

    /** Readings for a feast, by their reference in the verse list (the same as the TV). */
    static String[] readings(String id) {
        switch (id) {
            case "nativity": return new String[] {"Luke 2:11", "Isa 9:2", "Isa 60:1"};
            case "theophany": return new String[] {"Matt 3:17", "John 1:29"};
            case "annunciation": return new String[] {"Luke 1:35"};
            case "lent": case "ash": return new String[] {"Matt 4:4"};
            case "palm": return new String[] {"Matt 21:9"};
            case "thursday": return new String[] {"John 13:34"};
            case "cross": return new String[] {"John 19:30", "John 15:13", "1John 3:16"};
            case "resurrection": return new String[] {"Matt 28:6", "John 14:6"};
            case "ascension": return new String[] {"Matt 28:20"};
            case "pentecost": return new String[] {"Acts 2:4", "John 16:13"};
        }
        return null;
    }

    // ---------- the Coptic date ----------
    private static final String[] MONTHS = {"Thout", "Paopi", "Hathor", "Kiahk", "Tobe", "Meshir", "Paremhat", "Paremoude",
            "Pashons", "Paoni", "Epip", "Mesori", "Pi Kogi Enavot"};
    private static final String[] MONTHS_AR = {"توت", "بابة", "هاتور", "كيهك", "طوبة", "أمشير", "برمهات", "برمودة",
            "بشنس", "بؤونة", "أبيب", "مسرى", "النسيء"};

    private static Calendar copticNewYear(int y) {
        boolean nextLeap = new GregorianCalendar().isLeapYear(y + 1);
        return new GregorianCalendar(y, Calendar.SEPTEMBER, nextLeap ? 12 : 11);
    }
    /** {month 1..13, day 1..30} in the Coptic calendar. */
    static int[] coptic(Calendar day) {
        int y = day.get(Calendar.YEAR);
        Calendar ny = copticNewYear(y);
        if (daysBetween(ny, day) < 0) { y--; ny = copticNewYear(y); }
        int n = daysBetween(ny, day);
        int month = Math.min(12, n / 30);
        return new int[] {month + 1, n - month * 30 + 1};
    }
    /** The monthly commemoration for today (Archangel Michael on the 12th, St. Mary on the 21st), or null. */
    static Feast monthly(Context c) {
        if (!calendar(c).equals("orthodox")) return null;
        int d = coptic(Calendar.getInstance())[1];
        return d == 12 ? feast("m_michael", 0) : d == 21 ? feast("m_mary", 0) : null;
    }

    /** e.g. "Hathor 9, 1743" (or the Arabic month name). */
    static String copticDate(Calendar day, boolean arabic) {
        int y = day.get(Calendar.YEAR);
        Calendar ny = copticNewYear(y);
        if (daysBetween(ny, day) < 0) { y--; ny = copticNewYear(y); }
        int n = daysBetween(ny, day);
        int month = Math.min(12, n / 30), d = n - month * 30 + 1;
        int am = y - 283;
        return arabic ? d + " " + MONTHS_AR[month] + " " + am : MONTHS[month] + " " + d + ", " + am;
    }

    static int daysBetween(Calendar from, Calendar to) {
        Calendar a = new GregorianCalendar(from.get(Calendar.YEAR), from.get(Calendar.MONTH), from.get(Calendar.DAY_OF_MONTH));
        Calendar b = new GregorianCalendar(to.get(Calendar.YEAR), to.get(Calendar.MONTH), to.get(Calendar.DAY_OF_MONTH));
        return (int) Math.round((b.getTimeInMillis() - a.getTimeInMillis()) / 86400000.0);
    }
    static Calendar westernEaster(int y) {
        int a = y % 19, b = y / 100, c = y % 100, d = b / 4, e = b % 4, f = (b + 8) / 25, g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30, i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = ((h + l - 7 * m + 114) % 31) + 1;
        return new GregorianCalendar(y, month - 1, day);
    }
    static Calendar orthodoxEaster(int y) {
        int a = y % 4, b = y % 7, c = y % 19, d = (19 * c + 15) % 30, e = (2 * a + 4 * b - d + 34) % 7;
        int month = (d + e + 114) / 31, day = ((d + e + 114) % 31) + 1;
        Calendar julian = new GregorianCalendar(y, month - 1, day);
        julian.add(Calendar.DAY_OF_MONTH, 13);
        return julian;
    }
}
