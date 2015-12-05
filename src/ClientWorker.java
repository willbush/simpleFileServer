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
        try {
            runClientWorker();
            exit();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private void runClientWorker() throws IOException {
        int command;
        do {
            command = tryReadByte();
            performCommand(command);
        } while (!isExitCommand(command));
    }

    private void performCommand(int command) throws IOException {
        switch (command) {
            case 1:
                sendFileListToClient();
                break;
            case 2:
                handleFileRequest();
                break;
            case 3:
                removeFile();
                break;
            case 4:
                addFileToServer();
                break;
        }
    }

    private void sendFileListToClient() throws IOException {
        toClientStream.writeUTF(server.getServerFileList());
        toClientStream.flush();
    }

    private void handleFileRequest() throws IOException {
        int fileNum = tryReadByte();
        File file = server.getFile(fileNum);
        if (file == null) {
            writeByte(Server.Code.ERROR.getValue());
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

    private void removeFile() throws IOException {
        if (server.tryRemoveFile(tryReadByte()))
            toClientStream.writeByte(Server.Code.ALL_OK.getValue());
        else
            toClientStream.writeByte(Server.Code.ERROR.getValue());
    }

    private int tryReadByte() {
        try {
            return fromClientStream.readByte();
        } catch (IOException e) {
            System.err.println("A client exited improperly.");
        }
        return -1;
    }

    private void addFileToServer() throws IOException {
        final int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        String fileName = fromClientStream.readUTF();
        long fileSizeToRead = fromClientStream.readLong();
        File fileToAdd = new File(fileName);
        FileOutputStream fileOutputStream = new FileOutputStream(fileToAdd);

        int bytesRead;
        int readLen = (int) Math.min(buffer.length, fileSizeToRead);

        while (fileSizeToRead > 0 && (bytesRead = fromClientStream.read(buffer, 0, readLen)) != -1) {
            fileOutputStream.write(buffer, 0, bytesRead);
            fileSizeToRead -= bytesRead;
        }
        fileOutputStream.close();
        server.addFile(fileToAdd);
        writeByte(Server.Code.ALL_OK.getValue());
    }

    private void writeByte(int message) throws IOException {
        toClientStream.writeByte(message);
        toClientStream.flush();
    }

    private boolean isExitCommand(int command) {
        return command == Server.Code.EXIT.getValue() || command == Server.Code.ERROR.getValue();
    }

    private void exit() throws IOException {
        client.close();
        fromClientStream.close();
        toClientStream.close();
    }
}
