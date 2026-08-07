package com.threading.downloadmanager.service;

import com.threading.downloadmanager.entity.DownloaderTask;
import com.threading.downloadmanager.enums.DownloadStatus;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        int numberOfThread=4;
        ExecutorService executor= Executors.newFixedThreadPool(numberOfThread);
        long chunks=totalSize/numberOfThread;
        long start=0;
        long end=0;
        for(int i=0;i<numberOfThread;i++)
        {
            start=i*chunks;
            if(i==3)
            {
                end=totalSize;
            }
            else
            {
                end=start+chunks-1;
            }
            executor.submit(new DownloaderThread(start,end,link,fileName));
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
