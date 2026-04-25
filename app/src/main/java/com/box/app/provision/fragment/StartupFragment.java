package com.box.app.provision.fragment;

import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import android.util.Log;
import com.box.app.R;
import com.box.app.provision.activity.DefaultActivity;
import com.box.app.provision.renderengine.GlowController;
import com.box.app.provision.renderengine.RenderViewLayout;
import com.box.app.provision.utils.AnimHelper;
import com.box.app.provision.utils.BlurUtils;
import com.box.app.provision.utils.IOnFocusListener;
import com.box.app.provision.utils.Utils;
import com.box.app.provision.utils.ViewUtils;

import fan.animation.listener.TransitionListener;
import fan.core.utils.MiuiBlurUtils;
import com.box.app.provision.base.OobeUtils;
import fan.transition.ActivityOptionsHelper;

public class StartupFragment extends BaseFragment implements IOnFocusListener {

    private static final String TAG = "StartupFragment";

    private record PickPageAnimationContext(
        DefaultActivity activity, View nextView, int locationX, int locationY, int width, int foregroundColor
    ) {
            private PickPageAnimationContext(
                @NonNull DefaultActivity activity,
                @NonNull View nextView,
                int locationX,
                int locationY,
                int width,
                int foregroundColor
            ) {
                this.activity = activity;
                this.nextView = nextView;
                this.locationX = locationX;
                this.locationY = locationY;
                this.width = width;
                this.foregroundColor = foregroundColor;
            }
        }

    private final boolean IS_SUPPORT_WELCOME_ANIM = !OobeUtils.isLiteOrLowDevice();

    private long lastClickTime = 0;

    private ImageView mBackgroundImage;
    private View mMiuiEnterLayout;


    private View mGlowEffectView;
    private RenderViewLayout mRenderViewLayout;

    private ImageView mLogoImage;

    private TextView mTextLogoImage;
    private View mLogoImageWrapper;

    private View mNextLayout;
    private View mNext;
    private ImageView mNextArrow;

    private GlowController mGlowController;

    private Handler mAnimationHandler;

    Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Runnable mDisplayOsAndoRunnable = () -> {
        restoreNextButtonState();
        if (mLogoImage != null) {
            mLogoImage.setVisibility(View.VISIBLE);
        }

        if (mLogoImageWrapper != null) {
            mLogoImageWrapper.setVisibility(View.VISIBLE);
        }
        Log.d(TAG, "displayOsAndoDelay");
    };
    private final Runnable mRestoreNextButtonRunnable = () -> {
        restoreNextButtonState();
        Utils.IS_START_ANIMA = false;
    };

    private final View.OnClickListener mNextClickListener = v -> {
        long jCurrentTimeMillis = System.currentTimeMillis();
        if (Math.abs(jCurrentTimeMillis - lastClickTime) < 2000) {
            Log.d(TAG, "click too fast");
            return;
        }
        lastClickTime = jCurrentTimeMillis;
        Log.d(TAG, "click next button");
        FragmentActivity activity = getActivity();
        View nextView = mNext;
        if (!(activity instanceof DefaultActivity defaultActivity) || nextView == null) {
            Log.w(TAG, "click next button: activity or next view is unavailable");
            return;
        }
        Utils.isFirstBoot = false;
        PickPageAnimationContext pickPageAnimationContext = buildPickPageAnimationContext(defaultActivity, nextView);
        defaultActivity.run(-1);
        enterLanguagePickPage(pickPageAnimationContext);
    };

    @SuppressLint("HandlerLeak")
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAnimationHandler = new Handler() {
            @Override
            public void handleMessage(@NonNull Message msg) {
                if (msg.what == 5) {
                    Log.i(TAG, " isFirstBoot value set");
                    Utils.isFirstBoot = false;
                }
            }
        };
    }


    @Override
    protected int getLayoutId() {
        return R.layout.provision_startup_layout;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        mLogoImage = view.findViewById(R.id.logo_image);

        mTextLogoImage = view.findViewById(R.id.text_logo_image);
        // 锁定字体：sans-serif-medium（MIUI 上映射为 MiPro Medium），不随系统字体变化
        mTextLogoImage.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mLogoImageWrapper = view.findViewById(R.id.logo_image_wrapper);
        mNextLayout = view.findViewById(R.id.next_layout);
        mNext = view.findViewById(R.id.next);
        mNextArrow = view.findViewById(R.id.next_arrow);
        mBackgroundImage = view.findViewById(R.id.background_image);
        mMiuiEnterLayout = view.findViewById(R.id.miui_enter_layout);

        if (!IS_SUPPORT_WELCOME_ANIM) {
            Log.i(TAG, "not support anim");
            mBackgroundImage.setImageResource(R.drawable.provision_logo_image_bg);
            mLogoImage.setImageResource(R.drawable.provision_logo_image_lite);
            mTextLogoImage.setTextColor(0xFF000000);
            setNextBackground();
        } else {
            mRenderViewLayout = view.findViewById(R.id.render_view_layout);
            mGlowEffectView = new View(requireContext());
            mRenderViewLayout.attachView(mGlowEffectView, 0.2f, -16777216);
            mGlowController = new GlowController(mGlowEffectView);
            if (Utils.isBlurEffectEnabled(getContext())) {
                Log.i(TAG, " MiuiBlur EffectEnabled ");
                MiuiBlurUtils.setBackgroundBlur(mMiuiEnterLayout, (int) ((getResources().getDisplayMetrics().density * 50.0f) + 0.5f));
                MiuiBlurUtils.setViewBlurMode(mMiuiEnterLayout, 0);
                BlurUtils.setupViewBlur(mLogoImage, true, new int[]{-867546550, -11579569, -15011328}, new int[]{19, 100, 106});

                BlurUtils.setupViewBlur(mTextLogoImage, true, new int[]{-867546550, -11579569, -15011328}, new int[]{19, 100, 106});
                mLogoImage.setImageResource(R.drawable.provision_logo_image);

                mTextLogoImage.setTextColor(0xFFFFFFFF);
                BlurUtils.setupViewBlur(mNext, true, new int[]{-13750738, -15011328}, new int[]{100, 106});
                mNext.setBackgroundResource(R.drawable.provision_next);
                mNextArrow.setVisibility(View.VISIBLE);
                mNextArrow.setImageResource(R.drawable.provision_icon_arrow);
            } else {
                Log.i(TAG, " MiuiBlur not EffectEnabled ");
                mLogoImage.setImageResource(R.drawable.provision_logo_image_lite);
                mTextLogoImage.setTextColor(0xFF000000);
                setNextBackground();
            }
        }

        if (Utils.IS_START_ANIMA) {
            syncNextButtonState();
        }

        mNextLayout.setOnClickListener(mNextClickListener);
        mNext.setOnClickListener(mNextClickListener);
        mNextArrow.setOnClickListener(mNextClickListener);


        if (IS_SUPPORT_WELCOME_ANIM && mLogoImageWrapper != null && mLogoImage != null &&
                mNextLayout != null && Utils.isFirstBoot) {
            Log.i(TAG, "SUPPORT_WELCOME_ANIM");
            mLogoImage.setVisibility(View.INVISIBLE);
            mLogoImageWrapper.setVisibility(View.INVISIBLE);
            hideNextButtonState();
        }
    }

    private void setNextBackground() {
        Bitmap bitmap = Utils.getCacheBitmap(isNeedRotation());
        if (bitmap != null) {
            mNext.setBackground(new BitmapDrawable(getResources(), bitmap));
        } else {
            mNext.setBackgroundResource(R.drawable.provision_next_lite);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        lastClickTime = 0;
        Log.d(TAG, "onStart");
        if (IS_SUPPORT_WELCOME_ANIM && !Utils.isFirstBoot && mGlowController != null) {
            mGlowController.start(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        lastClickTime = 0;
        syncNextButtonState();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (IS_SUPPORT_WELCOME_ANIM) {
            Log.i(TAG, " onWindowFocusChanged " + hasFocus + " isFirst " + Utils.isFirstBoot);
            if (Utils.isFirstBoot) {
                if (mGlowController != null) {
                    mGlowController.start(true);
                    mGlowController.setCircleYOffsetWithView(mLogoImageWrapper, mRenderViewLayout);
                }

                if (mLogoImage != null) {
                    mLogoImage.setVisibility(View.VISIBLE);
                    AnimHelper.startPageLogoAnim(mLogoImage);
                }

                if (mLogoImageWrapper != null) {
                    mLogoImageWrapper.setVisibility(View.VISIBLE);
                    AnimHelper.startPageLogoAnim(mLogoImageWrapper);
                }
                if (mNextLayout != null) {
                    mNextLayout.setVisibility(View.VISIBLE);
                    AnimHelper.startPageBtnAnim(mNextLayout, new TransitionListener() {
                        @Override
                        public void onComplete(Object obj) {
                            super.onComplete(obj);
                            restoreNextButtonState();
                            Log.d(TAG, "onComplete: mNextLayout restored");
                        }
                    });
                }
                resetFirstStart();
                displayOsAndoDelay();
            }
        }
    }

    /*@Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
        if (mGlowController != null) {
            mGlowController.stop();
            Log.d(TAG, "GlowController: stop");
        }
    }*/

    private void resetFirstStart() {
        if (mAnimationHandler != null) {
            mAnimationHandler.removeMessages(5);
            mAnimationHandler.sendEmptyMessageDelayed(5, Utils.isFoldDevice() ? 3000L : 0L);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        mMainHandler.removeCallbacks(mDisplayOsAndoRunnable);
        mMainHandler.removeCallbacks(mRestoreNextButtonRunnable);
        if (mAnimationHandler != null) {
            mAnimationHandler.removeCallbacksAndMessages(null);
        }
    }

    private void displayOsAndoDelay() {
        mMainHandler.removeCallbacks(mDisplayOsAndoRunnable);
        mMainHandler.postDelayed(mDisplayOsAndoRunnable, 2500L);
    }

    private void syncNextButtonState() {
        mMainHandler.removeCallbacks(mRestoreNextButtonRunnable);
        if (IS_SUPPORT_WELCOME_ANIM && Utils.isFirstBoot) {
            return;
        }
        if (!Utils.IS_START_ANIMA) {
            restoreNextButtonState();
            return;
        }
        hideNextButtonState();
        mMainHandler.postDelayed(mRestoreNextButtonRunnable, 505L);
    }

    private void hideNextButtonState() {
        if (mNextLayout != null) {
            mNextLayout.setVisibility(View.INVISIBLE);
            mNextLayout.setEnabled(false);
            mNextLayout.setClickable(false);
        }
        if (mNext != null) {
            mNext.setEnabled(false);
            mNext.setClickable(false);
        }
        if (mNextArrow != null) {
            mNextArrow.setEnabled(false);
            mNextArrow.setClickable(false);
        }
    }

    private void restoreNextButtonState() {
        if (mNextLayout != null) {
            mNextLayout.setVisibility(View.VISIBLE);
            mNextLayout.setEnabled(true);
            mNextLayout.setClickable(true);
        }
        if (mNext != null) {
            mNext.setEnabled(true);
            mNext.setClickable(true);
        }
        if (mNextArrow != null) {
            mNextArrow.setEnabled(true);
            mNextArrow.setClickable(true);
        }
    }

    @NonNull
    private PickPageAnimationContext buildPickPageAnimationContext(@NonNull DefaultActivity activity, @NonNull View nextView) {
        int[] location = new int[2];
        nextView.getLocationInWindow(location);
        int width = ((nextView.getWidth() - nextView.getPaddingRight()) - nextView.getPaddingLeft()) / 2;
        return new PickPageAnimationContext(
            activity,
            nextView,
            location[0],
            location[1],
            width,
            getAnimForeGroundColor(activity)
        );
    }

    private void enterLanguagePickPage(@NonNull PickPageAnimationContext animationContext) {
        if (animationContext.activity.isFinishing() || animationContext.activity.isDestroyed()) {
            Log.w(TAG, "enterLanguagePickPage: activity is unavailable");
            return;
        }
        ViewUtils.captureRoundedBitmap(
            animationContext.activity,
            animationContext.nextView,
            mMainHandler,
            bitmap -> launchPermissionPickPage(animationContext, bitmap)
        );
    }


    private void launchPermissionPickPage(@NonNull PickPageAnimationContext animationContext, @Nullable Bitmap bitmap) {
        DefaultActivity activity = animationContext.activity;
        if (activity.isFinishing() || activity.isDestroyed()) {
            Log.w(TAG, "launchPermissionPickPage: activity unavailable after bitmap capture");
            return;
        }

        ActivityOptions activityOptions = bitmap != null ? buildPickPageAnimation(animationContext, bitmap) : null;

        if (activityOptions == null) {
            if (bitmap == null) {
                Log.d(TAG, "roundedBitmap is null");
            } else {
                Log.w(TAG, "launchPermissionPickPage: scale-up animation is unavailable, launching without ActivityOptions");
            }
            Utils.IS_START_ANIMA = false;
            activity.enterCurrentState(null);
            return;
        }

        Log.d(TAG, "launchPermissionPickPage: start activity with scale-up animation");
        activity.enterCurrentState(activityOptions.toBundle());
    }

    @Nullable
    private ActivityOptions buildPickPageAnimation(@NonNull PickPageAnimationContext animationContext, @NonNull Bitmap bitmap) {
        if (animationContext.width <= 0) {
            Log.w(TAG, "buildPickPageAnimation: next view width is invalid");
            return null;
        }
        View nextLayout = mNextLayout;
        ActivityOptions activityOptionsMakeScaleUpAnim = ActivityOptionsHelper.makeScaleUpAnim(
            animationContext.nextView,
            bitmap,
            animationContext.locationX,
            animationContext.locationY,
            animationContext.width,
            animationContext.foregroundColor,
            1.0f,
            mMainHandler,
            buildExitStartedCallback(nextLayout),
            buildExitFinishCallback(nextLayout),
            null,
            null,
            102
        );
        if (activityOptionsMakeScaleUpAnim == null) {
            return null;
        }
        Utils.LOCATION_X = animationContext.locationX;
        Utils.LOCATION_Y = animationContext.locationY;
        Utils.CACHE_BITMAP = bitmap;
        Utils.IS_RTL = isRtl();
        Utils.IS_START_ANIMA = true;
        return activityOptionsMakeScaleUpAnim;
    }

    @NonNull
    private Runnable buildExitStartedCallback(@Nullable View nextLayout) {
        return () -> {
            if (nextLayout != null) {
                nextLayout.setVisibility(View.INVISIBLE);
                Log.d(TAG, "exitStartedCallback: " + nextLayout.getVisibility());
            }
        };
    }

    @NonNull
    private Runnable buildExitFinishCallback(@Nullable View nextLayout) {
        return () -> {
            if (nextLayout != null) {
                nextLayout.setVisibility(View.VISIBLE);
                Log.d(TAG, "exitFinishCallback: " + nextLayout.getVisibility());
            }
        };
    }

    private int isRtl() {
        return Utils.isRTL() ? 1 : 2;
    }


    private boolean isNeedRotation() {
        return isRtl() != Utils.IS_RTL;
    }

    private int getAnimForeGroundColor(@NonNull Context context) {
        if (Utils.isBlurEffectEnabled(context)) {
            return context.getColor(R.color.anim_foreground_color_blur_enable);
        }
        return context.getColor(R.color.anim_foreground_color);
    }


}
