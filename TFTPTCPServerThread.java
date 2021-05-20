package tftp.tcp.server;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
   

public class TFTPTCPServerThread extends Thread{

    private Socket slaveSocket = null;
    DataOutputStream dos = null;

    public TFTPTCPServerThread(Socket socket) throws IOException{
        super("TFTPUDPServerThread");
        this.slaveSocket = socket;
        this.dos = new DataOutputStream(slaveSocket.getOutputStream());
    }
    
    @Override
    public void run() {
        //runs forever unless error occurs
        try {
            InputStream is = slaveSocket.getInputStream();
            byte[] data = new byte[516];
            is.read(data);
            int opCode = data[1];//get opcode from client packet
            //is.close();
            //gets file name from request data
            int endIndex = 0;
            for(int i = 1;i<data.length-2;i++){
                if(data[i] == 0){
                    endIndex = i;
                    break;
                }
            }
            
            String fileName = new String(Arrays.copyOfRange(data,2,endIndex));
            switch (opCode) {
                case 1: //read request
                    System.out.println();
                    System.out.println("Read request received");
                    writeFile(new File("src\\tftp\\tcp\\server\\"+fileName));
                    break;
                case 2: //write request
                    System.out.println();
                    System.out.println("Write request received");
                    readFile(fileName);
                    break;

            }
        } catch (IOException e) {
            System.err.println(e);
        }
    }
    
    /**
     * Sends file to client
     * 
     * @param file file to write to client
     * @throws IOException 
     */ 
    private void writeFile(File file) throws IOException {
        //if file is not on the server error packet sent
        if(!file.exists()){
            sendError("File is not stored on the server".getBytes(), (byte) 1);
            System.out.println("File not on server");
        }

        else{
            Path path = Paths.get(file.getAbsolutePath());
            byte[] fileData = Files.readAllBytes(path); //converts file to byte array
            byte[] fileDataSub = fileData;
            boolean endOfFile = false; //end of file flag
            byte[] block = new byte[516]; //current block of data
            int blockNum = 1;
            
            while(!endOfFile){
                int endIndex = 0;
                //takes first 512 bytes of the file
                if (fileData.length > 512){
                    fileDataSub = Arrays.copyOfRange(fileData,0,512);
                    endIndex = 512;
                    block = new byte[516];

                }
                else{
                    fileDataSub = Arrays.copyOfRange(fileData,0,fileData.length);
                    endIndex = fileData.length;
                    block = new byte[fileData.length+4];
                    endOfFile = true;
                }

                //if both the block number bytes needs to be used
                if(blockNum >=255){ //resets block number of max value with two bytes is reached
                    blockNum = 0;
                    block[2] = (byte) 0;
                    block[3] = (byte) blockNum;
                }
                else if (blockNum > 127){
                    block[2] = (byte) (blockNum-127);
                    block[3] = (byte) 127;
                }
                else{
                    block[2] = (byte) 0;
                    block[3] = (byte) blockNum;
                }
                
                //file data for current block
                fileData = Arrays.copyOfRange(fileData,endIndex,fileData.length);

                //opcode added to data packet
                block[0]=0;
                block[1]=3;

                //add data block to current data packet
                for(int i = 4;i<fileDataSub.length+4;i++){
                    block[i] = fileDataSub[i-4];
                }

                //send data packet
                sendPacket(block);
                System.out.println("Data packet sent:"+blockNum);
              

                blockNum++;
            }
            System.out.println("File sent");
        }
    }
    
    /**
     * Takes data packets and saves file to program source folder 
     * 
     * @param fileName
     * @throws IOException
     */
    private void readFile(String fileName)throws IOException {
        boolean endOfFile = false; //end of file flag
        byte[] dataFile = new byte[0]; //array containing all file data
        //loops until file transfer complete
        while(!endOfFile){
            //receiving data packet
            while(true){
                try{
                    InputStream is = slaveSocket.getInputStream();
                    byte[] inputData = new byte[516];
                    int dataSize = is.read(inputData);
                    //get file data without opcode and block number
                    byte[] data = Arrays.copyOfRange(inputData,4,dataSize); 
                    dataFile = concatenateArrays(dataFile,data);

                    System.out.println("Block "+ (inputData[2] + inputData[3])+" received");

                    //check if end of file reached
                    if (data.length < 512){
                        endOfFile = true;
                    }
                    break;
                }
                catch(IOException e){
                    System.err.println(e);
                }
            }         
        }
        System.out.println("End of file reached");
        //saving file
        try (FileOutputStream fos = new FileOutputStream("src\\tftp\\tcp\\server\\"+fileName)) {
                fos.write(dataFile);
        }
        System.out.println("File saved: src\\tftp\\tcp\\server\\"+fileName);
    }
    
    /**
     * sends packet to the connected client socket
     * 
     * @param packet packet to send
     * @throws IOException 
     */
    private void sendPacket(byte[] packet) throws IOException{
        try{
            dos.write(packet);
        }catch(IOException e){
            System.err.println(e);
        }
    }
    
    /**
     * Sends appropriate error packet to client
     * 
     * @param message error message
     * @param code error code
     * @throws IOException 
     */
    private void sendError(byte[] message, byte code) throws IOException{
        byte[] error = concatenateArrays(new byte[]{0,5,0,code}, message);
        error = concatenateArrays(error,new byte[]{0});
        sendPacket(error);
    }
    
    /**
     * Concatenates the two arrays given
     * 
     * @param a first array
     * @param b second array
     * @return a and b arrays concatenated
     */
    private byte[] concatenateArrays(byte[] a, byte[] b){
        byte[] combined = new byte[a.length+b.length];
        
        int index = 0;
        for (byte element : a) {
            combined[index] = element;
            index++;
        }

        for (byte element : b) {
            combined[index] = element;
            index++;
        }
        return combined; 
    }
}