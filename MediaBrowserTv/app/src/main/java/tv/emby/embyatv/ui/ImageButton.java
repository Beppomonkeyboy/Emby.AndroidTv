package tv.emby.embyatv.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import tv.emby.embyatv.R;

/**
 * Created by Eric on 2/20/2015.
 */
public class ImageButton extends ImageView {

    public static int STATE_PRIMARY = 0;
    public static int STATE_SECONDARY = 1;

    private TextView mHelpView;
    private String mHelpText = "";
    private int mPrimaryImage;
    private int mSecondaryImage;
    private int mState;

    public ImageButton(Context context, AttributeSet attributeSet){
        super(context, attributeSet);
        setOnFocusChangeListener(focusChangeListener);
    }

    public ImageButton(Context context, int imageResource, int size, final OnClickListener clicked) {
        this(context, imageResource, size, "", null, clicked);
    }

    public ImageButton(Context context, int imageResource, int size, String helpText, TextView helpView, final OnClickListener clicked) {
        super(context, null, R.style.spaced_buttons);
        setImageResource(imageResource);
        setMaxHeight(size);
        setAdjustViewBounds(true);
        setAlpha(.8f);
        setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        setFocusable(true);
        setOnClickListener(clicked);
        mHelpView = helpView;
        mHelpText = helpText;
        setOnFocusChangeListener(focusChangeListener);

    }

    public void setHelpView(TextView view) {
        mHelpView = view;
    }
    public void setHelpText(String text) { mHelpText = text; }

    public void setState(int state) {
        mState = state;
        if (mSecondaryImage > 0) setImageResource(mState == STATE_SECONDARY ? mSecondaryImage : mPrimaryImage);
    }

    public void toggleState() {
        setState(mState == STATE_PRIMARY ? STATE_SECONDARY : STATE_PRIMARY);
    }

    private OnFocusChangeListener focusChangeListener = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus) {
                if (mHelpView != null) mHelpView.setText(mHelpText);
                v.setBackgroundColor(getResources().getColor(R.color.lb_default_brand_color));
            } else {
                if (mHelpView != null) mHelpView.setText("");
                v.setBackgroundColor(0);
            }
        }
    };

    public void setPrimaryImage(int mPrimaryImage) {
        this.mPrimaryImage = mPrimaryImage;
    }

    public void setSecondaryImage(int mSecondaryImage) {
        this.mSecondaryImage = mSecondaryImage;
    }

}
