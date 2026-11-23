package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import java.util.function.IntSupplier;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Draws the tvOS style lighter band behind the first (dock) row and adds a gap below it. */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    /** Extra band below the tiles for their names (always_show_names), in px. */
    private final float namesRoom;
    /** Light glass like the top bar's pills (config.xml dock_glass): a white tint that fades downwards. */
    private final boolean lightGlass;
    /** Padding all round the tiles equal to the gap between them (config.xml dock_even_padding). */
    private final boolean evenPadding;
    /** The band's top and bottom the glass gradient was made for; it is remade when they move. */
    private float shaderTop = Float.NaN, shaderBottom = Float.NaN;
    /** Draw the band and its edge (config.xml dock_band); false leaves the tiles floating on the wallpaper. */
    private final boolean bandVisible;
    /** single_row: the tiles after the dock share its row, so they take the same offsets to stay level with it. */
    private final boolean singleRow;
    /** dock_band_all: in a single row, the band runs behind every app instead of the dock apps only. */
    private final boolean bandAll;
    /** input_group (h-group): the mode word, the grid's direction, and the live input and tile-width counts; see 19.15. */
    private final String groupMode;
    private final boolean rtl;
    private final IntSupplier inputCount, tileWidth;
    private final Paint divider = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerLine = new Paint();
    private float dividerTop = Float.NaN, dividerBottom = Float.NaN;
    private final RectF rectIn = new RectF(), rectApp = new RectF();

    /**
     * @param translucent the cinematic shelf's band: the artwork and the glow show through it.
     * @param names       the dock tiles show their names, so the band reaches down over them.
     * @param opacity     the plain band's opacity in percent (dock_opacity), so a wallpaper can show through it.
     * @param lightGlass  a light frosted band like the pills, for a sky or wallpaper behind it.
     * @param evenPadding the band's padding is the gap between the tiles on all four sides (dock_even_padding).
     * @param bandVisible false skips the band and its edge, so the tiles float on the wallpaper (dock_band).
     * @param singleRow   every app sits in the dock's row (single_row); the band covers the dock apps only, unless bandAll.
     * @param bandAll     with singleRow, the band runs behind every app in the row (dock_band_all).
     */
    DockDecoration(int dockSize, float density, boolean translucent, boolean names, int opacity, boolean lightGlass,
                   boolean evenPadding, boolean bandVisible, boolean singleRow, boolean bandAll,
                   String groupMode, boolean rtl, IntSupplier inputCount, IntSupplier tileWidth) {
        this.dockSize = dockSize;
        this.singleRow = singleRow;
        this.bandAll = singleRow && bandAll;
        this.density = density;
        this.groupMode = groupMode;
        this.rtl = rtl;
        this.inputCount = inputCount;
        this.tileWidth = tileWidth;
        dividerLine.setColor(GlassTint.INNER_SHADOW);
        this.namesRoom = names ? 22 * density : 0;
        this.lightGlass = lightGlass;
        this.evenPadding = evenPadding;
        this.bandVisible = bandVisible;
        band.setColor(translucent ? 0x8C121014 : (Math.round(opacity * 2.55f) << 24) | 0x141416);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
        edge.setColor(lightGlass ? 0x38FFFFFF : translucent ? 0x26FFFFFF : 0x1FFFFFFF);
    }

    /** Concentric corners: the band's radius is the tiles' radius plus the band's padding around them. */
    static float radiusPx(float tileRadiusPx, float paddingPx) {
        return tileRadiusPx + paddingPx;
    }

    /**
     * The band's padding on every side for dock_even_padding: the gap between two dock tiles (a cell minus its tile, as
     * AppAdapter.tileWidthPx lays them out), capped by the room the grid leaves at its edges.
     */
    static float evenPaddingPx(int innerWidthPx, int columns, int tileWidthPx, float roomPx) {
        float gap = columns > 0 ? innerWidthPx / (float) columns - tileWidthPx : 0f;
        return Math.max(0f, Math.min(gap, roomPx));
    }

    /** input_group: dp of extra room per side in divider mode, and the gap in separate mode (2 * 10 dp of band padding + 12 dp). */
    static final float DIVIDER_INSET_DP = 8f, SEPARATE_GAP_DP = 32f;

    /**
     * input_group: how much narrower each cell beside the group boundary is made on its boundary side, in px. 0 for off (or
     * anything unknown), 8 dp for divider, and for separate whatever lifts the gap between the last input tile and the first
     * app from the gutter {@code gutterPx} to 32 dp. Never negative. The two cells together widen the gap by this much.
     */
    static float groupInsetPx(String mode, float gutterPx, float density) {
        if (Config.INPUT_GROUP_DIVIDER.equals(mode)) return DIVIDER_INSET_DP * density;
        if (Config.INPUT_GROUP_SEPARATE.equals(mode)) return Math.max(0f, SEPARATE_GAP_DP * density - gutterPx);
        return 0f;
    }

    /** The room between two dock cells beyond their tile, in px: what input_group's inset is added to. Never negative. */
    static float gutterPx(int innerWidthPx, int columns, int tileWidthPx) {
        return columns > 0 ? Math.max(0f, innerWidthPx / (float) columns - tileWidthPx) : 0f;
    }

    /** Which side of a dock item the group inset goes on: true for the right. Inputs face the apps, apps face the inputs. */
    static boolean insetOnRight(boolean inputItem, boolean rtl) {
        return inputItem != rtl;
    }

    /** The x of the divider: the middle of the gap between the last input card and the first app card. */
    static float dividerX(float lastInputRight, float firstAppLeft) {
        return (lastInputRight + firstAppLeft) / 2f;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && (singleRow || pos < dockSize)) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (12 * density);
        }
        int ic = inputCount == null ? 0 : inputCount.getAsInt();
        if (ic > 0 && !singleRow && pos >= 0 && pos < dockSize && (divider() || separate())) {
            int inner = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
            int inset = Math.round(groupInsetPx(groupMode, gutterPx(inner, dockSize, tileWidth.getAsInt()), density));
            if (insetOnRight(pos < ic, rtl)) outRect.right = inset;
            else outRect.left = inset;
        }
    }

    private boolean divider() { return Config.INPUT_GROUP_DIVIDER.equals(groupMode); }

    private boolean separate() { return Config.INPUT_GROUP_SEPARATE.equals(groupMode); }

    private View cardOf(RecyclerView parent, View child) {
        RecyclerView.ViewHolder vh = parent.getChildViewHolder(child);
        return vh instanceof AppAdapter.Holder ? ((AppAdapter.Holder) vh).card : child.findViewById(R.id.card);
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int ic = inputCount == null ? 0 : inputCount.getAsInt();
        if (bandVisible) drawBand(c, parent, ic);
        if (ic > 0 && divider()) drawDivider(c, parent, ic);
    }

    /** input_group divider: a slim lit capsule in the middle of the wider gap between the last input and the first app. */
    private void drawDivider(Canvas c, RecyclerView parent, int ic) {
        View in = null, app = null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos == ic - 1) in = child;
            else if (pos == ic) app = child;
        }
        if (in == null || app == null) return;
        View a = cardOf(parent, in), b = cardOf(parent, app);
        if (a == null || b == null) return;
        float aEdge = rtl ? in.getLeft() + a.getLeft() : in.getLeft() + a.getLeft() + a.getWidth();
        float bEdge = rtl ? app.getLeft() + b.getLeft() + b.getWidth() : app.getLeft() + b.getLeft();
        float x = dividerX(aEdge, bEdge);
        float mid = in.getTop() + a.getTop() + a.getHeight() / 2f + in.getTranslationY();
        float half = a.getHeight() * 0.30f;
        float top = mid - half, bottom = mid + half, w = 2 * density;
        if (top != dividerTop || bottom != dividerBottom) {
            divider.setShader(new LinearGradient(0, top, 0, bottom, 0x66FFFFFF, 0x1AFFFFFF, Shader.TileMode.CLAMP));
            dividerTop = top;
            dividerBottom = bottom;
        }
        rect.set(x - w / 2, top, x + w / 2, bottom);
        c.drawRoundRect(rect, w / 2, w / 2, divider);
        // The 1 px dark line on the app side, as the glass rim's inner shadow.
        float line = rtl ? rect.left - 1 : rect.right;
        c.drawRect(line, top, line + 1, bottom, dividerLine);
    }

    /** input_group separate: each group in its own capsule, 10 dp round its cards, same fill and edge as the single band. */
    private void drawSeparate(Canvas c, RecyclerView parent, int cardWidth) {
        float pad = 10 * density;
        float radius = radiusPx(AppAdapter.cornerRadius(cardWidth, density), pad);
        RectF[] groups = {rectIn, rectApp};
        for (RectF g : groups) {
            float padL = Math.max(0f, Math.min(pad, g.left - density));
            float padR = Math.max(0f, Math.min(pad, parent.getWidth() - g.right - density));
            g.set(g.left - padL, g.top - pad, g.right + padR, g.bottom + pad + namesRoom);
        }
        if (lightGlass && (rectIn.top != shaderTop || rectIn.bottom != shaderBottom)) {
            band.setShader(new LinearGradient(0, rectIn.top, 0, rectIn.bottom, 0x24FFFFFF, 0x0DFFFFFF, Shader.TileMode.CLAMP));
            shaderTop = rectIn.top;
            shaderBottom = rectIn.bottom;
        }
        for (RectF g : groups) {
            c.drawRoundRect(g, radius, radius, band);
            c.drawRoundRect(g, radius, radius, edge);
        }
    }

    private void drawBand(Canvas c, RecyclerView parent, int ic) {
        boolean split = ic > 0 && separate() && !singleRow;
        boolean haveIn = false, haveApp = false;
        boolean any = false;
        int cardWidth = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || (!bandAll && pos >= dockSize)) continue;
            RecyclerView.ViewHolder vh = parent.getChildViewHolder(child);
            View card = vh instanceof AppAdapter.Holder ? ((AppAdapter.Holder) vh).card : child.findViewById(R.id.card);
            if (card == null) continue;
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            cardWidth = card.getWidth();
            if (split && pos < dockSize) {
                RectF g = pos < ic ? rectIn : rectApp;
                if (pos < ic ? haveIn : haveApp) g.union(l, t, r, b); else g.set(l, t, r, b);
                if (pos < ic) haveIn = true; else haveApp = true;
            }
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) return;
        if (split && haveIn && haveApp) {
            drawSeparate(c, parent, cardWidth);
            return;
        }
        float pad = 14 * density;
        // The band can only draw inside the grid, and the first and last dock tiles sit 12dp in from its
        // edges: a full 14dp at the sides sliced the rounded ends flat (#454). Keep the sides, with the
        // 1dp edge line, inside the grid; the top and bottom have room.
        // 22dp at the sides (the grid's 10dp padding makes room): a focused first or last tile grows about 7dp
        // outwards and still keeps about the band's top margin from its rounded end.
        float sidePad = 22 * density;
        float side = Math.max(0f, Math.min(sidePad, Math.min(rect.left, parent.getWidth() - rect.right) - density));
        float radius = AppAdapter.cornerRadius(cardWidth, density);
        if (evenPadding) {
            // The same padding on all four sides, so the band's corners are concentric with the tiles' everywhere.
            int inner = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
            // A single row's tiles can run off the screen, so only its top limits the padding; its ends are clamped below.
            float room = (singleRow ? rect.top : Math.min(rect.top, Math.min(rect.left, parent.getWidth() - rect.right))) - density;
            float even = evenPaddingPx(inner, dockSize, cardWidth, room);
            rect.inset(-even, -even);
            radius = radiusPx(radius, even);
        } else {
            // dock_even_padding off: the band as before, with its fixed 26 dp corners.
            rect.inset(singleRow ? -sidePad : -side, -pad);
            radius = 26 * density;
        }
        if (singleRow) {
            // Tiles past the screen's edge: the band stops at the grid's edge with its rounded end instead of being
            // sliced square there.
            rect.left = Math.max(rect.left, density);
            rect.right = Math.min(rect.right, parent.getWidth() - density);
        }
        rect.bottom += namesRoom;
        if (lightGlass && (rect.top != shaderTop || rect.bottom != shaderBottom)) {
            // The pills' glass (pill_bg: white, 18% fading to 8%), a little fainter over this much larger area.
            band.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, 0x24FFFFFF, 0x0DFFFFFF,
                    Shader.TileMode.CLAMP));
            shaderTop = rect.top;
            shaderBottom = rect.bottom;
        }
        c.drawRoundRect(rect, radius, radius, band);
        c.drawRoundRect(rect, radius, radius, edge);
    }
}
