import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

public class Client {
    private Socket socket;
    private DataOutputStream toServerStream;
    private DataInputStream fromServerStream;
    private Scanner scanner;
    private final static String menu = "1. Display the names of all files\n" +
            "2. Get file\n" +
            "3. Remove a file.\n" +
            "4. Add a file.\n" +
            "5. exit\n";

    public Client(String host, int port) {
        scanner = new Scanner(System.in);

        try {
            socket = new Socket(host, port);
            toServerStream = new DataOutputStream(socket.getOutputStream());
            fromServerStream = new DataInputStream(socket.getInputStream());
        } catch (UnknownHostException e) {
            System.err.println(e.getMessage());
            System.err.println("Unknown Host: " + host);
            System.exit(1);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.err.println("Please ensure your server is running.");
            System.exit(1);
        }
    }

    public void start() {
        int command;
        do {
            printMenu();
            command = tryGetNextInt();
            performCommand(command);
        } while (!isExitCommand(command));

        exit();
    }

    private void printMenu() {
        System.out.println("======");
        System.out.print(menu);
        System.out.println("======");
        System.out.print("Enter your the number of your choice:");
    }

    private int tryGetNextInt() {
        int result;
        try {
            result = scanner.nextInt();
        } catch (Exception e) {
            result = -1;
        }
        scanner.nextLine(); // clear scanner buffer
        return result;
    }

    private void performCommand(int command) {
        switch (command) {
            case 1:
                tryPrintServerFileList();
                break;
            case 2:
                tryRequestFile();
                break;
            case 3:
                tryRemoveFile();
                break;
            case 4:
                tryAddFile();
                break;
        }
    }

    private void tryRequestFile() {
        try {
            requestFile();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }

    private void requestFile() throws Exception {
        int fileNum = requestFileNumberFromUser();

        tryWriteByteToServerStream(Server.Code.GET_FILE.getValue());
        tryWriteByteToServerStream(fileNum);
        int serverCode = fromServerStream.readByte();

        if (serverIsGoodToGo(serverCode))
            readFile();
        else {
            System.out.println("The file you requested was not found.");
            requestFile();
        }
    }

    private boolean serverIsGoodToGo(int code) throws Exception {
        if (code == Server.Code.ERROR.getValue())
            return false;
        if (code != Server.Code.ALL_OK.getValue())
            throw new InvalidServerCodeException("Received invalid server code in the client.");

        return true;
    }

    private void readFile() throws IOException {
        String fileName = fromServerStream.readUTF();
        long fileSizeToRead = fromServerStream.readLong();
        FileOutputStream fileOutputStream = new FileOutputStream(fileName);
        byte[] buffer = new byte[1024];

        int bytesRead;
        int readLen = (int) Math.min(buffer.length, fileSizeToRead);

        while (fileSizeToRead > 0 && (bytesRead = fromServerStream.read(buffer, 0, readLen)) != -1) {
            fileOutputStream.write(buffer, 0, bytesRead);
            fileSizeToRead -= bytesRead;
        }
        fileOutputStream.close();
        printGreenText("File saved to your local directory.");
    }

    private void printGreenText(String message) {
        String green = "\u001B[32m";
        String reset = "\u001B[0m";
        System.out.println(green + message + reset);
    }

    private int requestFileNumberFromUser() {
        int fileNum;
        do {
            tryPrintServerFileList();
            System.out.print("Enter the number of the file:");
            fileNum = tryGetNextInt();
        } while (fileNum < 1);

        return fileNum;
    }

    private void tryPrintServerFileList() {
        tryWriteByteToServerStream(Server.Code.PRINT.getValue());

        try {
            System.out.println("======");
            System.out.print(fromServerStream.readUTF());
            System.out.println("======");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void tryWriteByteToServerStream(int command) {
        try {
            toServerStream.writeByte(command);
            toServerStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isExitCommand(int command) {
        return command == 5;
    }

    private void tryRemoveFile() {
        System.out.print("Please enter the file number to remove:");
        int fileNumToRemove = tryGetNextInt();
        tryWriteByteToServerStream(Server.Code.REMOVE_FILE.getValue());
        tryWriteByteToServerStream(fileNumToRemove);
    }

    private void tryAddFile() {
        try {
            addFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addFile() throws IOException {
        File file = requestFileFromUser();
        byte[] buffer = new byte[(int) file.length()];
        DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
        in.readFully(buffer, 0, buffer.length);
        in.close();

        toServerStream.writeByte(4); // add file command
        toServerStream.writeUTF(file.getName());
        toServerStream.writeLong(buffer.length);
        toServerStream.write(buffer, 0, buffer.length);
        toServerStream.flush();

        if (fromServerStream.readByte() == Server.Code.ALL_OK.getValue())
            printGreenText("File added to the server successfully.");
        else
            throw new IOException("File write to the server failed.");
    }

    private File requestFileFromUser() {
        System.out.print("Enter the path to the file you want to add:");
        String path = scanner.nextLine();
        File file = new File(path);

        while (!file.exists()) {
            if (path.equals("back"))
                start();

            System.out.println("File not found.");
            System.out.print("Please enter 'back' to go to the main menu or reenter the file path:");
            path = scanner.nextLine();
            file = new File(path);
        }
        return file;
    }

    private void exit() {
        try {
            tryWriteByteToServerStream(Server.Code.EXIT.getValue());
            fromServerStream.close();
            toServerStream.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String args[]) {
        if (args.length != 2) {
            System.err.println("Please provide the hostname and port number as arguments.");
            System.err.println("Example: java Client localhost 5000");
            System.exit(1);
        }
        String host = args[0];
        int port = Integer.valueOf(args[1]);

        Client c = new Client(host, port);
        c.start();
    }
}
