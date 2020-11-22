package com.anyline.RNImageToPDF;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.pdf.PdfDocument;
import android.os.AsyncTask;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static java.lang.String.format;

public class CreatePDFAsyncTask extends AsyncTask<Void, Void, WritableMap> {


    private final ReactApplicationContext mContext;
    private final Promise mPromise;
    private final ReadableMap mOptions;

    public CreatePDFAsyncTask(ReactApplicationContext context, Promise promise, ReadableMap options) {
        mContext = context;
        mPromise = promise;
        mOptions = options;
    }

    @Override
    protected WritableMap doInBackground(Void... ignored) {
        if (isCancelled()) return null;

        ReadableArray images = mOptions.getArray("imagePaths");

        String documentName = mOptions.getString("name");

        ReadableMap maxSize = mOptions.hasKey("maxSize") ? mOptions.getMap("maxSize") : null;
        int maxHeight = maxSize != null && maxSize.hasKey("height") ? maxSize.getInt("height") : 0;
        int maxWidth = maxSize != null && maxSize.hasKey("width") ? maxSize.getInt("width") : 0;

        int quality = mOptions.hasKey("quality") ? (int) Math.round(100 * mOptions.getDouble("quality")) : 0;

        PdfDocument document = new PdfDocument();

        WritableMap result = Arguments.createMap();

        try {
            for (int idx = 0; idx < images.size(); idx++) {

                // get image
                Bitmap bmp = BitmapUtils.getImageFromFile(images.getString(idx), mContext);

                // resize
                bmp = BitmapUtils.resize(bmp, maxWidth, maxHeight);

                // compress
                bmp = BitmapUtils.compress(bmp, quality);

                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(bmp.getWidth(), bmp.getHeight(), 1).create();

                // start a page
                PdfDocument.Page page = document.startPage(pageInfo);

                // add image to page
                Canvas canvas = page.getCanvas();
                canvas.drawBitmap(bmp, 0, 0, null);

                document.finishPage(page);

                if (isCancelled()) {
                    document.close();
                    return null;
                }
            }

            // write the document content
            File targetPath = mContext.getExternalFilesDir(null);
            File filePath = new File(targetPath, documentName);
            document.writeTo(new FileOutputStream(filePath));

            result.putString("filePath", filePath.getAbsolutePath());
        } catch (IOException e) {
            document.close();
            return null;
        }

        document.close();

        return result;
    }

    @Override
    protected void onPostExecute(WritableMap result) {
        super.onPostExecute(result);

        if (result == null) {
            mPromise.reject("no.result", "No result");
        }
        mPromise.resolve(result);
    }
}
