package org.mythdroid.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.mythdroid.resource.Messages;

/** Socket Utilities - mostly for managing muxed (encrypted) connections */
public class SocketUtil {
    
    private static boolean insecure = false;
    
    /** A Socket that will mux (and, if possible, encrypt) connections */
    public static class MuxedSocket extends CryptSocket {
        
        /**
         * Constructor
         * @param keys List of potential crypto keys
         * @param timeout Default socket timeout in milliseconds
         * @throws SocketException
         */
        public MuxedSocket(List<String> keys, int timeout)
            throws SocketException {
            super(keys);
            setTcpNoDelay(true);
            setSoTimeout(timeout);
        }
        
        @Override
        public void connect(SocketAddress addr, int timeout)
            throws IOException {
            InetSocketAddress iaddr = (InetSocketAddress)addr; 
            super.connect(
                new InetSocketAddress(iaddr.getAddress(), 16550),
                timeout
            );
            initMuxedConnection(this, iaddr.getPort());
        }
      
    }
    
    /** A plain old Socket that sets TCPNoDelay and a default timeout */
    public static class PlainSocket extends Socket {
        
        /**
         * Constructor
         * @param timeout default socket timeout in milliseconds
         * @throws SocketException
         */
        public PlainSocket(int timeout) throws SocketException {
            super();
            setTcpNoDelay(true);
            setSoTimeout(timeout);
        }
        
    }

    /**
     * Get a SchemeRegistry for doing HTTP with muxed connections
     * @param keys a List of potential crypto keys
     * @return a populated SchemeRegistry with appropriate socket factories
     */
    public static SchemeRegistry muxedSchemeRegistry(List<String> keys) {
        SchemeRegistry registry = new SchemeRegistry();
        registry.register(
            new Scheme(
                "http", new MuxedHTTPSocketFactory(keys), 80 //$NON-NLS-1$
            )
        );
        return registry;
    }
    
    /** A replacement for CipherInputStream, which insists on reading the
        number of bytes requested rather than the number available */
    private static class CryptInputStream extends FilterInputStream {
        
        private Cipher cipher = null;
        
        public CryptInputStream(InputStream is, Cipher c) {
            super(is);
            cipher = c;
        }
        
        @Override 
        public int read(byte[] buf, int off, int len) throws IOException {
            byte[] input = new byte[len];
            int ret = in.read(input, 0, len);
            if (ret == -1)
                return ret;
            try {
                return cipher.update(input, 0, ret, buf, off);
            } catch (ShortBufferException e) {
                return 0;
            }
        }
        
        @Override
        public long skip(long byteCount) throws IOException {
            byte[] buf = new byte[(int)byteCount];
            return read(buf, 0, (int)byteCount);
        }

        @Override
        public int available() throws IOException {
            return 0;
        }

        @Override
        public void close() throws IOException {
            in.close();
            try {
                cipher.doFinal();
            } catch (GeneralSecurityException ignore) {}
        }

        @Override
        public boolean markSupported() {
            return false;
        }
        
    }

    
    private static class CryptSocket extends Socket {
        
        private static class CryptParams {
            private byte[] key;
            private byte[] iv;

            public CryptParams(byte[] k, byte[] i) {
                key = k;
                iv  = i;
            }
        }
        
        private CryptInputStream   inputStream  = null;
        private CipherOutputStream outputStream = null;
        private List<String>       keys = null;
        private CryptParams        params = null;
        
        public CryptSocket(List<String> keys) {
            super();
            this.keys = keys;
        }
        
        @Override
        public void connect(SocketAddress addr, int timeout)
            throws IOException {
            super.connect(addr, timeout);
            // We shouldn't need auth/crypt if we come from localhost (via SSH)
            if (
                !insecure &&
                !((InetSocketAddress)addr).getAddress().isLoopbackAddress()
            )
                try {
                    params = authenticate();
                } catch (Exception e) {
                    close();
                    throw new IOException(e.getMessage());
                }
        }
        
        @Override
        public InputStream getInputStream() throws IOException {
            if (params == null)
                return super.getInputStream();
            if (inputStream != null)
                return inputStream;
            try {
                Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding"); //$NON-NLS-1$
                IvParameterSpec ivps = new IvParameterSpec(params.iv);
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(params.key, "AES"), ivps //$NON-NLS-1$
                );
                inputStream =
                    new CryptInputStream(super.getInputStream(), cipher);
                return inputStream;
            } catch (Exception e) {
                throw new IOException(e.getMessage());
            }
        }
        
        @Override
        public OutputStream getOutputStream() throws IOException {
            if (params == null)
                return super.getOutputStream();
            if (outputStream != null)
                return outputStream;
            try {
                Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding"); //$NON-NLS-1$
                IvParameterSpec ivps = new IvParameterSpec(params.iv);
                cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(params.key, "AES"), ivps //$NON-NLS-1$
                );
                outputStream =
                    new CipherOutputStream(super.getOutputStream(), cipher);
                return outputStream;
            } catch (Exception e) {
                throw new IOException(e.getMessage());
            }
        }
        
        private CryptParams authenticate()
            throws NoSuchAlgorithmException, NoSuchPaddingException,
                   InvalidKeyException, IOException, IllegalBlockSizeException,
                   BadPaddingException {
            
            byte[] nonce = new byte[16];
            
            int timeout = super.getSoTimeout();
            super.setSoTimeout(2000);
            int ret;
            try {
                ret = super.getInputStream().read(nonce, 0, 16);
            } catch (SocketTimeoutException e) {
                LogUtil.warn(
                    "No challenge from CMux, attempt insecure connection" //$NON-NLS-1$
                );
                LogUtil.warn(
                    "Sorry for the wait, you should enable crypt in MDD" //$NON-NLS-1$
                );
                insecure = true;
                return null;
            } finally {
                super.setSoTimeout(timeout);
            }
            if (ret != 16) {
                LogUtil.error("Received an invalid nonce"); //$NON-NLS-1$
                return null;
            }
                        
            if (keys == null || keys.size() == 0)
                throw new IOException(Messages.getString("SocketUtil.0")); //$NON-NLS-1$

            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding"); //$NON-NLS-1$
            
            byte[] resp  = new byte[2];
            byte[] correctKey = null;
            
            for (String key : keys) {
                byte[] k = keyToBytes(key);
                cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(k, "AES")); //$NON-NLS-1$
                byte[] enc = cipher.doFinal(nonce);
                super.getOutputStream().write(enc);
                super.getInputStream().read(resp, 0, 2);
                if (resp[0] == 'O' && resp[1] == 'K') {
                    correctKey = k;
                    break;
                }
            }
            
            if (correctKey == null)
                throw new IOException(Messages.getString("SocketUtil.1")); //$NON-NLS-1$
            return new CryptParams(correctKey, nonce);
        }
        
    }

    
    private static class MuxedHTTPSocketFactory implements SocketFactory {

        private List<String> keys = null;
        
        public MuxedHTTPSocketFactory(List<String> keys) {
            this.keys = keys;
        }
        
        @Override
        public Socket connectSocket(
            Socket sock, String host, int port, InetAddress localAddress,
            int localPort, HttpParams params
        ) throws IOException, UnknownHostException, ConnectTimeoutException {
            
            if (host == null)
                throw new IllegalArgumentException("host may not be null"); //$NON-NLS-1$
            if (params == null)
                throw new IllegalArgumentException("params may not be null"); //$NON-NLS-1$

            if (sock == null) createSocket();
            
            if (sock == null) throw new IOException();
            
            if ((localAddress != null) || (localPort > 0)) {

                // we need to bind explicitly
                if (localPort < 0)
                    localPort = 0; // indicates "any"

                InetSocketAddress isa =
                    new InetSocketAddress(localAddress, localPort);
                sock.bind(isa);
            }

            int timeout = HttpConnectionParams.getConnectionTimeout(params);
            InetSocketAddress rAddr = new InetSocketAddress(host, 16550);
            try {
                sock.connect(rAddr, timeout);
            } catch (SocketTimeoutException ex) {
                throw new ConnectTimeoutException(
                    Messages.getString("ConnMgr.3") + rAddr //$NON-NLS-1$
                );
            }
            sock.setTcpNoDelay(true);
            initMuxedConnection(sock, port);
            return sock;
            
        }

        @Override
        public Socket createSocket() throws IOException {
            return new CryptSocket(keys);
        }

        @Override
        public boolean isSecure(Socket sock) throws IllegalArgumentException {
            /* Possibly a lie, but we don't want the HttpClient to think it
               got an HTTPS connection when it didn't want one */ 
            return false;
        }
        
    }
    
    /**
     * Prepare a muxed connection
     * @param sock Socket to prepare
     * @param port The remote port we want to connect to
     * @throws IOException
     */
    private static void initMuxedConnection(Socket sock, int port)
        throws IOException {
        // Write the port number we want to the socket and get the response
        byte[] buf = new byte[512];
        sock.getOutputStream().write(String.valueOf(port).getBytes());
        sock.getInputStream().read(buf, 0, 2);
        if (buf[0] == 'O' && buf[1] == 'K')
            return;
        // There was a problem, read the rest of the err msg
        sock.getInputStream().read(buf, 2, 510);
        throw new IOException(new String(buf));
    }
  
    private static byte[] keyToBytes(String key) {
        byte[] k = new byte[16];
        for (int i = 0; i < 16; i++) {
            k[i] = (byte)Integer.parseInt(key.substring(i * 2, i * 2 + 2), 16);
        }
        return k;
    }
    
}
