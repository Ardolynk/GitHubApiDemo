package com.ardolynk.githubapidemo;

import android.app.Activity;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ResultReceiver;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.SearchView;

import java.util.List;

/**
 * Created by Michael Tikhonenko on 2/7/17.
 */

public class GHActivity extends Activity {

    private ListView mListView;
    private SwipeRefreshLayout mRefreshLayout;
    private View mLoadHintView;

    private GHListAdapter mListAdapter;
    private GHDataService mService;
    private ServiceConnection mServiceConnection;
    private String mRecentSearchString;

    private final static int SEARCH_STRING__MIN_LENGTH = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_view);
        mLoadHintView = findViewById(R.id.load_hint);
        mListView = (ListView) findViewById(R.id.list_view);
        mRecentSearchString = null;
        mRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.refresh_layout);

        mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {

                //Initial data load

                mService = ((GHDataService.LocalBinder) service).getService();
                mService.startLoadingList("");
                mRefreshLayout.setRefreshing(true);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mService = null;
            }
        };

        Intent serviceIntent = new Intent(this, GHDataService.class);
        serviceIntent.putExtra(GHDataService.RECEIVER, new ResultReceiver(new Handler()) {
            @Override
            public void onReceiveResult(int resultCode, Bundle resultBundle) {

                //List data loaded

                int resultFlags = resultBundle.getInt(GHDataService.RESULT_FLAGS);
                final List<GHData.Item> resultData = (List<GHData.Item>) resultBundle.getSerializable(GHDataService.RESULT_DATA);
                if (mListAdapter == null) {
                    mListAdapter = new GHListAdapter(GHActivity.this, mService);
                    mListView.setAdapter(mListAdapter);
                }
                mListAdapter.populateData(resultData, resultFlags);
                mRefreshLayout.setRefreshing(false);

                if ((resultFlags & GHDataService.FLAG_APPEND) == 0) {

                    //List loaded from scratch, scroll to the top

                    mListView.setSelectionAfterHeaderView();
                }

                if ((resultFlags & GHDataService.FLAG_ERROR) != 0) {
                    if (mListAdapter.getCount() == 0) {
                        mLoadHintView.setVisibility(View.VISIBLE);
                    }
                }
            }
        });

        mRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {

                //Reload list by pull

                if (mService == null) {
                    mRefreshLayout.setRefreshing(false);
                }
                else {
                    mLoadHintView.setVisibility(View.GONE);
                    mService.startLoadingList(mRecentSearchString);;
                }
            }
        });

        bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.options_menu, menu);

        // Get the SearchView and set the searchable configuration

        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        MenuItem searchItem = menu.findItem(R.id.menu_search);
        SearchView searchView = (SearchView) searchItem.getActionView();

        // Assumes current activity is the searchable activity

        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                mRecentSearchString = null;
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                if (mRecentSearchString != null) {
                    mRecentSearchString = null;
                    mService.startLoadingList("");
                    mRefreshLayout.setRefreshing(true);
                }
                return true;
            }
        });

        //Prevent submission for too short requests

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return query.length() < SEARCH_STRING__MIN_LENGTH;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            if (query.length() >= SEARCH_STRING__MIN_LENGTH) {
                mService.startLoadingList(query);
                mRecentSearchString = query;
                mLoadHintView.setVisibility(View.GONE);
                mRefreshLayout.setRefreshing(true);
            }
        }
    }

    @Override
    public void onDestroy() {
        unbindService(mServiceConnection);
        super.onDestroy();
    }
}
