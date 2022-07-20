package mtb.assistant.balance.services;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import mtb.assistant.balance.views.DataFragment;

public class UdpClientThread extends Thread {
  private final DataFragment.UdpClientHandler handler;
  private DatagramSocket socket = null;
  private DatagramPacket receivePacket = null;
  private boolean isThreadRunning = false;
  private final int bufferLength;
  private final byte[] receiveByte;
  private Thread clientThread;

  public UdpClientThread(DataFragment.UdpClientHandler handler) {
    super();
    this.handler = handler;
    this.bufferLength = 1024;
    this.receiveByte = new byte[this.bufferLength];
  }

  public void startUDPSocket() {
    if (this.socket != null) return;
    try {
      this.socket = new DatagramSocket(18600, InetAddress.getByName("0.0.0.0"));
      if (this.receivePacket == null) {
        this.receivePacket = new DatagramPacket(this.receiveByte, this.bufferLength);
      }
      this.startSocketThread();
    } catch (SocketException | UnknownHostException e) {
      e.printStackTrace();
    }
  }

  private void startSocketThread() {
    this.clientThread = new Thread(UdpClientThread.this::receiveMessage);
    this.isThreadRunning = true;
    this.clientThread.start();
  }

  private void receiveMessage() {
    while (this.isThreadRunning) {
      try {
        this.socket.receive(this.receivePacket);
        if (this.receivePacket == null || this.receivePacket.getLength() == 0)
          continue;
        String strReceive = new String(this.receivePacket.getData(), this.receivePacket.getOffset(), this.receivePacket.getLength());
        this.handler.sendMessage(this.handler.obtainMessage(1, strReceive));
      } catch (IOException e) {
        stopUDPSocket();
        e.printStackTrace();
        return;
      }
    }
  }

  public void stopUDPSocket() {
    this.isThreadRunning = false;
    this.receivePacket = null;
    this.clientThread.interrupt();
    if (this.socket != null) {
      this.socket.close();
      this.socket = null;
    }
  }
}
