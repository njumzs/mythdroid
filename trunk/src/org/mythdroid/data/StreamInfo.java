package org.mythdroid.data;

import org.json.JSONException;
import org.json.JSONObject;

/** Information about a HTTP Live Stream */
public class StreamInfo {
    
    @SuppressWarnings("javadoc")
    public int id, width, height, vb, ab, numSegments, curSegment,
               percentComplete;
    
    @SuppressWarnings("javadoc")
    public String url, status, statusMsg; 
    
    /** Construct from a "LiveStreamInfo" JSONObject */
    public StreamInfo(JSONObject jo) throws JSONException {
        id              = jo.getInt("Id"); //$NON-NLS-1$
        width           = jo.getInt("Width"); //$NON-NLS-1$
        height          = jo.getInt("Height"); //$NON-NLS-1$
        vb              = jo.getInt("Bitrate"); //$NON-NLS-1$
        ab              = jo.getInt("AudioBitrate"); //$NON-NLS-1$
        numSegments     = jo.getInt("SegmentCount"); //$NON-NLS-1$
        curSegment      = jo.getInt("CurrentSegment"); //$NON-NLS-1$
        percentComplete = jo.getInt("PercentComplete"); //$NON-NLS-1$
        url             = jo.getString("FullURL"); //$NON-NLS-1$
        status          = jo.getString("StatusStr"); //$NON-NLS-1$
        statusMsg       = jo.getString("StatusMessage"); //$NON-NLS-1$
    }

}
