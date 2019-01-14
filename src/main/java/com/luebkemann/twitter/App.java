package com.luebkemann.twitter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.luebkemann.mysportsfeed.MySportsFeeds;
import com.luebkemann.mysportsfeed.V1_2;
import com.luebkemann.mysportsfeed.VersionNotRecognizedException;

import twitter4j.*;
import twitter4j.conf.ConfigurationBuilder;

import java.awt.*;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.Buffer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalUnit;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Hello world!
 *
 */
public class App 
{
    //NOT ACTUAL VALUE FOR THE KEYS/SECRETS
    static final String CONSUMER_KEY = "CONSUMER-KEY";
    static final String CONSUMER_SECRET = "CONSUMER-SECRET";
    static final String ACCESS_TOKEN = "ACCESS-TOKEN";
    static final String ACCESS_TOKEN_SECRET = "ACCESS-TOKEN-SECRET";
    static Map<String, Integer> playerIdTotalPointsMap = new HashMap<>();

    public static Twitter getTwitterInstance() {
        //Gets an instance of the Twitter class thats tied to my twitter account.
        //Use method in the class to tweet from this program
        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.setDebugEnabled(true)
                .setOAuthConsumerKey(CONSUMER_KEY)
                .setOAuthConsumerSecret(CONSUMER_SECRET)
                .setOAuthAccessToken(ACCESS_TOKEN)
                .setOAuthAccessTokenSecret(ACCESS_TOKEN_SECRET);

        TwitterFactory tf = new TwitterFactory(cb.build());
        return tf.getInstance();
    }


    public static void main( String[] args )  {
        Runnable runnable = () -> {
            try {
                tweetOutMostPoints();
            } catch (VersionNotRecognizedException e) {
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (TwitterException e) {
                e.printStackTrace();
            }
        };
        ScheduledExecutorService executorService = Executors
                .newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(runnable,0, 24, TimeUnit.HOURS);

    }

    //This will be directly called in the main method.
    private static void tweetOutMostPoints() throws VersionNotRecognizedException, FileNotFoundException, TwitterException {
        //get the twitter instance, mysportsfeed instance
        Twitter twitter = getTwitterInstance();
        MySportsFeeds sportsFeeds = new MySportsFeeds(1.2);
        //authenticates through the mysportsfeed api then retrieves the daily_player_stats log in a json file
        //NOT ACTUAL VALUES FOR THE USERNAME/PASS
        sportsFeeds.authenticate("API-KEY", "PASSWORDFORMYSPORTSFEED");
        sportsFeeds.get("nba", "current", "daily_player_stats", "json", "fordate=" + getYesterdaysDate());
        String jsonFile = "C:\\Users\\fireb\\Twitter\\daily_player_stats-nba-current.json";


        fillPlayerPointsMap(jsonFile);
        Map<String, Integer> mostPointsMap = getIdWithMostTotalPoints(playerIdTotalPointsMap);
        int totalPoints = firstValueInMap(mostPointsMap);

        List<String> playerNames = getPlayerNamesFromIds(mostPointsMap, jsonFile);

        tweetMostPoints(twitter, totalPoints, playerNames);
    }

    private static String getYesterdaysDate() {
        //This methods retrieves yesterdays date in the proper format for our sports api.
        LocalDate date = LocalDate.now().minusDays(1);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        return date.format(formatter);
    }

    //TAKES THE TWITTER INSTANCE, TOTAL POINTS, AND THE LIST OF PLAYERS TO SCORE THE TOTALPOINTS AND
    //CREATES A STRING TO TWEET OUT ALL OF THAT INFO. THEN USES TWITTER INSTANCE TO TWEET IT
    private static void tweetMostPoints(Twitter twitter, int totalPoints, List<String> playerNames) throws TwitterException {
        StringBuilder tweet = new StringBuilder();
        LocalDate date = LocalDate.now().minusDays(1);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        String tweetableDate = date.format(formatter);
        tweet.append("NBA Leader(s) for points scored on " + tweetableDate + ": " + "\n");
        if(playerNames.size() == 1){
            tweet.append(playerNames.get(0) +": " + totalPoints);
        } else{
            for(int i = 0; i < playerNames.size(); i++){
                tweet.append(playerNames.get(i) +": " + totalPoints + "\n");
            }
        }

        String finalTweet = tweet.toString();
        twitter.updateStatus(finalTweet);
    }

    //HELPER METHOD TO RETRIEVE THE FIRST VALUE IN A MAP.
    private static int firstValueInMap(Map map){
        if(map == null) return -1;
        Iterator<Map.Entry> iterator = map.entrySet().iterator();
        Map.Entry entry = iterator.next();
        return (int) entry.getValue();
     }

     //
    private static List<String> getPlayerNamesFromIds(Map<String,Integer> mostPointsMap, String jsonFile) throws FileNotFoundException {
        List<String> playerNames = new ArrayList<>();
        Iterator<Map.Entry<String, Integer>> iterator = mostPointsMap.entrySet().iterator();
        List listOfPlayers = getPlayers(jsonFile);
        while(iterator.hasNext()){
            Map.Entry entry = iterator.next();
            String id = (String) entry.getKey();

            for(int i = 0; i < listOfPlayers.size(); i++){

                Map playerTeamStatsMap = (Map) listOfPlayers.get(i);
                //System.out.println(playerMap);
                Set iterator2 = playerTeamStatsMap.keySet(); //this is for one player
                //System.out.println(iterator2); //player, team, stats
                Map playerMap = (Map) playerTeamStatsMap.get("player");
                Map teamMap = (Map) playerTeamStatsMap.get("team");
                if(String.valueOf(playerMap.get("ID")).equals(id)){
                    String firstName = (String) playerMap.get("FirstName");
                    String lastName = (String) playerMap.get("LastName");
                    String teamAbb = (String) teamMap.get("Abbreviation");
                    String fullName = firstName + " " + lastName + "(" + teamAbb + ")";
                    playerNames.add(fullName);
                }
            }
        }
        return playerNames;
    }

    //THIS METHOD RETURN MAP OF IDS WITH THE MOST POINTS SCORED. ALL THE VALUES WILL BE THE SAME (HIGH SCORE),
    //THE KEYS WILL BE THE IDS
    public static Map<String, Integer> getIdWithMostTotalPoints(Map map)  {
        List<Integer> mostPoints = new ArrayList<>();
        Map<String, Integer> mapOfMostPointsWithId = new HashMap<>();
        mostPoints.add(0);
        Iterator<Map.Entry> iterator = map.entrySet().iterator();

        while(iterator.hasNext()){
            Map.Entry entry = iterator.next();
            String id = (String) entry.getKey();
            int points = (int) entry.getValue();

            if (points >= mostPoints.get(0)){
                if(points > mostPoints.get(0)) {
                   mapOfMostPointsWithId.clear();
                   mostPoints.clear();
                }
                mostPoints.add(points);
                mapOfMostPointsWithId.put(id, points);
            }
        }
        return mapOfMostPointsWithId;
    }

    //THIS WILL RETURN A LIST OF PLAYERS FROM THE JSON FILE WE GET FROM THE MYSPORTSFEED API.
    private static List getPlayers(String jsonFile) throws FileNotFoundException{
        BufferedReader reader = new BufferedReader(new FileReader(jsonFile));

        Map jsonObject = new Gson().fromJson(reader, Map.class);
        Map map = (Map) jsonObject.get("dailyplayerstats");

        return (List) map.get("playerstatsentry"); //this is every player
    }

    //THIS FILLS A MAP OF IDS AS KEYS, AND TOTAL POINTS OF THAT PLAYER AS THE VALUES
    private static void fillPlayerPointsMap(String jsonFile) throws FileNotFoundException {

        List listOfPlayers = getPlayers(jsonFile); //each index in this list represents one player and their stats/profile
        for(int i = 0; i < listOfPlayers.size(); i++){


            Map playerTeamStatsMap = (Map) listOfPlayers.get(i); //

            Set iterator2 = playerTeamStatsMap.keySet(); //this is for one player (3 maps - player, team, stats)

            Map playerMap = (Map) playerTeamStatsMap.get("player"); //user the player map to get the id/points for that player
            Map statsMap = (Map) playerTeamStatsMap.get("stats");

            Map pointsMap = (Map) statsMap.get("Pts");
            int totalPoints = Integer.parseInt(String.valueOf(pointsMap.get("#text"))); //total points from that player
            String playerId = String.valueOf(playerMap.get("ID"));
            playerIdTotalPointsMap.put(playerId, totalPoints);
//

//
        }

    }


}
