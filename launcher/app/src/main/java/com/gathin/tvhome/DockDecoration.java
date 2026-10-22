package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

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

    /**
     * @param translucent the cinematic shelf's band: the artwork and the glow show through it.
     * @param names       the dock tiles show their names, so the band reaches down over them.
     */
    DockDecoration(int dockSize, float density, boolean translucent, boolean names) {
        this.dockSize = dockSize;
        this.density = density;
        this.namesRoom = names ? 22 * density : 0;
        // Modern translucent glass bar
        band.setColor(0x52121018);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(1.2f * density);
        edge.setColor(0x30FFFFFF);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        outRect.top = (int) (6 * density);
        outRect.bottom = (int) (14 * density);
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int padLeft = parent.getPaddingLeft();
        int padTop = parent.getPaddingTop();
        int padRight = parent.getPaddingRight();
        float left = padLeft + (4f * density);
        float right = parent.getWidth() - padRight - (4f * density);
        if (right <= left) return;

        float cardH = 101f * density;
        float barTop = padTop - (4f * density);
        float barBottom = barTop + cardH + namesRoom + (30f * density);

        rect.set(left, barTop, right, barBottom);
        float radius = 22f * density;
        c.drawRoundRect(rect, radius, radius, band);
        c.drawRoundRect(rect, radius, radius, edge);
    }
}
