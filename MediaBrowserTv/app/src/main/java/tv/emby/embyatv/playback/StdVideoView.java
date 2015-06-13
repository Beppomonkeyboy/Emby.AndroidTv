package tv.emby.embyatv.playback;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.VideoView;

/**
 * Created by Eric on 6/13/2015.
 */
public class StdVideoView extends VideoView implements IVideoView {
    public StdVideoView(Context context) {
        super(context);
    }

    public StdVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StdVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

}
