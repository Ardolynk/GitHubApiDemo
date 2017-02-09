package com.ardolynk.githubapidemo;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.volley.toolbox.NetworkImageView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Michael Tikhonenko on 2/7/17.
 */

public class GHListAdapter extends ArrayAdapter<GHData.Item> {
    private GHDataService mService;
    private ProgressBar mSentinelViewNormal;
    private Button mSentinelViewFailure;
    private boolean mNextPageLoading = false;
    private boolean mHasNext = false;
    private boolean mLastUpdateFailed = false;

    public GHListAdapter(Context context, GHDataService service) {
        super(context, 0, new ArrayList<GHData.Item>());
        mSentinelViewNormal = new ProgressBar(context);
        mSentinelViewFailure = new Button(context);
        mSentinelViewFailure.setText(R.string.list_hint_more);
        mSentinelViewFailure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mService.continueLoadingList();
                mLastUpdateFailed = false;
                notifyDataSetChanged();
            }
        });
        mService = service;
    }

    public void populateData(List<GHData.Item> data, int flags) {
        mLastUpdateFailed = ((flags & GHDataService.FLAG_ERROR) != 0);
        if (mLastUpdateFailed) {
            if ((flags & GHDataService.FLAG_CONDITION_CHANGED) != 0) {

                //Search failed with changed condition, so we have to clear list

                clear();
                mHasNext = false;
            }
        }
        else {
            if ((flags & GHDataService.FLAG_APPEND) == 0) {
                clear();
            }
            addAll(data);
            mHasNext = ((flags & GHDataService.FLAG_HAS_NEXT) != 0);
        }
        notifyDataSetChanged();
        mNextPageLoading = false;
    }

    @Override
    public int getCount() {

         //We might need a sentinel view (progress indicator) if there might be next page available

        int count = super.getCount();
        if (mHasNext) {
            count++;
        }
        return count;
    }

    @Override
    public GHData.Item getItem(int position) {
        if (position == super.getCount()) {
            return null;
        }
        return super.getItem(position);
    }

    @Override
    @SuppressWarnings("deprecation")
    public View getView(int position, View convertView, ViewGroup parent) {

        if (position == super.getCount()) {
            if (mLastUpdateFailed) {
                return mSentinelViewFailure;
            }
            if (!mNextPageLoading) {
                mService.continueLoadingList();
                mNextPageLoading = true;
            }
            return mSentinelViewNormal;
        }

        GHData.Item item = getItem(position);

        if (convertView == null || convertView == mSentinelViewNormal || convertView == mSentinelViewFailure) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_view, parent, false);
        }

        TextView fullNameView = (TextView) convertView.findViewById(R.id.full_name);
        fullNameView.setText(item.getProjectName());

        NetworkImageView avatarView = (NetworkImageView) convertView.findViewById(R.id.avatar);
        mService.loadAvatar(avatarView, item);

        TextView ownerNameView = (TextView) convertView.findViewById(R.id.owner_name);
        ownerNameView.setText(item.getOwner().getLogin());

        TextView starCountView = (TextView) convertView.findViewById(R.id.stars);
        starCountView.setText(String.format(" %d", item.getStarCount()));

        return convertView;
    }
}
