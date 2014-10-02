/**
 * Created by rolandius on 25.09.14.
 */

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;


public class HttpServer {
    private static final String DOCUMENT_ROOT = "./";
    private static final String SERVER_NAME = "rolandiuserv!";
    private static final String INDEX_FILE = "index.html";
    private static final Integer PORT = 80;
    private static final Integer WORKERS = 4; //Runtime.getRuntime().availableProcessors();

    private static class WorkQueue {
        private final PoolWorker[] threads;
        private final LinkedList<Runnable> queue;

        public WorkQueue(int nThreads) {
            queue = new LinkedList<>();
            threads = new PoolWorker[nThreads];

            for (int i=0; i<nThreads; i++) {
                threads[i] = new PoolWorker();
                threads[i].start();
            }
        }

        public void execute(Runnable r) {
            synchronized(queue) {
                queue.addLast(r);
                queue.notify();
            }
        }

        private class PoolWorker extends Thread {
            public void run() {
                Runnable runnable;

                while (true) {
                    synchronized(queue) {
                        while (queue.isEmpty()) {
                            try
                            {
                                queue.wait();
                            }
                            catch (InterruptedException ignored)
                            {
                            }
                        }
                        runnable = queue.removeFirst();
                    }

                    try {
                        runnable.run();
                    }
                    catch (RuntimeException e) {
                        System.out.print("Something is wrong in runtime :(");
                    }
                }
            }
        }
    }

    private static class SocketWork implements Runnable {

        private Socket socket;
        private InputStream in;
        private OutputStream out;

        public SocketWork(ServerSocket serverSocket) throws Throwable {
            this.socket = serverSocket.accept();
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
        }

        public void run() {
            try {
                String header = getHeader();

                String method = header.substring(0, header.indexOf(" "));
                if (!method.equals("GET") && !method.equals("HEAD")){
                    sendHeader(405, "", 0);
                }

                String path = DOCUMENT_ROOT + getURI(header);
                if (path.contains("../")){
                    sendHeader(403, "", 0);
                } else {

                    path = URLDecoder.decode(path, "UTF-8");
                    File file = new File(path);
                    if (!file.exists()){
                        sendHeader(404, "", 0);
                    } else {
                        byte isIndex = 0;
                        if (file.isDirectory()){
                            path += INDEX_FILE;
                            file = new File(path);
                            isIndex = 1;
                        }
                        if (isIndex == 1 && !file.exists()) {
                            sendHeader(403, "", 0);
                            out.flush();
                        } else {
                            String type[] = path.split("\\.");
                            sendHeader(200, contentType(type[type.length -1]), file.length());
                            if (method.equals("GET")) {
                                Path pathForRead = Paths.get(path);
                                byte[] byteArray = Files.readAllBytes(pathForRead);
                                out.write(byteArray);
                            }
                            out.flush();
                        }
                    }
                }
                socket.close();
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }

        private String contentType(String type) {
            switch (type) {
                case "html":
                    return "text/html";
                case "css":
                    return "text/css";
                case "js":
                    return "text/javascript";
                case "jpg":
                case "jpeg":
                    return "image/jpeg";
                case "png":
                    return "image/png";
                case "gif":
                    return "image/gif";
                case "swf":
                    return "application/x-shockwave-flash";
                default:
                    return "text/plain";
            }
        }

        private String getHeader() throws Throwable {
            StringBuilder header = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String string;
            while (!(string = reader.readLine()).isEmpty()) {
                header.append(string).append(System.lineSeparator());
            }
            return header.toString();
        }

        private String getURI(String header) {
            int start = header.indexOf("/");
            String uri = header.substring(start, header.indexOf(" ", start));

            int paramIndex;
            if ((paramIndex = uri.indexOf("?")) != -1) {
                uri = uri.substring(0, paramIndex);
            }
            return uri;
        }

        private void sendHeader(int code, String contentType, long length) throws Throwable{
            DateFormat dateFormat =
                    new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);

            String result =
                    "HTTP/1.1 " + code + codeType(code) + "\r\n"
                            + "Date: " + dateFormat.format(new Date()) + "\r\n"
                            + "Server: " + SERVER_NAME + "\r\n"
                            + "Content-Length: " + length + "\r\n"
                            + "Content-Type: " + contentType + "\r\n"
                            + "Connection: close\r\n\r\n";
            out.write(result.getBytes());
        }

        private String codeType(int code) {
            switch (code) {
                case 200:
                    return " OK";
                case 404:
                    return " Not Found";
                case 405:
                    return " Method Not Allowed";
                case 403:
                    return " Forbidden";
                default:
                    return " Internal Server Error";
            }
        }
    }

    public static void main(String[] args) throws Throwable {
        ServerSocket serverSocket = new ServerSocket(PORT);

        System.out.println("HI (:");
        System.out.printf("Available processors: %d \n", WORKERS);

        WorkQueue workQueue = new WorkQueue(WORKERS);

        while (true) {
            workQueue.execute(new SocketWork(serverSocket));
        }
    }
}
