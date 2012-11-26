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

package org.mythdroid.data;

import java.text.ParseException;
import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;
import org.mythdroid.Globals;
import org.mythdroid.Enums.RecDupIn;
import org.mythdroid.Enums.RecDupMethod;
import org.mythdroid.Enums.RecType;
import org.mythdroid.services.Params;

/**
 * Represents a recording rule
 */
public class RecordingRule {
    
    @SuppressWarnings("javadoc")
    public int     id, parentid, season, episode, chanid, day, recpriority, 
                   preferredinput, startoffset, endoffset, maxepisodes, 
                   filter, transcoder, avgdelay;

    @SuppressWarnings("javadoc")
    public long    findid;
    
    @SuppressWarnings("javadoc")
    public String  title, subtitle, description, category, programid, inetref,
                   callsign, time, recProfile, recGroup, storGroup, playGroup,
                   nextrec, lastrec, lastdel, searchtype, seriesid;
    
    @SuppressWarnings("javadoc")
    public boolean inactive, autoexpire, maxnewest, autoflag, autotranscode,
                   autometa, autojob1, autojob2, autojob3, autojob4;
    
    @SuppressWarnings("javadoc")
    public Date            startTime, endTime;
    @SuppressWarnings("javadoc")
    public RecType         type;
    @SuppressWarnings("javadoc")
    public RecDupMethod    dupMethod;
    @SuppressWarnings("javadoc")
    public RecDupIn        dupIn;
    
    /**
     * Instantiate from a "RecRule" JSONObject
     * @param jo a RecRule JSONObject
     * @throws ParseException 
     */
    public RecordingRule(JSONObject jo) throws JSONException, ParseException {
        
        id              = jo.getInt("Id"); //$NON-NLS-1$
        parentid        = jo.getInt("ParentId"); //$NON-NLS-1$
        inactive        = jo.getBoolean("Inactive"); //$NON-NLS-1$
        title           = jo.getString("Title"); //$NON-NLS-1$
        subtitle        = jo.getString("SubTitle"); //$NON-NLS-1$
        description     = jo.getString("Description"); //$NON-NLS-1$
        season          = jo.getInt("Season"); //$NON-NLS-1$
        episode         = jo.getInt("Episode"); //$NON-NLS-1$
        category        = jo.getString("Category"); //$NON-NLS-1$
        startTime       = Globals.utcFmt.parse(jo.getString("StartTime")); //$NON-NLS-1$
        endTime         = Globals.utcFmt.parse(jo.getString("EndTime")); //$NON-NLS-1$
        seriesid        = jo.getString("SeriesId"); //$NON-NLS-1$
        programid       = jo.getString("ProgramId"); //$NON-NLS-1$
        inetref         = jo.getString("Inetref"); //$NON-NLS-1$
        chanid          = jo.getInt("ChanId"); //$NON-NLS-1$
        callsign        = jo.getString("CallSign"); //$NON-NLS-1$
        day             = jo.getInt("Day"); //$NON-NLS-1$
        time            = jo.getString("Time"); //$NON-NLS-1$
        findid          = jo.getInt("FindId"); //$NON-NLS-1$
        type            = RecType.get(jo.getString("Type")); //$NON-NLS-1$
        searchtype      = jo.getString("SearchType"); //$NON-NLS-1$
        recpriority     = jo.getInt("RecPriority"); //$NON-NLS-1$
        preferredinput  = jo.getInt("PreferredInput"); //$NON-NLS-1$
        startoffset     = jo.getInt("StartOffset"); //$NON-NLS-1$
        endoffset       = jo.getInt("EndOffset"); //$NON-NLS-1$
        dupMethod       = RecDupMethod.get(jo.getString("DupMethod")); //$NON-NLS-1$
        dupIn           = RecDupIn.get(jo.getString("DupIn")); //$NON-NLS-1$
        filter          = jo.getInt("Filter"); //$NON-NLS-1$
        recProfile      = jo.getString("RecProfile"); //$NON-NLS-1$
        recGroup        = jo.getString("RecGroup"); //$NON-NLS-1$
        storGroup       = jo.getString("StorageGroup"); //$NON-NLS-1$
        playGroup       = jo.getString("PlayGroup"); //$NON-NLS-1$
        autoexpire      = jo.getBoolean("AutoExpire"); //$NON-NLS-1$
        maxepisodes     = jo.getInt("MaxEpisodes"); //$NON-NLS-1$
        maxnewest       = jo.getBoolean("MaxNewest"); //$NON-NLS-1$
        autoflag        = jo.getBoolean("AutoCommflag"); //$NON-NLS-1$
        autotranscode   = jo.getBoolean("AutoTranscode"); //$NON-NLS-1$
        autometa        = jo.getBoolean("AutoMetaLookup"); //$NON-NLS-1$
        autojob1        = jo.getBoolean("AutoUserJob1"); //$NON-NLS-1$
        autojob2        = jo.getBoolean("AutoUserJob2"); //$NON-NLS-1$
        autojob3        = jo.getBoolean("AutoUserJob3"); //$NON-NLS-1$
        autojob4        = jo.getBoolean("AutoUserJob4"); //$NON-NLS-1$
        transcoder      = jo.getInt("Transcoder"); //$NON-NLS-1$
        nextrec         = jo.getString("NextRecording"); //$NON-NLS-1$
        lastrec         = jo.getString("LastRecorded"); //$NON-NLS-1$
        lastdel         = jo.getString("LastDeleted"); //$NON-NLS-1$
        avgdelay        = jo.getInt("AverageDelay"); //$NON-NLS-1$
    }
    
    /** Construct a RecordingRule from a Program */
    public RecordingRule(Program prog) {
        chanid      = prog.ChanID;
        startTime   = prog.StartTime;
        endTime     = prog.EndTime;
        title       = prog.Title;
        subtitle    = prog.SubTitle;
        description = prog.Description;
        callsign    = prog.Channel;
        category    = prog.Category;
        type        = prog.Type;
        recpriority = prog.RecPrio;
        dupMethod   = prog.DupMethod;
        dupIn       = prog.DupIn;
        recGroup    = prog.RecGroup;
        storGroup   = prog.StorGroup;
    }
    
    /** Construct an empty recording rule */
    public RecordingRule() {}

    /**
     * Get a Params object representing this rule
     * @return Params object
     */
    public Params toParams() {
        Params params = new Params();
        params.put("ParentId", parentid); //$NON-NLS-1$
        params.put("Inactive", inactive); //$NON-NLS-1$
        params.put("Season", season); //$NON-NLS-1$
        params.put("Episode", episode); //$NON-NLS-1$
        params.put("StartTime", Globals.utcFmt.format(startTime)); //$NON-NLS-1$
        params.put("EndTime", Globals.utcFmt.format(endTime)); //$NON-NLS-1$
        params.put("Inetref", inetref); //$NON-NLS-1$
        params.put("ChanId", chanid); //$NON-NLS-1$
        params.put("FindId", findid); //$NON-NLS-1$
        params.put("Type", type.json()); //$NON-NLS-1$
        params.put("SearchType", searchtype); //$NON-NLS-1$
        params.put("RecPriority", recpriority); //$NON-NLS-1$
        params.put("PreferredInput", preferredinput); //$NON-NLS-1$
        params.put("StartOffset", startoffset); //$NON-NLS-1$
        params.put("EndOffset", endoffset); //$NON-NLS-1$
        params.put("DupMethod", dupMethod.json()); //$NON-NLS-1$
        params.put("DupIn", dupIn.json()); //$NON-NLS-1$
        params.put("Filter", filter); //$NON-NLS-1$
        params.put("RecProfile", recProfile); //$NON-NLS-1$
        params.put("RecGroup", recGroup); //$NON-NLS-1$
        params.put("StorageGroup", storGroup); //$NON-NLS-1$
        params.put("PlayGroup", playGroup); //$NON-NLS-1$
        params.put("AutoExpire", autoexpire); //$NON-NLS-1$
        params.put("MaxEpisodes", maxepisodes); //$NON-NLS-1$
        params.put("MaxNewest", maxnewest); //$NON-NLS-1$
        params.put("AutoCommflag", autoflag); //$NON-NLS-1$
        params.put("AutoTranscode", autotranscode); //$NON-NLS-1$
        params.put("AutoMetaLookup", autometa); //$NON-NLS-1$
        params.put("AutoUserJob1", autojob1); //$NON-NLS-1$
        params.put("AutoUserJob2", autojob2); //$NON-NLS-1$
        params.put("AutoUserJob3", autojob3); //$NON-NLS-1$
        params.put("AutoUserJob4", autojob4); //$NON-NLS-1$
        params.put("Transcoder", transcoder); //$NON-NLS-1$
        return params;
    }

}
