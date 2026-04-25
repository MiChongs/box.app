package com.box.app.provision.widget;

import android.annotation.SuppressLint;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.fragment.app.FragmentActivity;

import com.box.app.R;

import fan.bottomsheet.BottomSheetBehavior;
import fan.bottomsheet.BottomSheetModal;

public class TermsAndStatementBottomSheet {

    static FragmentActivity mActivity;
    BottomSheetModal mBottomSheet;

    @SuppressLint("StaticFieldLeak")
    static ProgressBar mProgressBar;
    static MarkdownView mMarkdownView;

    public TermsAndStatementBottomSheet(FragmentActivity activity) {
        mActivity = activity;
        mBottomSheet = new BottomSheetModal(activity);
        mBottomSheet.setDragHandleViewEnabled(true);
        BottomSheetBehavior<FrameLayout> behavior = mBottomSheet.getBehavior();
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        behavior.setForceFullHeight(true);
        behavior.setModeConfig(0);
        behavior.setDraggable(true);
        behavior.setSkipHalfExpanded(true);
        behavior.setSkipCollapsed(true);
        behavior.setFixedHeightRatioEnabled(false);

        mBottomSheet.setContentView(R.layout.fragment_bottom_sheet_web);
        View rootView = mBottomSheet.getRootView();
        mProgressBar = rootView.findViewById(R.id.progress_bar);
        mMarkdownView = rootView.findViewById(R.id.markdown);
        initView();
    }

    public static void initView() {
        mProgressBar.setVisibility(View.VISIBLE);
        mMarkdownView.setVisibility(View.INVISIBLE);

        mMarkdownView.setOnMarkdownLoadListener(success -> {
            if (success) {
                mProgressBar.setVisibility(View.INVISIBLE);
                mMarkdownView.setVisibility(View.VISIBLE);
            }
        });
    }

    public static void loadMarkdown(String uri) {
        if (mMarkdownView != null) {
            mMarkdownView.loadMarkdownFromUrl(uri);
        }
    }

    public void show() {
        if (mBottomSheet != null) {
            mBottomSheet.show();
        }
    }

    private void dismiss() {
        if (mBottomSheet != null) {
            mBottomSheet.dismiss();
        }
    }
}
