package com.anyline.RNImageToPDF;

/**
 * Created by jonas on 23.08.17.
 */

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.pdf.PdfDocument;
import android.graphics.pdf.PdfDocument.Page;
import android.graphics.pdf.PdfDocument.PageInfo;
import android.graphics.pdf.PdfDocument.PageInfo.Builder;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.logging.Logger;

import static java.lang.String.format;


public class RNImageToPdf extends ReactContextBaseJavaModule {

    public static final String REACT_CLASS = "RNImageToPdf";

    private final ReactApplicationContext reactContext;
    private static final Logger log = Logger.getLogger(RNImageToPdf.REACT_CLASS);

    RNImageToPdf(ReactApplicationContext context) {
        super(context);
        this.reactContext = context;
    }

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @ReactMethod
    public void createPDFbyImages(ReadableMap options, final Promise promise) {
        ReadableArray images = options.getArray("imagePaths");

        String documentName = options.getString("name");

        final int dpi = options.hasKey("dpi") ? options.getInt("dpi") : 72;
        final float pdfScale = 72.f / dpi;

        ReadableMap maxSize = options.hasKey("maxSize") ? options.getMap("maxSize") : null;
        int maxHeight = maxSize != null && maxSize.hasKey("height") ? maxSize.getInt("height") : 0;
        int maxWidth = maxSize != null && maxSize.hasKey("width") ? maxSize.getInt("width") : 0;

        int quality = options.hasKey("quality") ? (int)Math.round(100 * options.getDouble("quality")) : 0;


        PdfDocument document = new PdfDocument();
        try {

            for (int idx = 0; idx < images.size(); idx++) {
                // get image
                Bitmap bmp = getImageFromFile(images.getString(idx));

                // resize
                bmp = resize(bmp, maxWidth, maxHeight);

                // compress
                bmp = compress(bmp, quality);
                bmp.setDensity(dpi);

                final int pageW = Math.round(bmp.getWidth()*pdfScale);
                final int pageH = Math.round(bmp.getHeight()*pdfScale);

                PageInfo pageInfo = new Builder(pageW, pageH, 1).create();

                // start a page
                Page page = document.startPage(pageInfo);

                // add image to page
                Canvas canvas = page.getCanvas();
                Matrix m = new Matrix();
                m.setScale(pdfScale, pdfScale);
                canvas.setMatrix(m);
                canvas.setDensity(dpi);
                canvas.drawBitmap(bmp, 0, 0, null);

                document.finishPage(page);
            }

            // write the document content
            File targetPath = reactContext.getExternalFilesDir(null);
            File filePath = new File(targetPath, documentName);
            document.writeTo(new FileOutputStream(filePath));
            log.info(format("Wrote %,d bytes to %s", filePath.length(), filePath.getPath()));
            WritableMap resultMap = Arguments.createMap();
            resultMap.putString("filePath", filePath.getAbsolutePath());
            promise.resolve(resultMap);
        } catch (Exception e) {
            promise.reject("failed", e);
        }

        // close the document
        document.close();
    }

    private Bitmap getImageFromFile(String path) throws IOException {
        if (path.startsWith("content://")) {
            return getImageFromContentResolver(path);
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        return BitmapFactory.decodeFile(path, options);
    }

    private Bitmap getImageFromContentResolver(String path) throws IOException {
        ParcelFileDescriptor parcelFileDescriptor = reactContext.getContentResolver().openFileDescriptor(Uri.parse(path), "r");
        FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
        parcelFileDescriptor.close();
        return image;
    }

    private Bitmap resize(Bitmap bitmap, int maxWidth, int maxHeight) {
        if (maxWidth == 0 || maxHeight == 0) return bitmap;
        if (bitmap.getWidth() <= maxWidth && bitmap.getHeight() <= maxHeight) return bitmap;

        double aspectRatio = (double) bitmap.getHeight() / bitmap.getWidth();
        int height = Math.round(maxWidth * aspectRatio) < maxHeight ? (int) Math.round(maxWidth * aspectRatio) : maxHeight;
        int width = (int) Math.round(height / aspectRatio);

        return Bitmap.createScaledBitmap(bitmap, width, height, true);
    }

    private Bitmap compress(Bitmap bmp, int quality) throws IOException {
        if (quality <= 0 || quality >= 100) return bmp;

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, stream);
        byte[] byteArray = stream.toByteArray();

        stream.close();
        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
    }

}
