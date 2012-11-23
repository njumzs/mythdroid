/*
    MythDroid: Android MythTV Remote
    Copyright (C) 2009-2010 foobum@gmail.com

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.mythdroid.activities;

import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.mythdroid.Globals;
import org.mythdroid.R;
import org.mythdroid.Enums.Extras;
import org.mythdroid.Enums.Category;
import org.mythdroid.data.Channel;
import org.mythdroid.data.Program;
import org.mythdroid.data.XMLHandler;
import org.mythdroid.data.Channel.ChannelListener;
import org.mythdroid.data.Channel.ChannelXMLParser;
import org.mythdroid.data.XMLHandler.Element;
import org.mythdroid.remote.TVRemote;
import org.mythdroid.resource.Messages;
import org.mythdroid.services.GuideService;
import org.mythdroid.util.ErrUtil;
import org.mythdroid.util.LogUtil;
import org.xml.sax.SAXException;

import android.R.drawable;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.sax.EndTextElementListener;
import android.util.Xml;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.DatePicker;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.TableRow.LayoutParams;

/** Display a program guide */
@SuppressLint("SimpleDateFormat")
public class Guide extends MDActivity {

    /**
     * A resultCode that child activities should set via setResult() to tell
     * Guide to refresh when they finish
     */
    final public static int  REFRESH_NEEDED = Activity.RESULT_FIRST_USER + 1;
    final private static int MENU_DATE    = 0, MENU_TIME = 1;
    final private static int DIALOG_DATE  = 0, DIALOG_TIME = 1;

    final private static Pattern catPat = Pattern.compile("[\\s/-]"); //$NON-NLS-1$
    
    /**
     * Change numHours to configure how many hours are displayed at a time 
     * This value is doubled for devices with one screen dimension > 1000 pixels 
     */
    private static int       numHours = 2, numTimes;
    final private static int colMins  = 5, hdrSpan = 6;

    private Date             now = null,   later = null;

    /** ArrayList of channel objects, capacity ensured during XML parsing */
    private ArrayList<Channel> channels = new ArrayList<Channel>();

	final private SimpleDateFormat
        date = new SimpleDateFormat("d MMM yy"), //$NON-NLS-1$
        time = new SimpleDateFormat("HH:mm"); //$NON-NLS-1$
    
    final private LayoutParams
        rowLayout     = new LayoutParams(), chanLayout    = new LayoutParams(),
        hdrDateLayout = new LayoutParams(), hdrTimeLayout = new LayoutParams(),
        spacerLayout  = new LayoutParams();

    final private Handler handler = new Handler();

    private long[]       times     = null;
    private String[]     hdrTimes  = null;
    private String       hdrDate   = null;
    private TableLayout  tbl       = null;
    /** Scale factor for pixel values for different display densities */
    private float        scale     = 1;
    
    private Drawable
        recordedIcon = null, willRecordIcon = null, failedIcon = null,
        conflictIcon = null, otherIcon = null;

    /**
    * Tweak colWidth to alter the visible width of the columns
    * Tweak rowHeight to alter the visible height of rows
    */
    private int          colWidth, rowHeight, chanWidth;
    
    private GuideService guideService;

    /** Get and sort the list of channels, add them to table in UI thread */
    final private Runnable getData = new Runnable() {
        @Override
        public void run() {
            getGuideData(now, later);
            Collections.sort(channels);
            handler.post(
                new Runnable() {
                    @Override
                    public void run() {

                        dismissLoadingDialog();

                        tbl.addView(getHeader());
                        // this is necessary to get proper layout
                        tbl.addView(getSpacer());

                        int j = 0;
                        int maxChan = channels.size();

                        for (int i = 0; i < maxChan; i++) {

                            Channel current = channels.get(i);

                            if (current.num.length() == 0)    continue;

                            /*
                             * MythTV 0.24 sometimes splits programs amongst
                             * channels with different ids but the same num
                             * and callsign.. :/
                             */
                            if (i < maxChan - 1) {

                                // Luckily, they're always adjacent
                                Channel next = channels.get(i+1);

                                /* 
                                 * See if the next channel has the same num
                                 * and callsign
                                 */
                                if (
                                    current.num.equals(next.num) &&
                                    current.callSign.equals(next.callSign)
                                ) {
                                    /* 
                                     * It does, add all our programs to it and
                                     * skip the current channel
                                     */
                                    next.programs.addAll(current.programs);
                                    continue;
                                }

                            }

                            // Add a header every 7 rows
                            if (j++ == 7) {
                                tbl.addView(getHeader());
                                j = 0;
                            }

                            // This became necessary in MythTV 0.24
                            Collections.sort(current.programs);

                            tbl.addView(getRowFromChannel(current));

                        }

                    }
                }
           );
        }
    };

    final private OnClickListener progClickListener =
        new OnClickListener() {
            @Override
            public void onClick(View v) {
                Globals.curProg = (Program)v.getTag();
                startActivityForResult(
                    new Intent()
                        .putExtra(Extras.GUIDE.toString(), true)
                        .setClass(ctx, RecordingDetail.class), 0
                );
            }
        };

    final private OnClickListener chanClickListener =
        new OnClickListener() {
            @Override
            public void onClick(View v) {
            startActivity(
                new Intent()
                    .putExtra(Extras.LIVETV.toString(), true)
                    .putExtra(Extras.JUMPCHAN.toString(),(Integer)v.getTag())
                    .setClass(ctx, TVRemote.class)
            );
        }
    };

    final private OnLongClickListener chanLongClickListener =
        new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                setExtra(Extras.LIVETV.toString());
                setExtra(Extras.JUMPCHAN.toString(), (Integer)v.getTag());
                nextActivity = TVRemote.class;
                showDialog(FRONTEND_CHOOSER);
                return true;
            }
        };

    @Override
    public void onCreate(Bundle icicle) {

        super.onCreate(icicle);
        setContentView(R.layout.guide);
        
        if (
            getWindowManager().getDefaultDisplay().getWidth()  > 1000 ||
            getWindowManager().getDefaultDisplay().getHeight() > 1000
        )
            numHours *= 2;
        
        numTimes = numHours * 60 / colMins;
        
        times = new long[numTimes + 1];
        hdrTimes = new String[numTimes / hdrSpan];

        scale = getResources().getDisplayMetrics().density;
        colWidth  = (int)(40  * scale + 0.5f);
        rowHeight = (int)(60  * scale + 0.5f);
        chanWidth = (int)(100 * scale + 0.5f);

        tbl = (TableLayout)findViewById(R.id.table);

        rowLayout.topMargin = rowLayout.bottomMargin = chanLayout.topMargin =
            chanLayout.bottomMargin = chanLayout.leftMargin =
            chanLayout.rightMargin = hdrDateLayout.leftMargin =
            hdrDateLayout.rightMargin = hdrTimeLayout.leftMargin =
            hdrTimeLayout.rightMargin = 1;

        rowLayout.height = chanLayout.height = rowHeight;

        chanLayout.column = hdrDateLayout.column = 0;
        chanLayout.span = hdrDateLayout.span = 1;
        chanLayout.width = hdrDateLayout.width = chanWidth;

        hdrTimeLayout.width = colWidth * hdrSpan;
        hdrTimeLayout.span = hdrSpan;

        spacerLayout.height = 1;
        spacerLayout.width = colWidth;
        spacerLayout.span = 1;

        recordedIcon = getResources().getDrawable(R.drawable.recorded);
        willRecordIcon = getResources().getDrawable(R.drawable.willrecord);
        failedIcon = getResources().getDrawable(R.drawable.failed);
        conflictIcon = getResources().getDrawable(R.drawable.conflict);
        otherIcon = getResources().getDrawable(R.drawable.other);

        date.setTimeZone(TimeZone.getDefault());
        time.setTimeZone(TimeZone.getDefault());
        
        if (Globals.haveServices())
            try {
                guideService = new GuideService(Globals.getBackend().addr);
            } catch (IOException e) {
                ErrUtil.err(this, e);
                return;
            }

        displayGuide(new Date());

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Globals.removeThreadPoolTask(getData);
        tbl.removeAllViews();
        channels.clear();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_DATE, Menu.NONE, R.string.chStartDate)
            .setIcon(drawable.ic_menu_today);
        menu.add(Menu.NONE, MENU_TIME, Menu.NONE, R.string.chStartTime)
            .setIcon(drawable.ic_menu_recent_history);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case MENU_DATE:
                showDialog(DIALOG_DATE);
                break;
            case MENU_TIME:
                showDialog(DIALOG_TIME);
                break;
        }

        return true;

    }

    @Override
    public Dialog onCreateDialog(int id) {

        switch (id) {

            case DIALOG_DATE:
                return new DatePickerDialog(this,
                    new OnDateSetListener() {
                        private boolean done = false;
                        @Override
                        public void onDateSet(
                            DatePicker view, int year, int month, int day
                        ) {
                            if (done) return;
                            now.setYear(year - 1900);
                            now.setMonth(month);
                            now.setDate(day);
                            displayGuide(now);
                            done = true;
                        }
                    },
                    now.getYear() + 1900, now.getMonth(), now.getDate()
                );

            case DIALOG_TIME:
                return new TimePickerDialog(this,
                    new OnTimeSetListener() {
                        private boolean done = false;
                        @Override
                        public void onTimeSet(
                            TimePicker view, int hour, int min
                        ) {
                            if (done) return;
                            now.setHours(hour);
                            now.setMinutes(min);
                            displayGuide(now);
                            done = true;
                        }
                    },
                    now.getHours(), now.getMinutes(), true
            );

            default:
                return super.onCreateDialog(id);

        }

    }

    @Override
    public void onActivityResult(int reqCode, int resCode, Intent data) {
        if (resCode == REFRESH_NEEDED)
            displayGuide(now);
    }

    @Override
    public void onPrepareDialog(int id, Dialog dialog) {

        switch (id) {

            case DIALOG_DATE:
                ((DatePickerDialog)dialog).updateDate(
                    now.getYear() + 1900, now.getMonth(), now.getDate()
                );
                break;

            case DIALOG_TIME:
                ((TimePickerDialog)dialog).updateTime(
                    now.getHours(), now.getMinutes()
                );
                break;
                
            default:
                super.onPrepareDialog(id, dialog);

        }

    }

    /**
     * Display the guide
     * @param when Date to start at
     */
    private void displayGuide(Date when) {

        showDialog(DIALOG_LOAD);

        tbl.removeAllViews();
        channels.clear();

        // round down to last 30 min boundary
        long nowTime = when.getTime();
        nowTime -= nowTime % 1000;
        now = new Date(nowTime);
        now.setSeconds(0);
        if (now.getMinutes() >= 30)
            now.setMinutes(30);
        else
            now.setMinutes(0);
        nowTime = now.getTime();
        hdrDate = date.format(now);

        long laterTime = nowTime + numHours * 3600000;
        later = new Date(laterTime);

        int j = 1;
        // 0th column is channel name
        times[0] = 0;
        // rest are at 5 min intervals
        for (long i = nowTime; i < laterTime; i += colMins * 60000)
            times[j++] = i;

        j = 1;

        for (int i = 0; i < hdrTimes.length; i++) {
            hdrTimes[i] = time.format(new Date(times[j]));
            j += hdrSpan;
        }

        Globals.runOnThreadPool(getData);

    }

    /**
     * Fetch and parse guide data from the backend
     * @param start Date that guide should start at
     * @param end Date that guide should end at
     */
    private void getGuideData(Date start, Date end) {
        
        if (Globals.haveServices()) {
            try {
                channels = guideService.GetProgramGuide(start, end);
            } catch (IOException e) {
                ErrUtil.postErr(this, e);
                return;
            } catch (JSONException e) {
                ErrUtil.postErr(this, e);
                return;
            }
            return;
        }

        XMLHandler handler = new XMLHandler("GetProgramGuideResponse"); //$NON-NLS-1$
        Element root = handler.rootElement();

        root.getChild("NumOfChannels").setTextElementListener( //$NON-NLS-1$
            new EndTextElementListener() {
                @Override
                public void end(String body) {
                    channels.ensureCapacity(Integer.valueOf(body));
                }
            }
        );

        Element chanElement = root.getChild("ProgramGuide") //$NON-NLS-1$
                                  .getChild("Channels") //$NON-NLS-1$
                                  .getChild("Channel"); //$NON-NLS-1$

        chanElement.setStartElementListener(
            new ChannelXMLParser(this, chanElement,
                new ChannelListener() {
                    @Override
                    public void channel(Channel chan) {
                        channels.add(chan);
                    }
                }
            )
        );

        try {

            URL url = new URL(
                Globals.getBackend().getStatusURL() +
                "/Myth/GetProgramGuide?" +  //$NON-NLS-1$
                "StartTime=" + Globals.dateFmt.format(start) + //$NON-NLS-1$
                "&EndTime="  + Globals.dateFmt.format(end)   + //$NON-NLS-1$
                "&StartChanId=0" + "&NumOfChannels=-1" + "&Details=1" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            );

            if (Globals.muxConns)
                url = new URL(
                    url.getProtocol() + "://" + url.getHost() +  //$NON-NLS-1$
                    ":16550" + url.getFile()  //$NON-NLS-1$
                );
            
            LogUtil.debug("Fetching XML from " + url.toExternalForm()); //$NON-NLS-1$
            
            Xml.parse(url.openStream(), Xml.Encoding.UTF_8, handler);
   
        } catch (SAXException e) {
            ErrUtil.postErr(this, Messages.getString("Guide.13")); //$NON-NLS-1$
        } catch (IOException e) {
            ErrUtil.postErr(this, e);
        }

    }

    /**
     * Add a small indicator to the provided TextView based on the Program
     * recording status
     * @param tv TextView to add an indicator to
     * @param prog Program to retrieve a recording status from
     */
    private void setStatusDrawable(TextView tv, Program prog) {

        Drawable icon = null;
        
        if (prog.Status == null) return; 

        switch (prog.Status) {
            case RECORDED:
            case CURRENT:
                icon = recordedIcon;
                break;
            case WILLRECORD:
            case RECORDING:
                icon = willRecordIcon;
                break;
            case CANCELLED:
            case ABORTED:
            case DONTRECORD:
            case FAILED:
            case TUNERBUSY:
                icon = failedIcon;
                break;
            case CONFLICT:
                icon = conflictIcon;
                break;
            case EARLIER:
            case LATER:
            case OTHER:
                icon = otherIcon;
                break;
        }

        if (icon != null) {
            tv.setCompoundDrawablesWithIntrinsicBounds(
                icon, null, null, null
            );
            tv.setCompoundDrawablePadding(8);
        }

    }

    /**
     * Generate a TableRow from the provided Channel
     * @param ch Channel to generate a row for
     * @return populated TableRow representing the channel
     */
	@SuppressLint("DefaultLocale")
	private TableRow getRowFromChannel(Channel ch) {

        final TableRow row = new TableRow(this);
        row.setLayoutParams(rowLayout);

        TextView tv = new TextView(this);
        tv.setBackgroundColor(0xffe0e0f0);
        tv.setTextColor(0xff202020);
        tv.setPadding(4, 4, 4, 4);
        tv.setMaxLines(2);
        tv.setTag(ch.ID);
        tv.setText(ch.num + " " + ch.callSign); //$NON-NLS-1$
        tv.setOnClickListener(chanClickListener);
        tv.setOnLongClickListener(chanLongClickListener);
        tv.setLayoutParams(chanLayout);

        row.addView(tv);

        LayoutParams layout = null;
        
        Program[] progs = ch.programs.toArray(new Program[ch.programs.size()]);
        int numprogs = progs.length;

        for (int i = 0; i < numprogs; i++) {

            if (progs[i].StartTime.equals(later))
                continue;

            tv = new TextView(this);
            layout = new LayoutParams(this, null);
            layout.topMargin = layout.bottomMargin =
                layout.leftMargin = layout.rightMargin = 1;
            layout.height = rowHeight;

            String cat =
                catPat.matcher(progs[i].Category.toLowerCase()).replaceAll(""); //$NON-NLS-1$

            try {
                tv.setBackgroundColor(Category.valueOf(cat).color());
            } catch (IllegalArgumentException e) {
                tv.setBackgroundColor(Category.unknown.color());
            }

            tv.setTextColor(0xfff0f0f0);
            tv.setPadding(4, 4, 4, 4);
            tv.setMaxLines(2);
            tv.setText(progs[i].Title);
            setStatusDrawable(tv, progs[i]);

            int width = setLayoutParams(layout, progs[i]) * colWidth;
            if (width < 1) continue;

            tv.setWidth(width);
            tv.setLayoutParams(layout);
            tv.setTag(progs[i]);
            tv.setOnClickListener(progClickListener);
            row.addView(tv);

        }

        return row;

    }

    /**
     * Get a header row containing column time values
     * @return header TableRow
     */
    private TableRow getHeader() {

        final TableRow row = new TableRow(this, null);
        TextView tv = new TextView(this, null);
        tv.setLayoutParams(hdrDateLayout);
        tv.setPadding(4, 4, 4, 4);
        tv.setBackgroundColor(0xffd0d0ff);
        tv.setTextColor(0xff161616);
        tv.setMaxLines(1);
        tv.setText(hdrDate);
        row.addView(tv);

        int j = 0;

        for (int i = 1; i < times.length; i += hdrSpan) {
            tv = new TextView(this, null);
            tv.setLayoutParams(hdrTimeLayout);
            tv.setPadding(4, 4, 4, 4);
            tv.setBackgroundColor(0xffd0d0ff);
            tv.setTextColor(0xff161616);
            tv.setMaxLines(1);
            tv.setText(hdrTimes[j++]);
            row.addView(tv);
        }

        return row;

    }

    /**
     * Get a spacer row
     * @return a spacer TableRow
     */
    private TableRow getSpacer() {

        final TableRow row = new TableRow(this, null);
        LayoutParams params = new LayoutParams();
        params.height = 1;
        row.setLayoutParams(params);
        View view = new View(this, null);
        params = new LayoutParams();
        params.height = 1;
        params.width = chanWidth;
        params.column = 0;
        params.span = 1;
        view.setLayoutParams(params);
        row.addView(view);

        for (int i = 1; i < times.length; i++) {
            view = new View(this, null);
            view.setLayoutParams(spacerLayout);
            row.addView(view);
        }

        return row;

    }

    /**
     * Round the given Date to the nearest column time
     * @param time
     */
    private void roundTime(Date time) {
        final int mins = time.getMinutes();
        final int off = mins % colMins;
        if (off == 0) return;
        int half = colMins / 2;
        if (half * 2 < colMins) half++;
        if (off < half)
            time.setMinutes(mins - off);
        else
            time.setMinutes(mins + (colMins - off));
    }

    /**
     * Populate the given LayoutParams based on a Program
     * @param params LayoutParams to populate
     * @param prog Program to generate LayoutParams from
     * @return integer containing the number of columns the program should span
     */
    private int setLayoutParams(LayoutParams params, Program prog) {

        final Date startTime = prog.StartTime, endTime = prog.EndTime;

        roundTime(startTime);
        roundTime(endTime);

        long start = startTime.getTime();
        long end = endTime.getTime();

        int maxcol = numTimes + 1;

        if (start < times[1]) {
            params.column = 1;
            start = times[1];
        }
        else {
            for (int i = 1; i < maxcol; i++) {
                if (start == times[i]) {
                    params.column = i;
                    break;
                }
            }
        }

        int span = (int) ((end - start) / (colMins * 60000));
        if (span + params.column > maxcol) span = maxcol - params.column;
        params.span = span;

        return span;

    }

}
