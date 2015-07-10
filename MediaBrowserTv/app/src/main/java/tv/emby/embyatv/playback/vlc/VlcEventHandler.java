package tv.emby.embyatv.playback.vlc;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import org.videolan.libvlc.EventHandler;
import org.videolan.libvlc.LibVLC;

import mediabrowser.model.logging.ILogger;

/**
 * Created by Luke on 6/10/2015.
 */
public class VlcEventHandler extends Handler {

    private ILogger logger;
    protected LibVLC mLibVLC;

    private long lastReportTime;

    public VlcEventHandler(ILogger logger, LibVLC mLibVLC) {
        this.logger = logger;
        this.mLibVLC = mLibVLC;
    }

    @Override
    public void handleMessage(Message msg) {
        /*VideoActivity player = mOwner.get();

        // SamplePlayer events
        if (msg.what == VideoSizeChanged) {
            player.setSize(msg.arg1, msg.arg2);
            return;
        }*/

        // Libvlc events
        Bundle b = msg.getData();
        switch (b.getInt("event")) {
            case EventHandler.HardwareAccelerationError:
                logger.Debug("MediaPlayerStopped");
                break;
            case EventHandler.MediaMetaChanged:
                logger.Debug("MediaMetaChanged");
                break;
            case EventHandler.MediaParsedChanged:
                logger.Debug("MediaParsedChanged");
                break;
            case EventHandler.MediaPlayerEncounteredError:
                logger.Debug("MediaPlayerEncounteredError");
                reportState("playbackstop");
                break;
            case EventHandler.MediaPlayerEndReached:
                logger.Debug("MediaPlayerEndReached");
                reportState("playbackstop");
                break;
            case EventHandler.MediaPlayerESAdded:
                logger.Debug("MediaPlayerESAdded");
                break;
            case EventHandler.MediaPlayerESDeleted:
                logger.Debug("MediaPlayerESDeleted");
                break;
            case EventHandler.MediaPlayerPaused:
                logger.Debug("MediaPlayerPaused");
                reportState("paused");
                break;
            case EventHandler.MediaPlayerPlaying:
                logger.Debug("MediaPlayerPlaying");
                reportState("playing");
                break;
            case EventHandler.MediaPlayerPositionChanged:
                logger.Debug("MediaPlayerPositionChanged");
                break;
            case EventHandler.MediaPlayerStopped:
                logger.Debug("MediaPlayerStopped");
                reportState("playbackstop");
                break;
            case EventHandler.MediaPlayerTimeChanged:
                logger.Debug("MediaPlayerTimeChanged");

                // Avoid overly aggressive reporting
                if ((System.currentTimeMillis() - lastReportTime) < 500){
                    return;
                }

                lastReportTime = System.currentTimeMillis();
                reportState("positionchange");
                break;
            case EventHandler.MediaPlayerVout:
                logger.Debug("MediaPlayerVout");
                break;
            default:
                break;
        }
    }

    private void reportState(String eventName) {

        int playerState = mLibVLC.getPlayerState();

        // Expected states by web plugins are: IDLE/CLOSE=0, OPENING=1, BUFFERING=2, PLAYING=3, PAUSED=4, STOPPING=5, ENDED=6, ERROR=7
        boolean isPaused = eventName.equalsIgnoreCase("playbackstop") ?
                false :
                eventName.equalsIgnoreCase("paused") || playerState == 4;

        logger.Debug("Vlc player state: %s", playerState);

        long length = mLibVLC.getLength() / 1000;

        long time = mLibVLC.getTime();

        int volume = mLibVLC.getVolume();

        String js = String.format("window.AudioRenderer.Current.report('%s', %s, %s, %s, %s)",
                eventName,
                String.valueOf(length).toLowerCase(),
                String.valueOf(time).toLowerCase(),
                String.valueOf(isPaused).toLowerCase(),
                String.valueOf(volume).toLowerCase());

        RespondToWebView(js);
    }

    private void RespondToWebView(final String js) {

        //logger.Info("Sending url to webView: %s", js);
        //MainActivity.RespondToWebView(js);
    }
}
