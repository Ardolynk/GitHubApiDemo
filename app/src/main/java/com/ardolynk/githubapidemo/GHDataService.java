package com.ardolynk.githubapidemo;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.util.LruCache;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NetworkImageView;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>This service is used to download project list data and avatar images</p>
 * <p>On binding, you should put your {@link ResultReceiver} as an intent extras
 * with {@link GHDataService#RECEIVER} key</p>
 *<p>The receiver bundle contains following extras
 *  <ul>
 *      <li>{@link GHDataService#RESULT_DATA} Downloaded data as a list of {@link com.ardolynk.githubapidemo.GHData.Item} objects</li>
 *      <li>{@link GHDataService#RESULT_FLAGS} is an integer value that contains following bits
 *          <ul>
 *              <li>{@link GHDataService#FLAG_APPEND} shows we have the next page data portion
 *              (otherwise our list should be cleared first)</li>
 *              <li>{@link GHDataService#FLAG_HAS_NEXT} shows we probably have next page to load</li>
 *              <li>{@link GHDataService#FLAG_CONDITION_CHANGED} shows the search string was different
 *              than previous one</li>
 *              <li>{@link GHDataService#FLAG_ERROR} shows that some error occurred, and we got no valid data</li>
 *          </ul>
 *      </li>
 *  </ul>
 * </p>
 *
 */
public class GHDataService extends Service {

    private final static String URL_TEMPLATE = "https://api.github.com/search/repositories?q=%s%%20language:Kotlin&sort=stars&order=desc&page=%d&per_page=%d";

    public final static String RECEIVER = "com.ardolynk.githubapidemo.RECEIVER";
    public final static String RESULT_DATA = "com.ardolynk.githubapidemo.RESULT_DATA";
    public final static String RESULT_FLAGS = "com.ardolynk.githubapidemo.RESULT_FLAGS";

    public final static int FLAG_APPEND = 1;
    public final static int FLAG_HAS_NEXT = FLAG_APPEND << 1;
    public final static  int FLAG_CONDITION_CHANGED = FLAG_HAS_NEXT << 1;
    public final static  int FLAG_ERROR = FLAG_CONDITION_CHANGED << 1;

    public final static int LIST_LOAD_FINISHED = 0;
    public final static int PER_PAGE = 30;
    public final static int STATUS_FORBIDDEN = 403;

    private ArrayList<GHData.Item> mRecentData;
    private int mPageNum;
    private boolean mHasNext;
    private StringRequest mListRequest;
    private RequestQueue mRequestQueue;
    private ImageLoader mImageLoader;
    private ResultReceiver mListLoadReceiver;
    private String mActualSearchString;
    private String mRecentSearchString;
    private Gson mGson = new Gson();

    @Override
    public void onCreate() {
        mRecentSearchString = "";
        mPageNum = 0;
        mRequestQueue = Volley.newRequestQueue(this);
        mImageLoader = new ImageLoader(mRequestQueue, new ImageLoader.ImageCache() {
            private final LruCache<String, Bitmap> mCache = new LruCache<String, Bitmap>(16);

            @Override
            public Bitmap getBitmap(String url) {
                return mCache.get(url);
            }

            @Override
            public void putBitmap(String url, Bitmap bitmap) {
                mCache.put(url, bitmap);
            }
        });
    }

    @Override
    public IBinder onBind(Intent intent) {
        mListLoadReceiver = (ResultReceiver) intent.getParcelableExtra(RECEIVER);
        return new LocalBinder();
    }


    /**
     * @return Number of data pages loaded from server
     */
    public int getPageNum() {
        return mPageNum;
    }

    /**
     * @return Number of items on each loaded page
     */
    public int getPageSize() {
        return PER_PAGE;
    }

    /**
     * @return Recently downloaded data (i.e. the last page content)
     */
    public List<GHData.Item> getRecentData() {
        return mRecentData;
    }

    /**
     * @return The most recent successful search string
     */
    public String getRecentSearchString() {
        return mRecentSearchString;
    }

    /**
     * @return True if there's probably next page available to load
     */
    public boolean hasNext() {
        return mHasNext;
    }

    /**
     * Initiates loading a first portion of project list
     *
     * @param searchString A keyword to search projects (show everything if null or empty)
     */
    public void startLoadingList(String searchString) {
        mActualSearchString = (searchString != null ? searchString : "");
        processLoadingList(false, searchString);
    }

    /**
     * Initiates loading a next portion of project list (i.e. the next page)
     *
     * @see GHDataService#getPageNum()
     * @see GHDataService#getPageSize()
     */
    public void continueLoadingList() {
        processLoadingList(true, mRecentSearchString);
    }

    private void processLoadingList(final boolean continueLoading, String searchString) {
        final int nextPageNum = (continueLoading ? mPageNum + 1 : 1);
        String url = String.format(URL_TEMPLATE, mActualSearchString, nextPageNum, PER_PAGE);
        if (mListRequest != null) {
            mListRequest.cancel();
        }
        mListRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        final Bundle resultBundle = new Bundle();
                        int resultFlags = 0;
                        try {
                            final ArrayList<GHData.Item> newData = mGson.fromJson(response, GHData.class).getItems();
                            resultBundle.putSerializable(RESULT_DATA, newData);
                            if (newData.size() == PER_PAGE) {
                                mHasNext = true;
                                resultFlags |= FLAG_HAS_NEXT;
                            }
                            if (!mActualSearchString.equals(mRecentSearchString)) {
                                resultFlags |= FLAG_CONDITION_CHANGED;
                            }

                            mRecentData = newData;
                            mRecentSearchString = mActualSearchString;
                            if (!continueLoading) {
                                mPageNum = 0;
                            }
                            mPageNum++;
                        }
                        catch(JsonSyntaxException exception) {
                            Toast.makeText(GHDataService.this, R.string.parse_error, Toast.LENGTH_LONG).show();
                        }

                        if (continueLoading) {
                            resultFlags |= FLAG_APPEND;
                        }
                        resultBundle.putInt(RESULT_FLAGS, resultFlags);
                        mListLoadReceiver.send(LIST_LOAD_FINISHED, resultBundle);

                        mListRequest = null;
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        final Bundle resultBundle = new Bundle();
                        int resultFlags = FLAG_ERROR;
                        if (!mActualSearchString.equals(mRecentSearchString)) {
                            resultFlags |= FLAG_CONDITION_CHANGED;
                        }
                        if (continueLoading) {
                            resultFlags |= FLAG_APPEND;
                        }
                        resultBundle.putInt(RESULT_FLAGS, resultFlags);
                        final int errorStringResource = (error.networkResponse.statusCode == STATUS_FORBIDDEN ?
                                R.string.forbidden_error : R.string.network_error);
                        Toast.makeText(GHDataService.this, errorStringResource, Toast.LENGTH_LONG).show();
                        mListLoadReceiver.send(LIST_LOAD_FINISHED, resultBundle);

                        mRecentSearchString = mActualSearchString;
                        mListRequest = null;
                    }
                });

        mRequestQueue.add(mListRequest);
    }

    /**
     * Loads an avatar instantly into {@link NetworkImageView} object
     *
     * @param imageView Destination view object
     * @param item Source data item containing image URL
     */
    public void loadAvatar(NetworkImageView imageView, GHData.Item item) {
        final String url = item.getOwner().getAvatarLink();
        final int placeholderID = R.drawable.avatar_placeholder;
        mImageLoader.get(url, ImageLoader.getImageListener(imageView, placeholderID, placeholderID));
        imageView.setImageUrl(url, mImageLoader);
    }

    public class LocalBinder extends Binder {
        public GHDataService getService() {
            return GHDataService.this;
        }

    }
}
