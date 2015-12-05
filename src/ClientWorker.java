import java.io.*;
import java.net.Socket;

public class ClientWorker implements Runnable {
    private DataOutputStream toClientStream;
    private DataInputStream fromClientStream;
    private final Server server;
    private final Socket client;

    public ClientWorker(Socket client, Server server) {
        this.client = client;
        this.server = server;

        try {
            toClientStream = new DataOutputStream(client.getOutputStream());
            fromClientStream = new DataInputStream(client.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        int command;
        do {
            command = tryReadByte();
            performCommand(command);
        } while (!isExitCommand(command));

        exit();
    }

    private int tryReadByte() {
        try {
            return fromClientStream.readByte();
        } catch (IOException e) {
            System.err.println("A client exited improperly.");
        }
        return -1;
    }

    private void performCommand(int command) {
        switch (command) {
            case 1:
                trySendFileListToClient();
                break;
            case 2:
                tryHandleFileRequest();
                break;
            case 3:
                server.removeFile(tryReadByte());
                break;
            case 4:
                tryAddFileToServer();
                break;
        }
    }

    private void trySendFileListToClient() {
        try {
            toClientStream.writeUTF(server.getServerFileList());
            toClientStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void tryHandleFileRequest() {
        try {
            handleFileRequest();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleFileRequest() throws IOException {
        int fileNum = tryReadByte();
        File file = server.getFile(fileNum);
        if (file == null) {
            tryWriteByte(Server.Code.ERROR.getValue());
            return;
        }

        byte[] buffer = new byte[(int) file.length()];
        DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
        in.readFully(buffer, 0, buffer.length);
        in.close();

        toClientStream.writeByte(Server.Code.ALL_OK.getValue());
        toClientStream.writeUTF(file.getName());
        toClientStream.writeLong(buffer.length);
        toClientStream.write(buffer, 0, buffer.length);
        toClientStream.flush();
    }

    private void tryWriteByte(int message) {
        try {
            toClientStream.writeByte(message);
            toClientStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isExitCommand(int command) {
        return command == 5 || command == -1;
    }

    private void tryAddFileToServer() {
        try {
            addFileToServer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addFileToServer() throws IOException {
        String fileName = fromClientStream.readUTF();
        long fileSizeToRead = fromClientStream.readLong();
        File fileToAdd = new File(fileName);
        FileOutputStream fileOutputStream = new FileOutputStream(fileToAdd);
        byte[] buffer = new byte[1024];

        int bytesRead;
        int readLen = (int) Math.min(buffer.length, fileSizeToRead);

        while (fileSizeToRead > 0 && (bytesRead = fromClientStream.read(buffer, 0, readLen)) != -1) {
            fileOutputStream.write(buffer, 0, bytesRead);
            fileSizeToRead -= bytesRead;
        }
        fileOutputStream.close();
        server.addFile(fileToAdd);
        tryWriteByte(Server.Code.ALL_OK.getValue());
    }

    private void exit() {
        try {
            client.close();
            fromClientStream.close();
            toClientStream.close();
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }
}
