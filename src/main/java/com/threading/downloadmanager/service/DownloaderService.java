package com.threading.downloadmanager.service;

import com.threading.downloadmanager.entity.DownloaderTask;
import com.threading.downloadmanager.enums.DownloadStatus;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DownloaderService
{
    ConcurrentHashMap<String, DownloaderTask> activeDownloads = new ConcurrentHashMap<>();
    public void startDownloading(String link) throws IOException
    {
        URL url=new URL(link);
        HttpURLConnection con=(HttpURLConnection)url.openConnection();
        DownloaderTask task;
        long downloadedBytes =0;
        if(activeDownloads.containsKey(link))
        {
            task=activeDownloads.get(link);
            downloadedBytes =task.getDownloadedSize();
            if(task.getDownloadStatus()==DownloadStatus.COMPLETED) return;
            task.setDownloadStatus(DownloadStatus.RESUMED);
        }
        else
        {
            task=new DownloaderTask();
            task.setUrl(link);
            task.setDownloadStatus(DownloadStatus.RESUMED);
            activeDownloads.put(link,task);
        }
        con.setRequestMethod("GET");
        con.setRequestProperty("Range","bytes="+ downloadedBytes +"-");
        con.connect();
        long totalSize=con.getContentLength();
        double total=totalSize/(1024.0*1024.0);
        task.setFileSize(totalSize);
        String disposition=con.getHeaderField("Content-Disposition");
        String fileName="";
        if(disposition!=null&&disposition.contains("filename="))
        {
            fileName = disposition.substring(
                    disposition.indexOf("filename=") + 9
            ).replace("\"", "");
        }
        if(fileName.isEmpty())
        {
            fileName="download"+System.currentTimeMillis() + ".bin";
        }
        File file=new File(fileName);
        if(file.exists())
        {
            if(task.getFileName()==null) fileName=fileName.substring(0,fileName.indexOf('.'))+ System.currentTimeMillis()+fileName.substring(fileName.indexOf('.'));
            else fileName=task.getFileName();
        }
        task.setFileName(fileName);
        System.out.println(con.getResponseCode());
        System.out.println(con.getHeaderField("Content-Length"));
        System.out.println(con.getHeaderField("Content-Type"));
        System.out.println(con.getHeaderField("Accept-Ranges"));
        System.out.println(con.getHeaderField("Content-Disposition"));
        try(InputStream in = con.getInputStream();
            RandomAccessFile raf = new RandomAccessFile(fileName,"rw");)
        {
            raf.seek(downloadedBytes);
            byte[] buffer = new byte[8192];
            int bytesRead;
            long downloaded=0;
            long start=System.currentTimeMillis();
            double check=10.0;
            while (task.getDownloadStatus()==DownloadStatus.RESUMED&&(bytesRead = in.read(buffer)) != -1)
            {
                raf.write(buffer, 0, bytesRead);
                long end=(System.currentTimeMillis()-start);
                downloaded+=bytesRead;
                double curr=downloaded/(1024.0*1024);
                double speed=(curr*1000.0)/(end);
                double per=(curr*100.0)/total;
                if(check<=per) {
                    System.out.print("#");
                    check += 10;
                }
            }
            task.setDownloadedSize(downloadedBytes+downloaded);
            if(downloaded==totalSize)
            {
                task.setDownloadStatus(DownloadStatus.COMPLETED);
            }
            activeDownloads.put(link,task);
            System.out.println(task.getFileName());
            System.out.println(task.getDownloadedSize());
            System.out.println(task.getDownloadStatus());
            System.out.println(task.getFileSize());
            System.out.println();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        con.disconnect();
    }
    public void pauseDownload(String link) {
        if (activeDownloads.containsKey(link) && activeDownloads.get(link).getDownloadStatus() == DownloadStatus.RESUMED)
        {
            System.out.println(activeDownloads.get(link).getFileName());
            activeDownloads.get(link).setDownloadStatus(DownloadStatus.PAUSED);
        }
    }
}
