package tv.emby.embyatv.playback.vlc;

import android.annotation.TargetApi;
import android.content.SharedPreferences;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class Util {
    public final static String TAG = "VLC/Util";
    private static final AtomicInteger sNextGeneratedId = new AtomicInteger(1);
    public static final String ACTION_SCAN_START = "org.videolan.vlc.gui.ScanStart";
    public static final String ACTION_SCAN_STOP = "org.videolan.vlc.gui.ScanStop";

    @TargetApi(android.os.Build.VERSION_CODES.GINGERBREAD)
    public static void commitPreferences(SharedPreferences.Editor editor){
        if (AndroidUtil.isGingerbreadOrLater())
            editor.apply();
        else
            editor.commit();
    }

    public static boolean close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
                return true;
            } catch (IOException e) {
                return false;
            }
        } else {
            return false;
        }
    }
}