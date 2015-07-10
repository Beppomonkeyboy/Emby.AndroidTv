package tv.emby.embyatv.playback.vlc;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.Presentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaRouter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.support.v4.view.GestureDetectorCompat;

import android.support.v7.widget.PopupMenu;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.GestureDetector;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import org.videolan.libvlc.EventHandler;
import org.videolan.libvlc.IVideoPlayer;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcUtil;
import org.videolan.libvlc.Media;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.StreamCorruptedException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import mediabrowser.apiinteraction.ApiClient;
import mediabrowser.apiinteraction.ApiEventListener;
import mediabrowser.apiinteraction.EmptyResponse;
import mediabrowser.apiinteraction.android.AndroidApiClient;
import mediabrowser.apiinteraction.android.AndroidDevice;
import mediabrowser.apiinteraction.android.GsonJsonSerializer;
import mediabrowser.apiinteraction.android.VolleyHttpClient;
import mediabrowser.apiinteraction.android.mediabrowser.Constants;
import mediabrowser.apiinteraction.device.IDevice;
import mediabrowser.apiinteraction.http.IAsyncHttpClient;
import mediabrowser.logging.ConsoleLogger;
import mediabrowser.model.dto.MediaSourceInfo;
import mediabrowser.model.logging.ILogger;
import mediabrowser.model.serialization.IJsonSerializer;
import mediabrowser.model.session.PlaybackProgressInfo;
import tv.emby.embyatv.R;
import tv.emby.embyatv.TvApp;
import tv.emby.embyatv.base.BaseActivity;

public class VideoPlayerActivity extends BaseActivity implements IVideoPlayer, GestureDetector.OnDoubleTapListener, IDelayController {

    public final static String TAG = "VLC/VideoPlayerActivity";

    // Internal intent identifier to distinguish between internal launch and
    // external intent.
    public final static String PLAY_FROM_VIDEOGRID = "org.videolan.vlc.gui.video.PLAY_FROM_VIDEOGRID";

    public final static String PLAY_EXTRA_ITEM_LOCATION = "item_location";
    public final static String PLAY_EXTRA_SUBTITLES_LOCATION = "subtitles_location";
    public final static String PLAY_EXTRA_ITEM_TITLE = "item_title";
    public final static String PLAY_EXTRA_FROM_START = "from_start";
    public final static String PLAY_EXTRA_OPENED_POSITION = "opened_position";

    private SurfaceView mSurfaceView;
    private SurfaceView mSubtitlesSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private SurfaceHolder mSubtitlesSurfaceHolder;
    private Surface mSurface = null;
    private Surface mSubtitleSurface = null;
    private FrameLayout mSurfaceFrame;
    private MediaRouter mMediaRouter;
    private MediaRouter.SimpleCallback mMediaRouterCallback;
    private SecondaryDisplay mPresentation;
    private int mPresentationDisplayId = -1;
    private LibVLC mLibVLC;
    private MediaWrapperListPlayer mMediaListPlayer;
    private String mLocation;
    private GestureDetectorCompat mDetector;

    private static final int SURFACE_BEST_FIT = 0;
    private static final int SURFACE_FIT_HORIZONTAL = 1;
    private static final int SURFACE_FIT_VERTICAL = 2;
    private static final int SURFACE_FILL = 3;
    private static final int SURFACE_16_9 = 4;
    private static final int SURFACE_4_3 = 5;
    private static final int SURFACE_ORIGINAL = 6;
    private int mCurrentSize = SURFACE_BEST_FIT;

    private SharedPreferences mSettings;

    /** Overlay */
    private ActionBar mActionBar;
    private View mOverlayProgress;
    private View mOverlayBackground;
    private View mOverlayButtons;
    private static final int OVERLAY_TIMEOUT = 4000;
    private static final int OVERLAY_INFINITE = -1;
    private static final int FADE_OUT = 1;
    private static final int SHOW_PROGRESS = 2;
    private static final int SURFACE_LAYOUT = 3;
    private static final int FADE_OUT_INFO = 4;
    private static final int START_PLAYBACK = 5;
    private static final int AUDIO_SERVICE_CONNECTION_FAILED = 6;
    private static final int RESET_BACK_LOCK = 7;
    private static final int CHECK_VIDEO_TRACKS = 8;
    private boolean mDragging;
    private boolean mShowing;
    private DelayState mDelay = DelayState.OFF;
    private int mUiVisibility = -1;
    private SeekBar mSeekbar;
    private TextView mTitle;
    private TextView mSysTime;
    private TextView mBattery;
    private TextView mTime;
    private TextView mLength;
    private TextView mInfo;
    private View mVerticalBar;
    private View mVerticalBarProgress;
    private ImageView mLoading;
    private TextView mLoadingText;
    private ImageView mTipsBackground;
    private ImageView mPlayPause;
    private ImageView mTracks;
    private ImageView mAdvOptions;
    private ImageView mDelayPlus;
    private ImageView mDelayMinus;
    private boolean mEnableBrightnessGesture;
    private boolean mEnableCloneMode;
    private boolean mDisplayRemainingTime = false;
    private int mScreenOrientation;
    private int mScreenOrientationLock;
    private ImageView mLock;
    private ImageView mSize;
    private boolean mIsLocked = false;
    private int mLastAudioTrack = -1;
    private int mLastSpuTrack = -2;
    private int mOverlayTimeout = 0;
    private boolean mLockBackButton = false;

    long resumePositionMs = 0;

    /**
     * For uninterrupted switching between audio and video mode
     */
    private boolean mSwitchingView;
    private boolean mHardwareAccelerationError;
    private boolean mEndReached;
    private boolean mCanSeek;

    // Playlist
    private int savedIndexPosition = -1;

    // size of the video
    private int mVideoHeight;
    private int mVideoWidth;
    private int mVideoVisibleHeight;
    private int mVideoVisibleWidth;
    private int mSarNum;
    private int mSarDen;

    //Volume
    private AudioManager mAudioManager;
    private int mAudioMax;
    private boolean mMute = false;
    private int mVolSave;
    private float mVol;

    //Touch Events
    private static final int TOUCH_NONE = 0;
    private static final int TOUCH_VOLUME = 1;
    private static final int TOUCH_BRIGHTNESS = 2;
    private static final int TOUCH_SEEK = 3;
    private int mTouchAction = TOUCH_NONE;
    private int mSurfaceYDisplayRange;
    private float mInitTouchY, mTouchY =-1f, mTouchX=-1f;

    //stick event
    private static final int JOYSTICK_INPUT_DELAY = 300;
    private long mLastMove;

    // Brightness
    private boolean mIsFirstBrightnessGesture = true;
    private float mRestoreAutoBrightness = -1f;

    // Tracks & Subtitles
    private Map<Integer,String> mAudioTracksList;
    private Map<Integer,String> mSubtitleTracksList;
    /**
     * Used to store a selected subtitle; see onActivityResult.
     * It is possible to have multiple custom subs in one session
     * (just like desktop VLC allows you as well.)
     */
    private final ArrayList<String> mSubtitleSelectedFiles = new ArrayList<String>();

    // Whether fallback from HW acceleration to SW decoding was done.
    private boolean mDisabledHardwareAcceleration = false;
    private int mPreviousHardwareAccelerationMode;

    /**
     * Flag to indicate whether the media should be paused once loaded
     * (e.g. lock screen, or to restore the pause state)
     */
    private boolean mPlaybackStarted = false;

    /**
     * Flag used by changeAudioFocus and mAudioFocusListener
     */
    private boolean mLostFocus = false;
    private boolean mHasAudioFocus = false;

    /* Flag to indicate if AudioService is bound or binding */
    private boolean mBound = false;

    // Tips
    private View mOverlayTips;
    private static final String PREF_TIPS_SHOWN = "video_player_tips_shown";

    // Navigation handling (DVD, Blu-Ray...)
    private boolean mHasMenu = false;
    private boolean mIsNavMenu = false;

    /* for getTime and seek */
    private long mForcedTime = -1;
    private long mLastTime = -1;

    private OnLayoutChangeListener mOnLayoutChangeListener;
    private AlertDialog mAlertDialog;

    private boolean mAudioServiceReady = false;
    private boolean mSurfaceReady = false;
    private boolean mSubtitleSurfaceReady = false;

    private boolean mHasHdmiAudio = false;

    private MediaSourceInfo currentMediaSource;
    private ILogger logger;
    private IJsonSerializer jsonSerializer = new GsonJsonSerializer();
    private IAsyncHttpClient httpClient;

    @Override
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!VLCInstance.testCompatibleCPU(this)) {
            Intent returnIntent = new Intent();
            returnIntent.putExtra("error",true);
            setResult(RESULT_CANCELED,returnIntent);
            return;
        }

        logger = TvApp.getApplication().getLogger();
        mHandler = new VideoPlayerHandler(this, logger);
        mLibVLC = VLCInstance.get(getApplication(), this, logger);
        mEventHandler = new VideoPlayerEventHandler(this, logger, mLibVLC);

        if (LibVlcUtil.isJellyBeanMR1OrLater()) {
            // Get the media router service (Miracast)
            mMediaRouter = (MediaRouter) getSystemService(Context.MEDIA_ROUTER_SERVICE);
            mMediaRouterCallback = new MediaRouter.SimpleCallback() {
                @Override
                public void onRoutePresentationDisplayChanged(
                        MediaRouter router, MediaRouter.RouteInfo info) {
                    logger.Debug("onRoutePresentationDisplayChanged: info=" + info);
                    final Display presentationDisplay = info.getPresentationDisplay();
                    final int newDisplayId = presentationDisplay != null ? presentationDisplay.getDisplayId() : -1;
                    if (newDisplayId != mPresentationDisplayId)
                        removePresentation();
                }
            };
            logger.Debug("MediaRouter information : " + mMediaRouter  .toString());
        }

        mSettings = PreferenceManager.getDefaultSharedPreferences(this);

        /* Services and miscellaneous */
        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        mAudioMax = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        mEnableCloneMode = mSettings.getBoolean("enable_clone_mode", false);
        createPresentation();
        setContentView(mPresentation == null ? R.layout.player : R.layout.player_remote_control);

        if (LibVlcUtil.isICSOrLater())
            getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(
                    new OnSystemUiVisibilityChangeListener() {
                        @Override
                        public void onSystemUiVisibilityChange(int visibility) {
                            if (visibility == mUiVisibility)
                                return;
                            if (visibility == View.SYSTEM_UI_FLAG_VISIBLE && !mShowing && !isFinishing()) {
                                showOverlay();
                            }
                            mUiVisibility = visibility;
                        }
                    }
            );

        /** initialize Views an their Events */
        mActionBar = getActionBar();
        if (mActionBar != null) {
            mActionBar.setDisplayShowHomeEnabled(false);
            mActionBar.setDisplayShowTitleEnabled(false);
            mActionBar.setBackgroundDrawable(null);
            mActionBar.setDisplayShowCustomEnabled(true);
            mActionBar.setCustomView(R.layout.player_action_bar);

        }

        ViewGroup view = (ViewGroup) findViewById(android.R.id.content);
        /* Dispatch ActionBar touch events to the Activity */
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                onTouchEvent(event);
                return true;
            }
        });
        mTitle = (TextView) view.findViewById(R.id.player_overlay_title);
        mSysTime = (TextView) findViewById(R.id.player_overlay_systime);
        mBattery = (TextView) findViewById(R.id.player_overlay_battery);
        mOverlayProgress = findViewById(R.id.progress_overlay);
        RelativeLayout.LayoutParams layoutParams =
                (RelativeLayout.LayoutParams)mOverlayProgress.getLayoutParams();
        if (AndroidDevices.isPhone(getApplicationContext()) || !AndroidDevices.hasNavBar()) {
            layoutParams.width = LayoutParams.MATCH_PARENT;
        } else {
            layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
        }
        mOverlayProgress.setLayoutParams(layoutParams);
        mOverlayBackground = findViewById(R.id.player_overlay_background);
        mOverlayButtons =  findViewById(R.id.player_overlay_buttons);

        // Position and remaining time
        mTime = (TextView) findViewById(R.id.player_overlay_time);
        mTime.setOnClickListener(mRemainingTimeListener);
        mLength = (TextView) findViewById(R.id.player_overlay_length);
        mLength.setOnClickListener(mRemainingTimeListener);

        // the info textView is not on the overlay
        mInfo = (TextView) findViewById(R.id.player_overlay_info);
        mVerticalBar = findViewById(R.id.verticalbar);
        mVerticalBarProgress = findViewById(R.id.verticalbar_progress);

        mEnableBrightnessGesture = mSettings.getBoolean("enable_brightness_gesture", true);
        mScreenOrientation = Integer.valueOf(
                mSettings.getString("screen_orientation_value", "4" /*SCREEN_ORIENTATION_SENSOR*/));

        mPlayPause = (ImageView) findViewById(R.id.player_overlay_play);
        mPlayPause.setOnClickListener(mPlayPauseListener);

        mTracks = (ImageView) findViewById(R.id.player_overlay_tracks);
        //mAdvOptions = (ImageView) findViewById(R.id.player_overlay_adv_function);
        mAdvOptions = null;
        mLock = (ImageView) findViewById(R.id.lock_overlay_button);
        mLock.setOnClickListener(mLockListener);

        mSize = (ImageView) findViewById(R.id.player_overlay_size);
        mSize.setOnClickListener(mSizeListener);

        mDelayPlus = (ImageView) findViewById(R.id.player_delay_plus);
        mDelayMinus = (ImageView) findViewById(R.id.player_delay_minus);

        mMediaListPlayer = MediaWrapperListPlayer.getInstance(mLibVLC);

        mSurfaceView = (SurfaceView) findViewById(R.id.player_surface);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceFrame = (FrameLayout) findViewById(R.id.player_surface_frame);

        mSubtitlesSurfaceView = (SurfaceView) findViewById(R.id.subtitles_surface);
        mSubtitlesSurfaceHolder = mSubtitlesSurfaceView.getHolder();
        mSubtitlesSurfaceView.setZOrderMediaOverlay(true);
        mSubtitlesSurfaceHolder.setFormat(PixelFormat.TRANSLUCENT);

        if (mLibVLC.useCompatSurface()) {
            mSubtitlesSurfaceView.setVisibility(View.GONE);
            mSubtitleSurfaceReady = true;
        }
        if (mPresentation == null) {
            mSurfaceHolder.addCallback(mSurfaceCallback);
            mSubtitlesSurfaceHolder.addCallback(mSubtitlesSurfaceCallback);
        }

        mSeekbar = (SeekBar) findViewById(R.id.player_overlay_seekbar);
        mSeekbar.setOnSeekBarChangeListener(mSeekListener);

        /* Loading view */
        mLoading = (ImageView) findViewById(R.id.player_overlay_loading);
        mLoadingText = (TextView) findViewById(R.id.player_overlay_loading_text);
        if (mPresentation != null)
            mTipsBackground = (ImageView) findViewById(R.id.player_remote_tips_background);
        startLoadingAnimation();

        mSwitchingView = false;
        mHardwareAccelerationError = false;
        mEndReached = false;

        IntentFilter filter = new IntentFilter();
        if (mBattery != null)
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Constants.SLEEP_INTENT);
        registerReceiver(mReceiver, filter);
        if (mReceiverV21 != null)
            registerV21();

        logger.Debug("Hardware acceleration mode: "
                        + Integer.toString(mLibVLC.getHardwareAcceleration()));


        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Extra initialization when no secondary display is detected
        if (mPresentation == null) {
            // Orientation
            // 100 is the value for screen_orientation_start_lock
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            // Tips
            mOverlayTips = findViewById(R.id.player_overlay_tips);
            if(isTv() || mSettings.getBoolean(PREF_TIPS_SHOWN, false))
                mOverlayTips.setVisibility(View.GONE);
            else {
                mOverlayTips.bringToFront();
                mOverlayTips.invalidate();
            }
        } else
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        resetHudLayout();
        updateNavStatus();
        mDetector = new GestureDetectorCompat(this, mGestureListener);
        mDetector.setOnDoubleTapListener(this);

    }

    public boolean onCreateOptionsMenu(Menu menu){
        return super.onCreateOptionsMenu(menu);
    }

    public boolean onPrepareOptionsMenu(Menu menu){
        return super.onPrepareOptionsMenu(menu);
    }

    public boolean onOptionsItemSelected(MenuItem item){
        switch (item.getItemId()) {
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    @Override
    protected void onPause() {
        super.onPause();

        /* Stop the earliest possible to avoid vout error */
        if (isFinishing())
            stopPlayback();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (!LibVlcUtil.isHoneycombOrLater())
            setSurfaceLayout(mVideoWidth, mVideoHeight, mVideoVisibleWidth, mVideoVisibleHeight, mSarNum, mSarDen);
        super.onConfigurationChanged(newConfig);
        resetHudLayout();
    }

    public void resetHudLayout() {
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)mOverlayButtons.getLayoutParams();
        if (getScreenOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            layoutParams.addRule(RelativeLayout.BELOW, R.id.player_overlay_length);
            layoutParams.addRule(RelativeLayout.RIGHT_OF, 0);
            layoutParams.addRule(RelativeLayout.LEFT_OF, 0);
        } else {
            layoutParams.addRule(RelativeLayout.BELOW, R.id.player_overlay_seekbar);
            layoutParams.addRule(RelativeLayout.RIGHT_OF, R.id.player_overlay_time);
            layoutParams.addRule(RelativeLayout.LEFT_OF, R.id.player_overlay_length);
        }
        mOverlayButtons.setLayoutParams(layoutParams);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    protected void onStop() {

        super.onStop();

        if (mAlertDialog != null && mAlertDialog.isShowing())
            mAlertDialog.dismiss();
        if (!isFinishing() && mSettings.getBoolean(PreferencesActivity.VIDEO_BACKGROUND, false)) {
            Util.commitPreferences(mSettings.edit().putBoolean(PreferencesActivity.VIDEO_RESTORE, true));
            switchToAudioMode(false);
        }
        stopPlayback();

        // Dismiss the presentation when the activity is not visible.
        if (mPresentation != null) {
            logger.Info("Dismissing presentation because the activity is no longer visible.");
            mPresentation.dismiss();
            mPresentation = null;
        }
        restoreBrightness();
    }

    @TargetApi(android.os.Build.VERSION_CODES.FROYO)
    private void restoreBrightness() {
        if (mRestoreAutoBrightness != -1f) {
            int brightness = (int) (mRestoreAutoBrightness*255f);
            /*Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS,
                    brightness);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);*/
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mReceiver != null)
            unregisterReceiver(mReceiver);
        if (mReceiverV21 != null)
            unregisterReceiver(mReceiverV21);

        mAudioManager = null;
    }

    private void bindAudioService() {
        if (mBound)
            return;
        mBound = true;

        // TODO: Review
        /*AudioServiceController.getInstance().bindAudioService(this,
                new AudioServiceController.AudioServiceConnectionListener() {
                    @Override
                    public void onConnectionSuccess() {
                        mAudioServiceReady = true;
                        mHandler.sendEmptyMessage(START_PLAYBACK);
                    }

                    @Override
                    public void onConnectionFailed() {
                        mBound = false;
                        mAudioServiceReady = false;
                        mHandler.sendEmptyMessage(AUDIO_SERVICE_CONNECTION_FAILED);
                    }
                });*/

        mAudioServiceReady = true;
        mHandler.sendEmptyMessage(START_PLAYBACK);
    }
    private void unbindAudioService() {
        //AudioServiceController.getInstance().unbindAudioService(this);
        mAudioServiceReady = false;
        mBound = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSwitchingView = false;

        bindAudioService();

        if (mIsLocked && mScreenOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR)
            setRequestedOrientation(mScreenOrientationLock);
    }

    /**
     * Add or remove MediaRouter callbacks. This is provided for version targeting.
     *
     * @param add true to add, false to remove
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void mediaRouterAddCallback(boolean add) {
        if(!LibVlcUtil.isJellyBeanMR1OrLater() || mMediaRouter == null) return;

        if(add)
            mMediaRouter.addCallback(MediaRouter.ROUTE_TYPE_LIVE_VIDEO, mMediaRouterCallback);
        else
            mMediaRouter.removeCallback(mMediaRouterCallback);
    }

    private void startPlayback() {
        /* start playback only when audio service and both surfaces are ready */
        if (mPlaybackStarted || !mAudioServiceReady || !mSurfaceReady || !mSubtitleSurfaceReady)
            return;

        mPlaybackStarted = true;

        if (LibVlcUtil.isHoneycombOrLater()) {
            if (mOnLayoutChangeListener == null) {
                mOnLayoutChangeListener = new View.OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(View v, int left, int top, int right,
                                               int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                        if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom)
                            setSurfaceLayout(mVideoWidth, mVideoHeight, mVideoVisibleWidth, mVideoVisibleHeight, mSarNum, mSarDen);
                    }
                };
            }
            mSurfaceFrame.addOnLayoutChangeListener(mOnLayoutChangeListener);
        }
        setSurfaceLayout(mVideoWidth, mVideoHeight, mVideoVisibleWidth, mVideoVisibleHeight, mSarNum, mSarDen);

        if (mMediaRouter != null) {
            // Listen for changes to media routes.
            mediaRouterAddCallback(true);
        }

        final EventHandler em = EventHandler.getInstance();
        em.addHandler(mEventHandler);

        loadMedia();

        mSurfaceView.setKeepScreenOn(true);

        // Add any selected subtitle file from the file picker
        if(mSubtitleSelectedFiles.size() > 0) {
            for(String file : mSubtitleSelectedFiles) {
                logger.Info("Adding user-selected subtitle " + file);
                mLibVLC.addSubtitleTrack(file);
            }
        }

        // Set user playback speed
        mLibVLC.setRate(mSettings.getFloat(PreferencesActivity.VIDEO_SPEED, 1));

    }

    private void stopPlayback() {
        if (!mPlaybackStarted)
            return;

        mPlaybackStarted = false;

        // TODO: Review
        /*if(mSwitchingView) {
            logger.Debug("mLocation = \"" + mLocation + "\"");
            AudioServiceController.getInstance().showWithoutParse(savedIndexPosition);
            unbindAudioService();
            return;
        }*/

        final EventHandler em = EventHandler.getInstance();
        em.removeHandler(mEventHandler);
        mEventHandler.removeCallbacksAndMessages(null);

        mHandler.removeCallbacksAndMessages(null);

        mSurfaceView.setKeepScreenOn(false);

        if (mMediaRouter != null) {
            // Stop listening for changes to media routes.
            mediaRouterAddCallback(false);
        }

        changeAudioFocus(false);

        final boolean isPaused = !mLibVLC.isPlaying();
        long time = getTime();
        long length = mLibVLC.getLength();
        //remove saved position if in the last 5 seconds
        if (length - time < 5000)
            time = 0;
        else
            time -= 5000; // go back 5 seconds, to compensate loading time
        mLibVLC.stop();

        SharedPreferences.Editor editor = mSettings.edit();

        if(isPaused)
            logger.Debug("Video paused - saving flag");

        // Save selected subtitles
        String subtitleList_serialized = null;
        if(mSubtitleSelectedFiles.size() > 0) {
            logger.Debug("Saving selected subtitle files");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(mSubtitleSelectedFiles);
                subtitleList_serialized = bos.toString();
            } catch(IOException e) {}
        }
        editor.putString(PreferencesActivity.VIDEO_SUBTITLE_FILES, subtitleList_serialized);

        editor.putString(PreferencesActivity.VIDEO_LAST, Uri.encode(mLocation));

        // Save user playback speed and restore normal speed
        editor.putFloat(PreferencesActivity.VIDEO_SPEED, mLibVLC.getRate());
        mLibVLC.setRate(1);

        Util.commitPreferences(editor);

        // HW acceleration was temporarily disabled because of an error, restore the previous value.
        if (mDisabledHardwareAcceleration)
            mLibVLC.setHardwareAcceleration(mPreviousHardwareAccelerationMode);

        if (LibVlcUtil.isHoneycombOrLater() && mOnLayoutChangeListener != null)
            mSurfaceFrame.removeOnLayoutChangeListener(mOnLayoutChangeListener);

        unbindAudioService();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(data == null) return;

        if(data.getDataString() == null) {
            logger.Debug("Subtitle selection dialog was cancelled");
        }
        if(data.getData() == null) return;

        String subtitlePath = data.getData().getPath();
        if(requestCode == CommonDialogs.INTENT_SPECIFIC) {
            logger.Debug("Specific subtitle file: " + subtitlePath);
        } else if(requestCode == CommonDialogs.INTENT_GENERIC) {
            logger.Debug("Generic subtitle file: " + subtitlePath);
        }
        mSubtitleSelectedFiles.add(subtitlePath);
    }

    public static void start(Context context, String location) {
        start(context, location, null, false, -1);
    }

    public static void start(Context context, String location, boolean fromStart) {
        start(context, location, null, fromStart, -1);
    }

    public static void start(Context context, String location, String title) {
        start(context, location, title, false, -1);
    }
    public static void startOpened(Context context, int openedPosition) {
        start(context, null, null, false, openedPosition);
    }

    private static void start(Context context, String location, String title, boolean fromStart, int openedPosition) {
        Intent intent = new Intent(context, VideoPlayerActivity.class);
        intent.setAction(VideoPlayerActivity.PLAY_FROM_VIDEOGRID);
        intent.putExtra(PLAY_EXTRA_ITEM_LOCATION, location);
        intent.putExtra(PLAY_EXTRA_ITEM_TITLE, title);
        intent.putExtra(PLAY_EXTRA_FROM_START, fromStart);
        intent.putExtra(PLAY_EXTRA_OPENED_POSITION, openedPosition);

        if (openedPosition != -1)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

        context.startActivity(intent);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String action = intent.getAction();
            if (action.equalsIgnoreCase(Intent.ACTION_BATTERY_CHANGED)) {
                if (mBattery == null)
                    return;
                int batteryLevel = intent.getIntExtra("level", 0);
                if (batteryLevel >= 50)
                    mBattery.setTextColor(Color.GREEN);
                else if (batteryLevel >= 30)
                    mBattery.setTextColor(Color.YELLOW);
                else
                    mBattery.setTextColor(Color.RED);
                mBattery.setText(String.format("%d%%", batteryLevel));
            }
            else if (action.equalsIgnoreCase(Constants.SLEEP_INTENT)) {
                finish();
            }
        }
    };

    @TargetApi(21)
    private void registerV21() {
        final IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG);
        registerReceiver(mReceiverV21, intentFilter);
    }

    private final BroadcastReceiver mReceiverV21 = LibVlcUtil.isLolliPopOrLater() ? new BroadcastReceiver()
    {
        @TargetApi(21)
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action == null)
                return;
            if (action.equalsIgnoreCase(AudioManager.ACTION_HDMI_AUDIO_PLUG)) {
                mHasHdmiAudio = true;
                logger.Debug("has hdmi audio");
            }
        }
    } : null;

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        showOverlay();
        return true;
    }

    @TargetApi(12) //only active for Android 3.1+
    public boolean dispatchGenericMotionEvent(MotionEvent event){
        //Check for a joystick event
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) !=
                InputDevice.SOURCE_JOYSTICK ||
                event.getAction() != MotionEvent.ACTION_MOVE)
            return false;

        InputDevice mInputDevice = event.getDevice();

        float dpadx = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float dpady = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if (mInputDevice == null || Math.abs(dpadx) == 1.0f || Math.abs(dpady) == 1.0f)
            return false;

        float x = AndroidDevices.getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_X);
        float y = AndroidDevices.getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_Y);
        float rz = AndroidDevices.getCenteredAxis(event, mInputDevice,
                MotionEvent.AXIS_RZ);

        if (System.currentTimeMillis() - mLastMove > JOYSTICK_INPUT_DELAY){
            if (Math.abs(x) > 0.3){
                if (isTv()) {
                    navigateDvdMenu(x > 0.0f ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT);
                } else
                    seekDelta(x > 0.0f ? 10000 : -10000);
            } else if (Math.abs(y) > 0.3){
                if (isTv())
                    navigateDvdMenu(x > 0.0f ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN);
                else {
                    if (mIsFirstBrightnessGesture)
                        initBrightnessTouch();
                    changeBrightness(-y / 10f);
                }
            } else if (Math.abs(rz) > 0.3){
                mVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int delta = -(int) ((rz / 7) * mAudioMax);
                int vol = (int) Math.min(Math.max(mVol + delta, 0), mAudioMax);
                setAudioVolume(vol);
            }
            mLastMove = System.currentTimeMillis();
        }
        return true;
    }

    private boolean isTv() {
        // TODO: Review
        return true;
    }

    @Override
    public void onBackPressed() {
        if (mLockBackButton) {
            mLockBackButton = false;
            mHandler.sendEmptyMessageDelayed(RESET_BACK_LOCK, 2000);
        } else if (isTv() && mShowing && !mIsLocked) {
            hideOverlay(true);
        } else
        {
            Intent returnIntent = new Intent();
            returnIntent.putExtra("position",mLibVLC.getTime());
            returnIntent.putExtra("error",false);
            setResult(RESULT_CANCELED,returnIntent);
            super.onBackPressed();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B)
            return super.onKeyDown(keyCode, event);
        showOverlayTimeout(OVERLAY_TIMEOUT);
        switch (keyCode) {
            case KeyEvent.KEYCODE_F:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seekDelta(10000);
                return true;
            case KeyEvent.KEYCODE_R:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seekDelta(-10000);
                return true;
            case KeyEvent.KEYCODE_BUTTON_R1:
                seekDelta(60000);
                return true;
            case KeyEvent.KEYCODE_BUTTON_L1:
                seekDelta(-60000);
                return true;
            case KeyEvent.KEYCODE_BUTTON_A:
                if (mOverlayProgress.getVisibility() == View.VISIBLE)
                    return false;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_SPACE:
                if (mIsNavMenu)
                    return navigateDvdMenu(keyCode);
                else
                    doPlayPause();
                return true;
            case KeyEvent.KEYCODE_O:
            case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_MENU:
                //showAdvancedOptions(mAdvOptions);
                return true;
            case KeyEvent.KEYCODE_V:
            case KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK:
            case KeyEvent.KEYCODE_BUTTON_X:
                onAudioSubClick(mTracks);
                return true;
            case KeyEvent.KEYCODE_N:
                showNavMenu();
                return true;
            case KeyEvent.KEYCODE_A:
                resizeVideo();
                return true;
            case KeyEvent.KEYCODE_M:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                updateMute();
                return true;
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_MEDIA_STOP:
                Intent returnIntent = new Intent();
                returnIntent.putExtra("error",false);
                returnIntent.putExtra("position",mLibVLC.getTime());
                setResult(RESULT_CANCELED,returnIntent);
                finish();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (mIsNavMenu)
                    return navigateDvdMenu(keyCode);
                else
                    return super.onKeyDown(keyCode, event);
            case KeyEvent.KEYCODE_J:
                delayAudio(-50000l);
                break;
            case KeyEvent.KEYCODE_K:
                delayAudio(50000l);
                break;
            case KeyEvent.KEYCODE_G:
                delaySubs(-50000l);
                break;
            case KeyEvent.KEYCODE_H:
                delaySubs(50000l);
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean navigateDvdMenu(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                mLibVLC.playerNavigate(LibVLC.INPUT_NAV_UP);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                mLibVLC.playerNavigate(LibVLC.INPUT_NAV_DOWN);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                mLibVLC.playerNavigate(LibVLC.INPUT_NAV_LEFT);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                mLibVLC.playerNavigate(LibVLC.INPUT_NAV_RIGHT);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_A:
                mLibVLC.playerNavigate(LibVLC.INPUT_NAV_ACTIVATE);
                return true;
            default:
                return false;
        }
    }

    @Override
    public void setSurfaceLayout(int width, int height, int visible_width, int visible_height, int sar_num, int sar_den) {
        if (width * height == 0)
            return;

        // store video size
        mVideoHeight = height;
        mVideoWidth = width;
        mVideoVisibleHeight = visible_height;
        mVideoVisibleWidth  = visible_width;
        mSarNum = sar_num;
        mSarDen = sar_den;
        Message msg = mHandler.obtainMessage(SURFACE_LAYOUT);
        mHandler.sendMessage(msg);
    }

    @Override
    public void showAudioDelaySetting() {
        mDelay = DelayState.AUDIO;
        showDelayControls();
    }

    @Override
    public void showSubsDelaySetting() {
        mDelay = DelayState.SUBS;
        showDelayControls();
    }

    public void showDelayControls(){
        mTouchAction = TOUCH_NONE;
        showOverlayTimeout(OVERLAY_INFINITE);
        mDelayMinus.setOnClickListener(mAudioDelayListener);
        mDelayPlus.setOnClickListener(mAudioDelayListener);
        mDelayMinus.setOnTouchListener(new OnRepeatListener(mAudioDelayListener));
        mDelayPlus.setOnTouchListener(new OnRepeatListener(mAudioDelayListener));
        mDelayMinus.setVisibility(View.VISIBLE);
        mDelayPlus.setVisibility(View.VISIBLE);
        initDelayInfo();
    }

    private void initDelayInfo() {
        mInfo.setVisibility(View.VISIBLE);
        String text = "";
        if (mDelay == DelayState.AUDIO) {
            text += getString(R.string.audio_delay)+"\n";
            text += mLibVLC.getAudioDelay() / 1000l;
        } else if (mDelay == DelayState.SUBS) {
            text += getString(R.string.spu_delay)+"\n";
            text += mLibVLC.getSpuDelay() / 1000l;
        } else
            text += "0";
        text += " ms";
        mInfo.setText(text);
    }

    @Override
    public void endDelaySetting() {
        mTouchAction = TOUCH_NONE;
        mDelay = DelayState.OFF;
        mDelayMinus.setOnClickListener(null);
        mDelayPlus.setOnClickListener(null);
        mDelayMinus.setVisibility(View.INVISIBLE);
        mDelayPlus.setVisibility(View.INVISIBLE);
        mInfo.setVisibility(View.INVISIBLE);
        mInfo.setText("");
    }

    private OnClickListener mAudioDelayListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()){
                case R.id.player_delay_minus:
                    if (mDelay == DelayState.AUDIO)
                        delayAudio(-50000);
                    else if (mDelay == DelayState.SUBS)
                        delaySubs(-50000);
                    break;
                case R.id.player_delay_plus:
                    if (mDelay == DelayState.AUDIO)
                        delayAudio(50000);
                    else if (mDelay == DelayState.SUBS)
                        delaySubs(50000);
                    break;
            }
        }
    };

    public void delayAudio(long delta){
        long delay = mLibVLC.getAudioDelay()+delta;
        mLibVLC.setAudioDelay(delay);
        mInfo.setText(getString(R.string.audio_delay)+"\n"+(delay/1000l)+" ms");
        if (mDelay == DelayState.OFF) {
            mDelay = DelayState.AUDIO;
            initDelayInfo();
        }
    }

    public void delaySubs(long delta){
        logger.Debug("delaySubs "+delta);
        long delay = mLibVLC.getSpuDelay()+delta;
        mLibVLC.setSpuDelay(delay);
        mInfo.setText(getString(R.string.spu_delay)+"\n"+(delay/1000l)+" ms");
        if (mDelay == DelayState.OFF) {
            mDelay = DelayState.SUBS;
            initDelayInfo();
        }
    }

    @Override
    public int configureSurface(Surface surface, final int width, final int height, final int hal) {
        return -1;
    }

    /**
     * Lock screen rotation
     */
    private void lockScreen() {
        if(mScreenOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR) {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
            else
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            mScreenOrientationLock = getScreenOrientation();
        }
        showInfo(R.string.locked, 1000);
        mLock.setImageResource(R.drawable.lock);
        mTime.setEnabled(false);
        mSeekbar.setEnabled(false);
        mLength.setEnabled(false);
        mSize.setEnabled(false);
        hideOverlay(true);
        mLockBackButton = true;
    }

    /**
     * Remove screen lock
     */
    private void unlockScreen() {
        if(mScreenOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR)
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        showInfo(R.string.unlocked, 1000);
        mLock.setImageResource(R.drawable.unlock);
        mTime.setEnabled(true);
        mSeekbar.setEnabled(true);
        mLength.setEnabled(true);
        mSize.setEnabled(true);
        mShowing = false;
        showOverlay();
        mLockBackButton = false;
    }

    /**
     * Show text in the info view and vertical progress bar for "duration" milliseconds
     * @param text
     * @param duration
     * @param barNewValue new volume/brightness value (range: 0 - 15)
     */
    private void showInfoWithVerticalBar(String text, int duration, int barNewValue) {
        showInfo(text, duration);
        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) mVerticalBarProgress.getLayoutParams();
        layoutParams.weight = barNewValue;
        mVerticalBarProgress.setLayoutParams(layoutParams);
        mVerticalBar.setVisibility(View.VISIBLE);
    }

    /**
     * Show text in the info view for "duration" milliseconds
     * @param text
     * @param duration
     */
    private void showInfo(String text, int duration) {
        if (mPresentation == null)
            mVerticalBar.setVisibility(View.INVISIBLE);
        mInfo.setVisibility(View.VISIBLE);
        mInfo.setText(text);
        mHandler.removeMessages(FADE_OUT_INFO);
        mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, duration);
    }

    private void showInfo(int textid, int duration) {
        if (mPresentation == null)
            mVerticalBar.setVisibility(View.INVISIBLE);
        mInfo.setVisibility(View.VISIBLE);
        mInfo.setText(textid);
        mHandler.removeMessages(FADE_OUT_INFO);
        mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, duration);
    }

    /**
     * Show text in the info view
     * @param text
     */
    private void showInfo(String text) {
        if (mPresentation == null)
            mVerticalBar.setVisibility(View.INVISIBLE);
        mHandler.removeMessages(FADE_OUT_INFO);
        mInfo.setVisibility(View.VISIBLE);
        mInfo.setText(text);
        hideInfo();
    }

    /**
     * hide the info view with "delay" milliseconds delay
     * @param delay
     */
    private void hideInfo(int delay) {
        mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, delay);
    }

    /**
     * hide the info view
     */
    private void hideInfo() {
        hideInfo(0);
    }

    private void fadeOutInfo() {
        if (mInfo.getVisibility() == View.VISIBLE)
            mInfo.startAnimation(AnimationUtils.loadAnimation(
                    VideoPlayerActivity.this, android.R.anim.fade_out));
        mInfo.setVisibility(View.INVISIBLE);

        if (mPresentation == null) {
            if (mVerticalBar.getVisibility() == View.VISIBLE)
                mVerticalBar.startAnimation(AnimationUtils.loadAnimation(
                        VideoPlayerActivity.this, android.R.anim.fade_out));
            mVerticalBar.setVisibility(View.INVISIBLE);
        }
    }

    private OnAudioFocusChangeListener mAudioFocusListener = !LibVlcUtil.isFroyoOrLater() ? null :
            new OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    if (!mPlaybackStarted)
                        return;
            /*
             * Pause playback during alerts and notifications
             */
                    switch (focusChange) {
                        case AudioManager.AUDIOFOCUS_LOSS:
                            changeAudioFocus(false);
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            if (mLibVLC.isPlaying()) {
                                mLostFocus = true;
                                mLibVLC.pause();
                            }
                            break;
                        case AudioManager.AUDIOFOCUS_GAIN:
                        case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                        case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                            if (!mLibVLC.isPlaying() && mLostFocus) {
                                mLibVLC.play();
                                mLostFocus = false;
                            }
                            break;
                    }
                }
            };

    @TargetApi(Build.VERSION_CODES.FROYO)
    private int changeAudioFocus(boolean acquire) {
        if(!LibVlcUtil.isFroyoOrLater()) // NOP if not supported
            return AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

        if (mAudioManager == null)
            return AudioManager.AUDIOFOCUS_REQUEST_FAILED;

        int result = AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (acquire) {
            if (!mHasAudioFocus) {
                result = mAudioManager.requestAudioFocus(mAudioFocusListener,
                        AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                mAudioManager.setParameters("bgm_state=true");
                mHasAudioFocus = true;
            }
        }
        else {
            if (mHasAudioFocus) {
                result = mAudioManager.abandonAudioFocus(mAudioFocusListener);
                mAudioManager.setParameters("bgm_state=false");
                mHasAudioFocus = true;
            }
        }

        return result;
    }

    /**
     *  Handle libvlc asynchronous events
     */
    private Handler mEventHandler;

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (!mIsLocked) {
            doPlayPause();
            return true;
        }
        return false;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    private static class VideoPlayerEventHandler extends WeakHandler<VideoPlayerActivity> {

        private ILogger logger;
        private LibVLC mLibVlc;
        private long lastReportTime;
        private Timer timer;

        public VideoPlayerEventHandler(VideoPlayerActivity owner, ILogger logger, LibVLC mLibVlc) {
            super(owner);
            this.logger = logger;
            this.mLibVlc = mLibVlc;
        }

        @Override
        public void handleMessage(Message msg) {
            VideoPlayerActivity activity = getOwner();
            if(activity == null) return;
            // Do not handle events if we are leaving the VideoPlayerActivity
            if (activity.mSwitchingView) return;

            switch (msg.getData().getInt("event")) {
                case EventHandler.MediaParsedChanged:
                    activity.updateNavStatus();
                    break;
                case EventHandler.MediaPlayerPlaying:
                    logger.Info("MediaPlayerPlaying");
                    activity.onPlaying();
                    startTimer();
                    break;
                case EventHandler.MediaPlayerPaused:
                    logger.Info("MediaPlayerPaused");
                    reportState("paused", false);
                    break;
                case EventHandler.MediaPlayerStopped:
                    logger.Info("MediaPlayerStopped");
                    stopTimer();
                    activity.changeAudioFocus(false);
                    break;
                case EventHandler.MediaPlayerEndReached:
                    logger.Info("MediaPlayerEndReached");
                    stopTimer();
                    activity.changeAudioFocus(false);
                    activity.endReached();
                    break;
                case EventHandler.MediaPlayerVout:
                    activity.updateNavStatus();
                    if (!activity.mHasMenu)
                        activity.handleVout(msg);
                    break;
                case EventHandler.MediaPlayerPositionChanged:
                    if (!activity.mCanSeek)
                        activity.mCanSeek = true;
                    //don't spam the logs
                    break;
                case EventHandler.MediaPlayerEncounteredError:
                    logger.Info("MediaPlayerEncounteredError");
                    stopTimer();
                    activity.encounteredError();
                    break;
                case EventHandler.HardwareAccelerationError:
                    logger.Info("HardwareAccelerationError");
                    activity.handleHardwareAccelerationError();
                    break;
                case EventHandler.MediaPlayerTimeChanged:
                    reportState("positionchange", true);
                    break;
                case EventHandler.MediaPlayerESAdded:
                case EventHandler.MediaPlayerESDeleted:
                    if (!activity.mHasMenu) {
                        activity.mHandler.removeMessages(CHECK_VIDEO_TRACKS);
                        activity.mHandler.sendEmptyMessageDelayed(CHECK_VIDEO_TRACKS, 1000);
                    }
                    activity.invalidateESTracks(msg.getData().getInt("data"));
                    break;
                default:
                    break;
            }
            activity.updateOverlayPausePlay();
        }

        private void startTimer(){

            timer = new Timer(true);

            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    reportState("positionchange", true);
                }
            }, 0, 1000);
        }

        private void stopTimer(){

            if (timer != null){
                timer.cancel();
                timer = null;
            }
        }

        private void reportState(String eventName, boolean checkLastReportTime) {

            if (checkLastReportTime){
                // avoid useless error logs
                // Avoid overly aggressive reporting
                if ((System.currentTimeMillis() - lastReportTime) < 800){
                    return;
                }

                lastReportTime = System.currentTimeMillis();
            }

            VideoPlayerActivity activity = getOwner();
            if(activity == null) return;

            LibVLC vlc = mLibVlc;

            int playerState = vlc.getPlayerState();

            // Expected states by web plugins are: IDLE/CLOSE=0, OPENING=1, BUFFERING=2, PLAYING=3, PAUSED=4, STOPPING=5, ENDED=6, ERROR=7
            boolean isPaused = eventName.equalsIgnoreCase("playbackstop") ?
                    false :
                    eventName.equalsIgnoreCase("paused") || playerState == 4;

            logger.Debug("Vlc player state: %s", playerState);

            long length = vlc.getLength() / 1000;

            long time = vlc.getTime();

            int volume = vlc.getVolume();

            activity.ReportPlaybackProgress(length, time, volume, isPaused);
        }
    };

    private void ReportPlaybackProgress(Long duration, Long time, int volume, boolean isPaused) {

        ApiClient apiClient = getApiClient();

        PlaybackProgressInfo info = getPlaybackProgressInfo();

        info.setVolumeLevel(volume);
        info.setIsPaused(isPaused);
        info.setPositionTicks(time * 10000);

        apiClient.ReportPlaybackProgressAsync(info, new EmptyResponse());
    }

    private PlaybackProgressInfo playbackStartInfo;
    private PlaybackProgressInfo getPlaybackProgressInfo(){
        return playbackStartInfo;
    }

    private ApiClient apiClient;
    private ApiClient getApiClient(){

        return apiClient;
    }

    /**
     * Handle resize of the surface and the overlay
     */
    private Handler mHandler ;

    private static class VideoPlayerHandler extends WeakHandler<VideoPlayerActivity> {

        private ILogger logger;

        public VideoPlayerHandler(VideoPlayerActivity owner, ILogger logger) {
            super(owner);
            this.logger = logger;
        }

        @Override
        public void handleMessage(Message msg) {
            VideoPlayerActivity activity = getOwner();
            if(activity == null) // WeakReference could be GC'ed early
                return;

            switch (msg.what) {
                case FADE_OUT:
                    activity.hideOverlay(false);
                    break;
                case SHOW_PROGRESS:
                    int pos = activity.setOverlayProgress();
                    if (activity.canShowProgress()) {
                        msg = obtainMessage(SHOW_PROGRESS);
                        sendMessageDelayed(msg, 1000 - (pos % 1000));
                    }
                    break;
                case SURFACE_LAYOUT:
                    activity.changeSurfaceLayout();
                    break;
                case FADE_OUT_INFO:
                    activity.fadeOutInfo();
                    break;
                case START_PLAYBACK:
                    activity.startPlayback();
                    break;
                case AUDIO_SERVICE_CONNECTION_FAILED:
                    Intent returnIntent = new Intent();
                    returnIntent.putExtra("error",true);
                    activity.setResult(RESULT_CANCELED,returnIntent);
                    activity.finish();
                    break;
                case RESET_BACK_LOCK:
                    activity.mLockBackButton = true;
                    break;
                case CHECK_VIDEO_TRACKS:
                    if (activity.mLibVLC.getVideoTracksCount() < 1 && activity.mLibVLC.getAudioTracksCount() > 0) {
                        logger.Info("No video track, open in audio mode");
                        activity.switchToAudioMode(true);
                    }
                    break;
            }
        }
    };

    private boolean canShowProgress() {
        return !mDragging && mShowing && mLibVLC.isPlaying();
    }

    private void onPlaying() {
        stopLoadingAnimation();
        showOverlay();
        setESTracks();
        changeAudioFocus(true);
        updateNavStatus();

        if (resumePositionMs > 0){
            seek(resumePositionMs);
        }
        resumePositionMs = 0;
    }

    private void endReached() {
        if(mMediaListPlayer.expand(savedIndexPosition) == 0) {
            logger.Debug("Found a video playlist, expanding it");
            mEventHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    loadMedia();
                }
            }, 1000);
        } else {
            /* Exit player when reaching the end */
            mEndReached = true;
            Intent returnIntent = new Intent();
            setResult(RESULT_OK,returnIntent);
            finish();
        }
    }

    private void encounteredError() {
        if (isFinishing())
            return;
        /* Encountered Error, exit player with a message */
        mAlertDialog = new AlertDialog.Builder(VideoPlayerActivity.this)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        Intent returnIntent = new Intent();
                        returnIntent.putExtra("error",true);
                        setResult(RESULT_CANCELED,returnIntent);
                        finish();
                    }
                })
                .setTitle(R.string.encountered_error_title)
                .setMessage(R.string.encountered_error_message)
                .create();
        mAlertDialog.show();
    }

    public void eventHardwareAccelerationError() {
        EventHandler em = EventHandler.getInstance();
        em.callback(EventHandler.HardwareAccelerationError, new Bundle());
    }

    private void handleHardwareAccelerationError() {
        mHardwareAccelerationError = true;
        if (mSwitchingView)
            return;
        mLibVLC.stop();

        if(!isFinishing())
        {
            mDisabledHardwareAcceleration = true;
            mPreviousHardwareAccelerationMode = mLibVLC.getHardwareAcceleration();
            mLibVLC.setHardwareAcceleration(LibVLC.HW_ACCELERATION_DISABLED);
            loadMedia();
        }
    }

    private void handleVout(Message msg) {
        if (msg.getData().getInt("data") == 0 && !mEndReached) {
            /* Video track lost, open in audio mode */
            logger.Info("Video track lost, switching to audio");
            mSwitchingView = true;
            finish();
        }
    }

    public void switchToAudioMode(boolean showUI) {
        if (mHardwareAccelerationError)
            return;
        // TODO: Review
        /*mSwitchingView = true;
        mLibVLC.setVideoTrack(-1);
        // Show the MainActivity if it is not in background.
        if (showUI && getIntent().getAction() != null
                && getIntent().getAction().equals(Intent.ACTION_VIEW)) {
            Intent i = new Intent(this, MainActivity.class);
            if (!Util.isCallable(i)){
                try {
                    i = new Intent(this, Class.forName("org.videolan.vlc.gui.tv.audioplayer.AudioPlayerActivity"));
                } catch (ClassNotFoundException e) {
                    return;
                }
            }
            startActivity(i);
        }
        finish();*/
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void changeSurfaceLayout() {
        int sw;
        int sh;

        // get screen size
        if (mPresentation == null) {
            sw = getWindow().getDecorView().getWidth();
            sh = getWindow().getDecorView().getHeight();
        } else {
            sw = mPresentation.getWindow().getDecorView().getWidth();
            sh = mPresentation.getWindow().getDecorView().getHeight();
        }
        if (mLibVLC != null && !mLibVLC.useCompatSurface())
            mLibVLC.setWindowSize(sw, sh);

        double dw = sw, dh = sh;
        boolean isPortrait;

        if (mPresentation == null) {
            // getWindow().getDecorView() doesn't always take orientation into account, we have to correct the values
            isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        } else {
            isPortrait = false;
        }

        if (sw > sh && isPortrait || sw < sh && !isPortrait) {
            dw = sh;
            dh = sw;
        }

        // sanity check
        if (dw * dh == 0 || mVideoWidth * mVideoHeight == 0) {
            logger.Error("Invalid surface size");
            return;
        }

        // compute the aspect ratio
        double ar, vw;
        if (mSarDen == mSarNum) {
            /* No indication about the density, assuming 1:1 */
            vw = mVideoVisibleWidth;
            ar = (double)mVideoVisibleWidth / (double)mVideoVisibleHeight;
        } else {
            /* Use the specified aspect ratio */
            vw = mVideoVisibleWidth * (double)mSarNum / mSarDen;
            ar = vw / mVideoVisibleHeight;
        }

        // compute the display aspect ratio
        double dar = dw / dh;

        switch (mCurrentSize) {
            case SURFACE_BEST_FIT:
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case SURFACE_FIT_HORIZONTAL:
                dh = dw / ar;
                break;
            case SURFACE_FIT_VERTICAL:
                dw = dh * ar;
                break;
            case SURFACE_FILL:
                break;
            case SURFACE_16_9:
                ar = 16.0 / 9.0;
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case SURFACE_4_3:
                ar = 4.0 / 3.0;
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case SURFACE_ORIGINAL:
                dh = mVideoVisibleHeight;
                dw = vw;
                break;
        }

        SurfaceView surface;
        SurfaceView subtitlesSurface;
        FrameLayout surfaceFrame;

        if (mPresentation == null) {
            surface = mSurfaceView;
            subtitlesSurface = mSubtitlesSurfaceView;
            surfaceFrame = mSurfaceFrame;
        } else {
            surface = mPresentation.mSurfaceView;
            subtitlesSurface = mPresentation.mSubtitlesSurfaceView;
            surfaceFrame = mPresentation.mSurfaceFrame;
        }

        // set display size
        LayoutParams lp = surface.getLayoutParams();
        lp.width  = (int) Math.ceil(dw * mVideoWidth / mVideoVisibleWidth);
        lp.height = (int) Math.ceil(dh * mVideoHeight / mVideoVisibleHeight);
        logger.Info("Surface set to w:%s h:%s",lp.width, lp.height);
        surface.setLayoutParams(lp);
        subtitlesSurface.setLayoutParams(lp);

        // set frame size (crop if necessary)
        lp = surfaceFrame.getLayoutParams();
        lp.width = (int) Math.floor(dw);
        lp.height = (int) Math.floor(dh);
        surfaceFrame.setLayoutParams(lp);

        surface.invalidate();
        subtitlesSurface.invalidate();
    }

    /**
     * show/hide the overlay
     */

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mDelay != DelayState.OFF){
            endDelaySetting();
            return true;
        }
        if (mDetector.onTouchEvent(event))
            return true;
        if (mIsLocked) {
            // locked, only handle show/hide & ignore all actions
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (!mShowing) {
                    showOverlay();
                } else {
                    hideOverlay(true);
                }
            }
            return false;
        }

        DisplayMetrics screen = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(screen);

        if (mSurfaceYDisplayRange == 0)
            mSurfaceYDisplayRange = Math.min(screen.widthPixels, screen.heightPixels);

        float x_changed, y_changed;
        if (mTouchX != -1f && mTouchY != -1f) {
            y_changed = event.getRawY() - mTouchY;
            x_changed = event.getRawX() - mTouchX;
        } else {
            x_changed = 0f;
            y_changed = 0f;
        }


        // coef is the gradient's move to determine a neutral zone
        float coef = Math.abs(y_changed / x_changed);
        float xgesturesize = ((x_changed / screen.xdpi) * 2.54f);
        float delta_y = Math.max(1f, ((mInitTouchY - event.getRawY()) / screen.xdpi + 0.5f) * 2f);

        /* Offset for Mouse Events */
        int[] offset = new int[2];
        mSurfaceView.getLocationOnScreen(offset);
        int xTouch = Math.round((event.getRawX() - offset[0]) * mVideoWidth / mSurfaceView.getWidth());
        int yTouch = Math.round((event.getRawY() - offset[1]) * mVideoHeight / mSurfaceView.getHeight());

        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN:
                // Audio
                mTouchY = mInitTouchY = event.getRawY();
                mVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                mTouchAction = TOUCH_NONE;
                // Seek
                mTouchX = event.getRawX();
                // Mouse events for the core
                LibVLC.sendMouseEvent(MotionEvent.ACTION_DOWN, 0, xTouch, yTouch);
                break;

            case MotionEvent.ACTION_MOVE:
                // Mouse events for the core
                LibVLC.sendMouseEvent(MotionEvent.ACTION_MOVE, 0, xTouch, yTouch);

                // No volume/brightness action if coef < 2 or a secondary display is connected
                //TODO : Volume action when a secondary display is connected
                if (mTouchAction != TOUCH_SEEK && coef > 2 && mPresentation == null) {
                    if (Math.abs(y_changed / mSurfaceYDisplayRange) < 0.05)
                        return false;
                    mTouchY = event.getRawY();
                    mTouchX = event.getRawX();
                    // Volume (Up or Down - Right side)
                    if (!mEnableBrightnessGesture || (int)mTouchX > (3 * screen.widthPixels / 5)){
                        doVolumeTouch(y_changed);
                        hideOverlay(true);
                    }
                    // Brightness (Up or Down - Left side)
                    if (mEnableBrightnessGesture && (int)mTouchX < (2 * screen.widthPixels / 5)){
                        doBrightnessTouch(y_changed);
                        hideOverlay(true);
                    }
                } else {
                    // Seek (Right or Left move)
                    doSeekTouch(Math.round(delta_y), xgesturesize, false);
                }
                break;

            case MotionEvent.ACTION_UP:
                // Mouse events for the core
                LibVLC.sendMouseEvent(MotionEvent.ACTION_UP, 0, xTouch, yTouch);

                if (mTouchAction == TOUCH_NONE) {
                    if (!mShowing) {
                        showOverlay();
                    } else {
                        hideOverlay(true);
                    }
                }
                // Seek
                if (mTouchAction == TOUCH_SEEK)
                    doSeekTouch(Math.round(delta_y), xgesturesize, true);
                mTouchX = -1f;
                mTouchY = -1f;
                break;
        }
        return mTouchAction != TOUCH_NONE;
    }

    private void doSeekTouch(int coef, float gesturesize, boolean seek) {
        if (coef == 0)
            coef = 1;
        // No seek action if coef > 0.5 and gesturesize < 1cm
        if (Math.abs(gesturesize) < 1 || !mCanSeek)
            return;

        if (mTouchAction != TOUCH_NONE && mTouchAction != TOUCH_SEEK)
            return;
        mTouchAction = TOUCH_SEEK;

        long length = mLibVLC.getLength();
        long time = getTime();

        // Size of the jump, 10 minutes max (600000), with a bi-cubic progression, for a 8cm gesture
        int jump = (int) ((Math.signum(gesturesize) * ((600000 * Math.pow((gesturesize / 8), 4)) + 3000)) / coef);

        // Adjust the jump
        if ((jump > 0) && ((time + jump) > length))
            jump = (int) (length - time);
        if ((jump < 0) && ((time + jump) < 0))
            jump = (int) -time;

        //Jump !
        if (seek && length > 0)
            seek(time + jump, length);

        if (length > 0)
            //Show the jump's size
            showInfo(String.format("%s%s (%s) x%d",
                    jump >= 0 ? "+" : "",
                    Strings.millisToString(jump),
                    Strings.millisToString(time + jump), coef), 1000);
        else
            showInfo(R.string.unseekable_stream, 1000);
    }

    private void doVolumeTouch(float y_changed) {
        if (mTouchAction != TOUCH_NONE && mTouchAction != TOUCH_VOLUME)
            return;
        float delta = - ((y_changed / mSurfaceYDisplayRange) * mAudioMax);
        mVol += delta;
        int vol = (int) Math.min(Math.max(mVol, 0), mAudioMax);
        if (delta != 0f) {
            setAudioVolume(vol);
        }
    }

    private void setAudioVolume(int vol) {
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);

        /* Since android 4.3, the safe volume warning dialog is displayed only with the FLAG_SHOW_UI flag.
         * We don't want to always show the default UI volume, so show it only when volume is not set. */
        int newVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        if (vol != newVol)
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, AudioManager.FLAG_SHOW_UI);

        mTouchAction = TOUCH_VOLUME;
        vol = vol * 100 / mAudioMax;
        showInfoWithVerticalBar(getString(R.string.volume) + '\u00A0' + Integer.toString(vol) + '%', 1000, vol);
    }

    private void mute(boolean mute) {
        mMute = mute;
        if (mMute)
            mVolSave = mLibVLC.getVolume();
        mLibVLC.setVolume(mMute ? 0 : mVolSave);
    }

    private void updateMute () {
        mute(!mMute);
        showInfo(mMute ? R.string.sound_off : R.string.sound_on,1000);
    }

    @TargetApi(android.os.Build.VERSION_CODES.FROYO)
    private void initBrightnessTouch() {
        float brightnesstemp = 0.6f;
        // Initialize the layoutParams screen brightness
        try {
            if (LibVlcUtil.isFroyoOrLater() &&
                    Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                /*Settings.System.putInt(getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);*/
                mRestoreAutoBrightness = android.provider.Settings.System.getInt(getContentResolver(),
                        android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255.0f;
            } else {
                brightnesstemp = android.provider.Settings.System.getInt(getContentResolver(),
                        android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255.0f;
            }
        } catch (SettingNotFoundException e) {
            e.printStackTrace();
        }
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = brightnesstemp;
        getWindow().setAttributes(lp);
        mIsFirstBrightnessGesture = false;
    }

    private void doBrightnessTouch(float y_changed) {
        if (mTouchAction != TOUCH_NONE && mTouchAction != TOUCH_BRIGHTNESS)
            return;
        if (mIsFirstBrightnessGesture) initBrightnessTouch();
        mTouchAction = TOUCH_BRIGHTNESS;

        // Set delta : 2f is arbitrary for now, it possibly will change in the future
        float delta = - y_changed / mSurfaceYDisplayRange;

        changeBrightness(delta);
    }

    private void changeBrightness(float delta) {
        // Estimate and adjust Brightness
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness =  Math.min(Math.max(lp.screenBrightness + delta, 0.01f), 1);
        // Set Brightness
        getWindow().setAttributes(lp);
        int brightness = Math.round(lp.screenBrightness * 100);
        showInfoWithVerticalBar(getString(R.string.brightness) + '\u00A0' + brightness + '%', 1000, brightness);
    }

    /**
     * handle changes of the seekbar (slicer)
     */
    private final OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mDragging = true;
            showOverlayTimeout(OVERLAY_INFINITE);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            mDragging = false;
            showOverlay(true);
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser && mCanSeek) {
                seek(progress);
                setOverlayProgress();
                mTime.setText(Strings.millisToString(progress));
                showInfo(Strings.millisToString(progress));
            }

        }
    };

    public void onAudioSubClick(View anchor){
        final Context context = this;
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenuInflater().inflate(R.menu.audiosub_tracks, popupMenu.getMenu());
        popupMenu.getMenu().findItem(R.id.video_menu_audio_track).setEnabled(mLibVLC.getAudioTracksCount() > 2);
        popupMenu.getMenu().findItem(R.id.video_menu_subtitles).setEnabled(mLibVLC.getSpuTracksCount() > 0);
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.video_menu_audio_track) {
                    selectAudioTrack();
                    return true;
                } else if (item.getItemId() == R.id.video_menu_subtitles) {
                    selectSubtitles();
                    return true;
                }

                return false;
            }
        });
        popupMenu.show();
    }

    private interface TrackSelectedListener {
        public boolean onTrackSelected(int trackID);
    }
    private void selectTrack(final Map<Integer,String> trackMap, int currentTrack, int titleId,
                             final TrackSelectedListener listener) {
        if (listener == null)
            throw new IllegalArgumentException("listener must not be null");
        if (trackMap == null)
            return;
        final String[] nameList = new String[trackMap.size()];
        final int[] idList = new int[trackMap.size()];
        int i = 0;
        int listPosition = 0;
        for(Map.Entry<Integer,String> entry : trackMap.entrySet()) {
            idList[i] = entry.getKey();
            nameList[i] = entry.getValue();
            // map the track position to the list position
            if(entry.getKey() == currentTrack)
                listPosition = i;
            i++;
        }

        mAlertDialog = new AlertDialog.Builder(VideoPlayerActivity.this)
                .setTitle(titleId)
                .setSingleChoiceItems(nameList, listPosition, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int listPosition) {
                        int trackID = -1;
                        // Reverse map search...
                        for (Map.Entry<Integer, String> entry : trackMap.entrySet()) {
                            if (idList[listPosition] == entry.getKey()) {
                                trackID = entry.getKey();
                                break;
                            }
                        }
                        listener.onTrackSelected(trackID);
                        dialog.dismiss();
                    }
                })
                .create();
        mAlertDialog.setCanceledOnTouchOutside(true);
        mAlertDialog.setOwnerActivity(VideoPlayerActivity.this);
        mAlertDialog.show();
    }

    private void selectAudioTrack() {
        setESTrackLists();
        selectTrack(mAudioTracksList, mLibVLC.getAudioTrack(), R.string.track_audio,
                new TrackSelectedListener() {
                    @Override
                    public boolean onTrackSelected(int trackID) {
                        if (trackID < 0)
                            return false;
                        mLibVLC.setAudioTrack(trackID);
                        return true;
                    }
                });
    }

    private void selectSubtitles() {
        setESTrackLists();
        selectTrack(mSubtitleTracksList, mLibVLC.getSpuTrack(), R.string.track_text,
                new TrackSelectedListener() {
                    @Override
                    public boolean onTrackSelected(int trackID) {
                        if (trackID < -1)
                            return false;
                        mLibVLC.setSpuTrack(trackID);
                        return true;
                    }
                });
    }

    private void showNavMenu() {
        /* Try to return to the menu. */
        /* FIXME: not working correctly in all cases */
        mLibVLC.setTitle(0);
    }

    /**
     *
     */
    private final OnClickListener mPlayPauseListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            doPlayPause();
        }
    };

    private final void doPlayPause() {
        if (mLibVLC.isPlaying()) {
            pause();
            showOverlayTimeout(OVERLAY_INFINITE);
        } else {
            play();
            showOverlayTimeout(OVERLAY_TIMEOUT);
        }
    }

    private long getTime() {
        long time = mLibVLC.getTime();
        if (mForcedTime != -1 && mLastTime != -1) {
            /* XXX: After a seek, mLibVLC.getTime can return the position before or after
             * the seek position. Therefore we return mForcedTime in order to avoid the seekBar
             * to move between seek position and the actual position.
             * We have to wait for a valid position (that is after the seek position).
             * to re-init mLastTime and mForcedTime to -1 and return the actual position.
             */
            if (mLastTime > mForcedTime) {
                if (time <= mLastTime && time > mForcedTime)
                    mLastTime = mForcedTime = -1;
            } else {
                if (time > mForcedTime)
                    mLastTime = mForcedTime = -1;
            }
        }
        return mForcedTime == -1 ? time : mForcedTime;
    }

    private void seek(long position) {
        seek(position, mLibVLC.getLength());
    }

    private void seek(long position, float length) {
        mForcedTime = position;
        mLastTime = mLibVLC.getTime();

        if (length == 0) {
            if (currentMediaSource != null && currentMediaSource.getRunTimeTicks() != null){
                length = (int)(currentMediaSource.getRunTimeTicks()/ 10000);
            }
        }

        if (length == 0f)
            mLibVLC.setTime(position);
        else
            mLibVLC.setPosition(position / length);
    }

    private void seekDelta(int delta) {
        // unseekable stream
        if(mLibVLC.getLength() <= 0 || !mCanSeek) return;

        long position = getTime() + delta;
        if (position < 0) position = 0;
        seek(position);
        showOverlay();
    }

    /**
     *
     */
    private final OnClickListener mLockListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            if (mIsLocked) {
                mIsLocked = false;
                unlockScreen();
            } else {
                mIsLocked = true;
                lockScreen();
            }
        }
    };

    /**
     *
     */
    private final OnClickListener mSizeListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            resizeVideo();
        }
    };

    private void resizeVideo() {
        if (mCurrentSize < SURFACE_ORIGINAL) {
            mCurrentSize++;
        } else {
            mCurrentSize = 0;
        }
        changeSurfaceLayout();
        switch (mCurrentSize) {
            case SURFACE_BEST_FIT:
                showInfo(R.string.surface_best_fit, 1000);
                break;
            case SURFACE_FIT_HORIZONTAL:
                showInfo(R.string.surface_fit_horizontal, 1000);
                break;
            case SURFACE_FIT_VERTICAL:
                showInfo(R.string.surface_fit_vertical, 1000);
                break;
            case SURFACE_FILL:
                showInfo(R.string.surface_fill, 1000);
                break;
            case SURFACE_16_9:
                showInfo("16:9", 1000);
                break;
            case SURFACE_4_3:
                showInfo("4:3", 1000);
                break;
            case SURFACE_ORIGINAL:
                showInfo(R.string.surface_original, 1000);
                break;
        }
        showOverlay();
    }

    private final OnClickListener mRemainingTimeListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            mDisplayRemainingTime = !mDisplayRemainingTime;
            showOverlay();
        }
    };

    /**
     * attach and disattach surface to the lib
     */
    private final SurfaceHolder.Callback mSurfaceCallback = new Callback() {
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if(mLibVLC != null) {
                final Surface newSurface = holder.getSurface();
                if (mSurface != newSurface) {
                    mSurface = newSurface;
                    logger.Debug("surfaceChanged: " + mSurface);
                    mLibVLC.attachSurface(mSurface, VideoPlayerActivity.this);
                    mSurfaceReady = true;
                    mHandler.sendEmptyMessage(START_PLAYBACK);
                }
            }
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            logger.Debug("surfaceDestroyed");
            if(mLibVLC != null) {
                mSurface = null;
                mLibVLC.detachSurface();
                mSurfaceReady = false;
            }
        }
    };

    private final SurfaceHolder.Callback mSubtitlesSurfaceCallback = new Callback() {
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if(mLibVLC != null) {
                final Surface newSurface = holder.getSurface();
                if (mSubtitleSurface != newSurface) {
                    mSubtitleSurface = newSurface;
                    mLibVLC.attachSubtitlesSurface(mSubtitleSurface);
                    mSubtitleSurfaceReady = true;
                    mHandler.sendEmptyMessage(START_PLAYBACK);
                }
            }
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            if(mLibVLC != null) {
                mSubtitleSurface = null;
                mLibVLC.detachSubtitlesSurface();
                mSubtitleSurfaceReady = false;
            }
        }
    };

    /**
     * show overlay
     * @param forceCheck: adjust the timeout in function of playing state
     */
    private void showOverlay(boolean forceCheck) {
        if (forceCheck)
            mOverlayTimeout = 0;
        showOverlayTimeout(0);
    }

    /**
     * show overlay with the previous timeout value
     */
    private void showOverlay() {
        showOverlay(false);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void setActionBarVisibility(boolean show) {
        if (show)
            mActionBar.show();
        else
            mActionBar.hide();
    }

    /**
     * show overlay
     */
    private void showOverlayTimeout(int timeout) {
        if (timeout != 0)
            mOverlayTimeout = timeout;
        if (mOverlayTimeout == 0)
            mOverlayTimeout = mLibVLC.isPlaying() ? OVERLAY_TIMEOUT : OVERLAY_INFINITE;
        if (mIsNavMenu){
            mShowing = true;
            return;
        }
        mHandler.sendEmptyMessage(SHOW_PROGRESS);
        if (!mShowing) {
            mShowing = true;
            if (!mIsLocked) {
                setActionBarVisibility(true);
                mPlayPause.setVisibility(View.VISIBLE);
                if (mTracks != null)
                    mTracks.setVisibility(View.VISIBLE);
                if (mAdvOptions !=null)
                    mAdvOptions.setVisibility(View.INVISIBLE);
                mSize.setVisibility(View.VISIBLE);
                dimStatusBar(false);
            }
            mOverlayProgress.setVisibility(View.VISIBLE);
            if (mPresentation != null) mOverlayBackground.setVisibility(View.VISIBLE);
        }
        mHandler.removeMessages(FADE_OUT);
        if (mOverlayTimeout != OVERLAY_INFINITE)
            mHandler.sendMessageDelayed(mHandler.obtainMessage(FADE_OUT), mOverlayTimeout);
        updateOverlayPausePlay();
    }


    /**
     * hider overlay
     */
    private void hideOverlay(boolean fromUser) {
        if (mShowing) {
            mHandler.removeMessages(FADE_OUT);
            mHandler.removeMessages(SHOW_PROGRESS);
            logger.Info("remove View!");
            if (mOverlayTips != null) mOverlayTips.setVisibility(View.INVISIBLE);
            if (!fromUser && !mIsLocked) {
                mOverlayProgress.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
                mPlayPause.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
                if (mTracks != null)
                    mTracks.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
                if (mAdvOptions !=null)
                    mAdvOptions.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
            } else
                mSize.setVisibility(View.INVISIBLE);
            if (mPresentation != null) {
                mOverlayBackground.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
                mOverlayBackground.setVisibility(View.INVISIBLE);
            }
            setActionBarVisibility(false);
            mOverlayProgress.setVisibility(View.INVISIBLE);
            mPlayPause.setVisibility(View.INVISIBLE);
            if (mTracks != null)
                mTracks.setVisibility(View.INVISIBLE);
            if (mAdvOptions !=null)
                mAdvOptions.setVisibility(View.INVISIBLE);
            mShowing = false;
            dimStatusBar(true);
        } else if (!fromUser) {
            /*
             * Try to hide the Nav Bar again.
             * It seems that you can't hide the Nav Bar if you previously
             * showed it in the last 1-2 seconds.
             */
            dimStatusBar(true);
        }
    }

    /**
     * Dim the status bar and/or navigation icons when needed on Android 3.x.
     * Hide it on Android 4.0 and later
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void dimStatusBar(boolean dim) {
        if (!LibVlcUtil.isHoneycombOrLater() || mIsNavMenu)
            return;
        int visibility = 0;
        int navbar = 0;

        if (!AndroidDevices.hasCombBar(getApplicationContext()) && LibVlcUtil.isJellyBeanOrLater()) {
            visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            navbar = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        }
        visibility |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        if (dim) {
            navbar |= View.SYSTEM_UI_FLAG_LOW_PROFILE;
            if (!AndroidDevices.hasCombBar(getApplicationContext())) {
                navbar |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                if (LibVlcUtil.isKitKatOrLater())
                    visibility |= View.SYSTEM_UI_FLAG_IMMERSIVE;
                visibility |= View.SYSTEM_UI_FLAG_FULLSCREEN;
            }
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            visibility |= View.SYSTEM_UI_FLAG_VISIBLE;
        }

        if (AndroidDevices.hasNavBar())
            visibility |= navbar;
        getWindow().getDecorView().setSystemUiVisibility(visibility);
    }

    private void updateOverlayPausePlay() {
        if (mLibVLC == null)
            return;
        mPlayPause.setImageResource(mLibVLC.isPlaying() ? R.drawable.lb_ic_pause
                : R.drawable.lb_ic_play);
    }

    /**
     * update the overlay
     */
    private int setOverlayProgress() {
        if (mLibVLC == null) {
            return 0;
        }
        int time = (int) getTime();
        int length = (int) mLibVLC.getLength();
        if (length == 0) {
            if (currentMediaSource.getRunTimeTicks() != null){
                length = (int)(currentMediaSource.getRunTimeTicks()/ 10000);
            }
        }

        // Update all view elements
        mSeekbar.setMax(length);
        mSeekbar.setProgress(time);
        if (mSysTime != null)
            mSysTime.setText(DateFormat.getTimeFormat(this).format(new Date(System.currentTimeMillis())));
        if (time >= 0) mTime.setText(Strings.millisToString(time));
        if (length >= 0) mLength.setText(mDisplayRemainingTime && length > 0
                ? "- " + Strings.millisToString(length - time)
                : Strings.millisToString(length));

        return time;
    }

    private void invalidateESTracks(int type) {
        switch (type) {
            case Media.Track.Type.Audio:
                mAudioTracksList = null;
                break;
            case Media.Track.Type.Text:
                mSubtitleTracksList = null;
                break;
        }
    }

    private void setESTracks() {
        if (mLastAudioTrack >= 0) {
            mLibVLC.setAudioTrack(mLastAudioTrack);
            mLastAudioTrack = -1;
        }
        if (mLastSpuTrack >= -1) {
            mLibVLC.setSpuTrack(mLastSpuTrack);
            mLastSpuTrack = -2;
        }
    }

    private void setESTrackLists() {
        if (mAudioTracksList == null && mLibVLC.getAudioTracksCount() > 1)
            mAudioTracksList = mLibVLC.getAudioTrackDescription();
        if (mSubtitleTracksList == null && mLibVLC.getSpuTracksCount() > 0)
            mSubtitleTracksList = mLibVLC.getSpuTrackDescription();
    }


    /**
     *
     */
    private void play() {
        mLibVLC.play();
        mSurfaceView.setKeepScreenOn(true);
    }

    /**
     *
     */
    private void pause() {
        mLibVLC.pause();
        mSurfaceView.setKeepScreenOn(false);
    }

    /*
     * Additionnal method to prevent alert dialog to pop up
     */
    @SuppressWarnings({ "unchecked" })
    private void loadMedia(boolean fromStart) {
        getIntent().putExtra(PLAY_EXTRA_FROM_START, fromStart);
        loadMedia();
    }

    /**
     * External extras:
     * - position (long) - position of the video to start with (in ms)
     */
    @SuppressWarnings({ "unchecked" })
    private void loadMedia() {
        mLocation = null;
        String title = getResources().getString(R.string.title);
        boolean fromStart = false;
        int openedPosition = -1;
        Uri data;
        String itemTitle = null;
        long intentPosition = -1; // position passed in by intent (ms)
        long mediaLength = 0l;

        boolean wasPaused;
        /*
         * If the activity has been paused by pressing the power button, then
         * pressing it again will show the lock screen.
         * But onResume will also be called, even if vlc-android is still in
         * the background.
         * To workaround this, pause playback if the lockscreen is displayed.
         */
        final KeyguardManager km = (KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        if (km.inKeyguardRestrictedInputMode())
            wasPaused = true;
        else
            wasPaused = false;
        if (wasPaused)
            logger.Debug("Video was previously paused, resuming in paused mode");

        if (getIntent().getAction() != null
                && getIntent().getAction().equals(Intent.ACTION_VIEW)) {
            /* Started from external application 'content' */
            data = getIntent().getData();
            if (data != null
                    && data.getScheme() != null
                    && data.getScheme().equals("content")) {


                // Mail-based apps - download the stream to a temporary file and play it
                if(data.getHost().equals("com.fsck.k9.attachmentprovider")
                        || data.getHost().equals("gmail-ls")) {
                    InputStream is = null;
                    OutputStream os = null;
                    try {
                        Cursor cursor = getContentResolver().query(data,
                                new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null);
                        if (cursor != null) {
                            cursor.moveToFirst();
                            String filename = cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME));
                            cursor.close();
                            logger.Info("Getting file " + filename + " from content:// URI");

                            is = getContentResolver().openInputStream(data);
                            os = new FileOutputStream(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + "/Download/" + filename);
                            byte[] buffer = new byte[1024];
                            int bytesRead = 0;
                            while((bytesRead = is.read(buffer)) >= 0) {
                                os.write(buffer, 0, bytesRead);
                            }
                            mLocation = LibVLC.PathToURI(AndroidDevices.EXTERNAL_PUBLIC_DIRECTORY + "/Download/" + filename);
                        }
                    } catch (Exception e) {
                        logger.Error("Couldn't download file from mail URI");
                        encounteredError();
                        return;
                    } finally {
                        Util.close(is);
                        Util.close(os);
                    }
                }
                // Media or MMS URI
                else {
                    try {
                        Cursor cursor = getContentResolver().query(data,
                                new String[]{ MediaStore.Video.Media.DATA }, null, null, null);
                        if (cursor != null) {
                            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
                            if (cursor.moveToFirst())
                                mLocation = LibVLC.PathToURI(cursor.getString(column_index));
                            cursor.close();
                        }
                        // other content-based URI (probably file pickers)
                        else {
                            mLocation = data.getPath();
                        }
                    } catch (Exception e) {
                        mLocation = data.getPath();
                        if (!mLocation.startsWith("file://"))
                            mLocation = "file://"+mLocation;
                        logger.Error("Couldn't read the file from media or MMS");
                    }
                }
            } /* External application */
            else if (getIntent().getDataString() != null) {
                // Plain URI
                mLocation = getIntent().getDataString();
                // Remove VLC prefix if needed
                if (mLocation.startsWith("vlc://")) {
                    mLocation = mLocation.substring(6);
                }
                // Decode URI
                if (!mLocation.contains("/")){
                    try {
                        mLocation = URLDecoder.decode(mLocation, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        logger.Warn("UnsupportedEncodingException while decoding MRL " + mLocation);
                    }
                }
            } else {
                logger.Error("Couldn't understand the intent");
                encounteredError();
                return;
            }

            // Try to get the position
            if(getIntent().getExtras() != null)
                intentPosition = getIntent().getExtras().getLong("position", -1);
        } /* ACTION_VIEW */
        /* Started from VideoListActivity */
        else if(getIntent().getAction() != null
                && getIntent().getAction().equals(PLAY_FROM_VIDEOGRID)
                && getIntent().getExtras() != null) {
            mLocation = getIntent().getExtras().getString(PLAY_EXTRA_ITEM_LOCATION);
            itemTitle = getIntent().getExtras().getString(PLAY_EXTRA_ITEM_TITLE);
            fromStart = getIntent().getExtras().getBoolean(PLAY_EXTRA_FROM_START);
            intentPosition = getIntent().getExtras().getLong("position", -1);

            String mediaSourceJson = getIntent().getExtras().getString("mediaSourceJson");
            currentMediaSource = (MediaSourceInfo)jsonSerializer.DeserializeFromString(mediaSourceJson, MediaSourceInfo.class);

            String apiAppName = getIntent().getExtras().getString("appName");
            String apiAppVersion = getIntent().getExtras().getString("appVersion");
            String apiDeviceId = getIntent().getExtras().getString("deviceId");
            String apiDeviceName = getIntent().getExtras().getString("deviceName");
            String apiUserId = getIntent().getExtras().getString("userId");
            String apiAccessToken = getIntent().getExtras().getString("accessToken");
            String apiServerUrl = getIntent().getExtras().getString("serverUrl");
            String playbackStartInfoJson = getIntent().getExtras().getString("playbackStartInfoJson");

            playbackStartInfo = (PlaybackProgressInfo)jsonSerializer.DeserializeFromString(playbackStartInfoJson, PlaybackProgressInfo.class);

            IDevice device = new AndroidDevice(getApplicationContext(), apiDeviceId, apiDeviceName);
            apiClient = TvApp.getApplication().getApiClient();
            apiClient.SetAuthenticationInfo(apiAccessToken, apiUserId);

            if (getIntent().hasExtra(PLAY_EXTRA_SUBTITLES_LOCATION))
                mSubtitleSelectedFiles.add(getIntent().getExtras().getString(PLAY_EXTRA_SUBTITLES_LOCATION));
            openedPosition = getIntent().getExtras().getInt(PLAY_EXTRA_OPENED_POSITION, -1);
        }

        if (openedPosition != -1) {
            // Provided externally from AudioService
            logger.Debug("Continuing playback from AudioService at index " + openedPosition);
            MediaWrapper openedMedia = mMediaListPlayer.getMediaList().getMedia(openedPosition);
            if (openedMedia == null) {
                encounteredError();
                return;
            }
            mLocation = openedMedia.getLocation();
            itemTitle = openedMedia.getTitle();
            savedIndexPosition = openedPosition;
        } else {
            /* prepare playback */
            // TODO: Review
            //AudioServiceController.getInstance().stop(); // Stop the previous playback.
            if (savedIndexPosition == -1 && mLocation != null && mLocation.length() > 0) {
                mMediaListPlayer.getMediaList().clear();
                final Media media = new Media(mLibVLC, mLocation);
                media.parse(); // FIXME: parse should'nt be done asynchronously
                media.release();
                mMediaListPlayer.getMediaList().add(new MediaWrapper(media));
                savedIndexPosition = mMediaListPlayer.getMediaList().size() - 1;
            }
        }
        mCanSeek = false;

        if (mLocation != null && mLocation.length() > 0) {

            // Start playback & seek
            if (openedPosition == -1) {
                VLCInstance.setAudioHdmiEnabled(this, mHasHdmiAudio);
                resumePositionMs = intentPosition;
                mMediaListPlayer.playIndex(savedIndexPosition, wasPaused);
            } else {
                mLibVLC.setVideoTrack(-1);
                mLibVLC.setVideoTrack(0);
                // AudioService-transitioned playback for item after sleep and resume
                if(!mLibVLC.isPlaying())
                    mMediaListPlayer.playIndex(savedIndexPosition);
                else
                    onPlaying();
            }


            // Get possible subtitles
            String subtitleList_serialized = mSettings.getString(PreferencesActivity.VIDEO_SUBTITLE_FILES, null);
            ArrayList<String> prefsList = new ArrayList<String>();
            if(subtitleList_serialized != null) {
                ByteArrayInputStream bis = new ByteArrayInputStream(subtitleList_serialized.getBytes());
                try {
                    ObjectInputStream ois = new ObjectInputStream(bis);
                    prefsList = (ArrayList<String>)ois.readObject();
                } catch(ClassNotFoundException e) {}
                catch (StreamCorruptedException e) {}
                catch (IOException e) {}
            }
            for(String x : prefsList){
                if(!mSubtitleSelectedFiles.contains(x))
                    mSubtitleSelectedFiles.add(x);
            }

            // Get the title
            if (itemTitle == null) {
                try {
                    title = URLDecoder.decode(mLocation, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                } catch (IllegalArgumentException e) {
                }
                if (title.startsWith("file:")) {
                    title = new File(title).getName();
                    int dotIndex = title.lastIndexOf('.');
                    if (dotIndex != -1)
                        title = title.substring(0, dotIndex);
                }
            }
        }
        if (itemTitle != null)
            title = itemTitle;
        mTitle.setText(title);
    }

    @SuppressWarnings("deprecation")
    private int getScreenRotation(){
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO /* Android 2.2 has getRotation */) {
            try {
                Method m = display.getClass().getDeclaredMethod("getRotation");
                return (Integer) m.invoke(display);
            } catch (Exception e) {
                return Surface.ROTATION_0;
            }
        } else {
            return display.getOrientation();
        }
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private int getScreenOrientation(){
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        int rot = getScreenRotation();
        /*
         * Since getRotation() returns the screen's "natural" orientation,
         * which is not guaranteed to be SCREEN_ORIENTATION_PORTRAIT,
         * we have to invert the SCREEN_ORIENTATION value if it is "naturally"
         * landscape.
         */
        @SuppressWarnings("deprecation")
        boolean defaultWide = display.getWidth() > display.getHeight();
        if(rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270)
            defaultWide = !defaultWide;
        if(defaultWide) {
            switch (rot) {
                case Surface.ROTATION_0:
                    return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                case Surface.ROTATION_90:
                    return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                case Surface.ROTATION_180:
                    // SCREEN_ORIENTATION_REVERSE_PORTRAIT only available since API
                    // Level 9+
                    return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                            : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                case Surface.ROTATION_270:
                    // SCREEN_ORIENTATION_REVERSE_LANDSCAPE only available since API
                    // Level 9+
                    return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                            : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                default:
                    return 0;
            }
        } else {
            switch (rot) {
                case Surface.ROTATION_0:
                    return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                case Surface.ROTATION_90:
                    return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                case Surface.ROTATION_180:
                    // SCREEN_ORIENTATION_REVERSE_PORTRAIT only available since API
                    // Level 9+
                    return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                            : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                case Surface.ROTATION_270:
                    // SCREEN_ORIENTATION_REVERSE_LANDSCAPE only available since API
                    // Level 9+
                    return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO ? ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                            : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                default:
                    return 0;
            }
        }
    }

    public void showAdvancedOptions(View v) {
        //FragmentManager fm = getFragmentManager();
        //AdvOptionsDialog advOptionsDialog = new AdvOptionsDialog();
        //advOptionsDialog.show(fm, "fragment_adv_options");
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void createPresentation() {
        if (mMediaRouter == null || mEnableCloneMode)
            return;

        // Get the current route and its presentation display.
        MediaRouter.RouteInfo route = mMediaRouter.getSelectedRoute(
                MediaRouter.ROUTE_TYPE_LIVE_VIDEO);

        Display presentationDisplay = route != null ? route.getPresentationDisplay() : null;

        if (presentationDisplay != null) {
            // Show a new presentation if possible.
            logger.Info("Showing presentation on display: " + presentationDisplay);
            mPresentation = new SecondaryDisplay(this, mLibVLC, presentationDisplay, logger);
            mPresentation.setOnDismissListener(mOnDismissListener);
            try {
                mPresentation.show();
                mPresentationDisplayId = presentationDisplay.getDisplayId();
            } catch (WindowManager.InvalidDisplayException ex) {
                logger.Warn("Couldn't show presentation!  Display was removed in "
                        + "the meantime.", ex);
                mPresentation = null;
            }
        } else
            logger.Info("No secondary display detected");
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void removePresentation() {
        if (mMediaRouter == null)
            return;

        // Dismiss the current presentation if the display has changed.
        logger.Info("Dismissing presentation because the current route no longer "
                + "has a presentation display.");
        if (mPresentation != null) mPresentation.dismiss();
        mPresentation = null;
        mPresentationDisplayId = -1;
        stopPlayback();

        recreate();
    }

    /**
     * Listens for when presentations are dismissed.
     */
    private final DialogInterface.OnDismissListener mOnDismissListener = new DialogInterface.OnDismissListener() {
        @Override
        public void onDismiss(DialogInterface dialog) {
            if (dialog == mPresentation) {
                logger.Info("Presentation was dismissed.");
                mPresentation = null;
            }
        }
    };

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private static final class SecondaryDisplay extends Presentation {
        public final static String TAG = "VLC/SecondaryDisplay";

        private SurfaceView mSurfaceView;
        private SurfaceView mSubtitlesSurfaceView;
        private SurfaceHolder mSurfaceHolder;
        private SurfaceHolder mSubtitlesSurfaceHolder;
        private FrameLayout mSurfaceFrame;
        private LibVLC mLibVLC;
        private ILogger logger;

        public SecondaryDisplay(Context context, LibVLC libVLC, Display display, ILogger logger) {
            super(context, display);
            if (context instanceof BaseActivity) {
                setOwnerActivity((BaseActivity) context);
            }
            mLibVLC = libVLC;
            this.logger = logger;
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.player_remote);

            mSurfaceView = (SurfaceView) findViewById(R.id.remote_player_surface);
            mSurfaceHolder = mSurfaceView.getHolder();
            mSurfaceFrame = (FrameLayout) findViewById(R.id.remote_player_surface_frame);

            VideoPlayerActivity activity = (VideoPlayerActivity)getOwnerActivity();
            if (activity == null) {
                logger.Error("Failed to get the VideoPlayerActivity instance, secondary display won't work");
                return;
            }

            mSurfaceHolder.addCallback(activity.mSurfaceCallback);

            mSubtitlesSurfaceView = (SurfaceView) findViewById(R.id.remote_subtitles_surface);
            mSubtitlesSurfaceHolder = mSubtitlesSurfaceView.getHolder();
            mSubtitlesSurfaceView.setZOrderMediaOverlay(true);
            mSubtitlesSurfaceHolder.setFormat(PixelFormat.TRANSLUCENT);
            mSubtitlesSurfaceHolder.addCallback(activity.mSubtitlesSurfaceCallback);

            if (mLibVLC.useCompatSurface())
                mSubtitlesSurfaceView.setVisibility(View.GONE);
            logger.Info("Secondary display created");
        }
    }

    /**
     * Start the video loading animation.
     */
    private void startLoadingAnimation() {
        AnimationSet anim = new AnimationSet(true);
        RotateAnimation rotate = new RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(800);
        rotate.setInterpolator(new DecelerateInterpolator());
        rotate.setRepeatCount(RotateAnimation.INFINITE);
        anim.addAnimation(rotate);
        mLoading.startAnimation(anim);
        mLoadingText.setVisibility(View.VISIBLE);
    }

    /**
     * Stop the video loading animation.
     */
    private void stopLoadingAnimation() {
        mLoading.setVisibility(View.INVISIBLE);
        mLoading.clearAnimation();
        mLoadingText.setVisibility(View.GONE);
        if (mPresentation != null) {
            mTipsBackground.setVisibility(View.VISIBLE);
        }
    }

    public void onClickOverlayTips(View v) {
        mOverlayTips.setVisibility(View.GONE);
    }

    public void onClickDismissTips(View v) {
        mOverlayTips.setVisibility(View.GONE);
        Editor editor = mSettings.edit();
        editor.putBoolean(PREF_TIPS_SHOWN, true);
        Util.commitPreferences(editor);
    }

    private void updateNavStatus() {
        mHasMenu = mLibVLC.getChapterCountForTitle(0) > 1 && mLibVLC.getTitleCount() > 1 && (mLocation == null || !mLocation.endsWith(".mkv"));
        mIsNavMenu = mHasMenu && mLibVLC.getTitle() == 0;
        /***
         * HACK ALERT: assume that any media with >1 titles = DVD with menus
         * Should be replaced with a more robust title/chapter selection popup
         */

        logger.Debug("updateNavStatus: getChapterCountForTitle(0) = "
                        + mLibVLC.getChapterCountForTitle(0)
                        + ", getTitleCount() = " + mLibVLC.getTitleCount());
        if (mIsNavMenu) {
            /*
             * Keep the overlay hidden in order to have touch events directly
             * transmitted to navigation handling.
             */
            hideOverlay(false);
        }
        else if (mHasMenu)
            setESTracks();
    }

    private GestureDetector.OnGestureListener mGestureListener = new GestureDetector.OnGestureListener() {
        @Override
        public boolean onDown(MotionEvent e) {
            return false;
        }

        @Override
        public void onShowPress(MotionEvent e) {}

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return false;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {}

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            return false;
        }
    };
}