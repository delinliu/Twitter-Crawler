import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;

import java.io.*;
import java.util.*;

public class IdToUsername{
    static public void main(String []args){
        IdToUsername tool = new IdToUsername();
        tool.start(10);
    }


    String allIdsFile = "ids.txt"; // save all ids we need to crawl
    String handledIdsFile = "id-username.txt"; // save all id-username pairs we have crawled
    int allIdsSetCursor = 0; // a cursor used to dispatch crawling task to all threads
    ArraySet<String> allIdsSet = new ArraySet<String>(); // save all ids
    ArraySet<String> handledIdsSet = new ArraySet<String>(); // save all id-username paires
    BufferedWriter idUsernameOs = null; // used to write id-username pairs to file system

    public void start(int threadNumber){
        initIdsSets();
        initIdUsernameWriter();
        startCrawler(threadNumber);
    }

    /**
     * Start crawler with threadNumber threads.
    **/
    private void startCrawler(int threadNumber){
        threadNumber = threadNumber < 1 ? 1 : threadNumber;
        while(threadNumber-- > 0){
            new CrawlThread().start();
        }
    }

    private void initIdUsernameWriter(){
        try{
            idUsernameOs = new BufferedWriter(new FileWriter(handledIdsFile, true));
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private void initIdsSets(){
        initAllIdsSet();
        initHandledIdsSet();
    }

    /**
     * Init the all ids set.
    **/
    private void initAllIdsSet(){
        try{
            BufferedReader br = new BufferedReader(new FileReader(allIdsFile));
            String line = null;
            while((line = br.readLine()) != null){
                if("".equals(line)) continue;
                allIdsSet.add(line);
            }
            br.close();
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Init the handled ids set.
    **/
    private void initHandledIdsSet(){
        try{
            BufferedReader br = new BufferedReader(new FileReader(handledIdsFile));
            String line = null;
            while((line = br.readLine()) != null){
                if("".equals(line)) continue;
                handledIdsSet.add(line.split(" ")[0]); // 0 is id, 1 is username
            }
            br.close();
        }catch(Exception e){ 
            e.printStackTrace();
        }
    }

    String tweeterid_url = "https://tweeterid.com/ajax.php";
    /**
     * Crawl the username by userid
    **/
    private String crawlUsername_tweeterid(String id){
        try{
            // execute the curl cmd in bash.
            String curlCmd = "curl -d \"input=" + id + "\" \"" + tweeterid_url + "\"";
            Process process = Runtime.getRuntime().exec(new String[]{"bash", "-c", curlCmd});
            int exitValue = process.waitFor();
            if(0 != exitValue){
                System.err.println("Error: the command [" + curlCmd + "] returns "  + exitValue);
                return null;
            }

            // get the response text
            BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = br.readLine();
            if(line == null || !line.startsWith("@")){
                return null;
            }
            return line.substring(1);
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    int timeout = 2000; // timeout of crawling a page
    String url = "http://id.twidder.info/?user_id=USER_ID"; // the url of the page we crawl
    String identify_key = "name";
    String identify_value = "screen_name";
    /**
     * Crawl the username by userid.
    **/
    private String crawlUsername(String id){
        try{
            Document html = Jsoup.connect(url.replace("USER_ID", id)).timeout(timeout).get();
            Elements screen_name_inputs = html.getElementsByAttributeValue(identify_key, identify_value);
            if(screen_name_inputs.size() != 1){
                System.err.println("Error, the page of id[" + id + "] contains " + screen_name_inputs.size() + " " + identify_value + " as " + identify_key + ".");
                return null;
            }
            String username = screen_name_inputs.get(0).attr("value");

            return username; // We don't check if the username is "",
                             // because the page we crawl always returns username="" with some ids.

        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    int finished = 0;
    Object lock = new Object();
    Object handledIdsSetLock = new Object();

    /**
     * A thread used to crawl twitter username by userid.
     * All ids are saved in allIdsSet. First, get a cursor of all ids set.
     * Then check if we have already crawled it. Then crawl the username and save.
     * All opertions are synchronized with other threads by using serveral locks.
    **/
    class CrawlThread extends Thread{
        
        @Override
        public void run(){
            while(true){
                // Got the cursor, then get the id.
                int cursor = -1;
                synchronized(allIdsSet){
                    if(allIdsSetCursor < allIdsSet.size()){
                        cursor = allIdsSetCursor++;
                    }else{
                        break;
                    }
                }
                String id= allIdsSet.get(cursor);

                // Check if the user id has already been crawled.
                synchronized(handledIdsSet){
                    if(handledIdsSet.contains(id)){
                        synchronized(lock){
                            ++finished;
                            System.out.println(finished + " already finished: " + id);
                        }
                        continue;
                    }
                }

                // Crawl the username
                String username = crawlUsername(id);
                // String username = crawlUsername_tweeterid(id);
                if(username == null){
                    System.out.println("cannot find: " + id);
                    continue;
                }

                // Save the id-username in file.
                synchronized(idUsernameOs){
                    try{
                        idUsernameOs.append(id + " " + username + "\n");
                        idUsernameOs.flush();
                    }catch(Exception e){
                        e.printStackTrace();
                        continue;
                    }
                }

                // Save the id in handled ids set.
                synchronized(handledIdsSet){
                    handledIdsSet.add(id);
                }

                synchronized(lock){
                    System.out.println(++finished + " finished " + id + ": " + username);
                }
                
            }
        }
    }
}
