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

package org.mythdroid.frontend;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import org.mythdroid.resource.Messages;
import org.mythdroid.util.LogUtil;

/** Send WakeOnLan packets */
public class WakeOnLan {

    static private byte[] addr;
    static private byte[] buf = new byte [17 * 6];

    /**
     * Send a wake on lan packet
     * @param hwaddr String containing the MAC address of the target
     */
    public static void Wake(String hwaddr) throws Exception {
        addr = parseAddr(hwaddr);
        for (int i = 0; i < 6; i++)
            buf[i] = (byte)0xff;
        for (int i = 6; i < buf.length; i += 6)
            System.arraycopy(addr, 0, buf, i, 6);
        LogUtil.debug(
            "Sending WOL packets to 255.255.255.255 " +  //$NON-NLS-1$
            "ports 7, 9 for MAC address " + hwaddr //$NON-NLS-1$
        );
        InetAddress address = InetAddress.getByName("255.255.255.255"); //$NON-NLS-1$
        DatagramPacket dgram = new DatagramPacket(buf, buf.length, address, 9);
        DatagramSocket sock = new DatagramSocket();
        sock.setBroadcast(true);
        sock.send(dgram);
        dgram.setPort(7);
        sock.send(dgram);
        sock.close();
    }

    private static byte[] parseAddr(String addr) throws IllegalArgumentException {
        byte[] bytes = new byte[6];
        String[] hex = addr.split(":"); //$NON-NLS-1$
        if (hex.length != 6)
            throw new IllegalArgumentException(
                Messages.getString("WakeOnLan.0")  //$NON-NLS-1$
            );
        try {
            for (int i = 0; i < 6; i++)
                bytes[i] = (byte)Integer.parseInt(hex[i], 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                Messages.getString("WakeOnLan.1") //$NON-NLS-1$
            );
        }
        return bytes;
    }

}
