package com.box.app.provision.fragment;

import android.graphics.text.LineBreakConfig;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.box.app.R;
import com.box.app.provision.base.OobeUtils;

public class TermsAndStatementFragment extends BaseFragment {

    private View mNextView;
    private TextView mPrivacyView;
    private CheckBox mAgreeCheckBox;

    @Override
    protected int getLayoutId() {
        return R.layout.provision_terms_and_statement_layout;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mPrivacyView = view.findViewById(R.id.privacy);

        // HTML 渲染：<p> 自动段间距、<b> 加粗标题、<br/> 精确换行
        String html = getString(R.string.provision_disclaimer_body);
        mPrivacyView.setText(Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT));

        // Android 13+ CJK 按词组换行，避免在词中间断行
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mPrivacyView.setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE);
        }

        mAgreeCheckBox = view.findViewById(R.id.checkbox_agree);
        mAgreeCheckBox.setVisibility(View.VISIBLE);
        mAgreeCheckBox.setChecked(false);
        mAgreeCheckBox.setText(R.string.provision_agree_terms);

        if (getActivity() != null) {
            mNextView = OobeUtils.getNextView(getActivity());
            if (mNextView instanceof TextView) {
                ((TextView) mNextView).setText(R.string.provision_agree_and_next);
            }
            mNextView.setEnabled(false);
            mNextView.setAlpha(OobeUtils.HALF_ALPHA);

            mAgreeCheckBox.setOnCheckedChangeListener((v, isChecked) -> {
                mNextView.setEnabled(isChecked);
                mNextView.setAlpha(isChecked ? OobeUtils.NO_ALPHA : OobeUtils.HALF_ALPHA);
            });
        }
    }

    public void goNext() {
        if (getActivity() != null) {
            getActivity().setResult(-1);
            getActivity().finish();
        }
    }
}
