package tftp.tcp.client;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;

public class TFTPTCPClient {

    protected Socket socket = null;
    DataOutputStream dos = null;

    public TFTPTCPClient(String address, int port) throws IOException{
        this.socket = new Socket(address,port);
        this.dos = new DataOutputStream(socket.getOutputStream());
    }
    
    public static void main(String[] args) throws IOException, FileNotFoundException, InterruptedException{
        String address = args[0];
        int port = Integer.parseInt(args[1]);
        
        if (args.length != 2) {
            System.err.println("Usage: java TFTPTCPClient <address> <port>");
            System.exit(1);
        }
        
        int choice = 0; //user menu choice
        Scanner input = new Scanner(System.in);
        while(true){
            boolean valid = false; //valid input flag
            do {
                input = new Scanner(System.in);
                try{
                    //menu
                    System.out.println();
                    System.out.println("1 - Write file to server");
                    System.out.println("2 - Read file from server");
                    System.out.println("3 - Exit");
                    choice = input.nextInt();
                    if (choice == 1 || choice == 2 || choice == 3){
                        valid = true;
                        //exit program if user quits
                        if (choice == 3){
                            System.exit(0);
                        }
                    }
                    else{
                        throw new Exception();
                    }
                }
                //invalid user input handling
                catch(Exception e){
                    System.out.println();
                    System.out.println("Only enter 1, 2 or 3");
                }
            } while (!valid);

            valid = false; //valid file entered
            String fileName = "";
            File file = null;
            do {
                try{
                    input = new Scanner(System.in);
                    System.out.println("Enter file name with extension");
                    fileName = input.nextLine();
                    if (choice == 1){
                        file = new File("src\\tftp\\tcp\\client\\"+fileName);
                        if (!Files.exists(Paths.get(file.getAbsolutePath()))){
                            throw new FileNotFoundException();
                        }  
                    }
                    valid = true;
                }
                catch(Exception e){
                    System.out.println();
                    System.out.println("File not found");
                }
            } while (!valid);

            TFTPTCPClient client = new TFTPTCPClient(address,port);
            byte[] request = null;
            switch (choice) {
                case 1: //write file to server (send request)
                    request = client.makeRequest((byte) 2, file.getName());
                    client.sendPacket(request);
                    System.out.println("WRQ sent");
                    System.out.println("");
                    System.out.println("Sending file... "+file.getName());
                    client.writeFile(file);
                    break;
                case 2: //read file from server
                    request = client.makeRequest((byte) 1, fileName);
                    client.sendPacket(request);
                    System.out.println("RRQ sent");
                    client.readFile(fileName);
                    break;
            }
        }
        
        }
    
    
    /**
     * 
     * Sends file to server
     * 
     * @param file file to write to client
     * @throws IOException 
     */ 
    private void writeFile(File file) throws IOException , InterruptedException{
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
    
    
    /**
     * sends packet to the server
     * 
     * @param packet packet data to send
     * @throws IOException 
     */
    private void sendPacket(byte[] packet) throws IOException, InterruptedException{
        try{
            Thread.sleep(10); //to prevent packet loss error that was occuring for some reason
            dos.write(packet);
        }catch(IOException e){
            System.err.println(e);
        }
    }

     /**
     * Takes data packets and saves file to program source folder 
     * 
     * @param fileName
     * @throws IOException
     */
    private void readFile(String fileName) throws IOException {
        boolean endOfFile = false; //end of file flag
        byte[] dataFile = new byte[0]; //array containing all file data
        //loops until file transfer complete
        while(!endOfFile){
            //receiving data packet
            while(true){
                try{
                    InputStream is = socket.getInputStream();
                    byte[] inputData = new byte[516];
                    int dataSize = is.read(inputData);

                    //error handling 
                    if(inputData[1] == 5){
                        errorHandling(inputData);
                    }
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
        try (FileOutputStream fos = new FileOutputStream("src\\tftp\\tcp\\client\\"+fileName)) {
                fos.write(dataFile);
        }
        System.out.println("File saved: src\\tftp\\tcp\\client\\"+fileName);
    }
    
    
    /**
     * Handles error packet from server
     * 
     * @param data packet data with opcode 5 (error packet)
     * @throws IOException
     */
    private void errorHandling(byte[] data) throws IOException{
        System.out.println();
        String message = new String(Arrays.copyOfRange(data,4,data.length));
        System.err.println(message);
        socket.close();
        System.exit(0);
    }
    
    /**
     * Creates read/write request packet
     * 
     * @param opCode read or write opcode
     * @param fileName 
     * @return request packet ready to be sent
     */
    private byte[] makeRequest(byte opCode, String fileName){
        byte[] request = new byte[9 + fileName.length()];
        int index = 0;
        String mode = "octet";

        request[index] = 0;
        index++;

        request[index] = opCode;
        index++;

        for(int i = 0; i<fileName.length();i++){
            request[index] = (byte) fileName.charAt(i);
            index++;
        }

        request[index] = 0;
        index++;

        for(int i = 0; i < mode.length();i++){
            request[index] = (byte) mode.charAt(i);
            index++;
        }

        request[index] = 0;
        return request;
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