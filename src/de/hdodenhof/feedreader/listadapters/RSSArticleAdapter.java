package de.hdodenhof.feedreader.listadapters;

import java.lang.ref.WeakReference;
import java.net.URL;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import de.hdodenhof.feedreader.R;
import de.hdodenhof.feedreader.provider.SQLiteHelper;
import de.hdodenhof.feedreader.provider.SQLiteHelper.ArticleDAO;

/**
 * 
 * @author Henning Dodenhof
 * 
 */
public class RSSArticleAdapter extends SimpleCursorAdapter implements RSSAdapter {

    @SuppressWarnings("unused")
    private static final String TAG = RSSArticleAdapter.class.getSimpleName();

    private int mLayout;
    private boolean mIncludeImages;

    public RSSArticleAdapter(Context context, int layout, Cursor c, String[] from, int[] to, int flags, boolean includeImages) {
        super(context, layout, c, from, to, flags);
        mLayout = layout;
        mIncludeImages = includeImages;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        String mTitle = cursor.getString(cursor.getColumnIndex(ArticleDAO.TITLE));
        String mSummary = cursor.getString(cursor.getColumnIndex(ArticleDAO.SUMMARY));
        String mImageURL = cursor.getString(cursor.getColumnIndex(ArticleDAO.IMAGE));
        int mRead = cursor.getInt(cursor.getColumnIndex(ArticleDAO.READ));

        final TextView mArticleTitle = (TextView) view.findViewById(R.id.list_item_entry_title);
        final TextView mArticleSummary = (TextView) view.findViewById(R.id.list_item_entry_summary);
        final ImageView mArticleImage = (ImageView) view.findViewById(R.id.list_item_entry_image);

        if (mArticleTitle != null) {
            mArticleTitle.setText(mTitle);
        }
        if (mArticleSummary != null) {
            mArticleSummary.setText(mSummary);
        }

        if (SQLiteHelper.toBoolean(mRead)) {
            mArticleTitle.setTextColor(Color.GRAY);
            mArticleSummary.setTextColor(Color.GRAY);
        } else {
            mArticleTitle.setTextColor(Color.BLACK);
            mArticleSummary.setTextColor(Color.BLACK);
        }

        if (mIncludeImages && mArticleImage != null && mImageURL != null && mImageURL != "") {
            if (cancelPotentialDownload(mImageURL, mArticleImage)) {
                ImageDownloaderTask mTask = new ImageDownloaderTask(mArticleImage);
                DownloadedDrawable mDownloadedDrawable = new DownloadedDrawable(mTask);
                mArticleImage.setImageDrawable(mDownloadedDrawable);
                mTask.execute(mImageURL);
            }
        }
    }

    @Override
    public Cursor swapCursor(Cursor c) {
        return super.swapCursor(c);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        final LayoutInflater mInflater = LayoutInflater.from(context);
        View mView = mInflater.inflate(mLayout, parent, false);

        if (mIncludeImages) {
            final ImageView mArticleImage = (ImageView) mView.findViewById(R.id.list_item_entry_image);
            mArticleImage.setVisibility(View.VISIBLE);
        }

        return mView;
    }

    public int getType() {
        return RSSAdapter.TYPE_ARTICLE;
    }

    public class ImageDownloaderTask extends AsyncTask<String, Void, Bitmap> {
        private final WeakReference<ImageView> mImageViewReference;
        private String mURL;

        public ImageDownloaderTask(ImageView imageView) {
            mImageViewReference = new WeakReference<ImageView>(imageView);
        }

        @Override
        protected Bitmap doInBackground(String... params) {
            mURL = params[0];
            try {
                return BitmapFactory.decodeStream(new URL(mURL).openConnection().getInputStream());
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (isCancelled()) {
                bitmap = null;
            }

            if (mImageViewReference != null) {
                ImageView mImageView = mImageViewReference.get();
                ImageDownloaderTask mImageDownloaderTask = getImageDownloaderTask(mImageView);
                if (this == mImageDownloaderTask) {
                    mImageView.setImageBitmap(bitmap);
                }
            }
        }
    }

    private static boolean cancelPotentialDownload(String url, ImageView imageView) {
        ImageDownloaderTask mImageDownloaderTask = getImageDownloaderTask(imageView);

        if (mImageDownloaderTask != null) {
            String mBitmapUrl = mImageDownloaderTask.mURL;
            if ((mBitmapUrl == null) || (!mBitmapUrl.equals(url))) {
                mImageDownloaderTask.cancel(true);
            } else {
                return false;
            }
        }
        return true;
    }

    private static ImageDownloaderTask getImageDownloaderTask(ImageView imageView) {
        if (imageView != null) {
            Drawable mDrawable = imageView.getDrawable();
            if (mDrawable instanceof DownloadedDrawable) {
                DownloadedDrawable mDownloadedDrawable = (DownloadedDrawable) mDrawable;
                return mDownloadedDrawable.getImageDownloaderTask();
            }
        }
        return null;
    }

    private static class DownloadedDrawable extends ColorDrawable {
        private final WeakReference<ImageDownloaderTask> mImageDownloaderTaskReference;

        public DownloadedDrawable(ImageDownloaderTask imageDownloaderTask) {
            super(Color.TRANSPARENT);
            mImageDownloaderTaskReference = new WeakReference<ImageDownloaderTask>(imageDownloaderTask);
        }

        public ImageDownloaderTask getImageDownloaderTask() {
            return mImageDownloaderTaskReference.get();
        }
    }

}
