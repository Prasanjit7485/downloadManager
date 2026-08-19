package com.threading.downloadmanager.service;

import com.threading.downloadmanager.DTO.DownloadChunkDTO;
import com.threading.downloadmanager.DTO.DownloaderTaskDTO;
import com.threading.downloadmanager.entity.DownloadChunk;
import com.threading.downloadmanager.entity.DownloaderTask;
import com.threading.downloadmanager.enums.DownloadStatus;
import com.threading.downloadmanager.exception.DownloaderTaskException;
import com.threading.downloadmanager.repository.DownloadChunkRepository;
import com.threading.downloadmanager.repository.DownloaderTaskRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class DownloaderService
{
    private final DownloaderTaskRepository downloaderTaskRepository;
    private final DownloadChunkRepository downloadChunkRepository;
    //Constructor
    public DownloaderService(DownloaderTaskRepository downloaderTaskRepository, DownloadChunkRepository downloadChunkRepository)
    {
        this.downloaderTaskRepository = downloaderTaskRepository;
        this.downloadChunkRepository = downloadChunkRepository;
    }
    //Thread Safe HashMap use for storage the task
    ConcurrentHashMap<Long, List<DownloaderThread>> activeDownloads = new ConcurrentHashMap<>();
    //queue of downloading task
    Queue<Long> downloaderTaskQueue = new ConcurrentLinkedQueue<>();

    //adding task in the queue;
    public void addDownloaderTask(String link) throws IOException
    {
        URL url=new URL(link);
        HttpURLConnection con=(HttpURLConnection)url.openConnection();
        String fileName="";
        long totalSize=con.getContentLengthLong();
        String disposition=con.getHeaderField("Content-Disposition");
        if(disposition!=null&&disposition.contains("filename="))
        {
            fileName = disposition.substring(
                    disposition.indexOf("filename=") + 9
            ).replace("\"", "");
        }
        if(fileName.isEmpty())
        {
            fileName="download"+System.currentTimeMillis()+ ".bin";
        }
        File file=new File(fileName);
        if(file.exists())
        {
            fileName=fileName.substring(0,fileName.indexOf('.'))+ System.currentTimeMillis()+fileName.substring(fileName.indexOf('.'));
        }
        DownloaderTask task=new DownloaderTask();
        task.setUrl(link);
        task.setFileName(fileName);
        task.setFileSize(totalSize);
        task.setDownloadedSize(0L);
        task.setDownloadStatus(DownloadStatus.QUEUED);
        DownloaderTask downloaderTask=downloaderTaskRepository.save(task);
        downloaderTaskQueue.add(downloaderTask.getId());
        con.disconnect();
    }
    //starting the queue
    @Transactional
    public void startDownloading(Long id) throws IOException, ExecutionException, InterruptedException {
        DownloaderTask downloaderTask=downloaderTaskRepository.findById(id).orElseThrow(()->new DownloaderTaskException(id));
        downloaderTask.setDownloadStatus(DownloadStatus.RESUMED);
        String link=downloaderTask.getUrl();
        //converting into URL
        URL url=new URL(link);
        //setting up connection
        HttpURLConnection con=(HttpURLConnection)url.openConnection();
        String fileName=downloaderTask.getFileName();
        long totalSize=downloaderTask.getFileSize();
        //double total=totalSize/(1024.0*1024.0);
        System.out.println("Downloading "+fileName);
        System.out.println("Total Bytes "+totalSize);
        //thread safe variable for total downloaded bytes
        AtomicLong downloadedBytes=new AtomicLong(0);
        //long totalDownloaded=0;
        //number of threads
        int numberOfThread=4;
        List<Future<?>> futures = new ArrayList<>();
        ExecutorService executor= Executors.newFixedThreadPool(numberOfThread);
        long chunks=totalSize/numberOfThread;
        long start;
        long end;

        List<DownloaderThread> downloaderThreadList=new CopyOnWriteArrayList<>();
        List<DownloadChunk> downloadChunkList=downloaderTask.getDownloadChunkList();

        for(int i=0;i<numberOfThread;i++)
        {
            start=i*chunks;
            if(i==3)
            {
                end=totalSize-1;
            }
            else
            {
                end=start+chunks-1;
            }
            DownloadChunk downloadChunk;
            if(downloaderTask.getDownloadChunkList().size()>i)
            {
                downloadChunk=downloaderTask.getDownloadChunkList().get(i);
            }
            else
            {
                downloadChunk=new DownloadChunk();
                downloadChunk.setDownloadedBytes(0L);
                downloadChunk.setDownloaderTask(downloaderTask);
                downloadChunk.setStartByte(start);
                downloadChunk.setEndByte(end);
            }
            downloadChunk.setDownloadStatus(DownloadStatus.RESUMED);
            DownloaderThread downloaderThread=new DownloaderThread(link,fileName,downloadChunk,downloaderTask,downloadedBytes,downloadChunkRepository);
            if(downloadChunk.getDownloadStatus()==DownloadStatus.FAILED)
            {
                downloaderThread=new DownloaderThread(link,fileName,downloadChunk,downloaderTask,downloadedBytes,downloadChunkRepository);
            }

            downloaderThreadList.add(downloaderThread);
            downloadChunkList.add(downloadChunk);
            downloadChunkRepository.save(downloadChunk);
            Future<?> future = executor.submit(downloaderThread);
            futures.add(future);
            if(downloaderTask.getDownloadChunkList().size()>i)
            {
                downloaderTask.getDownloadChunkList().set(i,downloadChunk);
            }
        }
        //adding downloaderThreadList to concurrent hashmap for further use
        activeDownloads.put(id,downloaderThreadList);
        //scheduler for showing downloader progress
        ScheduledExecutorService progressExecutor=Executors.newSingleThreadScheduledExecutor();
        long[] previousBytes={0};
        final int Breaktime=2000;
        int currTime[]={0};

        progressExecutor.scheduleAtFixedRate(() -> {
            long currentBytes=downloadedBytes.get();
            long bytesDownloaded=currentBytes-previousBytes[0];
            if(bytesDownloaded==0)
            {
                currTime[0]+=500;
            }
            else if(bytesDownloaded>0) currTime[0]=0;
            double speed=bytesDownloaded/0.5;
            double progress = ((double)currentBytes/totalSize)* 100.0;
            System.out.printf(
                    "Progress: %.2f%% | Speed: %.2f MB/s%n",
                    progress,
                    speed / (1024 * 1024)
            );
            previousBytes[0] = currentBytes;
            if(downloaderTask.getDownloadStatus()!=DownloadStatus.RESUMED||currTime[0]==Breaktime) progressExecutor.shutdown();
        }, 0, 500, TimeUnit.MILLISECONDS);

        for(Future<?> future:futures)
        {
            future.get();
        }
        downloaderTask.setDownloadedSize(downloaderTask.getDownloadedSize()+downloadedBytes.get());
        System.out.println("Downloaded "+downloaderTask.getDownloadedSize());
        if(Objects.equals(downloaderTask.getDownloadedSize(), downloaderTask.getFileSize()))
        {
            downloaderTask.setDownloadStatus(DownloadStatus.COMPLETED);
        }
        downloaderTaskRepository.save(downloaderTask);
        executor.shutdown();        
        con.disconnect();
    }

    //pause the current download
    @Transactional
    public void pauseDownload(Long id)
    {
        DownloaderTask task=downloaderTaskRepository.findById(id).orElseThrow(()->new DownloaderTaskException(id));
        if (task.getDownloadStatus() == DownloadStatus.RESUMED)
        {
            System.out.println(task.getFileName()+" is paused");
            task.setDownloadStatus(DownloadStatus.PAUSED);
        }
        List<DownloaderThread> downloaderThreadList=activeDownloads.get(id);
        if (downloaderThreadList == null) {
            return;
        }
        for(DownloaderThread downloaderThread:downloaderThreadList)
        {
            downloaderThread.pause();
        }
        System.out.println("Downloading is paused");
    }
    public List<DownloaderTaskDTO>  getAllDownloaderTask()
    {
        List<DownloaderTask> downloaderTaskList=downloaderTaskRepository.findAll();
        List<DownloaderTaskDTO>
                downloaderTaskDTOList=new CopyOnWriteArrayList<>();
        for(DownloaderTask downloaderTask:downloaderTaskList) downloaderTaskDTOList.add(toDto(downloaderTask));
        return downloaderTaskDTOList;
    }
    //starting download
    public void startQueueDownloading() throws IOException, ExecutionException, InterruptedException {
        if(downloaderTaskQueue.isEmpty())
        {
            List<DownloaderTask> downloaderTaskList=downloaderTaskRepository.findAllByDownloadStatus(DownloadStatus.QUEUED);
            for(DownloaderTask downloaderTask:downloaderTaskList) downloaderTaskQueue.add(downloaderTask.getId());
        }
        while(!downloaderTaskQueue.isEmpty())
        {
            Long id=downloaderTaskQueue.poll();
            System.out.println(id);
            startDownloading(id);
           
        }
    }
    //cancellation of Download
    @Transactional
    public void cancelDownload(Long id)
    {
        DownloaderTask task=downloaderTaskRepository.findById(id).orElseThrow(()->new DownloaderTaskException(id));
        pauseDownload(task.getId());
        task.setDownloadStatus(DownloadStatus.CANCELED);
        String fileName=task.getFileName();
        Path path= Paths.get(fileName);
        try
        {
            Files.deleteIfExists(path);
            System.out.println("Downloading is canceled");
        }
        catch (IOException e)
        {
            System.out.println(e.getMessage());
        }
        downloaderTaskRepository.deleteById(id);
    }
    //resumeDownloader
    public void resumeDownloader(long id)
    {
        downloaderTaskQueue.add(id);
    }
    //downloaderTaskDTO to downloaderTask
    private DownloaderTask toEntity(DownloaderTaskDTO downloaderTaskDTO)
    {
        DownloaderTask downloaderTask=new DownloaderTask();
        downloaderTask.setId(downloaderTaskDTO.getId());
        downloaderTask.setUrl(downloaderTaskDTO.getUrl());
        downloaderTask.setDownloadedSize(downloaderTaskDTO.getDownloadedSize());
        downloaderTask.setFileName(downloaderTaskDTO.getFileName());
        downloaderTask.setFileSize(downloaderTaskDTO.getFileSize());
        downloaderTask.setDownloadStatus(downloaderTaskDTO.getDownloadStatus());
        return downloaderTask;
    }
    //downloaderTask to downloaderTaskDTO
    private DownloaderTaskDTO toDto(DownloaderTask downloaderTask)
    {
        DownloaderTaskDTO downloaderTaskDTO=new DownloaderTaskDTO(downloaderTask.getUrl());
        downloaderTaskDTO.setId(downloaderTask.getId());
        downloaderTaskDTO.setDownloadedSize(downloaderTask.getDownloadedSize());
        downloaderTaskDTO.setFileName(downloaderTask.getFileName());
        downloaderTaskDTO.setFileSize(downloaderTask.getFileSize());
        downloaderTaskDTO.setDownloadStatus(downloaderTask.getDownloadStatus());
        List<DownloadChunkDTO> downloadChunkDTOS=new CopyOnWriteArrayList<>();
        for(DownloadChunk downloadChunk:downloaderTask.getDownloadChunkList()) downloadChunkDTOS.add(toChunkDto(downloadChunk));
        downloaderTaskDTO.setDownloadChunkListDTO(downloadChunkDTOS);
        return downloaderTaskDTO;
    }
    //downloadChunk to downloadChunkDTO
    private DownloadChunkDTO toChunkDto(DownloadChunk downloadChunk)
    {
        DownloadChunkDTO downloadChunkDTO=new DownloadChunkDTO();
        downloadChunkDTO.setId(downloadChunk.getId());
        downloadChunkDTO.setDownloadedBytes(downloadChunk.getDownloadedBytes());
        downloadChunkDTO.setStart(downloadChunk.getStartByte());
        downloadChunkDTO.setEnd(downloadChunk.getEndByte());
        downloadChunkDTO.setDownloadStatus(downloadChunk.getDownloadStatus());
        downloadChunkDTO.setDownloaderTaskId(downloadChunk.getDownloaderTask().getId());
        return downloadChunkDTO;
    }
}
