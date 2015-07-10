package tv.emby.embyatv.playback.vlc;



import mediabrowser.apiinteraction.android.mediabrowser.IMediaRes;
import tv.emby.embyatv.R;

/**
 * Created by Luke on 7/4/2015.
 */
public class MediaRes implements IMediaRes {

    @Override
    public int getFavoriteOnIcon() {
        return R.drawable.ic_star_on;
    }

    @Override
    public int getFavoriteOffIcon() {
        return R.drawable.ic_star_off;
    }

    @Override
    public int getFavoriteButtonString() {
        return R.string.favorite;
    }

    @Override
    public int getAppIcon() {
        return R.drawable.icon;
    }
}
