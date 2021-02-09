/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.systemui.statusbar.phone;

import static com.android.keyguard.KeyguardUpdateMonitor.LOCK_SCREEN_MODE_LAYOUT_1;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.Interpolators;
import com.android.systemui.keyguard.KeyguardIndication;

import java.util.LinkedList;

/**
 * A view to show hints on Keyguard ("Swipe up to unlock", "Tap again to open").
 */
public class KeyguardIndicationTextView extends TextView {
    private static final int FADE_OUT_MILLIS = 200;
    private static final int FADE_IN_MILLIS = 250;
    private static final long MSG_DURATION_MILLIS = 600;
    private long mNextAnimationTime = 0;
    private boolean mAnimationsEnabled = true;
    private LinkedList<CharSequence> mMessages = new LinkedList<>();
    private LinkedList<KeyguardIndication> mKeyguardIndicationInfo = new LinkedList<>();

    private boolean mUseNewAnimations = false;

    public KeyguardIndicationTextView(Context context) {
        super(context);
    }

    public KeyguardIndicationTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public KeyguardIndicationTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public KeyguardIndicationTextView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setLockScreenMode(int lockScreenMode) {
        mUseNewAnimations = lockScreenMode == LOCK_SCREEN_MODE_LAYOUT_1;
    }

    /**
     * Changes the text with an animation and makes sure a single indication is shown long enough.
     */
    public void switchIndication(int textResId) {
        switchIndication(getResources().getText(textResId), null);
    }

    /**
     * Changes the text with an animation and makes sure a single indication is shown long enough.
     *
     * @param indication The text to show.
     */
    public void switchIndication(KeyguardIndication indication) {
        switchIndication(indication == null ? null : indication.getMessage(), indication);
    }

    /**
     * Changes the text with an animation and makes sure a single indication is shown long enough.
     *
     * @param text The text to show.
     * @param indication optional display information for the text
     */
    public void switchIndication(CharSequence text, KeyguardIndication indication) {
        if (text == null) text = "";

        CharSequence lastPendingMessage = mMessages.peekLast();
        if (TextUtils.equals(lastPendingMessage, text)
                || (lastPendingMessage == null && TextUtils.equals(text, getText()))) {
            return;
        }
        mMessages.add(text);
        mKeyguardIndicationInfo.add(indication);

        final boolean hasIcon = indication != null && indication.getIcon() != null;
        final AnimatorSet animSet = new AnimatorSet();
        final AnimatorSet.Builder animSetBuilder = animSet.play(getOutAnimator());

        // Make sure each animation is visible for a minimum amount of time, while not worrying
        // about fading in blank text
        long timeInMillis = System.currentTimeMillis();
        long delay = Math.max(0, mNextAnimationTime - timeInMillis);
        setNextAnimationTime(timeInMillis + delay + getFadeOutDuration());

        if (!text.equals("") || hasIcon) {
            setNextAnimationTime(mNextAnimationTime + MSG_DURATION_MILLIS);
            animSetBuilder.before(getInAnimator());
        }

        animSet.setStartDelay(delay);
        animSet.start();
    }

    private AnimatorSet getOutAnimator() {
        AnimatorSet animatorSet = new AnimatorSet();
        Animator fadeOut = ObjectAnimator.ofFloat(this, View.ALPHA, 0f);
        fadeOut.setDuration(getFadeOutDuration());
        fadeOut.setInterpolator(Interpolators.FAST_OUT_LINEAR_IN);
        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                KeyguardIndication info = mKeyguardIndicationInfo.poll();
                if (info != null) {
                    setTextColor(info.getTextColor());
                    setOnClickListener(info.getClickListener());
                    final Drawable icon = info.getIcon();
                    if (icon != null) {
                        icon.setTint(getCurrentTextColor());
                        if (icon instanceof AnimatedVectorDrawable) {
                            ((AnimatedVectorDrawable) icon).start();
                        }
                    }
                    setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null);
                }
                setText(mMessages.poll());
            }
        });

        if (mUseNewAnimations) {
            Animator yTranslate =
                    ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, 0, -getYTranslationPixels());
            yTranslate.setDuration(getFadeOutDuration());
            fadeOut.setInterpolator(Interpolators.FAST_OUT_LINEAR_IN);
            animatorSet.playTogether(fadeOut, yTranslate);
        } else {
            animatorSet.play(fadeOut);
        }

        return animatorSet;
    }

    private AnimatorSet getInAnimator() {
        AnimatorSet animatorSet = new AnimatorSet();
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(this, View.ALPHA, 1f);
        fadeIn.setStartDelay(getFadeInDelay());
        fadeIn.setDuration(getFadeInDuration());
        fadeIn.setInterpolator(Interpolators.LINEAR_OUT_SLOW_IN);

        if (mUseNewAnimations) {
            Animator yTranslate =
                    ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, getYTranslationPixels(), 0);
            yTranslate.setDuration(getYInDuration());
            yTranslate.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationCancel(Animator animation) {
                    setTranslationY(0);
                }
            });
            animatorSet.playTogether(yTranslate, fadeIn);
        } else {
            animatorSet.play(fadeIn);
        }

        return animatorSet;
    }

    @VisibleForTesting
    public void setAnimationsEnabled(boolean enabled) {
        mAnimationsEnabled = enabled;
    }

    private long getFadeInDelay() {
        if (!mAnimationsEnabled) return 0L;
        if (mUseNewAnimations) return 150L;
        return 0L;
    }

    private long getFadeInDuration() {
        if (!mAnimationsEnabled) return 0L;
        if (mUseNewAnimations) return 317L;
        return FADE_IN_MILLIS;
    }

    private long getYInDuration() {
        if (!mAnimationsEnabled) return 0L;
        if (mUseNewAnimations) return 600L;
        return 0L;
    }

    private long getFadeOutDuration() {
        if (!mAnimationsEnabled) return 0L;
        if (mUseNewAnimations) return 167L;
        return FADE_OUT_MILLIS;
    }

    private void setNextAnimationTime(long time) {
        if (mAnimationsEnabled) {
            mNextAnimationTime = time;
        } else {
            mNextAnimationTime = 0L;
        }
    }

    private int getYTranslationPixels() {
        return mContext.getResources().getDimensionPixelSize(
                com.android.systemui.R.dimen.keyguard_indication_y_translation);
    }
}
