import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

public class Client {
    public enum ConsoleColor {
        GREEN("\u001B[32m"), RED("\u001B[31m"), BLUE("\u001B[34m"), CYAN("\u001B[36m"),
        PURPLE("\u001B[35m"), RESET("\u001B[0m");

        private final String ANSI_ESCAPE_CODE;

        ConsoleColor(String AnsiEscapeCode) {
            ANSI_ESCAPE_CODE = AnsiEscapeCode;
        }

        public String getAnsiCode() {
            return ANSI_ESCAPE_CODE;
        }
    }

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
        try {
            runClient();
            exit();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private void runClient() throws Exception {
        int command;
        do {
            printMenu();
            command = tryGetNextInt();
            performCommand(command);
        } while (!isExitCommand(command));
    }

    private void printMenu() {
        colorPrint(ConsoleColor.CYAN, "======\n");
        colorPrint(ConsoleColor.BLUE, menu);
        colorPrint(ConsoleColor.CYAN, "======\n");
        colorPrint(ConsoleColor.PURPLE, "Enter your the number of your choice:");
    }

    private int tryGetNextInt() {
        int result;
        try {
            result = scanner.nextInt();
        } catch (Exception e) {
            result = -1;
        }
        scanner.nextLine(); // clear /n out of scanner buffer
        return result;
    }

    private void performCommand(int command) throws Exception {
        switch (command) {
            case 1:
                printServerFileList();
                break;
            case 2:
                requestFile();
                break;
            case 3:
                removeFile();
                break;
            case 4:
                addFile();
                break;
        }
    }

    /*
    This method request a file from the server via the proper code and file number.
    The return code from the server is checked if the file was sent or not. If the file
    was sent, then it will be read from the stream into a file and saved locally.
     */
    private void requestFile() throws Exception {
        int fileNum = requestFileNumberFromUser();
        if (fileNum == 0) // user choose to go back to the menu.
            return;

        writeByteToServerStream(Server.Code.GET_FILE.getValue());
        writeByteToServerStream(fileNum);

        if (serverIsGoodToGo(fromServerStream.readByte())) {
            readServerFileIntoLocalFile();
            colorPrint(ConsoleColor.GREEN, "File saved to your local directory.\n");
        } else {
            colorPrint(ConsoleColor.RED, "The file you requested was not found.\n");
            requestFile();
        }
    }

    /*
    This method reads the file from the server stream into a file and saves it locally.
     */
    private void readServerFileIntoLocalFile() throws IOException {
        final int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        String fileName = fromServerStream.readUTF();
        long fileSizeToRead = fromServerStream.readLong();
        FileOutputStream fileOutputStream = new FileOutputStream(fileName);

        int bytesRead;
        int readLen = (int) Math.min(buffer.length, fileSizeToRead);

        while (fileSizeToRead > 0 && (bytesRead = fromServerStream.read(buffer, 0, readLen)) != -1) {
            fileOutputStream.write(buffer, 0, bytesRead);
            fileSizeToRead -= bytesRead;
        }
        fileOutputStream.close();
    }

    /*
    This method request the file number from the user and validates it's not negative.
    However, notice the branch back to the menu. It is therefore important to make sure the
    "toServer" and "fromServer" streams are when calling this method.
     */
    private int requestFileNumberFromUser() throws IOException {
        int fileNum;
        do {
            printServerFileList();
            colorPrint(ConsoleColor.PURPLE, "Enter 0 to go back or the number of the file:");
            fileNum = tryGetNextInt();
        } while (fileNum < 0);

        return fileNum;
    }

    private void printServerFileList() throws IOException {
        writeByteToServerStream(Server.Code.PRINT.getValue());
        colorPrint(ConsoleColor.CYAN, "======\n");
        System.out.print(fromServerStream.readUTF());
        colorPrint(ConsoleColor.CYAN, "======\n");
    }

    /*
    This method gets the file number to remove from the user, sends the proper code to the server,
    and then sends the file number to the server for it to remove. The server will respond with a
    status code on if the file was removed or not.
     */
    private void removeFile() throws Exception {
        colorPrint(ConsoleColor.PURPLE, "Please enter the file number to remove:");
        int fileNumToRemove = tryGetNextInt();
        writeByteToServerStream(Server.Code.REMOVE_FILE.getValue());
        writeByteToServerStream(fileNumToRemove);

        if (!serverIsGoodToGo(fromServerStream.readByte()))
            colorPrint(ConsoleColor.RED, "File not found.\n");
        else
            colorPrint(ConsoleColor.GREEN, "File successfully removed from the server.\n");
    }

    private boolean serverIsGoodToGo(int code) throws Exception {
        if (code == Server.Code.ERROR.getValue())
            return false;
        if (code != Server.Code.ALL_OK.getValue())
            throw new InvalidServerCodeException("Received invalid server code in the client.");

        return true;
    }

    /*
    This method gets the file path from the user and validates the file exists. It then sends the
    proper code to the server, and then sends the file to the server. The server will respond with
    a status code on if the file was successfully added.
     */
    private void addFile() throws IOException {
        File file = requestFileFromUser();
        if (file == null) // user choose to go back to the menu
            return;

        byte[] buffer = new byte[(int) file.length()];
        DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
        in.readFully(buffer, 0, buffer.length);
        in.close();

        toServerStream.writeByte(Server.Code.ADD_FILE.getValue());
        toServerStream.writeUTF(file.getName());
        toServerStream.writeLong(buffer.length);
        toServerStream.write(buffer, 0, buffer.length);
        toServerStream.flush();

        if (fromServerStream.readByte() == Server.Code.ALL_OK.getValue())
            colorPrint(ConsoleColor.GREEN, "File added to the server successfully.\n");
        else
            throw new IOException("File write to the server failed.");
    }

    /*
    This method request the file path from the user and validates it. However, notice the branch
    back to the menu. It is therefore important to make sure the "toServer" and "fromServer"
    streams are when calling this method.
     */
    private File requestFileFromUser() throws IOException {
        String requestMessage = "Please enter 'back' to go to the main menu or reenter the file path:";
        colorPrint(ConsoleColor.PURPLE, requestMessage);
        String path = scanner.nextLine();
        File file = new File(path);

        while (!file.exists()) {
            if (path.trim().equals("back"))
                return null;

            colorPrint(ConsoleColor.RED, "File not found.\n");
            colorPrint(ConsoleColor.PURPLE, requestMessage);
            path = scanner.nextLine();
            file = new File(path);
        }
        return file;
    }

    private void colorPrint(ConsoleColor color, String message) {
        System.out.print(color.getAnsiCode() + message + ConsoleColor.RESET.getAnsiCode());
    }

    private boolean isExitCommand(int command) {
        return command == Server.Code.EXIT.getValue();
    }

    private void exit() throws IOException {
        writeByteToServerStream(Server.Code.EXIT.getValue());
        fromServerStream.close();
        toServerStream.close();
        socket.close();
    }

    private void writeByteToServerStream(int command) throws IOException {
        toServerStream.writeByte(command);
        toServerStream.flush();
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
