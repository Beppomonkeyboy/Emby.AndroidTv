package tv.emby.embyatv.presentation;

/**
 * Created by Eric on 12/29/2014.
 * Modified ImageCard with no fade on the badge
 */

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.support.v17.leanback.R;
import android.support.v17.leanback.widget.BaseCardView;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import mediabrowser.model.dto.BaseItemDto;
import tv.emby.embyatv.TvApp;
import tv.emby.embyatv.itemhandling.BaseRowItem;
import tv.emby.embyatv.util.Utils;

/**
 * A card view with an {@link ImageView} as its main region.
 */
public class MyImageCardView extends BaseCardView {

    private ImageView mBanner;
    private ViewGroup mInfoOverlay;
    private ImageView mOverlayIcon;
    private TextView mOverlayName;
    private TextView mOverlayCount;
    private ImageView mImageView;
    private View mInfoArea;
    private TextView mTitleView;
    private TextView mContentView;
    private ImageView mBadgeImage;
    private int BANNER_SIZE = Utils.convertDpToPixel(TvApp.getApplication(), 50);

    public MyImageCardView(Context context) {
        this(context, null, true);
    }

    public MyImageCardView(Context context, boolean showInfo) {
        this(context, null, showInfo);

    }

    public MyImageCardView(Context context, AttributeSet attrs, boolean showInfo) {
        this(context, attrs, R.attr.imageCardViewStyle, showInfo);
    }

    public MyImageCardView(Context context, AttributeSet attrs, int defStyle, boolean showInfo) {
        super(context, attrs, defStyle);

        if (!showInfo) {
            setCardType(CARD_TYPE_MAIN_ONLY);
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        View v = inflater.inflate(tv.emby.embyatv.R.layout.image_card_view, this);

        mImageView = (ImageView) v.findViewById(tv.emby.embyatv.R.id.main_image);
        mImageView.setVisibility(View.INVISIBLE);
        mInfoArea = v.findViewById(tv.emby.embyatv.R.id.info_field);
        mTitleView = (TextView) v.findViewById(tv.emby.embyatv.R.id.title_text);
        mContentView = (TextView) v.findViewById(tv.emby.embyatv.R.id.content_text);
        mBadgeImage = (ImageView) v.findViewById(tv.emby.embyatv.R.id.extra_badge);
        mOverlayName = (TextView) v.findViewById(tv.emby.embyatv.R.id.overlay_text);
        mOverlayName.setTypeface(TvApp.getApplication().getDefaultFont());
        mOverlayCount = (TextView) v.findViewById(tv.emby.embyatv.R.id.overlay_count);
        mOverlayCount.setTypeface(TvApp.getApplication().getDefaultFont());
        mOverlayIcon = (ImageView) v.findViewById(tv.emby.embyatv.R.id.icon);
        mInfoOverlay = (ViewGroup) v.findViewById(tv.emby.embyatv.R.id.name_overlay);
        mInfoOverlay.setVisibility(GONE);

        if (mInfoArea != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.lbImageCardView,
                    defStyle, 0);
            try {
                setInfoAreaBackground(
                        a.getDrawable(R.styleable.lbImageCardView_infoAreaBackground));
            } finally {
                a.recycle();
            }
        }

    }


    public void setBanner(int bannerResource) {
        if (mBanner == null) {
            mBanner = new ImageView(getContext());
            mBanner.setLayoutParams(new ViewGroup.LayoutParams(BANNER_SIZE, BANNER_SIZE));

            ((ViewGroup)getRootView()).addView(mBanner);
        }

        mBanner.setImageResource(bannerResource);
        mBanner.setVisibility(VISIBLE);
    }

    public final ImageView getMainImageView() {
        return mImageView;
    }

    public void setMainImageAdjustViewBounds(boolean adjustViewBounds) {
        if (mImageView != null) {
            mImageView.setAdjustViewBounds(adjustViewBounds);
        }
    }

    public void setMainImageScaleType(ImageView.ScaleType scaleType) {
        if (mImageView != null) {
            mImageView.setScaleType(scaleType);
        }
    }

    /**
     * Set drawable with fade-in animation.
     */
    public void setMainImage(Drawable drawable) {
        setMainImage(drawable, true);
    }

    /**
     * Set drawable with optional fade-in animation.
     */
    public void setMainImage(Drawable drawable, boolean fade) {
        if (mImageView == null) {
            return;
        }

        mImageView.setImageDrawable(drawable);
        if (drawable == null) {
            mImageView.animate().cancel();
            mImageView.setAlpha(1f);
            mImageView.setVisibility(View.INVISIBLE);
        } else {
            mImageView.setVisibility(View.VISIBLE);
            if (fade) {
                fadeIn(mImageView);
            } else {
                mImageView.animate().cancel();
                mImageView.setAlpha(1f);
            }
        }
    }

    public void setMainImageDimensions(int width, int height) {
        ViewGroup.LayoutParams lp = mImageView.getLayoutParams();
        lp.width = width;
        lp.height = height;
        mImageView.setLayoutParams(lp);
        if (mBanner != null) mBanner.setX(width - BANNER_SIZE);
    }

    public Drawable getMainImage() {
        if (mImageView == null) {
            return null;
        }

        return mImageView.getDrawable();
    }

    public Drawable getInfoAreaBackground() {
        if (mInfoArea != null) {
            return mInfoArea.getBackground();
        }
        return null;
    }

    public void setInfoAreaBackground(Drawable drawable) {
        if (mInfoArea != null) {
            mInfoArea.setBackground(drawable);
            if (mBadgeImage != null) {
                mBadgeImage.setBackground(drawable);
            }
        }
    }

    public void setInfoAreaBackgroundColor(int color) {
        if (mInfoArea != null) {
            mInfoArea.setBackgroundColor(color);
            if (mBadgeImage != null) {
                mBadgeImage.setBackgroundColor(color);
            }
        }
    }

    public void setTitleText(CharSequence text) {
        if (mTitleView == null) {
            return;
        }

        mTitleView.setText(text);
        setTextMaxLines();
    }

    public void setOverlayInfo(BaseRowItem item) {
        if (mOverlayName == null) return;

        if (getCardType() == BaseCardView.CARD_TYPE_MAIN_ONLY && item.showCardInfoOverlay()) {
            switch (item.getType()) {
                case "Photo":
                    mOverlayName.setText(item.getBaseItem().getPremiereDate() != null ? android.text.format.DateFormat.getDateFormat(TvApp.getApplication()).format(Utils.convertToLocalDate(item.getBaseItem().getPremiereDate())) : item.getFullName());
                    mOverlayIcon.setImageResource(tv.emby.embyatv.R.drawable.camera);
                    break;
                case "PhotoAlbum":
                    mOverlayName.setText(item.getFullName());
                    mOverlayIcon.setImageResource(tv.emby.embyatv.R.drawable.photoalbum);
                    break;
                case "Video":
                    mOverlayName.setText(item.getFullName());
                    mOverlayIcon.setImageResource(tv.emby.embyatv.R.drawable.film);
                    break;
                case "MusicArtist":
                case "Person":
                    mOverlayName.setText(item.getFullName());
                    mOverlayIcon.setImageResource(tv.emby.embyatv.R.drawable.user);
                    break;
                default:
                    mOverlayName.setText(item.getFullName());
                    mOverlayIcon.setImageResource(item.isFolder() ? tv.emby.embyatv.R.drawable.foldersmall : tv.emby.embyatv.R.drawable.blank30x30);
                    break;
            }
            mOverlayCount.setText(item.getChildCountStr());
            mInfoOverlay.setVisibility(VISIBLE);
        } else {
            mInfoOverlay.setVisibility(GONE);
        }
    }

    public CharSequence getTitleText() {
        if (mTitleView == null) {
            return null;
        }

        return mTitleView.getText();
    }

    public void setContentText(CharSequence text) {
        if (mContentView == null) {
            return;
        }

        mContentView.setText(text);
        setTextMaxLines();
    }

    public CharSequence getContentText() {
        if (mContentView == null) {
            return null;
        }

        return mContentView.getText();
    }

    public void setBadgeImage(Drawable drawable) {
        if (mBadgeImage == null) {
            return;
        }

        if (drawable != null) {
            mBadgeImage.setImageDrawable(drawable);
            mBadgeImage.setVisibility(View.VISIBLE);
        } else {
            mBadgeImage.setVisibility(View.GONE);
        }
    }

    public Drawable getBadgeImage() {
        if (mBadgeImage == null) {
            return null;
        }

        return mBadgeImage.getDrawable();
    }

    private void fadeIn(View v) {
        v.setAlpha(0f);
        v.animate().alpha(1f).setDuration(v.getContext().getResources().getInteger(
                android.R.integer.config_shortAnimTime)).start();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void setTextMaxLines() {
        if (TextUtils.isEmpty(getTitleText())) {
            mContentView.setMaxLines(2);
        } else {
            mContentView.setMaxLines(1);
        }
        if (TextUtils.isEmpty(getContentText())) {
            mTitleView.setMaxLines(2);
        } else {
            mTitleView.setMaxLines(1);
        }
    }

    public void clearBanner() {
        if (mBanner != null) {
            mBanner.setVisibility(GONE);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        mImageView.animate().cancel();
        mImageView.setAlpha(1f);
        super.onDetachedFromWindow();
    }

}

