package tv.emby.embyatv.ui;

import android.content.Context;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.jakewharton.disklrucache.Util;

import java.util.Date;

import mediabrowser.model.livetv.ProgramInfoDto;
import tv.emby.embyatv.R;
import tv.emby.embyatv.TvApp;
import tv.emby.embyatv.livetv.LiveTvGuideActivity;
import tv.emby.embyatv.util.InfoLayoutHelper;
import tv.emby.embyatv.util.Utils;

/**
 * Created by Eric on 5/4/2015.
 */
public class ProgramGridCell extends RelativeLayout implements IRecordingIndicatorView {

    private LiveTvGuideActivity mActivity;
    private TextView mProgramName;
    private LinearLayout mInfoRow;
    private ProgramInfoDto mProgram;
    private ImageView mRecIndicator;
    private int mBackgroundColor = 0;
    private final int IND_HEIGHT = Utils.convertDpToPixel(TvApp.getApplication(), 10);

    public ProgramGridCell(Context context, ProgramInfoDto program) {
        super(context);
        initComponent((LiveTvGuideActivity) context, program);
    }

    private void initComponent(LiveTvGuideActivity activity, ProgramInfoDto program) {
        mActivity = activity;
        mProgram = program;
        LayoutInflater inflater = LayoutInflater.from(activity);
        View v = inflater.inflate(R.layout.program_grid_cell, this, false);
        this.addView(v);

        mProgramName = (TextView) findViewById(R.id.programName);
        mInfoRow = (LinearLayout) findViewById(R.id.infoRow);
        mProgramName.setText(program.getName());
        mProgram = program;
        mProgramName.setFocusable(false);
        mInfoRow.setFocusable(false);
        mRecIndicator = (ImageView) findViewById(R.id.recIndicator);

        if (program.getIsMovie())
            mBackgroundColor = getResources().getColor(R.color.guide_movie_bg);
        else if (program.getIsNews())
            mBackgroundColor = getResources().getColor(R.color.guide_news_bg);
        else if (program.getIsSports())
            mBackgroundColor = getResources().getColor(R.color.guide_sports_bg);
        else if (program.getIsKids())
            mBackgroundColor = getResources().getColor(R.color.guide_kids_bg);

        setBackgroundColor(mBackgroundColor);

        if (program.getStartDate() != null && program.getEndDate() != null) {
            TextView time = new TextView(activity);
            Date localStart = Utils.convertToLocalDate(program.getStartDate());
            if (localStart.getTime() + 60000 < activity.getCurrentLocalStartDate()) mProgramName.setText("<< "+mProgramName.getText());
            time.setText(android.text.format.DateFormat.getTimeFormat(TvApp.getApplication()).format(Utils.convertToLocalDate(program.getStartDate()))
                    + "-" + android.text.format.DateFormat.getTimeFormat(TvApp.getApplication()).format(Utils.convertToLocalDate(program.getEndDate())));
            mInfoRow.addView(time);
        }

        if (program.getOfficialRating() != null && !program.getOfficialRating().equals("0")) {
            InfoLayoutHelper.addSpacer(activity, mInfoRow, "  ", 10);
            InfoLayoutHelper.addBlockText(activity, mInfoRow, program.getOfficialRating(), 10);
        }

        if (program.getIsHD() != null && program.getIsHD()) {
            InfoLayoutHelper.addSpacer(activity, mInfoRow, "  ", 10);
            InfoLayoutHelper.addBlockText(activity, mInfoRow, "HD", 10);
        }

        if (program.getSeriesTimerId() != null) {
            mRecIndicator.setImageResource(R.drawable.recseries);
        } else if (program.getTimerId() != null) {
            mRecIndicator.setImageResource(R.drawable.rec);
        }

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mActivity.showProgramOptions();
            }
        });

    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);

        if (gainFocus) {
            setBackgroundColor(getResources().getColor(R.color.lb_default_brand_color));
            mActivity.setSelectedProgram(this);
        } else {
            setBackgroundColor(mBackgroundColor);
        }

        //TvApp.getApplication().getLogger().Debug("Focus on "+mProgram.getName()+ " was " +(gainFocus ? "gained" : "lost"));
    }

    public ProgramInfoDto getProgram() { return mProgram; }

    public void setRecTimer(String id) {
        mProgram.setTimerId(id);
        mRecIndicator.setImageResource(id != null ? R.drawable.rec : R.drawable.blank10x10);
    }
    public void setRecSeriesTimer(String id) {
        mProgram.setSeriesTimerId(id);
        mRecIndicator.setImageResource(id != null ? R.drawable.recseries : R.drawable.blank10x10);
    }
}
