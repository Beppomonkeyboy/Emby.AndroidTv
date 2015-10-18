package tv.emby.embyatv.base;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import tv.emby.embyatv.R;
import tv.emby.embyatv.TvApp;
import tv.emby.embyatv.search.SearchActivity;
import tv.emby.embyatv.util.Utils;

/**
 * Created by Eric on 2/18/2015.
 */
public class BaseActivity extends Activity {

    private TvApp app = TvApp.getApplication();
    private long timeoutInterval = 3600000;
    private Handler handler = new Handler();
    private Runnable loop;
    private IKeyListener keyListener;
    private IMessageListener messageListener;

    private View messageUi;
    private TextView messageTitle;
    private TextView messageMessage;
    private ImageView messageIcon;
    private int messageTimeout = 6000;
    private int farRight;
    private int msgPos;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        timeoutInterval = Long.parseLong(PreferenceManager.getDefaultSharedPreferences(app).getString("pref_auto_logoff_timeout","3600000"));
        startAutoLogoffLoop();
        TvApp.getApplication().setCurrentActivity(this);

        //Add message UI - delay to ensure on top of other views
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                FrameLayout root = (FrameLayout) findViewById(android.R.id.content);
                messageUi = View.inflate(app.getCurrentActivity(), R.layout.message, null);
                messageTitle = (TextView) messageUi.findViewById(R.id.msgTitle);
                messageMessage = (TextView) messageUi.findViewById(R.id.message);
                messageIcon = (ImageView) messageUi.findViewById(R.id.msgIcon);
                messageUi.setAlpha(0);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.RIGHT | Gravity.BOTTOM);
                params.bottomMargin = Utils.convertDpToPixel(TvApp.getApplication().getCurrentActivity(), 50);
                Rect windowSize = new Rect();
                getWindow().getDecorView().getWindowVisibleDisplayFrame(windowSize);
                farRight = windowSize.right;
                msgPos = farRight-Utils.convertDpToPixel(TvApp.getApplication().getCurrentActivity(), 500);
                messageUi.setLeft(farRight);
                root.addView(messageUi, params);
            }
        }, 200);

    }

    public void showMessage(String title, String msg) {
        showMessage(title, msg, messageTimeout);
    }

    public void showMessage(String title, String msg, int timeout) {
        if (messageUi != null && !isFinishing()) {
            messageTitle.setText(title);
            messageMessage.setText(msg);
            messageUi.animate().x(msgPos).alpha(1).setDuration(300);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (messageUi != null && !isFinishing()) {
                        messageUi.animate().alpha(0).x(farRight).setDuration(300);
                    }
                }
            },timeout);
        }

    }

    //Banish task bars and navigation controls on non-TV devices
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && android.os.Build.VERSION.SDK_INT >= 19) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
    }

    @Override
    protected void onResume() {
        super.onResume();
        TvApp.getApplication().setCurrentActivity(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        TvApp.getApplication().setCurrentActivity(null);
    }

    @Override
    protected void onDestroy() {
        if (handler != null && loop != null) handler.removeCallbacks(loop);
        super.onDestroy();
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        app.setLastUserInteraction(System.currentTimeMillis());
    }

    private void startAutoLogoffLoop() {
        loop = new Runnable() {
            @Override
            public void run() {
                if (System.currentTimeMillis() > app.getLastUserInteraction() + timeoutInterval) {
                    app.getLogger().Info("Logging off due to inactivity "+app.getLastUserInteraction());
                    Utils.showToast(app, "Emby Logging off due to inactivity...");
                    if (app.getPlaybackController() != null && app.getPlaybackController().isPaused()) {
                        app.getLogger().Info("Playback was paused, stopping gracefully...");
                        app.getPlaybackController().stop();
                    }
                    finish();
                } else {
                    handler.postDelayed(this, 30000);
                }
            }
        };

        handler.postDelayed(loop, 60000);

    }

    public void registerKeyListener(IKeyListener listener) {
        keyListener = listener;
    }

    public void registerMessageListener(IMessageListener listener) {
        messageListener = listener;
    }

    public void sendMessage(CustomMessage message) {
        if (messageListener != null) messageListener.onMessageReceived(message);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return keyListener != null ? keyListener.onKeyUp(keyCode, event) || super.onKeyUp(keyCode, event) : super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onSearchRequested() {
        Intent searchIntent = new Intent(this, SearchActivity.class);
        startActivity(searchIntent);
        return true;
    }
}
