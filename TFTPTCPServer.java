package tftp.tcp.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class TFTPTCPServer {

   public static void main(String[] args) throws IOException {
        int portNumber = 15000; //port number server is bound to
        ServerSocket masterSocket = new ServerSocket(portNumber);
        System.out.println("Server Started");
        
        //runs forever
        //accepts multiple clients by assigning a thread to each
        while (true) {
            Socket slaveSocket = masterSocket.accept();
            System.out.println("Connection from: " + slaveSocket.getInetAddress() + ", " + slaveSocket.getPort() + "...");
            new TFTPTCPServerThread(slaveSocket).start();
        }
    }
}
