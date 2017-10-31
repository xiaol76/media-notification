package com.example.remoteviewdemo;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.graphics.Palette;
import android.util.LayoutDirection;
import android.view.View;
import android.widget.Button;
import android.widget.RemoteViews;

import java.util.List;

import static android.support.v4.graphics.ColorUtils.calculateLuminance;
import static com.example.remoteviewdemo.NotificationColorUtil.satisfiesTextContrast;


public class MainActivity extends AppCompatActivity {
    private static final int RESIZE_BITMAP_AREA = 150 * 150;
    private static final int LIGHTNESS_TEXT_DIFFERENCE_LIGHT = 20;
    public static final int COLOR_INVALID = 1;
    private float[] mFilteredBackgroundHsl = null;
    private static final int LIGHTNESS_TEXT_DIFFERENCE_DARK = -10;
    private static final float POPULATION_FRACTION_FOR_WHITE_OR_BLACK = 2.5f;
    private static final float BLACK_MAX_LIGHTNESS = 0.08f;
    private static final float WHITE_MIN_LIGHTNESS = 0.90f;
    private static final float POPULATION_FRACTION_FOR_MORE_VIBRANT = 1.0f;
    private static final float MIN_SATURATION_WHEN_DECIDING = 0.19f;
    private static final double MINIMUM_IMAGE_FRACTION = 0.002;
    private static final float POPULATION_FRACTION_FOR_DOMINANT = 0.01f;
    private Palette.Filter mBlackWhiteFilter = new Palette.Filter() {
        @Override
        public boolean isAllowed(int rgb, float[] hsl) {
            return !isWhiteOrBlack(hsl);
        }
    };

    private int mBackgroundColor;
    private int mForegroundColor;
    private int mPrimaryTextColor;
    private int mSecondaryTextColor;
    private Bitmap mBitmap;
    private int mTextColorsAreForBackground = COLOR_INVALID;
    private final ImageGradientColorizer mColorizer = new ImageGradientColorizer();

    private int mIconArray[] = {R.drawable.large_icon, R.drawable.large_icon1, R.drawable.large_icon2, R.drawable.large_icon3};

    private static final int REQUEST_CODE_STORAGE_ACCESS = 40;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button openRemoteView = findViewById(R.id.open_remote);
        openRemoteView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int id = 0;
                RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.layout_notification);
                Bitmap bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.drawColor(getBackgroundColor(R.drawable.large_icon4));
                ensureColors();
                remoteViews.setImageViewBitmap(R.id.background, bitmap);
                remoteViews.setTextViewText(R.id.title, "My Notification");
                remoteViews.setTextColor(R.id.title, mPrimaryTextColor);
                remoteViews.setTextViewText(R.id.content, "Hello World");
                remoteViews.setTextColor(R.id.content, mSecondaryTextColor);
                remoteViews.setImageViewBitmap(R.id.icon, mBitmap);
                Notification notification = new Notification.Builder(MainActivity.this)
                        .setContent(remoteViews)
                        .setStyle(new Notification.BigPictureStyle())
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .build();
                NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                manager.notify(id, notification);
            }
        });

        Button openDocment = findViewById(R.id.open_document);
        openDocment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    triggerStorageAccessFramework();
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void triggerStorageAccessFramework() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, REQUEST_CODE_STORAGE_ACCESS);
    }

    private int getBackgroundColor(int resourceId) {
        Drawable drawable = getResources().getDrawable(resourceId);
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        int area = width * height;
        if (area > RESIZE_BITMAP_AREA) {
            double factor = Math.sqrt((float) RESIZE_BITMAP_AREA / area);
            width = (int) (factor * width);
            height = (int) (factor * height);
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);
        Palette.Builder paletteBuilder = Palette.from(bitmap)
                .setRegion(0, 0, bitmap.getWidth() / 2, bitmap.getHeight())
                .clearFilters() // we want all colors, red / white / black ones too!
                .resizeBitmapArea(RESIZE_BITMAP_AREA);
        Palette palette = paletteBuilder.generate();
        mBackgroundColor = findBackgroundColorAndFilter(palette);
        float textColorStartWidthFraction = 0.4f;
        paletteBuilder.setRegion((int) (bitmap.getWidth() * textColorStartWidthFraction), 0,
                bitmap.getWidth(),
                bitmap.getHeight());
        if (mFilteredBackgroundHsl != null) {
            paletteBuilder.addFilter(new Palette.Filter() {
                @Override
                public boolean isAllowed(int rgb, float[] hsl) {
                    float diff = Math.abs(hsl[0] - mFilteredBackgroundHsl[0]);
                    return diff > 10 && diff < 350;
                }
            });
        }
        paletteBuilder.addFilter(mBlackWhiteFilter);
        palette = paletteBuilder.generate();
        mForegroundColor = selectForegroundColor(mBackgroundColor, palette);
        mBitmap = mColorizer.colorize(drawable, mBackgroundColor,
                getResources().getConfiguration().getLayoutDirection() ==
                        LayoutDirection.RTL);
        return mBackgroundColor;
    }

    private int findBackgroundColorAndFilter(Palette palette) {
        // by default we use the dominant palette
        Palette.Swatch dominantSwatch = palette.getDominantSwatch();
        if (dominantSwatch == null) {
            // We're not filtering on white or black
            mFilteredBackgroundHsl = null;
            return Color.WHITE;
        }

        if (!isWhiteOrBlack(dominantSwatch.getHsl())) {
            mFilteredBackgroundHsl = dominantSwatch.getHsl();
            return dominantSwatch.getRgb();
        }
        // Oh well, we selected black or white. Lets look at the second color!
        List<Palette.Swatch> swatches = palette.getSwatches();
        float highestNonWhitePopulation = -1;
        Palette.Swatch second = null;
        for (Palette.Swatch swatch : swatches) {
            if (swatch != dominantSwatch
                    && swatch.getPopulation() > highestNonWhitePopulation
                    && !isWhiteOrBlack(swatch.getHsl())) {
                second = swatch;
                highestNonWhitePopulation = swatch.getPopulation();
            }
        }
        if (second == null) {
            // We're not filtering on white or black
            mFilteredBackgroundHsl = null;
            return dominantSwatch.getRgb();
        }
        if (dominantSwatch.getPopulation() / highestNonWhitePopulation
                > POPULATION_FRACTION_FOR_WHITE_OR_BLACK) {
            // The dominant swatch is very dominant, lets take it!
            // We're not filtering on white or black
            mFilteredBackgroundHsl = null;
            return dominantSwatch.getRgb();
        } else {
            mFilteredBackgroundHsl = second.getHsl();
            return second.getRgb();
        }
    }

    private boolean isWhiteOrBlack(float[] hsl) {
        return isBlack(hsl) || isWhite(hsl);
    }

    /**
     * @return true if the color represents a color which is close to black.
     */
    private boolean isBlack(float[] hslColor) {
        return hslColor[2] <= BLACK_MAX_LIGHTNESS;
    }

    /**
     * @return true if the color represents a color which is close to white.
     */
    private boolean isWhite(float[] hslColor) {
        return hslColor[2] >= WHITE_MIN_LIGHTNESS;
    }

    private int selectForegroundColor(int backgroundColor, Palette palette) {
        if (isColorLight(backgroundColor)) {
            return selectForegroundColorForSwatches(palette.getDarkVibrantSwatch(),
                    palette.getVibrantSwatch(),
                    palette.getDarkMutedSwatch(),
                    palette.getMutedSwatch(),
                    palette.getDominantSwatch(),
                    Color.BLACK);
        } else {
            return selectForegroundColorForSwatches(palette.getLightVibrantSwatch(),
                    palette.getVibrantSwatch(),
                    palette.getLightMutedSwatch(),
                    palette.getMutedSwatch(),
                    palette.getDominantSwatch(),
                    Color.WHITE);
        }
    }

    public static boolean isColorLight(int backgroundColor) {
        return calculateLuminance(backgroundColor) > 0.5f;
    }

    private int selectForegroundColorForSwatches(Palette.Swatch moreVibrant,
                                                 Palette.Swatch vibrant, Palette.Swatch moreMutedSwatch, Palette.Swatch mutedSwatch,
                                                 Palette.Swatch dominantSwatch, int fallbackColor) {
        Palette.Swatch coloredCandidate = selectVibrantCandidate(moreVibrant, vibrant);
        if (coloredCandidate == null) {
            coloredCandidate = selectMutedCandidate(mutedSwatch, moreMutedSwatch);
        }
        if (coloredCandidate != null) {
            if (dominantSwatch == coloredCandidate) {
                return coloredCandidate.getRgb();
            } else if ((float) coloredCandidate.getPopulation() / dominantSwatch.getPopulation()
                    < POPULATION_FRACTION_FOR_DOMINANT
                    && dominantSwatch.getHsl()[1] > MIN_SATURATION_WHEN_DECIDING) {
                return dominantSwatch.getRgb();
            } else {
                return coloredCandidate.getRgb();
            }
        } else if (hasEnoughPopulation(dominantSwatch)) {
            return dominantSwatch.getRgb();
        } else {
            return fallbackColor;
        }
    }

    private Palette.Swatch selectMutedCandidate(Palette.Swatch first,
                                                Palette.Swatch second) {
        boolean firstValid = hasEnoughPopulation(first);
        boolean secondValid = hasEnoughPopulation(second);
        if (firstValid && secondValid) {
            float firstSaturation = first.getHsl()[1];
            float secondSaturation = second.getHsl()[1];
            float populationFraction = first.getPopulation() / (float) second.getPopulation();
            if (firstSaturation * populationFraction > secondSaturation) {
                return first;
            } else {
                return second;
            }
        } else if (firstValid) {
            return first;
        } else if (secondValid) {
            return second;
        }
        return null;
    }

    private Palette.Swatch selectVibrantCandidate(Palette.Swatch first, Palette.Swatch second) {
        boolean firstValid = hasEnoughPopulation(first);
        boolean secondValid = hasEnoughPopulation(second);
        if (firstValid && secondValid) {
            int firstPopulation = first.getPopulation();
            int secondPopulation = second.getPopulation();
            if (firstPopulation / (float) secondPopulation
                    < POPULATION_FRACTION_FOR_MORE_VIBRANT) {
                return second;
            } else {
                return first;
            }
        } else if (firstValid) {
            return first;
        } else if (secondValid) {
            return second;
        }
        return null;
    }

    private boolean hasEnoughPopulation(Palette.Swatch swatch) {
        // We want a fraction that is at least 1% of the image
        return swatch != null
                && (swatch.getPopulation() / (float) RESIZE_BITMAP_AREA > MINIMUM_IMAGE_FRACTION);
    }

    private void ensureColors() {
        int backgroundColor = mBackgroundColor;
        if (mPrimaryTextColor == COLOR_INVALID
                || mSecondaryTextColor == COLOR_INVALID
                || mTextColorsAreForBackground != backgroundColor) {
            mTextColorsAreForBackground = backgroundColor;
            if (mForegroundColor != COLOR_INVALID) {
                double backLum = NotificationColorUtil.calculateLuminance(backgroundColor);
                double textLum = NotificationColorUtil.calculateLuminance(mForegroundColor);
                double contrast = NotificationColorUtil.calculateContrast(mForegroundColor,
                        backgroundColor);
                // We only respect the given colors if worst case Black or White still has
                // contrast
                boolean backgroundLight = backLum > textLum
                        && satisfiesTextContrast(backgroundColor, Color.BLACK)
                        || backLum <= textLum
                        && !satisfiesTextContrast(backgroundColor, Color.WHITE);
                if (contrast < 4.5f) {
                    if (backgroundLight) {
                        mSecondaryTextColor = NotificationColorUtil.findContrastColor(
                                mForegroundColor,
                                backgroundColor,
                                true /* findFG */,
                                4.5f);
                        mPrimaryTextColor = NotificationColorUtil.changeColorLightness(
                                mSecondaryTextColor, -LIGHTNESS_TEXT_DIFFERENCE_LIGHT);
                    } else {
                        mSecondaryTextColor =
                                NotificationColorUtil.findContrastColorAgainstDark(
                                        mForegroundColor,
                                        backgroundColor,
                                        true /* findFG */,
                                        4.5f);
                        mPrimaryTextColor = NotificationColorUtil.changeColorLightness(
                                mSecondaryTextColor, -LIGHTNESS_TEXT_DIFFERENCE_DARK);
                    }
                } else {
                    mPrimaryTextColor = mForegroundColor;
                    mSecondaryTextColor = NotificationColorUtil.changeColorLightness(
                            mPrimaryTextColor, backgroundLight ? LIGHTNESS_TEXT_DIFFERENCE_LIGHT
                                    : LIGHTNESS_TEXT_DIFFERENCE_DARK);
                    if (NotificationColorUtil.calculateContrast(mSecondaryTextColor,
                            backgroundColor) < 4.5f) {
                        // oh well the secondary is not good enough
                        if (backgroundLight) {
                            mSecondaryTextColor = NotificationColorUtil.findContrastColor(
                                    mSecondaryTextColor,
                                    backgroundColor,
                                    true /* findFG */,
                                    4.5f);
                        } else {
                            mSecondaryTextColor
                                    = NotificationColorUtil.findContrastColorAgainstDark(
                                    mSecondaryTextColor,
                                    backgroundColor,
                                    true /* findFG */,
                                    4.5f);
                        }
                        mPrimaryTextColor = NotificationColorUtil.changeColorLightness(
                                mSecondaryTextColor, backgroundLight
                                        ? -LIGHTNESS_TEXT_DIFFERENCE_LIGHT
                                        : -LIGHTNESS_TEXT_DIFFERENCE_DARK);
                    }
                }
            }
        }
    }
}
