import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashSet;

public class Server {
    public enum Code {
        ERROR(-1), ALL_OK(0), PRINT(1), GET_FILE(2), REMOVE_FILE(3), ADD_FILE(4), EXIT(5);

        private final int value;

        Code(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    private static final String SERVER_FILES_DIR = "./ServerFiles";
    private final ArrayList<File> serverFiles = new ArrayList<File>();
    // HashSet allows O(1) access to check if the set already contains a file before adding it.
    private final HashSet<Integer> fileHashes = new HashSet<Integer>();
    private ServerSocket socket;

    public Server(int port) {
        File folder = new File(SERVER_FILES_DIR);
        if (folder.exists())
            initializeFiles(folder.listFiles());
        else {
            System.err.println("'ServerFiles' directory not found.");
            System.err.println("The server uses the 'ServerFiles' directory to initialize files.");
            System.err.println("Please ensure there is a directory named 'ServerFiles' in the " +
                    "same directory from which you are running the server.");
            System.exit(-1);
        }

        try {
            socket = new ServerSocket(port);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }

    // Adds all files in the server serverFiles directory including subdirectory files.
    private void initializeFiles(File[] files) {
        for (File file : files)
            if (file.isDirectory())
                initializeFiles(file.listFiles());
            else
                add(file);
    }

    public synchronized void addFile(File file) {
        String fileName = file.getName();

        if (!fileHashes.contains(file.hashCode())) {
            add(file);
            System.out.println(fileName + " added to the server.");
        } else
            System.out.println(fileName + " is already on the server.");
    }

    private void add(File file) {
        serverFiles.add(file);
        fileHashes.add(file.hashCode());
    }

    public void run() {
        try {
            spawnClientWorkersUponClientConnection();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }

    /*
    The socket accept method blocks until a client connections. The server runs until
    the user sends the Control-C "SIGINT" program interrupt signal.
     */
    private void spawnClientWorkersUponClientConnection() throws IOException {
        System.out.println("Server running on " + socket.getLocalPort() + ".");
        System.out.println("Press Control-C to stop the server.");

        while (true) {
            Thread t = new Thread(new ClientWorker(socket.accept(), this));
            t.start();
        }
    }

    public synchronized String getServerFileList() {
        String serverFileList = "";

        for (int i = 0; i < serverFiles.size(); i++)
            serverFileList += (i + 1) + ". " + serverFiles.get(i).getName() + "\n";

        return serverFileList;
    }

    public synchronized File getFile(int fileNumber) {
        if (isInBounds(fileNumber))
            return serverFiles.get(fileNumber - 1);
        else
            return null;
    }

    public synchronized boolean tryRemoveFile(int fileNumber) {
        if (isInBounds(fileNumber)) {
            File file = serverFiles.remove(fileNumber - 1);
            fileHashes.remove(file.hashCode());
            return true;
        }
        return false;
    }

    private boolean isInBounds(int fileNumber) {
        return fileNumber > 0 && fileNumber <= serverFiles.size();
    }

    public static void main(String args[]) {
        if (args.length != 1) {
            System.err.println("Please provide the port number as an argument.");
            System.err.println("Example: java Server 5000");
            System.exit(1);
        }
        int port = Integer.valueOf(args[0]);
        Server s = new Server(port);
        s.run();
    }
}

class InvalidServerCodeException extends RuntimeException {
    public InvalidServerCodeException(String message) {
        super(message);
    }
}
