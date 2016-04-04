import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;

import java.io.*;
import java.util.*;

import org.json.*;

public class TwitterCrawler{
    static public void main(String[] args){
        TwitterCrawler crawler = new TwitterCrawler();
        crawler.startCrawler(1);
    }

    String usernameSetFile = "usernames.txt";
    String handledSetFile = "handled-usernames.txt";
    String indexFile = "index.txt";
    int filenameIndex = 0;
    int usernameSetCursor = 0;
    ArraySet<String> usernameSet = new ArraySet<String>();
    ArraySet<String> handledSet = new ArraySet<String>();
    BufferedWriter handledOs;
    Object textLock = new Object();
    RandomAccessFile textOs;
    String saveFolder = "twitter-content";

    public void startCrawler(int threadNumber){
        initSets();
        initHandledOs();
        initFilenameIndex();
        openTextOs(filenameIndex);

        for(int i=0; i<threadNumber; i++){
            new CrawlerThread().start();
        }
    }

    private void initHandledOs(){
        try{
            handledOs = new BufferedWriter(new FileWriter(handledSetFile, true));
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private void initFilenameIndex(){
        try{
            BufferedReader br = new BufferedReader(new FileReader(indexFile));
            String line = br.readLine();
            if(line != null){
                filenameIndex = Integer.parseInt(line);
            }
        }catch(FileNotFoundException notFound){
            
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private void openTextOs(int index){
        try{
            File file = new File(saveFolder + "/" + getFileName(index));
            textOs = new RandomAccessFile(file, "rw");
            textOs.seek(textOs.length());
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private String getFileName(int index){
        String name = "" + index;
        while(name.length() < 5){
            name = "0" + name;
        }
        return name;
    }

    private void saveText(String username, String text){
        try{
            synchronized(textLock){
                if(textOs.length() > singleFileSize){
                    textOs.close();
                    openTextOs(++filenameIndex);
                    BufferedWriter br = new BufferedWriter(new FileWriter(indexFile));
                    br.write("" + filenameIndex);
                    br.close();
                }
                textOs.write(("================" + username + "================\n").getBytes());
                textOs.write(text.getBytes());
                textOs.write("\n\n\n\n".getBytes());
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private void initSets(){
        initSet(usernameSetFile, usernameSet);
        initSet(handledSetFile, handledSet);
        finished = handledSet.size();
    }

    private void initSet(String file, ArraySet<String> set){
        try{
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line = null;
            while((line = br.readLine()) != null){
                if("".equals(line)) continue;
                set.add(line);
            }
            br.close();
            System.out.println(set.size());
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    int finished = 0;
    Object lock = new Object();
    class CrawlerThread extends Thread{
        @Override
        public void run(){
            while(true){
                int cursor = -1;
                
                /*
                synchronized(usernameSet){
                    if(usernameSetCursor >= usernameSet.size()){
                        break;
                    }
                    cursor = usernameSetCursor++;
                }
                */

                // Attention! This Statement is conflict with the above usernameSetCursor.
                // I just want to random.
                cursor = (int)(Math.random()*usernameSet.size());

                String username = usernameSet.get(cursor);
                synchronized(handledSet){
                    if(handledSet.contains(username)){
                        /*
                        int number = 0;
                        synchronized(lock){
                            number = ++finished;
                        }
                        */
                        System.out.println("Already finished: " + username);
                        continue;
                    }
                }
                String text = crawlAllTwitterOfOneUser(username);
                if(text == null || "".equals(text)){
                    System.err.println("Cannot crawler [" + username + "]");
                    continue;
                }

                saveText(username, text);
                synchronized(handledSet){
                    handledSet.add(username);
                }
                synchronized(handledOs){
                    try{
                        handledOs.append(username + "\n");
                        handledOs.flush();
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }
                int number = 0;
                synchronized(lock){
                    number = ++finished;
                }
                System.out.println(number + " finished: " + username);
            }
        }
    }

    int timeout = 10000;

    private String crawlAllTwitterOfOneUser(String username){
        StringBuilder text = new StringBuilder();
        Set<String> twitterIdSets = new HashSet<String>();
        PackOfTwitter pack = crawlFirstPage(username, twitterIdSets);
        if(pack != null){
            text.append(pack.getText());
        }
        int count = 1;
        int retry = 0;
        while(pack != null && pack.getRetweetId() != null){
            PackOfTwitter pot = crawlOtherPage(username, pack.getRetweetId(), twitterIdSets, count++);
            if(pot == null && retry > 0){
                retry--;
                try{
                    Thread.sleep(1000);
                }catch(Exception e){
                    e.printStackTrace();
                }
                continue;
            }
            pack = pot;
            if(pack != null){
                text.append(pack.getText());
            }
        }
        return text.toString();
    }

    private PackOfTwitter parseTheHtml(Document html, Set<String> set){
        StringBuilder text = new StringBuilder();
        int count = 0;
        Elements twitterClassContainer = html.getElementsByClass("tweet");
        for(Element twitterClass : twitterClassContainer){
            Elements twitterContainers = twitterClass.getElementsByClass("js-tweet-text-container");
            for(Element twitterContainer : twitterContainers){
                String dataTweetId = twitterClass.attr("data-tweet-id");
                if(set.contains(dataTweetId)){
                    break;
                }
                set.add(dataTweetId);
                count++;
                Elements twitterPs = twitterContainer.getElementsByTag("p");
                for(Element p : twitterPs){
                    if("".equals(p.ownText().trim())){
                        continue;
                    }
                    text.append(p.ownText().trim());
                    text.append("\n");
                }
            }
        }
        String identity = "data-retweet-id";
        Elements twitters = html.getElementsByAttribute(identity);
        String lastTwitterId = twitters.size() > 0 ? twitters.get(twitters.size()-1).attr(identity) : null;
        return new PackOfTwitter(text.toString(), lastTwitterId);
    }

    private int sleepTime = 250;
    private int singleFileSize = 1024*1024;
    private PackOfTwitter crawlFirstPage(String username, Set<String> set){
        try{
            System.out.println("[0] Crawling: " + username);
            Thread.sleep(sleepTime);
            String url = "https://twitter.com/" + username;
            Document html = Jsoup.connect(url).timeout(timeout).get();
            return parseTheHtml(html, set);
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    private PackOfTwitter crawlOtherPage(String username, String retweetId, Set<String> set, int times){
        try{
            System.out.println("[" + times + "] Crawling: " + username);
            Thread.sleep(sleepTime);
            String url = "https://twitter.com/i/profiles/show/USERNAME/timeline/tweets?max_position=RETWEETID";
            url = url.replace("USERNAME", username).replace("RETWEETID", retweetId);
            String json = Jsoup.connect(url).timeout(timeout).ignoreContentType(true).execute().body();
            JSONObject jsonObj = new JSONObject(json);
            Document html = Jsoup.parse(jsonObj.getString("items_html"));
            return parseTheHtml(html, set);
        }catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }
}

class PackOfTwitter{
    private String text;
    private String retweetId;
    public PackOfTwitter(String text, String retweetId){
        this.text = text;
        this.retweetId = retweetId;
    }
    public String getText(){
        return text;
    }
    public String getRetweetId(){
        return retweetId;
    }
}
