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
 * {@link AppCompatImageView} con zoom por pellizco y arrastre sin dependencias externas.
 *
 * <p>La vista mantiene una matriz propia, reencuadra el drawable al cambiar de tamaño y limita
 * tanto la escala como la traslación para que la imagen no abandone el área visible.</p>
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

    /**
     * Crea la vista a partir de código usando la configuración por defecto.
     *
     * @param context contexto Android propietario de la vista.
     */
    public ZoomableImageView(@NonNull Context context) {
        this(context, null);
    }

    /**
     * Crea la vista a partir de XML conservando el conjunto de atributos declarado.
     *
     * @param context contexto Android propietario de la vista.
     * @param attrs atributos XML asociados a la instancia.
     */
    public ZoomableImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Crea la vista configurando la matriz base y el detector de escala que gobernará los gestos.
     *
     * @param context contexto Android propietario de la vista.
     * @param attrs atributos XML asociados a la instancia.
     * @param defStyleAttr estilo por defecto aplicado por Android.
     */
    public ZoomableImageView(@NonNull Context context,
                             @Nullable AttributeSet attrs,
                             int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    /**
     * Sustituye el drawable actual y reencuadra la imagen para que vuelva a ajustarse a la
     * vista antes de aplicar nuevos gestos de zoom.
     *
     * @param drawable recurso visual a mostrar, o {@code null} para limpiar la imagen.
     */
    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        post(this::fitImageToView);
    }

    /**
     * Carga una imagen a partir de su {@link Uri} y recalcula la matriz base una vez que la
     * vista ya conoce su tamaño.
     *
     * @param uri ubicación de la imagen a mostrar, o {@code null} para vaciar el contenido.
     */
    @Override
    public void setImageURI(@Nullable Uri uri) {
        super.setImageURI(uri);
        post(this::fitImageToView);
    }

    /**
     * Detecta cambios de tamaño en la vista para volver a centrar la imagen con la escala base.
     *
     * @param w nuevo ancho disponible.
     * @param h nuevo alto disponible.
     * @param oldw ancho anterior.
     * @param oldh alto anterior.
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        isLaidOut = w > 0 && h > 0;
        fitImageToView();
    }

    /**
     * Procesa pellizcos y arrastres sobre la imagen, delegando el zoom en
     * {@link ScaleGestureDetector} y limitando el paneo al área visible.
     *
     * @param event evento táctil recibido por la vista.
     * @return {@code true} cuando la vista consume la interacción para seguir gestionando el zoom.
     */
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

    /**
     * Conserva el contrato de clic de {@link AppCompatImageView} aunque la vista consuma los
     * gestos táctiles para el zoom.
     *
     * @return el resultado de {@link AppCompatImageView#performClick()}.
     */
    @Override
    public boolean performClick() {
        return super.performClick();
    }

    /**
     * Calcula la escala base que encaja el drawable dentro del contenido útil de la vista y
     * reinicia cualquier transformación previa de zoom o arrastre.
     */
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

    /**
     * Corrige la traslación acumulada para que la imagen ampliada no deje huecos vacíos ni se
     * desplace fuera de los límites visibles.
     */
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

    /**
     * Obtiene el rectángulo actual del drawable después de aplicar la matriz de transformación.
     *
     * @return límites transformados de la imagen, o {@code null} si no hay drawable cargado.
     */
    @Nullable
    private RectF getMatrixRectF() {
        Drawable drawable = getDrawable();
        if (drawable == null) return null;

        RectF rect = new RectF(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        matrix.mapRect(rect);
        return rect;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        /**
         * Ajusta la escala actual alrededor del punto de foco del gesto sin sobrepasar los
         * límites definidos por {@link #MIN_SCALE} y {@link #MAX_SCALE}.
         *
         * @param detector detector que aporta factor y punto focal del gesto de pinch.
         * @return {@code true} para indicar que el gesto de escala ha sido procesado.
         */
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
