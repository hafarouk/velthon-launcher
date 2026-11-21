package com.velthon.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

/**
 * The home screen area: one grid. Widgets take blocks of cells (as small as one icon), app icons fill the free cells.
 * Children are added with {@link #putIcon} / {@link #putWidget}; their cell is kept in the tag.
 */
class Desk extends ViewGroup {
    static final class Cell { int index; boolean widget; int height, cols, start, row = -1, gx = -1, gy, gw, gh; Object item; }
    int columns = 5;
    private int cellW, cellH, iconTop;
    private int highlight = -1;
    private final Paint hl = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    Desk(Context c) {
        super(c);
        setWillNotDraw(false);
        hl.setStyle(Paint.Style.STROKE);
        hl.setStrokeWidth(U.dp(c, 1.6f));
        hl.setColor(0xCCFFC400);
        setClipChildren(false);
    }

    int rows() { return cellH == 0 ? 0 : Math.max(0, getHeight() / cellH); }
    int capacity() { return free.size(); }
    /** Grid cells not covered by a widget, in reading order: icon number k sits in free.get(k). */
    private final ArrayList<int[]> free = new ArrayList<>();

    void putIcon(View v, int index) {
        Cell c = new Cell(); c.index = index; c.item = v.getTag();
        v.setTag(R_CELL, c);
        addView(v);
    }
    int cellWidth() { return cellW; }
    int cellHeight() { return cellH; }
    void putWidget(View v, int height) { putWidget(v, height, 0, 0); }
    /** A widget: its height, and how many columns wide it is (0 = the full width) starting at column {@code start}. */
    void putWidget(View v, int height, int cols, int start) { putWidget(v, height, cols, start, -1); }
    /** ... and the row the user moved it to ({@code row} -1 = wherever it fits, from the top). */
    void putWidget(View v, int height, int cols, int start, int row) {
        Cell c = new Cell(); c.widget = true; c.height = height; c.cols = cols; c.start = start; c.row = row;
        v.setTag(R_CELL, c);
        addView(v);
    }
    static final int R_CELL = 0x7f0f0001;
    static Cell cell(View v) { return (Cell) v.getTag(R_CELL); }

    void clearIcons() {
        for (int i = getChildCount() - 1; i >= 0; i--) { Cell c = cell(getChildAt(i)); if (c != null && !c.widget) removeViewAt(i); }
    }
    void clearWidgets() {
        for (int i = getChildCount() - 1; i >= 0; i--) { Cell c = cell(getChildAt(i)); if (c != null && c.widget) removeViewAt(i); }
    }

    /** The icon place under (x, y) in this view's coordinates, or -1 (outside, or on a widget). */
    int cellAt(float x, float y) {
        if (cellW == 0 || cellH == 0) return -1;
        int col = (int) (x / cellW), row = (int) (y / cellH);
        for (int k = 0; k < free.size(); k++) if (free.get(k)[0] == row && free.get(k)[1] == col) return k;
        return -1;
    }
    void highlight(int index) { if (index != highlight) { highlight = index; invalidate(); } }

    @Override protected void onMeasure(int ws, int hs) {
        int w = MeasureSpec.getSize(ws), h = MeasureSpec.getSize(hs);
        setMeasuredDimension(w, h);
        cellW = w / columns;
        // rows stretch to fill the whole area down to the dock: no strip left that cannot hold apps
        int base = U.dpi(getContext(), 86), least = U.dpi(getContext(), 66);   // a second row fits as soon as there is room for it
        int rows = Math.max(1, Math.min(Math.round(h / (float) base), Math.max(1, h / least)));
        cellH = Math.max(1, h / rows);
        boolean[][] used = new boolean[rows][columns];
        int gap = U.dpi(getContext(), 8);
        // widgets first: each takes a block of cells (its columns x its rows), packed from the top
        // widgets the user placed somewhere go first, so they get that place; the rest fill in from the top
        for (int k = 0; k < getChildCount() * 2; k++) {
            int i = k % getChildCount();
            View v = getChildAt(i); Cell c = cell(v);
            if (c == null || !c.widget) continue;
            if ((c.row >= 0) != (k < getChildCount())) continue;
            int cols = c.cols > 0 && c.cols < columns ? c.cols : columns;
            int rr = Math.max(1, Math.min(rows, Math.round((c.height + U.dp(getContext(), 6)) / (float) base)));
            int want = Math.max(0, Math.min(columns - cols, c.start));
            c.gx = -1;
            if (c.row >= 0) {   // the place the user moved it to, when it is free
                int y = Math.min(c.row, rows - rr);
                boolean ok = y >= 0;
                for (int yy = y; yy < y + rr && ok; yy++) for (int xx = want; xx < want + cols; xx++) if (used[yy][xx]) { ok = false; break; }
                if (ok) {
                    for (int yy = y; yy < y + rr; yy++) for (int xx = want; xx < want + cols; xx++) used[yy][xx] = true;
                    c.gx = want; c.gy = y; c.gw = cols; c.gh = rr;
                }
            }
            outer:
            for (int y = 0; c.gx < 0 && y + rr <= rows; y++) {
                for (int pass = 0; pass < 2; pass++) {
                    for (int x0 = 0; x0 + cols <= columns; x0++) {
                        int x = pass == 0 ? want : x0;
                        if (pass == 0 && x0 > 0) break;
                        boolean ok = true;
                        for (int yy = y; yy < y + rr && ok; yy++) for (int xx = x; xx < x + cols; xx++) if (used[yy][xx]) { ok = false; break; }
                        if (ok) {
                            for (int yy = y; yy < y + rr; yy++) for (int xx = x; xx < x + cols; xx++) used[yy][xx] = true;
                            c.gx = x; c.gy = y; c.gw = cols; c.gh = rr;
                            break outer;
                        }
                    }
                }
            }
            if (c.gx < 0) { v.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY)); continue; }
            v.measure(MeasureSpec.makeMeasureSpec(cols * cellW - gap, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(rr * cellH - gap, MeasureSpec.EXACTLY));
        }
        free.clear();
        for (int y = 0; y < rows; y++) for (int x = 0; x < columns; x++) if (!used[y][x]) free.add(new int[] {y, x});
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i); Cell c = cell(v);
            if (c == null || !c.widget) v.measure(MeasureSpec.makeMeasureSpec(cellW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(cellH, MeasureSpec.EXACTLY));
        }
        iconTop = 0;
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int half = U.dpi(getContext(), 4);
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i); Cell c = cell(v);
            if (c != null && c.widget) {
                if (c.gx < 0) { v.layout(0, 0, 0, 0); v.setVisibility(INVISIBLE); continue; }
                v.setVisibility(VISIBLE);
                int x = c.gx * cellW + half, y = c.gy * cellH + half;
                v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
            } else if (c != null) {
                if (c.index < 0 || c.index >= free.size()) { v.layout(0, 0, 0, 0); v.setVisibility(INVISIBLE); continue; }
                v.setVisibility(VISIBLE);
                int[] p = free.get(c.index);
                int x = p[1] * cellW, y = p[0] * cellH;
                v.layout(x, y, x + cellW, y + cellH);
            }
        }
    }

    @Override protected void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);
        if (highlight >= 0 && cellW > 0) {
            if (highlight >= free.size()) return;
            int row = free.get(highlight)[0], col = free.get(highlight)[1];
            float in = U.dp(getContext(), 6);
            r.set(col * cellW + in, row * cellH + in, (col + 1) * cellW - in, (row + 1) * cellH - in);
            c.drawRoundRect(r, U.dp(getContext(), 18), U.dp(getContext(), 18), hl);
        }
    }
}
