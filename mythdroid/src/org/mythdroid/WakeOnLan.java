package org.mythdroid;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import android.util.Log;

public class WakeOnLan {

    private byte[] addr;
    private byte[] buf = new byte [17 * 6];
    
    public WakeOnLan(String hwaddr) throws Exception {
        addr = parseAddr(hwaddr);
        for (int i = 0; i < 6; i++) 
            buf[i] = (byte)0xff;
        for (int i = 6; i < buf.length; i += 6)
            System.arraycopy(addr, 0, buf, i, 6);
        if (MythDroid.debug) 
            Log.d(
                "WakeOnLAN", 
                "Sending WOL packets to 255.255.255.255 " + 
                "ports 7, 9 for MAC address " + hwaddr
            );
        InetAddress address = InetAddress.getByName("255.255.255.255");
        DatagramPacket dgram = new DatagramPacket(buf, buf.length, address, 9);
        DatagramSocket sock = new DatagramSocket();
        sock.setBroadcast(true);
        sock.send(dgram);
        dgram.setPort(7);
        sock.send(dgram);
        sock.close();
    }
    
    private byte[] parseAddr(String addr) throws IllegalArgumentException {
        byte[] bytes = new byte[6];
        String[] hex = addr.split(":");
        if (hex.length != 6) 
            throw new IllegalArgumentException("Invalid MAC address");
        try {
            for (int i = 0; i < 6; i++)
                bytes[i] = (byte)Integer.parseInt(hex[i], 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Invalid hex digit in MAC address"
            );
        }
        return bytes;
    }
    
}
