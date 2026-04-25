package com.box.app.provision.fragment;

import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.box.app.R;
import com.box.app.provision.compat.AppLanguageHelper;
import com.box.app.provision.renderengine.GlowController;
import com.box.app.provision.renderengine.RenderViewLayout;
import com.box.app.provision.utils.IOnFocusListener;
import com.box.app.provision.utils.Utils;

import fan.animation.Folme;
import fan.animation.FolmeEase;
import fan.animation.IStateStyle;
import fan.animation.base.AnimConfig;
import fan.animation.controller.AnimState;
import fan.animation.listener.TransitionListener;
import fan.animation.property.ViewProperty;
import fan.core.utils.MiuiBlurUtils;
import fan.internal.utils.LiteUtils;
import com.box.app.provision.base.OobeUtils;

public class CongratulationFragment extends BaseFragment implements IOnFocusListener {

    private static final String TAG = "Provision:CongratulationFragment";

    // Box 无需等待系统配置，仅保留入场动画时长
    private static final int LOGO_ANIM_DURATION = 1500;
    private static final int BTN_APPEAR_DELAY = 800;
    private static final int BTN_ANIM_DURATION = 450;
    // 退出动画时长，需短于 Activity 过渡 (400ms)，让内容先消失再切场景
    private static final int EXIT_ANIM_DURATION = 300;

    private boolean isExiting;

    private View mGlowEffectView;
    private View mContentView;
    private ImageView mLogoImage;
    private TextView mTextLogoImage;
    private View mLogoImageWrapper;
    private View mNext;
    private View mNextView;
    private TextView mSystemStateText;
    private RenderViewLayout mRenderViewLayout;

    private GlowController mGlowController;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    protected int getLayoutId() {
        return R.layout.provision_congratulation_layout;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initView(view);
        initBackground();
        setupBlurBackground();
        // 入场动画序列
        playEntrySequence();
    }

    private void initView(View view) {
        mRenderViewLayout = view.findViewById(R.id.render_view_layout);
        mContentView = view.findViewById(R.id.content_view);
        mLogoImageWrapper = view.findViewById(R.id.logo_image_wrapper);
        mLogoImage = view.findViewById(R.id.logo_image);
        mTextLogoImage = view.findViewById(R.id.text_logo_image);
        mTextLogoImage.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        mSystemStateText = view.findViewById(R.id.system_state_text);
        mNextView = view.findViewById(R.id.next);
        mNext = view.findViewById(R.id.btn_bg);

        mSystemStateText.setText(R.string.provision_system_complete);

        // 初始状态：全部隐藏，等入场动画依次展示
        mLogoImageWrapper.setAlpha(0f);
        mNextView.setVisibility(View.INVISIBLE);
        mNextView.setEnabled(false);

        Folme.use(mNextView).touch().setScale(1.0f).handleTouchOf(this.mNextView);
        mNextView.setOnClickListener(v -> onCompleteClick());
    }

    // ── 入场动画：Logo 上浮渐入 → 按钮渐入 ──

    private void playEntrySequence() {
        // 1) Logo + 文字 + 状态文字：上浮渐入
        mLogoImageWrapper.setVisibility(View.VISIBLE);
        AnimState from = new AnimState("start")
            .add(ViewProperty.ALPHA, 0.0d)
            .add(ViewProperty.TRANSLATION_Y, 80.0d);
        AnimState to = new AnimState("end")
            .add(ViewProperty.ALPHA, 1.0d)
            .add(ViewProperty.TRANSLATION_Y, 0.0d);
        AnimConfig logoConfig = new AnimConfig();
        logoConfig.setEase(FolmeEase.quartOut(LOGO_ANIM_DURATION));
        Folme.use(mLogoImageWrapper).state().setTo(from).to(to, logoConfig);

        // 2) 按钮：延迟渐入
        mHandler.postDelayed(() -> {
            if (!isAdded()) return;
            mNextView.setVisibility(View.VISIBLE);
            AnimConfig btnConfig = new AnimConfig();
            btnConfig.setEase(FolmeEase.sinOut(BTN_ANIM_DURATION));
            btnConfig.addListeners(new TransitionListener() {
                @Override
                public void onComplete(Object toTag) {
                    if (isAdded()) mNextView.setEnabled(true);
                }
            });
            IStateStyle state = Folme.use(mNextView).state();
            state.setTo(ViewProperty.ALPHA, 0f).to(ViewProperty.ALPHA, 1f, btnConfig);
        }, BTN_APPEAR_DELAY);
    }

    // ── 退出动画 + 跳转 ──

    private void onCompleteClick() {
        if (isExiting) return;
        isExiting = true;
        mNextView.setEnabled(false);

        // 退出动画：内容缩小渐隐（与 Activity 过渡的 scale 0.95→1.0 形成 zoom-through 视差）
        AnimState from = new AnimState("start")
            .add(ViewProperty.ALPHA, 1.0d)
            .add(ViewProperty.SCALE_X, 1.0d)
            .add(ViewProperty.SCALE_Y, 1.0d);
        AnimState to = new AnimState("end")
            .add(ViewProperty.ALPHA, 0.0d)
            .add(ViewProperty.SCALE_X, 0.9d)
            .add(ViewProperty.SCALE_Y, 0.9d);
        AnimConfig config = new AnimConfig();
        config.setEase(FolmeEase.sinOut(EXIT_ANIM_DURATION));

        if (mLogoImageWrapper != null) {
            Folme.use(mLogoImageWrapper).state().setTo(from).to(to, config);
        }
        if (mNextView != null) {
            Folme.use(mNextView).state().setTo(from).to(to, config);
        }
        // Glow 背景也同步渐隐
        if (mContentView != null) {
            AnimState bgFrom = new AnimState("start").add(ViewProperty.ALPHA, 1.0d);
            AnimState bgTo = new AnimState("end").add(ViewProperty.ALPHA, 0.0d);
            AnimConfig bgConfig = new AnimConfig();
            bgConfig.setEase(FolmeEase.sinOut(EXIT_ANIM_DURATION));
            Folme.use(mContentView).state().setTo(bgFrom).to(bgTo, bgConfig);
        }

        // 内容渐隐完毕 → Activity 静默结束 → Compose 侧入场动画接管
        mHandler.postDelayed(this::startHome, EXIT_ANIM_DURATION);
    }

    // ── 完成并返回主界面 ──

    private void startHome() {
        if (!isAdded() || requireActivity().isFinishing()) return;

        boolean isDebugOobe = OobeUtils.isDebugOobeMode(requireActivity());
        if (!isDebugOobe) {
            AppLanguageHelper.freezeCurrentLocaleIfUnset(requireContext());
            OobeUtils.setProvisionedSync(requireContext(), true);
            requireContext().getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("onboarding_completed", true).commit();
        }
        requireActivity().setResult(-1);
        requireActivity().finish();
        requireActivity().overridePendingTransition(0, 0);
    }

    // ── 背景 ──

    private void initBackground() {
        if (Utils.IS_SUPPORT_ANIM && mContentView != null) {
            mContentView.setBackground(null);
            if (mRenderViewLayout != null) {
                mRenderViewLayout.setVisibility(View.VISIBLE);
                mGlowEffectView = new View(requireContext());
                mRenderViewLayout.attachView(mGlowEffectView, 0.2f, -16777216);
                mGlowController = new GlowController(mGlowEffectView);
                mGlowController.start(false);
            }
        } else if (mContentView != null) {
            mContentView.setBackgroundResource(R.drawable.provision_logo_image_bg);
        }
    }

    private void setupBlurBackground() {
        if (!MiuiBlurUtils.isEnable() || LiteUtils.isCommonLiteStrategy() ||
            !MiuiBlurUtils.isEffectEnable(requireContext())) return;

        MiuiBlurUtils.setBackgroundBlur(mContentView, (int) ((getResources().getDisplayMetrics().density * 50.0f) + 0.5f));
        MiuiBlurUtils.setViewBlurMode(mContentView, 0);

        if (mLogoImage != null) {
            setupViewBlur(mLogoImage, new int[]{-867546550, -11579569, -15011328}, new int[]{19, 100, 106});
            mLogoImage.setImageResource(R.drawable.provision_logo_image);
        }
        if (mTextLogoImage != null) {
            setupViewBlur(mTextLogoImage, new int[]{-867546550, -11579569, -15011328}, new int[]{19, 100, 106});
            mTextLogoImage.setTextColor(0xFFFFFFFF);
        }
        if (mNext != null) {
            setupViewBlur(mNext, new int[]{-12763843, -15021056}, new int[]{100, 106});
            mNext.setBackgroundResource(R.drawable.provision_next_btn_background);
        }
        if (mSystemStateText != null) {
            setupViewBlur(mSystemStateText, new int[]{-869915098, -1724697805}, new int[]{19, 3});
        }
    }

    private void setupViewBlur(View view, int[] colors, int[] modes) {
        if (view == null) return;
        MiuiBlurUtils.setViewBlurMode(view, 3);
        for (int i = 0; i < colors.length; i++) {
            MiuiBlurUtils.addBackgroundBlenderColor(view, colors[i], modes[i]);
        }
    }

    // ── 生命周期 ──

    @Override
    public void onStart() {
        super.onStart();
        if (mGlowController != null) mGlowController.start(false);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mGlowController != null) mGlowController.stop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!Utils.IS_SUPPORT_ANIM && mContentView != null) {
            mContentView.setBackgroundResource(R.drawable.provision_logo_image_bg);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        // no-op
    }
}
