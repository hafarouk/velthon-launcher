package com.velthon.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * The Coptic calendar page (swipe left on the home screen): today's Coptic date, the saint of the day with
 * every commemoration of the day from the Synaxarium, the season or fast, and the feasts coming up.
 * Swipe right or press Back to go home.
 */
public class ChurchActivity extends Activity {
    private Scene scene;
    private LinearLayout list;
    private boolean ar;
    /** The page follows the verse language (English, Arabic, French or Spanish), not the phone's. */
    private String lang = "en";
    private java.util.Locale loc = java.util.Locale.getDefault();
    private String t(String en) { return L.in(lang, en); }
    private float x0, y0;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        U.clearBars(getWindow());   // no dark band: the picture runs behind the status bar
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        lang = U.langs(this);
        ar = lang.equals("ar");
        loc = U.langLocale(this);
        scene = new Scene(this);
        View dim = new View(this);
        dim.setBackgroundColor(0x8C060A18);
        scene.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(20), dp(12), dp(20), dp(40));
        sv.addView(list, new FrameLayout.LayoutParams(-1, -2));
        scene.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        final View closeBtn = U.gestureNav(this) ? null : U.closeX(this, new Runnable() { @Override public void run() { finish(); } });
        if (closeBtn != null) scene.addView(closeBtn, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.TOP | Gravity.END));
        // the X only shows at the top of the page: further down it would sit on top of what scrolls under it
        // (the month's › arrow); the phone's back button still closes the page from anywhere
        if (closeBtn != null && Build.VERSION.SDK_INT >= 23) sv.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override public void onScrollChange(View v, int x, int y, int ox, int oy) {
                boolean show = y < dp(24);
                if (show != (closeBtn.getVisibility() == View.VISIBLE)) {
                    closeBtn.animate().cancel();
                    if (show) { closeBtn.setVisibility(View.VISIBLE); closeBtn.animate().alpha(1f).setDuration(150).start(); }
                    else closeBtn.animate().alpha(0f).setDuration(150).withEndAction(new Runnable() { @Override public void run() { closeBtn.setVisibility(View.GONE); } }).start();
                }
            }
        });
        setContentView(scene);
        scene.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                list.setPadding(dp(20), in.getSystemWindowInsetTop() + dp(14), dp(20), in.getSystemWindowInsetBottom() + dp(40));
                if (closeBtn != null) {   // the X sits just under the status bar, top right
                    FrameLayout.LayoutParams xl = (FrameLayout.LayoutParams) closeBtn.getLayoutParams();
                    xl.topMargin = in.getSystemWindowInsetTop() + dp(10); xl.rightMargin = dp(14); closeBtn.setLayoutParams(xl);
                }
                return in;
            }
        });
        build();
        // your own events show on this page: ask for the calendar once
        if (!Events.allowed(this) && Build.VERSION.SDK_INT >= 23 && !U.prefs(this).getBoolean("cal_asked", false)) {
            U.prefs(this).edit().putBoolean("cal_asked", true).apply();
            requestPermissions(new String[] {android.Manifest.permission.READ_CALENDAR}, 5);
        }
        Synax.load(this, new Runnable() { @Override public void run() {
            runOnUiThread(new Runnable() { @Override public void run() { build(); } }); } });
        if (U.anims(this)) {
            list.setAlpha(0f); list.setTranslationX(-dp(40));
            list.animate().alpha(1f).translationX(0).setDuration(380)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
        }
    }

    private final Calendar shown = Calendar.getInstance();
    private int selDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);

    @Override public void onRequestPermissionsResult(int req, String[] p, int[] g) { build(); }

    private void buildMonth() {
        final int y = shown.get(Calendar.YEAR), m = shown.get(Calendar.MONTH);
        Calendar today = Calendar.getInstance();
        java.util.HashMap<Integer, java.util.ArrayList<String[]>> ev = Events.month(this, y, m);
        // header: ‹ November 2026 ›
        LinearLayout head = new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL); head.setPadding(0, dp(10), 0, dp(6));
        TextView prev = text(26, 0xFFF2B640, Typeface.DEFAULT_BOLD); prev.setText("‹"); prev.setPadding(dp(4), 0, dp(16), 0);
        TextView next = text(26, 0xFFF2B640, Typeface.DEFAULT_BOLD); next.setText("›"); next.setPadding(dp(16), 0, dp(4), 0);
        TextView title = text(20, Color.WHITE, Typeface.DEFAULT_BOLD); title.setGravity(Gravity.CENTER);
        title.setText(new SimpleDateFormat("LLLL yyyy", loc).format(shown.getTime()));
        prev.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { shown.add(Calendar.MONTH, -1); selDay = 1; build(); } });
        next.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { shown.add(Calendar.MONTH, 1); selDay = 1; build(); } });
        head.addView(prev); head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f)); head.addView(next);
        list.addView(head);
        // the weekday names, in the phone's own order (Sunday or Monday first)
        Calendar c = Calendar.getInstance(); c.clear(); c.set(y, m, 1);
        int first = c.getFirstDayOfWeek();
        LinearLayout wk = new LinearLayout(this);
        SimpleDateFormat wf = new SimpleDateFormat("EEEEE", loc);
        Calendar w = Calendar.getInstance(); w.set(Calendar.DAY_OF_WEEK, first);
        for (int i = 0; i < 7; i++) {
            TextView t = text(12, 0x99FFFFFF, Typeface.DEFAULT_BOLD); t.setGravity(Gravity.CENTER);
            t.setText(wf.format(w.getTime())); w.add(Calendar.DAY_OF_MONTH, 1);
            wk.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        list.addView(wk);
        int lead = (c.get(Calendar.DAY_OF_WEEK) - first + 7) % 7;
        int days = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        boolean orth = Church.calendar(this).equals("orthodox");
        LinearLayout row = null;
        for (int cell = 0; cell < 42; cell++) {
            if (cell % 7 == 0) {
                if (cell >= lead + days) break;
                row = new LinearLayout(this); list.addView(row);
            }
            final int d = cell - lead + 1;
            LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER_HORIZONTAL);
            box.setPadding(0, dp(6), 0, dp(6));
            if (d >= 1 && d <= days) {
                Calendar day = Calendar.getInstance(); day.clear(); day.set(y, m, d);
                boolean isToday = today.get(Calendar.YEAR) == y && today.get(Calendar.MONTH) == m && today.get(Calendar.DAY_OF_MONTH) == d;
                Church.Feast f = Church.feastOf(this, day);
                TextView n = text(16, isToday ? 0xFF1A1206 : Color.WHITE, Typeface.DEFAULT_BOLD); n.setGravity(Gravity.CENTER); n.setText(String.valueOf(d));
                android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(); g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                if (isToday) g.setColor(0xFFF2B640); else if (d == selDay) { g.setColor(0x33FFFFFF); g.setStroke(dp(1), 0xFFF2B640); } else g.setColor(0);
                n.setBackground(g);
                box.addView(n, new LinearLayout.LayoutParams(dp(34), dp(34)));
                if (orth) { TextView cd = text(9.5f, 0x99FFFFFF, Typeface.DEFAULT); cd.setGravity(Gravity.CENTER); cd.setText(String.valueOf(Church.coptic(day)[1])); box.addView(cd); }
                LinearLayout dots = new LinearLayout(this); dots.setGravity(Gravity.CENTER);
                if (f != null) dots.addView(dot(0xFFF2B640));
                if (ev.containsKey(d)) dots.addView(dot(0xFF8FD0FF));
                box.addView(dots, new LinearLayout.LayoutParams(-2, dp(8)));
                box.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { selDay = d; build(); } });
            }
            row.addView(box, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        // the chosen day: its date, the feast, your events
        Calendar sel = Calendar.getInstance(); sel.clear(); sel.set(y, m, Math.min(selDay, days));
        LinearLayout card = card();
        TextView sd = text(17, Color.WHITE, Typeface.DEFAULT_BOLD);
        sd.setText(new SimpleDateFormat("EEEE, MMMM d", loc).format(sel.getTime()));
        card.addView(sd);
        if (orth) { TextView cd = text(14, 0xFFF2B640, Typeface.DEFAULT); cd.setText(Church.copticDate(sel, lang)); card.addView(cd); }
        Church.Feast f = Church.feastOf(this, sel);
        if (f != null) { TextView ft = text(15, 0xFFF2B640, Typeface.DEFAULT_BOLD); ft.setText("✦ " + f.in(lang)); ft.setPadding(0, dp(8), 0, 0); card.addView(ft); }
        java.util.ArrayList<String[]> l = ev.get(sel.get(Calendar.DAY_OF_MONTH));
        if (l != null) for (String[] e : l) {
            TextView et = text(15, Color.WHITE, Typeface.DEFAULT);
            et.setText("•  " + e[0] + "   ·   " + e[1]); et.setPadding(0, dp(6), 0, 0);
            card.addView(et);
        }
        else if (!Events.allowed(this)) {
            TextView et = text(13, 0xB3FFFFFF, Typeface.DEFAULT);
            et.setText(t("Allow the calendar to see your own events here.")); et.setPadding(0, dp(8), 0, 0);
            card.addView(et);
        }
        else if (f == null) { TextView et = text(13, 0x99FFFFFF, Typeface.DEFAULT); et.setText(t("Nothing on this day.")); et.setPadding(0, dp(8), 0, 0); card.addView(et); }
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2); cl.topMargin = dp(12);
        list.addView(card, cl);
    }
    private View dot(int color) {
        View v = new View(this);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(); g.setShape(android.graphics.drawable.GradientDrawable.OVAL); g.setColor(color);
        v.setBackground(g);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(dp(6), dp(6)); l.leftMargin = l.rightMargin = dp(1.5f);
        v.setLayoutParams(l);
        return v;
    }

    private void build() {
        // a tap on a day or a month arrow rebuilds the page: keep it where it was
        final View sc = (View) list.getParent();
        final int y = sc != null ? sc.getScrollY() : 0;
        list.removeAllViews();
        buildDay();
        if (sc != null && y > 0) sc.post(new Runnable() { @Override public void run() { sc.scrollTo(0, y); } });
    }

    /** Your next events, from the phone's calendar: the next five, within two months. */
    private void nextEvents() {
        LinearLayout card = card();
        TextView h = text(12.5f, 0xFF8FD0FF, Typeface.DEFAULT_BOLD);
        h.setText(t("Next on your calendar")); h.setLetterSpacing(.06f);
        card.addView(h);
        if (!Events.allowed(this)) {
            TextView t = text(14, 0xCCFFFFFF, Typeface.DEFAULT);
            t.setText(t("Allow the calendar to see your own events here.")); t.setPadding(0, dp(6), 0, 0);
            card.addView(t);
            card.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= 23) requestPermissions(new String[] {android.Manifest.permission.READ_CALENDAR}, 5); } });
        } else {
            java.util.ArrayList<String[]> ev = Events.upcoming(this, 5, 60);
            if (ev.isEmpty()) {
                TextView t = text(14, 0x99FFFFFF, Typeface.DEFAULT);
                t.setText(t("Nothing coming up.")); t.setPadding(0, dp(6), 0, 0);
                card.addView(t);
            } else for (int i = 0; i < ev.size(); i++) {
                String[] e = ev.get(i);
                TextView t = text(i == 0 ? 17 : 15, Color.WHITE, i == 0 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                t.setText(e[0]); t.setPadding(0, dp(i == 0 ? 8 : 10), 0, 0);
                card.addView(t);
                TextView w = text(13, e.length > 2 ? 0xFFFF8A80 : 0xFF8FD0FF, Typeface.DEFAULT);
                w.setText(e[1]);
                card.addView(w);
            }
        }
        add(card);
    }

    private void buildDay() {
        boolean orthodox = Church.calendar(this).equals("orthodox");
        Calendar now = Calendar.getInstance();
        // the date
        TextView k = text(13, 0xFFF2B640, Typeface.DEFAULT_BOLD);
        k.setText(ar ? "التقويم القبطي" : t(orthodox ? "Coptic calendar" : "Church calendar").toUpperCase(loc));
        k.setLetterSpacing(.12f);
        list.addView(k);
        TextView d = text(30, Color.WHITE, Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        d.setText(orthodox ? Church.copticDate(now, lang) : new SimpleDateFormat("EEEE, MMMM d", loc).format(now.getTime()));
        d.setPadding(0, dp(4), 0, 0);
        list.addView(d);
        if (orthodox) {
            TextView g = text(15, 0xD9FFFFFF, Typeface.DEFAULT);
            g.setText(new SimpleDateFormat("EEEE, MMMM d, yyyy", loc).format(now.getTime()));
            list.addView(g);
        }
        String[] season = Church.season(this);
        if (season != null) {
            TextView s = chip(lang.equals("ar") ? season[1] : lang.equals("fr") && season.length > 2 ? season[2] : lang.equals("es") && season.length > 3 ? season[3] : season[0]);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2); sl.topMargin = dp(10);
            list.addView(s, sl);
        }

        // the saint of the day, and everything the Synaxarium remembers today
        if (orthodox) {
            LinearLayout card = card();
            LinearLayout head = new LinearLayout(this);
            head.setGravity(Gravity.CENTER_VERTICAL);
            ImageView pic = new ImageView(this);
            pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
            pic.setClipToOutline(true);
            pic.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) { o.setOval(0, 0, v.getWidth(), v.getHeight()); } });
            android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
            ring.setShape(android.graphics.drawable.GradientDrawable.OVAL); ring.setColor(0x33FFC94A);
            pic.setBackground(ring);
            Bitmap bm = Synax.picture(this);
            if (bm != null) pic.setImageBitmap(bm);
            else { pic.setImageDrawable(new Ico(Ico.CROSS, 0xFFF2B640)); pic.setPadding(dp(16), dp(16), dp(16), dp(16)); }
            head.addView(pic, new LinearLayout.LayoutParams(dp(64), dp(64)));
            LinearLayout hl = new LinearLayout(this);
            hl.setOrientation(LinearLayout.VERTICAL);
            hl.setPadding(dp(14), 0, 0, 0);
            TextView t1 = text(12.5f, 0xFFF2B640, Typeface.DEFAULT_BOLD);
            t1.setText(t("Saint of the day"));
            hl.addView(t1);
            String st = Synax.saint(this);
            TextView t2 = text(18, Color.WHITE, Typeface.DEFAULT_BOLD);
            t2.setText(st == null ? t("Loading the Synaxarium…") : tidy(st));
            hl.addView(t2);
            head.addView(hl, new LinearLayout.LayoutParams(0, -2, 1f));
            card.addView(head);
            String all = Synax.all(this);
            if (all != null && all.contains("\n")) {
                TextView h2 = text(13, 0xCCFFFFFF, Typeface.DEFAULT_BOLD);
                h2.setText(t("Also remembered today"));
                h2.setPadding(0, dp(14), 0, dp(4));
                card.addView(h2);
                String[] lines = all.split("\n");
                for (int i = 1; i < lines.length; i++) {
                    TextView li = text(15, Color.WHITE, Typeface.DEFAULT);
                    li.setText("•  " + tidy(lines[i]));
                    li.setPadding(0, dp(4), 0, dp(4));
                    card.addView(li);
                }
            }
            TextView read = text(14.5f, 0xFFF2B640, Typeface.DEFAULT_BOLD);
            read.setText(t("Read today's Synaxarium  ›"));
            read.setPadding(0, dp(16), 0, dp(2));
            read.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Synax.url(ChurchActivity.this)))); } catch (Throwable ignored) { } } });
            card.addView(read);
            add(card);
        }

        // feasts coming up
        // the regular calendar right under the Coptic day: the month with feasts and your events, then your next events
        buildMonth();
        nextEvents();
        java.util.ArrayList<Church.Feast> fs = Church.upcoming(this, 14);
        if (!fs.isEmpty()) {
            LinearLayout card = card();
            TextView h = text(12.5f, 0xFFF2B640, Typeface.DEFAULT_BOLD);
            h.setText(t("Feasts and fasts ahead"));
            h.setPadding(0, 0, 0, dp(6));
            card.addView(h);
            Calendar day = Calendar.getInstance();
            for (Church.Feast f : fs) {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(7), 0, dp(7));
                Calendar on = (Calendar) day.clone(); on.add(Calendar.DAY_OF_MONTH, f.days);
                LinearLayout when = new LinearLayout(this);
                when.setOrientation(LinearLayout.VERTICAL);
                when.setGravity(Gravity.CENTER);
                TextView dd = text(19, f.days == 0 ? 0xFFF2B640 : Color.WHITE, Typeface.DEFAULT_BOLD);
                dd.setGravity(Gravity.CENTER);
                dd.setText(String.valueOf(on.get(Calendar.DAY_OF_MONTH)));
                when.addView(dd);
                TextView mm = text(11, 0xB3FFFFFF, Typeface.DEFAULT);
                mm.setGravity(Gravity.CENTER);
                mm.setText(new SimpleDateFormat("MMM", loc).format(on.getTime()).toUpperCase(loc));
                when.addView(mm);
                row.addView(when, new LinearLayout.LayoutParams(dp(46), -2));
                LinearLayout tx = new LinearLayout(this);
                tx.setOrientation(LinearLayout.VERTICAL);
                tx.setPadding(dp(12), 0, 0, 0);
                TextView n = text(15.5f, Color.WHITE, f.days == 0 ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                n.setText(f.in(lang));
                tx.addView(n);
                TextView w = text(12, 0xB3FFFFFF, Typeface.DEFAULT);
                w.setText(f.days == 0 ? t("Today") : f.days == 1 ? t("Tomorrow")
                        : String.format(loc, t("In %d days"), f.days)
                        + (orthodox ? "  ·  " + Church.copticDate(on, lang) : ""));
                tx.addView(w);
                row.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
                card.addView(row);
            }
            add(card);
        }
        if (orthodox) {
            LinearLayout card = card();
            TextView h = text(12.5f, 0xFFF2B640, Typeface.DEFAULT_BOLD);
            h.setText(t("Every Coptic month"));
            card.addView(h);
            TextView m = text(15, Color.WHITE, Typeface.DEFAULT);
            m.setText(ar ? "١٢: تذكار رئيس الملائكة ميخائيل\n٢١: تذكار السيدة العذراء\n٢٩: تذكار الميلاد والبشارة والقيامة"
                    : lang.equals("fr") ? "Le 12 : l'archange Michel\nLe 21 : la Vierge Marie, Mère de Dieu\nLe 29 : l'Annonciation, la Nativité et la Résurrection"
                    : lang.equals("es") ? "Día 12: el arcángel Miguel\nDía 21: la Virgen María, Madre de Dios\nDía 29: la Anunciación, la Natividad y la Resurrección"
                    : "12th: Archangel Michael\n21st: St. Mary, the Mother of God\n29th: the Annunciation, Nativity and Resurrection");
            m.setLineSpacing(dp(4), 1f);
            m.setPadding(0, dp(6), 0, 0);
            card.addView(m);
            add(card);
        }
        TextView src = text(11.5f, 0x99FFFFFF, Typeface.DEFAULT);
        src.setText(t("Synaxarium: CopticChurch.net"));
        src.setGravity(Gravity.CENTER);
        src.setPadding(0, dp(18), 0, 0);
        list.addView(src);
    }

    private static String tidy(String s) { return s.replaceFirst("^(?i)the\\s+", "").replaceAll("[.]$", ""); }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(0x5C0A1020); g.setCornerRadius(dp(24));
        c.setBackground(g);
        return c;
    }
    private void add(View v) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2); l.topMargin = dp(16);
        list.addView(v, l);
    }
    private TextView chip(String s) {
        TextView t = text(13, 0xFF141414, Typeface.DEFAULT_BOLD);
        t.setText(s);
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(0xFFFFC94A); g.setCornerRadius(dp(20));
        t.setBackground(g);
        return t;
    }
    private TextView text(float sp, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setShadowLayer(dp(3), 0, dp(1), 0x99000000);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // swipe right (the way it came) goes home
    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { x0 = e.getX(); y0 = e.getY(); }
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            float dx = e.getX() - x0, dy = e.getY() - y0;
            if (dx > dp(90) && Math.abs(dx) > Math.abs(dy) * 1.5f) { finish(); return true; }
        }
        return super.dispatchTouchEvent(e);
    }
    @Override public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right);
    }
    @Override protected void onStop() { super.onStop(); if (!isChangingConfigurations()) finish(); }
}
