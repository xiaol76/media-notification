## Android O Media Notification图片颜色渐变实现

### 实现效果

![背景虚化效果](http://note.youdao.com/yws/api/personal/file/WEB278da27ebeddf9408af864e4bf05b0b9?method=download&shareKey=096cbe1bdd9a3b436c8c3734ae1d2665)

实现分为3部分：1.取背景色前景色、2.根据背景色前景色计算背景上的文字颜色、 3.根据背景色对图片进行模糊着色处理，这种效果可以用来实现换肤或者和更加复杂的视觉，实现方案如下：

- 前景色用来生成背景上的文字颜色，前景色和背景色对比度大于4.5时使用前景色作为文字颜色，小于4.5时根据前景色进行运算直到找到和背景色对比度大于4.5的颜色作为文字颜色
- 背景色用来作为背景view的backgroundColor，模糊是对图片进行模糊处理，如图：

![示意图](http://on-img.com/chart_image/59f71bf6e4b0edf0e25ca034.png)

- 图片模糊过程主要分为三个步骤

  1）使用LinearGradient和DST_IN将原图画成透明度从0.0f到0.4f到1.0f的图像

  2）将paint的colorFilter设置目标颜色为背景色减去原来图像上每个像素的亮度和背景色亮度的差值的颜色，paint的透明度50%，使用paint将变化后图像画到新的bitmap上

  3）使用LinearGradient和DST_IN在1）的图片上再次绘制，将图片渐变调整为从0.0f到0.6f到1.0f，然后绘制到新的bitmap上

  新的bitmap就是我们需要的模糊渐变图片

为方便理解首先了解下HSL色彩空间的一些概念，以下来自维基百科

> HSL即色相、饱和度、亮度（英语：Hue, Saturation, Lightness），又称HSL。
色相（H）是色彩的基本属性，就是平常所说的颜色名称，如红色、黄色等。
饱和度（S）是指色彩的纯度，越高色彩越纯，低则逐渐变灰，取0-100%的数值。
明度（V），亮度（L），取0-100%。


### 背景色

1.主背景色通过support包中的`Palette`实现，实现思路是使用`Palette`对图片进行取样，从图片左侧40%区域的颜色取出使用最多的一种颜色，如果这种颜色是黑色或者白色，就取使用第二多的颜色，实现源码：
```
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
```
其中对黑色和白色的判断代码
```
private static final float BLACK_MAX_LIGHTNESS = 0.08f;
private static final float WHITE_MIN_LIGHTNESS = 0.90f;
    
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
```

### 前景色
前景色获取的思路是首先过滤掉黑色白色，过滤掉和背景色的色相角相差小于10度的颜色，然后按照像素数从多到少和背景色是亮色还是暗色选取鲜明的暗色或者亮色，如果鲜明的暗色或者亮色是主色调就选中，如果不是需要判断当前颜色在主色调中的占比，如果占比超过0.01小于0.01并且主色调纯度超过0.19就选择主色调，否则看当前颜色整个占比是不是超过1%，不超过的话就选择黑色或者白色了，具体代码实现：
```
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
```
判断当前颜色是亮色还是暗色代码：
```
    public static boolean isColorLight(int backgroundColor) {
        return calculateLuminance(backgroundColor) > 0.5f;
    }

```

### 背景上的文字颜色
接下来就是使用背景色和前景色生成文字颜色了，获取背景色上文字颜色的核心思想是保证文字颜色和背景色能有足够的对比度，根据[w3c标准](https://www.w3.org/TR/WCAG20-TECHS/G18.html)对于较小的字体（小于 18 磅的常规字体或 14 磅的加粗字体）对比度应该在4.5以上（[Android doc](https://support.google.com/accessibility/android/answer/7158390?hl=zh-Hans)），所以根据计算如果前景色和背景色对比度大于4.5，文字颜色即为前景色，如果小于4.5会在前景色的基础上根据背景色找出对比度大于4.5的颜色。代码实现：
```
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
```
以上就可以确定图片背景色和文字颜色了，接下来需要处理图片边缘模糊

### 图片边缘模糊
图片边缘模糊实现方式是使用黑色百分之五十透明度的LinearGradient配合`PorterDuff.Mode.DST_IN`对图片实现模糊渐变效果，然后使用`paint colorfilter`在模糊后的bitmap上使用原图在进行绘制，根据模糊后的图片bitmap上不同像素的颜色根据背景色增加明度，具体实现代码：
```
public Bitmap colorize(Drawable drawable, int backgroundColor, boolean isRtl) {
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        int size = Math.min(width, height);
        int widthInset = (width - size) / 2;
        int heightInset = (height - size) / 2;
        drawable = drawable.mutate();
        drawable.setBounds(- widthInset, - heightInset, width - widthInset, height - heightInset);
        Bitmap newBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(newBitmap);

        // Values to calculate the luminance of a color
        float lr = 0.2126f;
        float lg = 0.7152f;
        float lb = 0.0722f;

        // Extract the red, green, blue components of the color extraction color in
        // float and int form
        int tri = Color.red(backgroundColor);
        int tgi = Color.green(backgroundColor);
        int tbi = Color.blue(backgroundColor);

        float tr = tri / 255f;
        float tg = tgi / 255f;
        float tb = tbi / 255f;

        // Calculate the luminance of the color extraction color
        float cLum = (tr * lr + tg * lg + tb * lb) * 255;

        ColorMatrix m = new ColorMatrix(new float[] {
                lr, lg, lb, 0, tri - cLum,
                lr, lg, lb, 0, tgi - cLum,
                lr, lg, lb, 0, tbi - cLum,
                0, 0, 0, 1, 0,
        });

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        LinearGradient linearGradient =  new LinearGradient(0, 0, size, 0,
                new int[] {0, Color.argb(127, 255, 255, 255), Color.BLACK},
                new float[] {0.0f, 0.4f, 1.0f}, Shader.TileMode.CLAMP);
        paint.setShader(linearGradient);
        Bitmap fadeIn = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas fadeInCanvas = new Canvas(fadeIn);
        drawable.clearColorFilter();
        drawable.draw(fadeInCanvas);

        if (isRtl) {
            // Let's flip the gradient
            fadeInCanvas.translate(size, 0);
            fadeInCanvas.scale(-1, 1);
        }
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        fadeInCanvas.drawPaint(paint);

        Paint coloredPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        coloredPaint.setColorFilter(new ColorMatrixColorFilter(m));
        coloredPaint.setAlpha((int) (0.5f * 255));
        canvas.drawBitmap(fadeIn, 0, 0, coloredPaint);

        linearGradient =  new LinearGradient(0, 0, size, 0,
                new int[] {0, Color.argb(127, 255, 255, 255), Color.BLACK},
                new float[] {0.0f, 0.6f, 1.0f}, Shader.TileMode.CLAMP);
        paint.setShader(linearGradient);
        fadeInCanvas.drawPaint(paint);
        canvas.drawBitmap(fadeIn, 0, 0, null);

        return newBitmap;
    }
```

相关API：[ColorMatrix](http://blog.csdn.net/sjf0115/article/details/8698619) [PorterDuff.Mode](http://www.jianshu.com/p/d11892bbe055) [LinearGradient](http://blog.csdn.net/u012702547/article/details/50821044)