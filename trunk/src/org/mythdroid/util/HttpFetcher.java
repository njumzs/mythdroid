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
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.mythdroid.Globals;
import org.mythdroid.resource.Messages;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/** A utility class for fetching stuff via HTTP */
public class HttpFetcher {
    
    static private HttpClient defaultClient = null, muxedClient = null;
    
    private HttpClient client = null;
    private HttpResponse resp = null;
    private HttpEntity entity = null;
    
    
    /**
     * Fetch an image from the supplied uri, or the cache if we can
     * @param uri uri of image to fetch
     * @param muxed used muxed connections via CMux?
     * @return a Bitmap, or null upon error
     */
    public static Bitmap getImage(URI uri, boolean muxed) {
        
        Bitmap bm = Globals.artCache.get(uri.toString());
        if (bm != null) return bm;
               
        LogUtil.debug("Fetching image from " + uri.toString()); //$NON-NLS-1$

         try {
             bm = new HttpFetcher(uri, Globals.muxConns).getImage();
         } catch (IOException e) { 
             ErrUtil.logWarn(e);
         } catch (OutOfMemoryError e) { 
             ErrUtil.logWarn(e.getMessage());
         }
         
         if (bm != null) Globals.artCache.put(uri.toString(), bm);

         return bm;
         
    }
    
    /**
     * Constructor
     * @param muxed used muxed connections?
     */
    public HttpFetcher(boolean muxed) {
        
        if (muxed && muxedClient != null) {
            client = muxedClient;
            return;
        }
        if (!muxed && client != null) {
            client = defaultClient;
            return;
        }
        
        client = new DefaultHttpClient();
        ClientConnectionManager cmgr = client.getConnectionManager();
        HttpParams params = client.getParams().copy();
        SchemeRegistry registry =
            muxed ?
                SocketUtil.muxedSchemeRegistry(
                    DatabaseUtil.getKeys(Globals.appContext)
                ) :
                cmgr.getSchemeRegistry();
        
        HttpConnectionParams.setConnectionTimeout(params, 6000);
        HttpConnectionParams.setSoTimeout(params, 6000);
        
        client = new DefaultHttpClient(
            new ThreadSafeClientConnManager(params, registry), params
        );
        
        if (muxed)
            muxedClient = client;
        else
            defaultClient = client;

    }
    
    /**
     * Constructor, executes a GET request of the supplied url
     * @param url String containing url to fetch
     * @param muxed used muxed connections?
     * @throws ClientProtocolException
     * @throws IOException
     */
    public HttpFetcher(String url, boolean muxed)
        throws ClientProtocolException, IOException {
        this(muxed);
        get(URI.create(url));
    }
    
    /**
     * Constructor, executes a GET request of the supplied url
     * @param uri URI to fetch
     * @param muxed used muxed connections?
     * @throws ClientProtocolException
     * @throws IOException
     */
    public HttpFetcher(URI uri, boolean muxed) throws IOException {
        this(muxed);
        get(uri);
    }
    
    /**
     * Constructor, executes the supplied request
     * @param req request to execute
     * @param muxed used muxed connections?
     * @throws IOException
     */
    public HttpFetcher(HttpUriRequest req, boolean muxed)
        throws IOException, ClientProtocolException {
        this(muxed);
        request(req);
    }
    
    /**
     * Fetch a URL
     * @param uri URI to fetch
     * @throws IOException
     */
    public void get(URI uri) throws IOException {
        try {
            resp = client.execute(new HttpGet(uri));
            int code = resp.getStatusLine().getStatusCode(); 
            if (code != 200)
                throw new IOException(
                    Messages.getString("HttpFetcher.0") + code //$NON-NLS-1$
                );
        } catch (IOException e) {
            if (resp != null)
                if ((entity = resp.getEntity()) != null)
                    entity.consumeContent();
            entity = null;
            throw(e);
        }
            
        entity = resp.getEntity();
        
    }
    
    /**
     * Execute a HttpUriRequest
     * @param req HttpUriRequest to execute
     * @throws ClientProtocolException
     * @throws IOException
     */
    public void request(HttpUriRequest req) throws IOException {
        
        try {
            resp = client.execute(req);
            int code = resp.getStatusLine().getStatusCode(); 
            if (code != 200)
                throw new IOException(
                    Messages.getString("HttpFetcher.0") + code //$NON-NLS-1$
                );
        } catch (IOException e) {
            if (resp != null)
                if ((entity = resp.getEntity()) != null)
                    entity.consumeContent();
            entity = null;
            throw(e);
        }
        
        entity = resp.getEntity();
        
    }
    
    /**
     * Get an InputStream for reading the content
     * @return an InputStream
     * @throws IllegalStateException
     * @throws IOException
     */
    public InputStream getInputStream()
        throws IllegalStateException, IOException {
        if (entity == null)
            throw new IllegalStateException(
                Messages.getString("HttpFetcher.1") //$NON-NLS-1$
            );
        return entity.getContent();
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
