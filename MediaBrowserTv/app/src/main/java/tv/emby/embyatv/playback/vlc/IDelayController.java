package tv.emby.embyatv.playback.vlc;

public interface IDelayController {
    public enum DelayState {OFF, AUDIO, SUBS};

    public void showAudioDelaySetting();
    public void showSubsDelaySetting();
    public void endDelaySetting();
}