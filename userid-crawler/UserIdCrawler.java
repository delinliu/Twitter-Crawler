import twitter4j.*;
import twitter4j.auth.*;

import java.io.*;
import java.util.*;
import java.net.*;

public class UserIdCrawler{

    public static void main(String [] args){
        UserIdCrawler crawler = new UserIdCrawler();
        crawler.startCrawler();
    }

    Twitter twitter;
    RequestToken requestToken;
    AccessToken accessToken;

    String allUserFile = "all-ids.txt";
    String handledUserFile = "handled-ids.txt";
    ArraySet<Long> allUserSet = new ArraySet<Long>();
    ArraySet<Long> handledUserSet = new ArraySet<Long>();
    BufferedWriter allUserOs;
    BufferedWriter handledUserOs;
    
    public void startCrawler(){
        auth();
        initUserSet();
        initSetWriter();
        crawlLoop();
    }

    /**
     * Get the auth from twitter.
     * 1. Use the consumerSecret and consumerKey (save in twitter4j.properties by default) to get the requestToken.
     * 2. Get a url from requestToken. Open the url using browser (requires login in twitter). Auth it and get the pin from browser.
     * 3. Use the pin to get the accessToken. Then we can crawl data by using twitter api, but with rate limits.
    **/
    private void auth(){
        try{
            twitter = new TwitterFactory().getInstance();
            requestToken = twitter.getOAuthRequestToken();
            System.out.println(requestToken.getAuthorizationURL());
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            while(accessToken == null){
                System.out.print("Input pin: ");
                String pin = br.readLine();
                try{
                    accessToken = twitter.getOAuthAccessToken(requestToken, pin);
                }catch(Exception accessE){
                    accessE.printStackTrace();
                }
            }
            System.out.println("Get access token successful.");
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Control the crawl job.
     * 
    **/
    private void crawlLoop(){
        while(handledUserSet.size() == 0 || allUserSet.size() * 1.0 / handledUserSet.size() > 4){
            Long id = allUserSet.get((int)(Math.random()*allUserSet.size()));
            crawlOne(id);
        }
        for(int i=0; i<allUserSet.size(); i++){
            Long id = allUserSet.get(i);
            crawlOne(id);
        }
    }

    /**
     * Crawl ids and merge them into allUserSet and save them in file system,
     * also, mark the user as handled.
    **/
    private void crawlOne(Long id){
        if(handledUserSet.contains(id)){
            return;
        }
        try{
            System.out.println("Crawling: " + id);
            long[] ids = crawlIds(id);
            merge(ids);
            handledUser(id);
        }catch(Exception e){
            e.printStackTrace();
            System.err.println("Crawling error: " + id);
            try{
                Thread.sleep(305000);
            }catch(Exception ee){
                ee.printStackTrace();
            }
        }
    }

    /**
     * Get the follower ids by using the twitter api.
     * Note: the rate is limited, 15 times every 15 minutes.
    **/
    private long[] crawlIds(Long id) throws Exception{
        IDs ids = twitter.getFollowersIDs(id, -1);
        return ids.getIDs();
    }

    /**
     * Init the user sets.
     * Load all-user-file to all-user-set.
     * Load handled-user-file to handled-user-set.
    **/
    private void initUserSet(){
        initUserSet(allUserSet, allUserFile);
        initUserSet(handledUserSet, handledUserFile);
    }

    /**
     * Load the file to the set.
     * Read line by line of the file in the path,
     * and convert each line to a long number,
     * insert the numbers into the set.
    **/
    private void initUserSet(ArraySet<Long> userSet, String path){
        try{
            BufferedReader br = new BufferedReader(new FileReader(path));
            String line = null;
            while((line = br.readLine()) != null){
                if("".equals(line)) continue;
                userSet.add(Long.parseLong(line));
            }
            br.close();
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Init two file writers to append new userIds in two files.
    **/
    private void initSetWriter(){
        try{
            allUserOs = new BufferedWriter(new FileWriter(allUserFile, true));
            handledUserOs = new BufferedWriter(new FileWriter(handledUserFile, true));
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * Handled a user.
     * So put the id in handledUserSet and flush it to file.
    */
    private void handledUser(Long userId){
        try{
            if(allUserSet.contains(userId) && !handledUserSet.contains(userId)){
                handledUserSet.add(userId);
                handledUserOs.append("" + userId + "\n");
                handledUserOs.flush();
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    /**
     * Got many new ids.
     * Merge them with allUserSet and flush them into file.
    **/
    private void merge(long ids[]){
        for(long id : ids){
            if(!allUserSet.contains(id)){
                allUserSet.add(id);
                try{
                    allUserOs.append("" + id + "\n");
                    allUserOs.flush();
                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        }
    }

}
