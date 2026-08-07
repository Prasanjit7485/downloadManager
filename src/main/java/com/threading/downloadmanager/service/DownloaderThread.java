package com.threading.downloadmanager.service;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloaderThread implements Runnable {
    private final long start;
    private final long end;
    private final String link;
    private final String fileName;

    public DownloaderThread(long start, long end, String link, String fileName) {
        this.start = start;
        this.end = end;
        this.link = link;
        this.fileName = fileName;
    }

    @Override
    public void run()
    {
        try
        {
            System.out.println("Downloading " + Thread.currentThread().getName());
            URL url = new URL(link);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Range", "bytes=" + start + "-" + end + "-");
            con.connect();
            long total=end-start+1;
            long s=System.currentTimeMillis();
            try (InputStream in = con.getInputStream();
                 RandomAccessFile raf = new RandomAccessFile(fileName, "rw");) {
                raf.seek(start);
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    raf.write(buffer, 0, bytesRead);
                }
            }
            long e=System.currentTimeMillis();
            long speed=total/(s-e+1);
            speed/=(1024*1024);
            System.out.println("Completed "+ Thread.currentThread().getName()+" Speed "+speed);
            con.disconnect();
        }
        catch (IOException e)        {
            throw new RuntimeException(e);
        }
    }
}
