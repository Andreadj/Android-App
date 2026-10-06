package com.mobiled.android.base.component;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import com.mobiled.android.LogSystem;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

//https://redketchup.io/color-picker
public class ColorPickerView extends View {

    private static final float PI = 3.1415926f;
    private static float CENTER_X = 100;
    private static float CENTER_Y = 100;
    private static float CENTER_RADIUS = 32;
    private Paint mPaint;
    private Paint mCenterPaint;
    private int[] mColors;

    private float brightness = 1F;
    private int color;
    private int a = 255;
    private int r;
    private int g;
    private int b;

    private float[] hsv;
    private OnColorChangedListener colorChangedListener;
    private boolean mTrackingCenter;
    private boolean mHighlightCenter;

    public ColorPickerView(Context context) {
        super(context);
        bindUI();
    }

    public ColorPickerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        bindUI();
    }

    public ColorPickerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        bindUI();
    }

    public ColorPickerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        bindUI();
    }

    public static int getHue(int red, int green, int blue) {

        float min = Math.min(Math.min(red, green), blue);
        float max = Math.max(Math.max(red, green), blue);

        if (min == max) {
            return 0;
        }

        float hue = 0f;
        if (max == red) {
            hue = (green - blue) / (max - min);

        } else if (max == green) {
            hue = 2f + (blue - red) / (max - min);

        } else {
            hue = 4f + (red - green) / (max - min);
        }

        hue = hue * 60;
        if (hue < 0) hue = hue + 360;

        return Math.round(hue);
    }

    @NotNull
    public int[] toArgb() {
        return new int[]{a, r, g, b};
    }

    public void increaseHue() {
        adjustHue(Math.round(hsv[0]) + 1);
        mCenterPaint.setColor(color);
        mCenterPaint.setAlpha(a);
        invalidate();
        if (colorChangedListener != null)
            colorChangedListener.colorChanged(color, new int[]{a, r, g, b}, hsv);
    }

    public void decreaseHue() {
        adjustHue(Math.round(hsv[0]) - 1);
        mCenterPaint.setColor(color);
        mCenterPaint.setAlpha(a);
        invalidate();
        if (colorChangedListener != null)
            colorChangedListener.colorChanged(color, new int[]{a, r, g, b}, hsv);
    }

    public void setColorChangedListener(OnColorChangedListener colorChangedListener) {
        this.colorChangedListener = colorChangedListener;
    }

    public void bindUI() {
        mColors = new int[]{
                0xFFFF0000, 0xFFFF00FF, 0xFF0000FF, 0xFF00FFFF, 0xFF00FF00,
                0xFFFFFF00, 0xFFFF0000
        };
        Shader s = new SweepGradient(0, 0, mColors, null);

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setShader(s);
        mPaint.setStyle(Paint.Style.STROKE);

        mCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCenterPaint.setStrokeWidth(5);
        r = 255;
        b = 0;
        g = 0;
        bindColorFromRGB();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        CENTER_X = getWidth() * 0.5F;
        CENTER_Y = getWidth() * 0.5F;
        CENTER_RADIUS = getWidth() * 0.2F;
        mPaint.setStrokeWidth(CENTER_X * 0.5F);
        //float r = CENTER_X - mPaint.getStrokeWidth() * 0.5f;
        float r = CENTER_X - (mPaint.getStrokeWidth() * 0.5f);

        canvas.translate(CENTER_X, CENTER_X);

        //canvas.drawOval(new RectF(-r, -r, r, r), mPaint);
        canvas.drawCircle(0, 0, r, mPaint);

//        if(BuildConfig.DEBUG)
//        {
//            canvas.drawCircle(0, 0, r, mPaint);
//            return;
//        }

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(0, 0, CENTER_RADIUS, paint);


        if (mTrackingCenter) {
            mCenterPaint.setStyle(Paint.Style.STROKE);

//            if (mHighlightCenter) {
//                mCenterPaint.setAlpha(0xFF);
//            } else {
//                mCenterPaint.setAlpha(0x80);
//            }
            canvas.drawCircle(0, 0,
                    CENTER_RADIUS + mCenterPaint.getStrokeWidth(),
                    mCenterPaint);

            mCenterPaint.setStyle(Paint.Style.FILL);
        }
        else
        {
            canvas.drawCircle(0, 0, CENTER_RADIUS, mCenterPaint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int size = Math.min(widthSize, heightSize);
        int finalMeasureSpec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
        super.onMeasure(finalMeasureSpec, finalMeasureSpec);
    }

    private int floatToByte(float x) {
        int n = java.lang.Math.round(x);
        return n;
    }

    private int pinToByte(int n) {
        if (n < 0) {
            n = 0;
        } else if (n > 255) {
            n = 255;
        }
        return n;
    }

    private int ave(int s, int d, float p) {
        return s + java.lang.Math.round(p * (d - s));
    }

    private void interpColor(int colors[], float unit) {
        if (unit <= 0) {
            mCenterPaint.setColor(colors[0]);
        }
        if (unit >= 1) {
            mCenterPaint.setColor(colors[colors.length - 1]);
        }

        float p = unit * (colors.length - 1);
        int i = (int) p;
        p -= i;

        // now p is just the fractional part [0...1) and i is the index
        int c0 = colors[i];
        int c1 = colors[i + 1];
//        a = ave(Color.alpha(c0), Color.alpha(c1), p);
        r = ave(Color.red(c0), Color.red(c1), p);
        g = ave(Color.green(c0), Color.green(c1), p);
        b = ave(Color.blue(c0), Color.blue(c1), p);

        bindColorFromRGB();
    }

    private void bindColorFromRGB() {
        color = Color.argb(a, r, g, b);
        adjustHSL(color);
        mCenterPaint.setColor(color);
        mCenterPaint.setAlpha(a);
    }

    private void adjustHSL(int argb) {
        hsv = new float[3];    //Create an array to pass to the colorToHSV function
        Color.colorToHSV(argb, hsv);    //Put the HSV components in the array created above
        LogSystem.e("ColorPickerView", "adjustBrightness() called with: HSV = [" + hsv[0] + "," + hsv[1] + "," + hsv[2] + "]");
        hsv[1] = 1f;
        hsv[2] = brightness;    //Whatever brightness you want to set. 0 is black, 1 is the pure color.
        color = Color.HSVToColor(hsv);
        //ColorUtils.colorToHSL(color, hsv);
//        r = ((color >> 16) & 0xff);
//        g = ((color >>  8) & 0xff);
//        b = ((color      ) & 0xff);
//        a = ((color >> 24) & 0xff);
        //mCenterPaint.setColor(color);
    }

    private void adjustHue(float hue) {
        if (hue > 360.F) {
            hue = 1F;
        } else if (hue < 1F) {
            hue = 360F;
        }
        hsv[0] = hue;    //Whatever brightness you want to set. 0 is black, 1 is the pure color.
        color = Color.HSVToColor(hsv);
        r = Color.red(color);
        g = Color.green(color);
        b = Color.blue(color);
    }

    private int rotateColor(int color, float rad) {
        float deg = rad * 180 / 3.1415927f;
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);

        ColorMatrix cm = new ColorMatrix();
        ColorMatrix tmp = new ColorMatrix();

        cm.setRGB2YUV();
        tmp.setRotate(0, deg);
        cm.postConcat(tmp);
        tmp.setYUV2RGB();
        cm.postConcat(tmp);

        final float[] a = cm.getArray();

        int ir = floatToByte(a[0] * r + a[1] * g + a[2] * b);
        int ig = floatToByte(a[5] * r + a[6] * g + a[7] * b);
        int ib = floatToByte(a[10] * r + a[11] * g + a[12] * b);

        return Color.argb(Color.alpha(color), pinToByte(ir),
                pinToByte(ig), pinToByte(ib));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        getParent().requestDisallowInterceptTouchEvent(true);
        float x = event.getX() - CENTER_X;
        float y = event.getY() - CENTER_Y;
        boolean inCenter = java.lang.Math.hypot(x, y) <= CENTER_RADIUS;
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mTrackingCenter = inCenter;
                if (inCenter) {
                    mHighlightCenter = true;
                    invalidate();
                    break;
                }
            case MotionEvent.ACTION_MOVE:
                if (mTrackingCenter) {
                    if (mHighlightCenter != inCenter) {
                        mHighlightCenter = inCenter;
                        invalidate();
                        colorChangedListener.colorChanged(mCenterPaint.getColor(), new int[]{a, r, g, b}, hsv);
                    }
                } else {
                    float angle = (float) java.lang.Math.atan2(y, x);
                    // need to turn angle [-PI ... PI] into unit [0....1]
                    float unit = angle / (2 * PI);
                    if (unit < 0) {
                        unit += 1;
                    }
                    interpColor(mColors, unit);
                    colorChangedListener.colorChanged(mCenterPaint.getColor(), new int[]{a, r, g, b}, hsv);
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mTrackingCenter) {
                    mTrackingCenter = false;    // so we draw w/o halo
                    invalidate();
                }
                if (inCenter) {
                    colorChangedListener.colorChanged(mCenterPaint.getColor(), new int[]{a, r, g, b}, hsv);
                }
                break;
        }
        return true;
    }

    public void setBrightness(@FloatRange(from = 0.0F, to = 1.0F) float brightness) {
        this.brightness = brightness;
    }

    public void setBrightness(@IntRange(from = 0, to = 255) int brightness) {
        LogSystem.d("TAG", "setBrightness() called with: brightness = [" + brightness + "]");
        setBrightness(brightness / 100F);
        bindColorFromRGB();
        invalidate();
        if (colorChangedListener != null)
            colorChangedListener.colorChanged(color, new int[]{a, r, g, b}, hsv);
    }

    public void setColorAlpha(@IntRange(from = 0, to = 100) int alpha) {
        LogSystem.e("TAG", "setAlpha() called with: alpha = [" + alpha + "]");
        a = Math.round((alpha / 100F) * 255F);
        bindColorFromRGB();
        invalidate();
        if (colorChangedListener != null)
            colorChangedListener.colorChanged(color, new int[]{a, r, g, b}, hsv);
    }

    public void setColorAlpha(@FloatRange(from = 0, to = 1.0) float alpha) {
        LogSystem.e("AlphaEffect", "setAlpha() called with: alpha = [" + alpha + "]");
        a = Math.round((alpha * 255F));
        bindColorFromRGB();
        invalidate();
        if (colorChangedListener != null)
            colorChangedListener.colorChanged(color, new int[]{a, r, g, b}, hsv);
    }

    public float[] getHsv() {
        return hsv;
    }

    public void setColor(int a, int r, int g, int b) {
        this.a = a;
        this.r = r;
        this.g = g;
        this.b = b;
        hsv = new float[3];
        Color.colorToHSV(Color.argb(a, r, g, b), hsv);
        brightness = hsv[2];
        bindColorFromRGB();

        invalidate();
        if (colorChangedListener != null)
            colorChangedListener.colorChanged(color, new int[]{a, r, g, b}, hsv);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (w > h) {
            super.onSizeChanged(h, h, oldw, oldh);
        } else {
            super.onSizeChanged(w, w, oldw, oldh);
        }
    }


    public interface OnColorChangedListener {
        void colorChanged(int color, @NonNull int[] argb, float[] hsv);
    }


}