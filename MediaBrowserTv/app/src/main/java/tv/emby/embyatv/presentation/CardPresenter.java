package tv.emby.embyatv.presentation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v17.leanback.widget.ImageCardView;
import android.support.v17.leanback.widget.Presenter;
import android.view.View;
import android.view.ViewGroup;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.util.Date;

import mediabrowser.model.dto.BaseItemDto;
import mediabrowser.model.entities.LocationType;
import mediabrowser.model.livetv.ChannelInfoDto;
import tv.emby.embyatv.R;
import tv.emby.embyatv.itemhandling.BaseRowItem;
import tv.emby.embyatv.model.ImageType;
import tv.emby.embyatv.util.Utils;

public class CardPresenter extends Presenter {
    private static final String TAG = "CardPresenter";
    private int mStaticHeight = 300;
    private String mImageType = ImageType.DEFAULT;

    private static Context mContext;
    private boolean mShowInfo = true;

    public CardPresenter() {
        super();
    }

    public CardPresenter(boolean showInfo) {
        this();
        mShowInfo = showInfo;
    }

    public CardPresenter(boolean showInfo, String imageType, int staticHeight) {
        this(showInfo, staticHeight);
        mImageType = imageType;
    }

    public CardPresenter(boolean showInfo, int staticHeight) {
        this(showInfo);
        mStaticHeight = staticHeight;
    }

    static class ViewHolder extends Presenter.ViewHolder {
        private int cardWidth = 230;

        private int cardHeight = 280;
        private BaseRowItem mItem;
        private MyImageCardView mCardView;
        private Drawable mDefaultCardImage;
        private PicassoImageCardViewTarget mImageCardViewTarget;

        public ViewHolder(View view) {
            super(view);
            mCardView = (MyImageCardView) view;

            mImageCardViewTarget = new PicassoImageCardViewTarget(mCardView);
            mDefaultCardImage = mContext.getResources().getDrawable(R.drawable.video);
        }

        public int getCardHeight() {
            return cardHeight;
        }

        public void setItem(BaseRowItem m) {
            setItem(m, ImageType.DEFAULT, 260, 300, 300);
        }

        public void setItem(BaseRowItem m, String imageType, int lHeight, int pHeight, int sHeight) {
            mItem = m;
            switch (mItem.getItemType()) {

                case BaseItem:
                    BaseItemDto itemDto = mItem.getBaseItem();
                    Double aspect = imageType.equals(ImageType.BANNER) ? 5.414 : imageType.equals(ImageType.THUMB) ? 1.779 : Utils.NullCoalesce(Utils.getImageAspectRatio(itemDto, m.getPreferParentThumb()), .7777777);
                    switch (itemDto.getType()) {
                        case "Audio":
                        case "MusicAlbum":
                            mDefaultCardImage = mContext.getResources().getDrawable(R.drawable.audio);
                            break;
                        case "Person":
                        case "MusicArtist":
                            mDefaultCardImage = mContext.getResources().getDrawable(R.drawable.person);
                            break;
                        case "RecordingGroup":
                            mDefaultCardImage = mContext.getResources().getDrawable(R.drawable.recgroup);
                            break;
                        case "Season":
                        case "Series":
                        case "Episode":
                            //TvApp.getApplication().getLogger().Debug("**** Image width: "+ cardWidth + " Aspect: " + Utils.getImageAspectRatio(itemDto, m.getPreferParentThumb()) + " Item: "+itemDto.getName());
                            mDefaultCardImage = mContext.getResources().getDrawable(R.drawable.tv);
                            switch (itemDto.getLocationType()) {

                                case FileSystem:
                                    break;
                                case Remote:
                                    break;
                                case Virtual:
                                    mCardView.setBanner((itemDto.getPremiereDate() != null ? Utils.convertToLocalDate(itemDto.getPremiereDate()) : new Date(System.currentTimeMillis()+1)).getTime() > System.currentTimeMillis() ? R.drawable.futurebanner : R.drawable.missingbanner);
                                    break;
                                case Offline:
                                    mCardView.setBanner(R.drawable.offlinebanner);
                                    break;
                            }
                            break;
                        case "CollectionFolder":
                        case "Folder":
                        case "MovieGenreFolder":
                        case "MusicGenreFolder":
                        case "MovieGenre":
                        case "Genre":
                        case "MusicGenre":
                        case "UserView":
                            mDefaultCardImage = mContext.getResources().getDrawable(R.drawable.folder);
                            break;
                        default:
                            mDefaultCardImage = mContext.getResources().getDrawable(R.drawable.video);
                            break;

                    }
                    cardHeight = !m.isStaticHeight() ? aspect > 1 ? lHeight : pHeight : sHeight;
                    cardWidth = (int)((aspect) * cardHeight);
                    if (cardWidth < 10) cardWidth = 230;  //Guard against zero size images causing picasso to barf
                    if (itemDto.getLocationType() == LocationType.Offline) mCardView.setBanner(R.drawable.offlinebanner);
                    if (itemDto.getIsPlaceHolder() != null && itemDto.getIsPlaceHolder()) mCardView.setBanner(R.drawable.externaldiscbanner);
                    mCardView.setMainImageDimensions(cardWidth, cardHeight);
                    break;
                case LiveTvChannel:
                    ChannelInfoDto channel = mItem.getChannelInfo();
                    Double tvAspect = imageType.equals(ImageType.BANNER) ? 5.414 : imageType.equals(ImageType.THUMB) ? 1.779 : Utils.NullCoalesce(channel.getPrimaryImageAspectRatio(), .7777777);
                    cardHeight = !m.isStaticHeight() ? tvAspect > 1 ? lHeight : pHeight : sHeight;
                    cardWidth = (int)((tvAspect) * cardHeight);
                    if (cardWidth < 10) cardWidth = 230;  //Guard against zero size images causing picasso to barf
                    mCardView.setMainImageDimensions(cardWidth, cardHeight);
                    mDefaultCardImage = mContext.getResources().getDrawable(R.drawable.tv);
                    break;

                case LiveTvProgram:
                    BaseItemDto program = mItem.getProgramInfo();
                    Double programAspect = program.getPrimaryImageAspectRatio();
                    if (programAspect == null) programAspect = .66667;
                    cardHeight = !m.isStaticHeight() ? programAspect > 1 ? lHeight : pHeight : sHeight;
                    cardWidth = (int)((programAspect) * cardHeight);
                    if (cardWidth < 10) cardWidth = 230;  //Guard against zero size images causing picasso to barf
                    switch (program.getLocationType()) {

                        case FileSystem:
                            break;
                        case Remote:
                            break;
                        case Virtual:
                            if (program.getStartDate() != null && Utils.convertToLocalDate(program.getStartDate()).getTime() > System.currentTimeMillis()) mCardView.setBanner(R.drawable.futurebanner);
                            if (program.getEndDate() != null && Utils.convertToLocalDate(program.getEndDate()).getTime() < System.currentTimeMillis()) mCardView.setBanner(R.drawable.missingbanner);
                            break;
                        case Offline:
                            break;
                    }
                    mCardView.setMainImageDimensions(cardWidth, cardHeight);
                    mDefaultCardImage = mContext.getResources().getDrawable(R.drawable.tv);
                    break;

                case LiveTvRecording:
                    BaseItemDto recording = mItem.getRecordingInfo();
                    Double recordingAspect = imageType.equals(ImageType.BANNER) ? 5.414 : (imageType.equals(ImageType.THUMB) ? 1.779 : Utils.NullCoalesce(recording.getPrimaryImageAspectRatio(), .7777777));
                    cardHeight = !m.isStaticHeight() ? recordingAspect > 1 ? lHeight : pHeight : sHeight;
                    cardWidth = (int)((recordingAspect) * cardHeight);
                    if (cardWidth < 10) cardWidth = 230;  //Guard against zero size images causing picasso to barf
                    mCardView.setMainImageDimensions(cardWidth, cardHeight);
                    mDefaultCardImage = mContext.getResources().getDrawable(R.drawable.tv);
                    break;

                case Server:
                    cardWidth = (int)(.777777777 * cardHeight);
                    mCardView.setMainImageDimensions(cardWidth, cardHeight);
                    mDefaultCardImage = mContext.getResources().getDrawable(R.drawable.server);
                case Person:
                    cardWidth = (int)(.777777777 * cardHeight);
                    mCardView.setMainImageDimensions(cardWidth, cardHeight);
                    mDefaultCardImage = mContext.getResources().getDrawable(R.drawable.person);
                    break;
                case User:
                    cardWidth = (int)(.777777777 * cardHeight);
                    mCardView.setMainImageDimensions(cardWidth, cardHeight);
                    mDefaultCardImage = mContext.getResources().getDrawable(R.drawable.person);
                    break;
                case Chapter:
                    cardWidth = (int)(1.779 * cardHeight);
                    mCardView.setMainImageDimensions(cardWidth, cardHeight);
                    mDefaultCardImage = mContext.getResources().getDrawable(R.drawable.video);
                    break;
                case SearchHint:
                    switch (mItem.getSearchHint().getType()) {
                        case "Episode":
                            cardWidth = (int)(1.779 * cardHeight);
                            mCardView.setMainImageDimensions(cardWidth, cardHeight);
                            mDefaultCardImage = mContext.getResources().getDrawable(R.drawable.tv);
                            break;
                        case "Person":
                            cardWidth = (int)(.777777777 * cardHeight);
                            mCardView.setMainImageDimensions(cardWidth, cardHeight);
                            mDefaultCardImage = mContext.getResources().getDrawable(R.drawable.person);
                            break;
                        default:
                            cardWidth = (int)(.777777777 * cardHeight);
                            mCardView.setMainImageDimensions(cardWidth, cardHeight);
                            mDefaultCardImage = mContext.getResources().getDrawable(R.drawable.video);
                            break;
                    }
                    break;
                case GridButton:
                    cardHeight = !m.isStaticHeight() ? pHeight : sHeight;
                    cardWidth = (int)(.777777777 * cardHeight);
                    mCardView.setMainImageDimensions(cardWidth, cardHeight);
                    mDefaultCardImage = mContext.getResources().getDrawable(R.drawable.video);
                    break;

            }
        }

        public BaseRowItem getItem() {
            return mItem;
        }

        public ImageCardView getCardView() {
            return mCardView;
        }

        protected void updateCardViewImage(int image) {
            Picasso.with(mContext)
                    .load(image)
                    .resize(cardWidth, cardHeight)
                    .centerCrop()
                    .error(mDefaultCardImage)
                    .into(mImageCardViewTarget);
        }

        protected void updateCardViewImage(String url) {
            if (url == null) {
                Picasso.with(mContext)
                        .load("nothing")
                        .resize(cardWidth, cardHeight)
                        .centerCrop()
                        .error(mDefaultCardImage)
                        .into(mImageCardViewTarget);

            } else {
                Picasso.with(mContext)
                        .load(url)
                        .skipMemoryCache()
                        .resize(cardWidth, cardHeight)
                        .centerCrop()
                        .error(mDefaultCardImage)
                        .into(mImageCardViewTarget);
            }
        }

        protected void resetCardViewImage() {
            mCardView.clearBanner();
            Picasso.with(mContext)
                    .load(Uri.parse("android.resource://tv.emby.embyatv/drawable/loading"))
                    .skipMemoryCache()
                    .resize(cardWidth, cardHeight)
                    .centerCrop()
                    .error(mDefaultCardImage)
                    .into(mImageCardViewTarget);

        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        //Log.d(TAG, "onCreateViewHolder");
        mContext = parent.getContext();

        MyImageCardView cardView = new MyImageCardView(mContext, mShowInfo);
        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);
        cardView.setBackgroundColor(mContext.getResources().getColor(R.color.lb_basic_card_info_bg_color));
        return new ViewHolder(cardView);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        if (!(item instanceof BaseRowItem)) return;
        BaseRowItem rowItem = (BaseRowItem) item;

        ((ViewHolder) viewHolder).setItem(rowItem, mImageType, 260, 300, mStaticHeight);

        //Log.d(TAG, "onBindViewHolder");
        ((ViewHolder) viewHolder).mCardView.setTitleText(rowItem.getFullName());
        ((ViewHolder) viewHolder).mCardView.setContentText(rowItem.getSubText());
        Drawable badge = rowItem.getBadgeImage();
        if (badge != null) {
            ((ViewHolder) viewHolder).mCardView.setBadgeImage(badge);

        }

        ((ViewHolder) viewHolder).updateCardViewImage(rowItem.getImageUrl(mImageType, ((ViewHolder) viewHolder).getCardHeight()));

    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        //Log.d(TAG, "onUnbindViewHolder");
        //Get the image out of there so won't be there if recycled
        ((ViewHolder) viewHolder).resetCardViewImage();
    }

    @Override
    public void onViewAttachedToWindow(Presenter.ViewHolder viewHolder) {
        //Log.d(TAG, "onViewAttachedToWindow");
    }

    public static class PicassoImageCardViewTarget implements Target {
        private ImageCardView mImageCardView;

        public PicassoImageCardViewTarget(ImageCardView mImageCardView) {
            this.mImageCardView = mImageCardView;
        }

        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom loadedFrom) {
            Drawable bitmapDrawable = new BitmapDrawable(mContext.getResources(), bitmap);
            mImageCardView.setMainImage(bitmapDrawable);
        }

        @Override
        public void onBitmapFailed(Drawable drawable) {
            mImageCardView.setMainImage(drawable);
        }

        @Override
        public void onPrepareLoad(Drawable drawable) {
            // Do nothing, default_background manager has its own transitions
        }
    }

}
