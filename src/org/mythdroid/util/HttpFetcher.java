package org.mythdroid.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpParams;
import org.mythdroid.resource.Messages;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/** A utility class for fetching stuff via HTTP */
public class HttpFetcher {
    
    private HttpResponse resp = null;
    private HttpEntity entity = null;
    private HttpClient client = null;
    
    /** Constructor */
    public HttpFetcher() {
        client = new DefaultHttpClient();
        ClientConnectionManager cmgr = client.getConnectionManager();
        HttpParams params = client.getParams();
        client = new DefaultHttpClient(
            new ThreadSafeClientConnManager(params, cmgr.getSchemeRegistry()),
            params
        );
    }
    
    /**
     * Constructor, executes a GET request of the supplied url
     * @param url String containing url to fetch
     * @throws ClientProtocolException
     * @throws IOException
     */
    public HttpFetcher(String url) throws ClientProtocolException, IOException {
        client = new DefaultHttpClient();
        get(URI.create(url));
    }
    
    /**
     * Constructor, executes a GET request of the supplied url
     * @param uri URI to fetch
     * @throws ClientProtocolException
     * @throws IOException
     */
    public HttpFetcher(URI uri) throws IOException {
        client = new DefaultHttpClient();
        get(uri);
    }
    
    /**
     * Fetch a URL
     * @param uri URI to fetch
     * @throws ClientProtocolException
     * @throws IOException
     */
    public void get(URI uri) throws IOException {
        
        resp = client.execute(new HttpGet(uri));
        int code = resp.getStatusLine().getStatusCode(); 
        if (code != 200) 
            throw new IOException(Messages.getString("HttpFetcher.0") + code); //$NON-NLS-1$
        entity = resp.getEntity();

    }
    
    /**
     * Execute a HttpUriRequest
     * @param req HttpUriRequest to execute
     * @throws ClientProtocolException
     * @throws IOException
     */
    public void fetch(HttpUriRequest req)
        throws ClientProtocolException, IOException {
        
        resp = client.execute(req);
        int code = resp.getStatusLine().getStatusCode(); 
        if (code != 200) 
            throw new IOException(Messages.getString("HttpFetcher.0") + code); //$NON-NLS-1$
        entity = resp.getEntity();
        
    }
    
    /**
     * Get the content of the url as a String
     * @return String containing the content of the url
     * @throws IOException
     */
    public String getContent() throws IOException {
        
        if (entity == null)
            throw new IllegalStateException(
                Messages.getString("HttpFetcher.1") //$NON-NLS-1$
            );
        
        InputStream st = null;
        try {
            st = entity.getContent();
        } catch (IllegalStateException e) {
            ErrUtil.logErr(e);
            return null;
        }
        
        if (st == null) return null;
        
        int len = 
            Integer.parseInt(resp.getLastHeader("Content-Length").getValue()); //$NON-NLS-1$
        
        final byte[] buf = new byte[len];
        int read = 0;
        while (read < len)
            read += st.read(buf, read, len);
        
        st.close();
        entity.consumeContent();
        entity = null;
        resp   = null;
        return new String(buf);
        
    }
    
    /**
     * Get an image from the url
     * @return Bitmap 
     * @throws IOException
     */
    public Bitmap getImage() throws IOException {
        
        if (entity == null)
            throw new IllegalStateException(
                Messages.getString("HttpFetcher.1") //$NON-NLS-1$
            );
        
        final InputStream is = new BufferedHttpEntity(entity).getContent();
        final BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled = false;
        final Bitmap bm = BitmapFactory.decodeStream(is, null, opts);
        is.close();
        entity.consumeContent();
        entity = null;
        resp   = null;
        return bm;
        
    }
    
    /**
     * Write the content of the url to an OutputStream
     * @param output OutputStream to write to
     * @throws IOException
     */
    public void writeTo(OutputStream output) throws IOException {
        
        if (entity == null)
            throw new IllegalStateException(
                Messages.getString("HttpFetcher.1") //$NON-NLS-1$
            );
        
        entity.writeTo(output);
        entity.consumeContent();
        entity = null;
        
    }

}
