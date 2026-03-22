package com.proyecto.moveon.ui.common.view;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * ImageView con zoom por pellizco y arrastre, sin dependencias externas.
 */
public class ZoomableImageView extends AppCompatImageView {

    private static final float MIN_SCALE = 1f;
    private static final float MAX_SCALE = 4f;

    private final Matrix matrix = new Matrix();
    private final ScaleGestureDetector scaleDetector;

    private float currentScale = 1f;
    private float lastTouchX;
    private float lastTouchY;
    private boolean isDragging;
    private boolean isLaidOut;

    public ZoomableImageView(@NonNull Context context) {
        this(context, null);
    }

    public ZoomableImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomableImageView(@NonNull Context context,
                             @Nullable AttributeSet attrs,
                             int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        post(this::fitImageToView);
    }

    @Override
    public void setImageURI(@Nullable Uri uri) {
        super.setImageURI(uri);
        post(this::fitImageToView);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        isLaidOut = w > 0 && h > 0;
        fitImageToView();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getDrawable() == null) return super.onTouchEvent(event);

        scaleDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                isDragging = false;
                getParent().requestDisallowInterceptTouchEvent(true);
                break;

            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1 && !scaleDetector.isInProgress()) {
                    float dx = event.getX() - lastTouchX;
                    float dy = event.getY() - lastTouchY;

                    if (!isDragging && (Math.abs(dx) > 2f || Math.abs(dy) > 2f)) {
                        isDragging = true;
                    }

                    if (currentScale > MIN_SCALE) {
                        matrix.postTranslate(dx, dy);
                        fixTranslation();
                        setImageMatrix(matrix);
                    }

                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                performClick();
                getParent().requestDisallowInterceptTouchEvent(false);
                break;
        }

        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void fitImageToView() {
        if (!isLaidOut) return;

        Drawable drawable = getDrawable();
        if (drawable == null) return;

        float availableWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        float availableHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        float drawableWidth = drawable.getIntrinsicWidth();
        float drawableHeight = drawable.getIntrinsicHeight();

        if (availableWidth <= 0 || availableHeight <= 0 || drawableWidth <= 0 || drawableHeight <= 0) {
            return;
        }

        matrix.reset();
        float scale = Math.min(availableWidth / drawableWidth, availableHeight / drawableHeight);
        float dx = getPaddingLeft() + (availableWidth - drawableWidth * scale) / 2f;
        float dy = getPaddingTop() + (availableHeight - drawableHeight * scale) / 2f;

        matrix.postScale(scale, scale);
        matrix.postTranslate(dx, dy);

        currentScale = 1f;
        setImageMatrix(matrix);
    }

    private void fixTranslation() {
        RectF rect = getMatrixRectF();
        if (rect == null) return;

        float deltaX = 0f;
        float deltaY = 0f;
        float contentLeft = getPaddingLeft();
        float contentTop = getPaddingTop();
        float contentRight = getWidth() - getPaddingRight();
        float contentBottom = getHeight() - getPaddingBottom();
        float contentWidth = contentRight - contentLeft;
        float contentHeight = contentBottom - contentTop;

        if (rect.width() <= contentWidth) {
            deltaX = contentLeft + (contentWidth - rect.width()) / 2f - rect.left;
        } else {
            if (rect.left > contentLeft) deltaX = contentLeft - rect.left;
            if (rect.right < contentRight) deltaX = contentRight - rect.right;
        }

        if (rect.height() <= contentHeight) {
            deltaY = contentTop + (contentHeight - rect.height()) / 2f - rect.top;
        } else {
            if (rect.top > contentTop) deltaY = contentTop - rect.top;
            if (rect.bottom < contentBottom) deltaY = contentBottom - rect.bottom;
        }

        matrix.postTranslate(deltaX, deltaY);
    }

    @Nullable
    private RectF getMatrixRectF() {
        Drawable drawable = getDrawable();
        if (drawable == null) return null;

        RectF rect = new RectF(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        matrix.mapRect(rect);
        return rect;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            float targetScale = currentScale * scaleFactor;

            if (targetScale > MAX_SCALE) {
                scaleFactor = MAX_SCALE / currentScale;
            } else if (targetScale < MIN_SCALE) {
                scaleFactor = MIN_SCALE / currentScale;
            }

            matrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
            currentScale *= scaleFactor;
            fixTranslation();
            setImageMatrix(matrix);
            return true;
        }
    }
}
