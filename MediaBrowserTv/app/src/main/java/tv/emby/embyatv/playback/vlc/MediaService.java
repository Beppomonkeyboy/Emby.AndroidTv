package tv.emby.embyatv.playback.vlc;

import android.annotation.TargetApi;

import mediabrowser.apiinteraction.android.VolleyHttpClient;
import mediabrowser.apiinteraction.android.mediabrowser.BaseMediaBrowserService;
import mediabrowser.apiinteraction.android.mediabrowser.IMediaRes;
import mediabrowser.apiinteraction.android.mediabrowser.IPlayback;
import mediabrowser.apiinteraction.android.mediabrowser.IPlaybackCallback;
import mediabrowser.logging.ConsoleLogger;
import mediabrowser.model.logging.ILogger;
import tv.emby.embyatv.TvApp;

@TargetApi(21)
public class MediaService extends BaseMediaBrowserService implements IPlaybackCallback {

    @Override
    protected ILogger createLogger() {
        return TvApp.getApplication().getLogger();
    }

    @Override
    protected IPlayback createPlayback() {
        return new Playback(this, mMediaProvider, logger);
    }

    @Override
    protected IMediaRes createMediaRes() {
        return new MediaRes();
    }

    @Override
    protected VolleyHttpClient getHttpClient() {

        VolleyHttpClient httpClient = null;

        if (httpClient == null) {
            httpClient = new VolleyHttpClient(new ConsoleLogger(), getApplicationContext());
        }

        return httpClient;
    }

    @Override
    public Class<?> getServiceClass() {
        return MediaService.class;
    }

    @Override
    protected void handleNextTrackRequest() {

        //MainActivity.RespondToWebView("MediaController.nextTrack();");
    }

    @Override
    protected void handlePreviousTrackRequest() {

        //MainActivity.RespondToWebView("MediaController.previousTrack();");
    }
}