package com.islam.tv;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Draws the translucent pinned bar behind the first (dock) row, sized dynamically to the app tile size. */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    /** Extra band below the tiles for their names (always_show_names), in px. */
    private final float namesRoom;

    private float cachedCardW = 0;
    private float cachedCardH = 0;

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

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            View card = child.findViewById(R.id.card);
            if (card != null && card.getHeight() > 0) {
                cachedCardH = Math.max(cachedCardH, card.getHeight());
                cachedCardW = Math.max(cachedCardW, card.getWidth());
                break;
            }
        }

        float barTop;
        float barBottom;
        float radius;

        if (cachedCardH > 0) {
            // The dock bar stays completely fixed at the top dock slot (padTop + 6dp offset)
            float cardSlotTop = padTop + (6f * density) + (8f * density); // 6dp outRect.top + 8dp item padding
            float cardSlotBottom = cardSlotTop + cachedCardH;
            barTop = cardSlotTop - (10f * density);
            barBottom = cardSlotBottom + namesRoom + (14f * density);
            radius = AppAdapter.cornerRadius((int) cachedCardW, density) + (6f * density);
        } else {
            int tileW = AppAdapter.tileWidthPx(parent.getWidth() - padLeft - padRight, dockSize, density);
            int tileH = AppAdapter.tileHeightPx(tileW);
            barTop = padTop - (4f * density);
            barBottom = barTop + tileH + namesRoom + (24f * density);
            radius = 20f * density;
        }

        rect.set(left, barTop, right, barBottom);
        c.drawRoundRect(rect, radius, radius, band);
        c.drawRoundRect(rect, radius, radius, edge);
    }
}
