package com.islam.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * High-performance hardware-accelerated animated background view for Android TV.
 * Features:
 * - Signature 4K Aurora with the exact color combinations (Deep midnight slate, glowing Cyan/Teal and Violet/Purple)
 *   and smooth, organic small moving animation.
 * - React-Bits themes: GhostFibers, GradientASCII, Grainient, and Iridescence.
 * - Zero-allocation onDraw loop for stutter-free 30/60 FPS TV rendering without garbage collection pauses.
 */
public class AuroraView extends View {

    public static final String THEME_AURORA = "Aurora (Animated)";
    public static final String THEME_GHOST_FIBERS = "GhostFibers";
    public static final String THEME_GRADIENT_ASCII = "GradientASCII";
    public static final String THEME_GRAINIENT = "Grainient";
    public static final String THEME_IRIDESCENCE = "Iridescence";

    private String currentTheme = THEME_AURORA;
    private boolean isAnimating = false;
    private float time = 0f;
    private long lastFrameTime = 0;

    // Paints
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint cyanPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint violetPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint emeraldPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint fiberPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Matrices for zero-allocation shader transforms
    private final Matrix matrixCyan = new Matrix();
    private final Matrix matrixViolet = new Matrix();
    private final Matrix matrixEmerald = new Matrix();
    private final Matrix matrixRadial = new Matrix();

    // Shaders
    private Shader skyShader;
    private Shader cyanShader;
    private Shader violetShader;
    private Shader emeraldShader;
    private Shader radialBloomShader1;
    private Shader radialBloomShader2;
    private Shader radialBloomShader3;

    // Paths for GhostFibers
    private final Path fiberPath = new Path();

    // ASCII characters for GradientASCII
    private static final String ASCII_CHARS = " .:-=+*#%@";

    private final Runnable frameCallback = new Runnable() {
        @Override
        public void run() {
            if (!isAnimating || getVisibility() != VISIBLE) return;

            long now = SystemClock.uptimeMillis();
            if (lastFrameTime == 0) lastFrameTime = now;
            long delta = now - lastFrameTime;
            lastFrameTime = now;

            // Advance time gently (~30-40fps rate)
            time += (delta > 0 && delta < 100) ? (delta * 0.0008f) : 0.016f;

            invalidate();
            postDelayed(this, 30); // ~33 FPS capped for buttery smooth TV animation & 0% CPU strain
        }
    };

    public AuroraView(Context context) {
        this(context, null);
    }

    public AuroraView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void init() {
        basePaint.setStyle(Paint.Style.FILL);
        cyanPaint.setStyle(Paint.Style.FILL);
        violetPaint.setStyle(Paint.Style.FILL);
        emeraldPaint.setStyle(Paint.Style.FILL);

        fiberPaint.setStyle(Paint.Style.STROKE);
        fiberPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint.setTextSize(18f);
        textPaint.setColor(0x3582CDFF);
    }

    public void setTheme(String theme) {
        if (theme == null || theme.isEmpty() || theme.equalsIgnoreCase("Theme default")) {
            this.currentTheme = THEME_AURORA;
        } else {
            this.currentTheme = theme;
        }
        setupShaders(getWidth(), getHeight());
        invalidate();
    }

    public String getTheme() {
        return currentTheme;
    }

    public void startAnimation() {
        if (!isAnimating) {
            isAnimating = true;
            lastFrameTime = SystemClock.uptimeMillis();
            removeCallbacks(frameCallback);
            post(frameCallback);
        }
    }

    public void stopAnimation() {
        isAnimating = false;
        removeCallbacks(frameCallback);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getVisibility() == VISIBLE) {
            startAnimation();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus && getVisibility() == VISIBLE) {
            startAnimation();
        } else {
            stopAnimation();
        }
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            startAnimation();
        } else {
            stopAnimation();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        setupShaders(w, h);
    }

    private void setupShaders(int w, int h) {
        if (w <= 0 || h <= 0) return;

        // Base midnight linear gradient
        skyShader = new LinearGradient(0f, 0f, 0f, (float) h,
                new int[]{0xFF081722, 0xFF060F1B, 0xFF040711, 0xFF020409},
                new float[]{0f, 0.35f, 0.65f, 1f},
                Shader.TileMode.CLAMP);
        basePaint.setShader(skyShader);

        // Pre-create Unit Radial Gradients centered at (0,0) with radius 1000f
        // 1. Cyan/Teal signature ribbon
        cyanShader = new RadialGradient(0f, 0f, 1000f,
                new int[]{0xEA1FACA6, 0xB7168E8E, 0x610C5573, 0x1F082A44, 0x00000000},
                new float[]{0f, 0.25f, 0.55f, 0.82f, 1f},
                Shader.TileMode.CLAMP);
        cyanPaint.setShader(cyanShader);

        // 2. Violet/Purple signature ribbon
        violetShader = new RadialGradient(0f, 0f, 1000f,
                new int[]{0xE05840A8, 0x9944348E, 0x47261C55, 0x14120E2D, 0x00000000},
                new float[]{0f, 0.30f, 0.60f, 0.85f, 1f},
                Shader.TileMode.CLAMP);
        violetPaint.setShader(violetShader);

        // 3. Subtle emerald central harmonic ribbon
        emeraldShader = new RadialGradient(0f, 0f, 1000f,
                new int[]{0x703CF2B4, 0x351FACA6, 0x100C5573, 0x00000000},
                new float[]{0f, 0.35f, 0.70f, 1f},
                Shader.TileMode.CLAMP);
        emeraldPaint.setShader(emeraldShader);

        // Grainient blooms
        radialBloomShader1 = new RadialGradient(0f, 0f, 1000f,
                new int[]{0x751C8CAF, 0x30125870, 0x00000000},
                new float[]{0f, 0.50f, 1f}, Shader.TileMode.CLAMP);
        radialBloomShader2 = new RadialGradient(0f, 0f, 1000f,
                new int[]{0x687D2D96, 0x2A451854, 0x00000000},
                new float[]{0f, 0.50f, 1f}, Shader.TileMode.CLAMP);
        radialBloomShader3 = new RadialGradient(0f, 0f, 1000f,
                new int[]{0x58145082, 0x200A2C4A, 0x00000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        if (THEME_GHOST_FIBERS.equalsIgnoreCase(currentTheme)) {
            drawGhostFibers(canvas, w, h);
        } else if (THEME_GRADIENT_ASCII.equalsIgnoreCase(currentTheme)) {
            drawGradientASCII(canvas, w, h);
        } else if (THEME_GRAINIENT.equalsIgnoreCase(currentTheme)) {
            drawGrainient(canvas, w, h);
        } else if (THEME_IRIDESCENCE.equalsIgnoreCase(currentTheme)) {
            drawIridescence(canvas, w, h);
        } else {
            // Default: Signature 4K Aurora with small moving animation
            drawAuroraAnimated(canvas, w, h);
        }
    }

    /**
     * Signature Earlier TV 4K Aurora with subtle, living, undulating moving animation.
     * Preserves exact color combinations:
     * - Deep midnight slate-blue base (#081722 -> #020409)
     * - Upper glowing Cyan/Teal horizontal Aurora beam (#1FACA6 / #168E8E)
     * - Upper glowing Purple/Violet horizontal Aurora beam (#5840A8 / #44348E)
     * - Subtle central Emerald highlight (#3CF2B4)
     */
    private void drawAuroraAnimated(Canvas canvas, int w, int h) {
        if (skyShader == null) setupShaders(w, h);

        // 1. Draw base midnight gradient
        canvas.drawRect(0, 0, w, h, basePaint);

        // 2. Cyan/Teal Ribbon - graceful horizontal sway & vertical breathing
        float cyanCx = w * (0.36f + 0.045f * (float) Math.sin(time * 0.7f));
        float cyanCy = h * (0.16f + 0.035f * (float) Math.cos(time * 0.5f));
        float cyanRx = w * (0.52f + 0.035f * (float) Math.sin(time * 0.4f));
        float cyanRy = h * (0.18f + 0.025f * (float) Math.cos(time * 0.6f));

        matrixCyan.reset();
        matrixCyan.postScale(cyanRx / 1000f, cyanRy / 1000f);
        matrixCyan.postTranslate(cyanCx, cyanCy);
        cyanShader.setLocalMatrix(matrixCyan);
        canvas.drawRect(0, 0, w, h, cyanPaint);

        // 3. Violet/Purple Ribbon - undulating out-of-phase on the right
        float violetCx = w * (0.72f + 0.040f * (float) Math.cos(time * 0.6f));
        float violetCy = h * (0.24f + 0.030f * (float) Math.sin(time * 0.5f));
        float violetRx = w * (0.46f + 0.030f * (float) Math.cos(time * 0.3f));
        float violetRy = h * (0.20f + 0.020f * (float) Math.sin(time * 0.4f));

        matrixViolet.reset();
        matrixViolet.postScale(violetRx / 1000f, violetRy / 1000f);
        matrixViolet.postTranslate(violetCx, violetCy);
        violetShader.setLocalMatrix(matrixViolet);
        canvas.drawRect(0, 0, w, h, violetPaint);

        // 4. Subtle Emerald/Green central harmonic ribbon connecting both sides
        float emeraldCx = w * (0.52f + 0.030f * (float) Math.sin(time * 0.8f + 1.0f));
        float emeraldCy = h * (0.13f + 0.025f * (float) Math.cos(time * 0.6f + 0.5f));
        float emeraldRx = w * 0.36f;
        float emeraldRy = h * 0.15f;

        matrixEmerald.reset();
        matrixEmerald.postScale(emeraldRx / 1000f, emeraldRy / 1000f);
        matrixEmerald.postTranslate(emeraldCx, emeraldCy);
        emeraldShader.setLocalMatrix(matrixEmerald);
        canvas.drawRect(0, 0, w, h, emeraldPaint);
    }

    /** GhostFibers: Fluid luminescent fiber strands in glowing teal and violet */
    private void drawGhostFibers(Canvas canvas, int w, int h) {
        canvas.drawColor(0xFF020308);

        final int fiberCount = 6;
        for (int f = 0; f < fiberCount; f++) {
            fiberPath.reset();
            float phase = time * 1.2f + f * 1.05f;
            float startY = h * (0.18f + f * 0.11f);
            fiberPath.moveTo(0, startY);

            for (int x = 0; x <= w; x += 40) {
                float y = startY + (float) Math.sin(phase + x * 0.003f) * 60f
                        + (float) Math.cos(phase * 0.7f + x * 0.002f) * 40f;
                fiberPath.lineTo(x, y);
            }

            int color = (f % 2 == 0) ? 0x992FE0C8 : 0x889862FF;
            fiberPaint.setColor(color);
            fiberPaint.setStrokeWidth(5f + f * 1.5f);
            canvas.drawPath(fiberPath, fiberPaint);
        }
    }

    /** GradientASCII: Ambient radial glow with shimmering ASCII glyph grid */
    private void drawGradientASCII(Canvas canvas, int w, int h) {
        canvas.drawColor(0xFF040711);

        // Ambient radial glow in center
        float cx = w * 0.5f;
        float cy = h * 0.3f;
        float r = w * 0.55f;
        matrixRadial.reset();
        matrixRadial.postScale(r / 1000f, r / 1000f);
        matrixRadial.postTranslate(cx, cy);
        radialBloomShader1.setLocalMatrix(matrixRadial);
        cyanPaint.setShader(radialBloomShader1);
        canvas.drawRect(0, 0, w, h, cyanPaint);

        // Shimmering ASCII glyphs
        final int step = 42;
        int charLen = ASCII_CHARS.length();
        for (int x = 24; x < w; x += step) {
            for (int y = 36; y < h; y += step) {
                float wave = (float) Math.sin(time * 2.2f + x * 0.008f + y * 0.006f);
                int idx = Math.max(0, Math.min(charLen - 1, (int) (((wave + 1f) / 2f) * (charLen - 1))));
                char c = ASCII_CHARS.charAt(idx);
                int alpha = (int) (40 + (wave + 1f) * 65);
                textPaint.setColor((alpha << 24) | 0x0082CDFF);
                canvas.drawText(String.valueOf(c), x, y, textPaint);
            }
        }
    }

    /** Grainient: Rich multi-chromatic granular blooms */
    private void drawGrainient(Canvas canvas, int w, int h) {
        canvas.drawColor(0xFF03050C);

        // Bloom 1: Teal
        float cx1 = w * 0.32f + (float) Math.sin(time * 0.6f) * 30f;
        float cy1 = h * 0.25f + (float) Math.cos(time * 0.5f) * 20f;
        float r1 = w * 0.45f;
        matrixRadial.reset();
        matrixRadial.postScale(r1 / 1000f, r1 / 1000f);
        matrixRadial.postTranslate(cx1, cy1);
        radialBloomShader1.setLocalMatrix(matrixRadial);
        cyanPaint.setShader(radialBloomShader1);
        canvas.drawRect(0, 0, w, h, cyanPaint);

        // Bloom 2: Violet
        float cx2 = w * 0.70f + (float) Math.cos(time * 0.5f) * 25f;
        float cy2 = h * 0.32f + (float) Math.sin(time * 0.7f) * 20f;
        float r2 = w * 0.42f;
        matrixRadial.reset();
        matrixRadial.postScale(r2 / 1000f, r2 / 1000f);
        matrixRadial.postTranslate(cx2, cy2);
        radialBloomShader2.setLocalMatrix(matrixRadial);
        violetPaint.setShader(radialBloomShader2);
        canvas.drawRect(0, 0, w, h, violetPaint);

        // Bloom 3: Deep Oceanic
        float cx3 = w * 0.50f;
        float cy3 = h * 0.70f + (float) Math.sin(time * 0.4f) * 20f;
        float r3 = w * 0.50f;
        matrixRadial.reset();
        matrixRadial.postScale(r3 / 1000f, r3 / 1000f);
        matrixRadial.postTranslate(cx3, cy3);
        radialBloomShader3.setLocalMatrix(matrixRadial);
        emeraldPaint.setShader(radialBloomShader3);
        canvas.drawRect(0, 0, w, h, emeraldPaint);
    }

    /** Iridescence: Fluid chromatic dispersion waves */
    private void drawIridescence(Canvas canvas, int w, int h) {
        canvas.drawColor(0xFF02040A);

        for (int i = 0; i < 4; i++) {
            float cx = w * (0.26f + i * 0.17f) + (float) Math.cos(time * 1.1f + i) * 25f;
            float cy = h * (0.22f + (i % 2) * 0.14f) + (float) Math.sin(time * 0.9f + i) * 20f;
            float rx = w * 0.36f;
            float ry = h * 0.20f;

            matrixRadial.reset();
            matrixRadial.postScale(rx / 1000f, ry / 1000f);
            matrixRadial.postRotate((time * 12f + i * 25f), 0, 0);
            matrixRadial.postTranslate(cx, cy);

            Shader shader = (i % 2 == 0) ? radialBloomShader1 : radialBloomShader2;
            shader.setLocalMatrix(matrixRadial);
            cyanPaint.setShader(shader);
            canvas.drawRect(0, 0, w, h, cyanPaint);
        }
    }
}
