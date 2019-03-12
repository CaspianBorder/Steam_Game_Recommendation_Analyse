package bonusAssignment;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.TreeSet;

public class PreProcess 
{
	private static int DLC_MATCH_THRESHOLD = 4 ; // defining a threshold when finding a DLC in reverse finding
	private static int PREFERENCE_PARTITION = 8 ; // like-unlike threshold is the time which 100% - (PREFERENCE_PARTITION * 0.05) 's people played on this game, use in method 2
	private static int ADVANCED_PARTITION = 14 ; // same as PREFERENC_PARTITION , used in method 3, a standard to define like and very like ( very like may have a higher weight )
	private static int TOTAL_TIME_THRESHOLD = 10 ; // like-unlike threshold is the time which 100% - (TOTAL_TIME_THRESHOLD * 0.05) 's play record, used in method 1
	private static boolean ADVANCED_ACTIVATED = true ; // indicate if advanced partition ( method 3 ) is activated
	
	private static String originFilePath = "data" + File.separator + "steam-200k.csv" ;
	private static String cleanedFilePath = "data" + File.separator + "steam-200k-cleaned.csv" ;
	private static String userStatFilePath = "data" + File.separator + "user.csv" ;
	private static String gameStatFilePath = "data" + File.separator + "game.csv" ;
	private static String totalTimePartitionFilePath = "data" + File.separator + "totalTimePartition.txt" ; 
	private static String gameThresholdFilePath = "data" + File.separator + "gameThreshold.csv" ;
	
	public static int GAME_TOTAL ; // quantity of game
	public static int USER_TOTAL ; // number of user
	public static double TOTAL_TIME_PARTITION ; // the like-unlike division time used in method 1
	public static int userCount[] ; // recording the number of games which an user has purchased ( the degree of the user )
	public static int gameCount[] ; // recording the number users which a game has been purchased ( the degree of the game )
	public static int dlcStatus[] ; // recording the DLC status of the game
	public static double gamePartition[] ; // recording the like-unlike division time for each game used in method 2
	public static double advancedPartition[] ; // recording the like-very like division time for each game used in method 3
	private static double userGameList[][] ; // recording the user-game relationship ( -1 means didn't purchase , number means played time )

	
	public static ArrayList<String> gameList = null ;
	public static ArrayList<String> userList = null ;
	private static BufferedReader br = null ;
	private static BufferedWriter bw = null ;
	
	
	private static void getGameList () throws IOException // first traverse of the origin file, to find the total number of game and user
	{
		String input ;
		String inputSplit[] ;
		TreeSet<String> deduplicate = null ;
		
		userList = new ArrayList<String> () ;
		gameList = new ArrayList<String> () ;
		br = new BufferedReader( new FileReader( originFilePath ) ) ;
		input = br.readLine() ;
		while ( input != null )
		{
			inputSplit = input.split( "\"" ) ;
			userList.add( inputSplit [ 0 ].substring( 0 , inputSplit [ 0 ].indexOf( "," ) ) ) ;
			gameList.add( inputSplit [ 1 ] ) ;
			System.out.println( "Current game is: " + inputSplit [ 1 ] ) ;
			input = br.readLine() ;
		}
		deduplicate = new TreeSet<String> ( userList ) ;
		userList = new ArrayList<String> ( deduplicate ) ;
		deduplicate = new TreeSet<String> ( gameList ) ;
		gameList = new ArrayList<String> ( deduplicate ) ;
		USER_TOTAL = userList.size() ;
		GAME_TOTAL = gameList.size() ;
		System.out.println( "--------------------" ) ;
		System.out.println( "total user quantity: " + USER_TOTAL ) ;
		System.out.println( "total game quantity: " + GAME_TOTAL ) ; 
		br.close() ;
		System.gc() ;
		return ;
	}
	
	private static void dataStat () throws IOException // initial analyze on origin file, get each user's and game's degree , and determine whether a game is a DLC
													// NOTE: DLC means Downloadable Content, an enhancement of a certain original game ( like extra play area , mission .etc ), need to be purchased but 
													// didn't have play time statistics. DLC need to attached to the original game, and playing time will also be counted on original one.
	{
		String input ;
		String gameStatus ;
		String inputSplit[] ;
		int currentUser ;
		int index ;

		userCount = new int [ USER_TOTAL ] ;
		gameCount = new int [ GAME_TOTAL ] ;
		dlcStatus = new int [ GAME_TOTAL ] ;
		for ( int i = 0 ; i < USER_TOTAL ; i ++ )
		{
			userCount [ i ] = 0 ;
		}
		for ( int i = 0 ; i < GAME_TOTAL ; i ++ )
		{
			gameCount [ i ] = 0 ;
			dlcStatus [ i ] = -1 ;
		}
		
		br = new BufferedReader( new FileReader( originFilePath ) ) ;
		input = br.readLine() ;
		while ( input != null )
		{
			inputSplit = input.split( "\"" ) ;
			currentUser = userList.indexOf( inputSplit [ 0 ].substring( 0 , inputSplit [ 0 ].indexOf( "," ) ) ) ;
			if ( currentUser == -1 )
			{
				System.err.println( "Can not find user \"" + inputSplit [ 0 ].substring( 0 , inputSplit [ 0 ].indexOf( "," ) ) + "\" in current record." );
				return ;
			}
			
			index = gameList.indexOf( inputSplit [ 1 ] ) ;
			if ( index == -1 )
			{
				System.err.println( "Can not find game \"" + inputSplit [ 1 ] + "\" in current record." );
				return ;
			}
			input = inputSplit [ 2 ] ;
			input = input.substring( input.indexOf( "," ) + 1 ) ;
			gameStatus = input.substring( 0 , input.indexOf( "," ) ) ;
			if ( gameStatus.equals( "play" ) ) // if a game has a play record, mark it as a GAME and remove it from the list
			{
				dlcStatus [ index ] = index ; // dlcStatus indicate the game is DLC or not. if in a storage contains it's own index , like dlcStatus [ 1 ] == 1, it means it's an original game
											// and if a storage contains other index, like dlcStatus [ 2 ] == 1, it means game no.index 's original game is 'storage one'
			}
			else if ( gameStatus.equals( "purchase" ) ) // if a game has a purchase record, put it into a list and wait for further process
			{
				userCount [ currentUser ] ++ ;
				gameCount [ index ] ++ ;
			}
			input = br.readLine() ;
		}
		
		for ( int i = 0 ; i < GAME_TOTAL ; i ++ ) // if a game in the list, it may be a DLC, or non of the buyer has played this game
		{
			if ( dlcStatus[ i ] == -1 )
			{
				for ( int j = i ; j > i - DLC_MATCH_THRESHOLD ; j -- ) // according to he TreeSet , game should be sorted, and DLC usually have same prefix of original game 
																	//(e.g. 'The Elder Scrolls V Skyrim' (original game) and 'The Elder Scrolls V Skyrim - Dawnguard' (DLC) )
																	// so if a game is DLC, it's highly possible that it's original game will be a little bit ahead of it
				{
					if ( j > 0 && gameList.get( i ).indexOf( gameList.get( dlcStatus [ j - 1 ] ) ) != -1 ) // if there are multiple DLCs, the latter will directly consider former's original game
					{
						dlcStatus [ i ] = dlcStatus [ j - 1 ] ;
						break ;
					}
					else if ( j > 0 && gameList.get( i ).indexOf( gameList.get( dlcStatus [ j - 1 ] ).substring( 0 , (int)( 0.6 * gameList.get( dlcStatus [ j - 1 ] ).length() ) ) ) != -1 ) // if original game has some suffix, it might required some process ( only consider 60% of the words from head )
																																															//( e.g. 'Halo Spartan Assault' (original game) and 'Halo Spartan Strike' (DLCs) ) 
					{
						dlcStatus [ i ] = dlcStatus [ j - 1 ] ;
						break ;
					}
				}
				if ( dlcStatus [ i ] == -1 )
				{
					dlcStatus [ i ] = i ;
				}
			}
		}
		return ;
	}
	
	private static void cleanOriginData () throws IOException // most of the original data contains two line of record, one is purchase, and the other is play
															// some game even didn't get played after being purchased, and DLC also won't have play record
															// some data even appears twice due to some enter error. so it need to be clean before further process
	{
		String input = null ;
		String output = null ;
		String inputSplit[] ;
		int gameIndex ;
		int currentUserID ;
		int lastUserID = -1 ;
		int lineCount = 0  ;
		int lastLine = 0 ;
		int gameStatus[] = new int [ GAME_TOTAL ] ;
		ArrayList<Integer> standbyList = new ArrayList<Integer> () ;
		TreeSet<Integer> deduplicate = null ; // TreSet is a data structure which has not duplicate and already sorted, so when change an ArrayList to a TreeSet and
											// change it back, the element in the ArrayList will be deduplicated and sorted
		br = new BufferedReader( new FileReader( originFilePath ) ) ;
		bw = new BufferedWriter( new FileWriter( cleanedFilePath ) ) ;
		input = br.readLine() ;
		while ( input != null )
		{
			output = null ;
			lineCount ++ ;
			if ( lineCount / 1000 != lastLine )
			{
				lastLine = lineCount / 1000 ;
				System.out.println( "Current line :" + lineCount ) ;
			}
			inputSplit = input.split( "," ) ;
			currentUserID = Integer.valueOf( inputSplit [ 0 ] ) ;
			if ( currentUserID != lastUserID )
			{
				if ( lastUserID != -1 )
				{
					deduplicate = new TreeSet<Integer> ( standbyList ) ; // clean the duplication in the standbyList
					standbyList = new ArrayList<Integer> ( deduplicate ) ; // according to the record UID:115037563 , it may have same purchase record due to input error , deduplicate for further uses.
					for ( int i = 0 ; i < standbyList.size() ; i ++ )
					{
						output = String.valueOf( userList.indexOf( String.valueOf( lastUserID ) ) ) + "," ;
						output += String.valueOf( standbyList.get( i ) ) ;
						output += ",0" ; 
						if ( output != null )
						{
							bw.write( output ) ;
							bw.newLine() ;
						}
					}
				}
				standbyList.clear() ;
				lastUserID = currentUserID ;
				gameStatus = new int [ GAME_TOTAL ] ;
			}
			output = String.valueOf( userList.indexOf( String.valueOf( currentUserID ) ) ) ;
			output += "," ;
			inputSplit = input.split( "\"" ) ;
			gameIndex = gameList.indexOf( inputSplit [ 1 ] ) ;
			if ( gameIndex == -1 )
			{
				System.err.println( "Can not find \"" + inputSplit [ 1 ] + "\" in current record." ) ;
				return ;
			}
			output += String.valueOf( gameIndex ) ;
			output += "," ;
			inputSplit = inputSplit [ 2 ].split( "," ) ;
			if ( inputSplit [ 1 ].equals( "purchase" ) ) // same logic in the dataStat(), when a record is "purchase", add it in the standbyList, and if it has
														// a "play" record accordingly, delete it from the standbyList. Anything that left in the standbyList
														// will be considered a 0.0 play time
			{
				if ( dlcStatus [ gameIndex ] == gameIndex ) 
				{
					if ( gameStatus [ gameIndex ] != 1 )
					{
						standbyList.add( gameIndex ) ;
					}
					input = br.readLine() ;
					continue ;
				}
				else
				{
					output += "0" ;
				}
			}
			else
			{
				gameStatus [ gameIndex ] = 1 ;
				if ( standbyList.indexOf( gameIndex ) != -1 )
				{
					standbyList.remove( standbyList.indexOf( gameIndex ) ) ;
				}
				output += Double.valueOf( inputSplit [ 2 ] ) ;
			}
			bw.write( output ) ;
			bw.newLine() ;
			input = br.readLine() ;
		}
		bw.flush() ;
		bw.close() ;
		br.close() ;
		return ;
	}
	
	private static void countUpdate () throws IOException // when data has been cleaned and deduplicated, update user and game's count again to maintain precise
	{
		String input ;
		String inputSplit[] ;
		userCount = new int [ USER_TOTAL ] ;
		gameCount = new int [ GAME_TOTAL ] ;
		for ( int i = 0 ; i < USER_TOTAL ; i ++ )
		{
			userCount [ i ] = 0 ;
		}
		for ( int i = 0 ; i < GAME_TOTAL ; i ++ )
		{
			gameCount [ i ] = 0 ;
		}
		br = new BufferedReader( new FileReader( cleanedFilePath ) ) ;
		input = br.readLine() ;
		while ( input != null )
		{
			inputSplit = input.split( "," ) ;
			userCount [ Integer.valueOf( inputSplit [ 0 ] ) ] ++ ;
			gameCount [ Integer.valueOf( inputSplit [ 1 ] ) ] ++ ;
			input = br.readLine() ;
		}
		br.close() ;
	}
	
	private static void saveUserStat () throws IOException // save the user statistics for manually check and reuse
	{
		bw = new BufferedWriter( new FileWriter( userStatFilePath ) ) ;
		bw.write( "total user quantity" ) ;
		bw.newLine() ;
		bw.write( String.valueOf( USER_TOTAL ) ) ;
		bw.newLine();
		bw.write( "user number,user ID,game consumed" ) ;
		bw.newLine() ;
		for ( int i = 0 ; i < USER_TOTAL ; i ++ )
		{
			bw.write( String.valueOf( i ) ) ;
			bw.write( "," ) ;
			bw.write( String.valueOf( userList.get( i ) ) ) ;
			bw.write( "," ) ;
			bw.write( String.valueOf( userCount [ i ] ) ) ;
			bw.newLine() ;
		}
		bw.flush() ;
		bw.close() ;
		return ;
	}
	
	private static void saveGameStat () throws IOException // save the game statistics for manually check and reuse
	{
		bw = new BufferedWriter( new FileWriter( gameStatFilePath ) ) ;
		bw.write( "total game quantity" ) ;
		bw.newLine() ;
		bw.write( String.valueOf( GAME_TOTAL ) ) ;
		bw.newLine() ;
		bw.write( "game ID,game name,consumed times,dlc origin" ) ;
		bw.newLine() ;
		for ( int i = 0 ; i < GAME_TOTAL ; i ++ )
		{
			bw.write( String.valueOf( i ) ) ;
			bw.write( "," ) ;
			bw.write( "\"" + gameList.get( i ) + "\"" ) ;
			bw.write( "," ) ;
			bw.write( String.valueOf( gameCount [ i ] ) ) ;
			bw.write( "," ) ;
			if ( dlcStatus [ i ] != i )
			{
				bw.write( String.valueOf( dlcStatus [ i ] ) ) ;
			}
			else
			{
				bw.write( "-1" ) ;
			}
			bw.newLine() ;
		}
		bw.flush() ;
		bw.close() ;
		return ;
	}
	
	private static void loadUserStat () throws IOException // load the user statistics
	{
		int i = 0 ;
		String input ;
		String inputSplit[] ;
		userList = new ArrayList<String> () ;
		br = new BufferedReader( new FileReader( userStatFilePath ) ) ;
		
		input = br.readLine() ;
		input = br.readLine() ;
		USER_TOTAL = Integer.valueOf( input ) ;
		userCount = new int [ USER_TOTAL ] ;
		
		input = br.readLine() ;
		input = br.readLine() ;
		while ( input != null )
		{
			inputSplit = input.split( "," ) ;
			userList.add( inputSplit [ 1 ] ) ;
			userCount [ i ] = Integer.valueOf( inputSplit [ 2 ] ) ;
			i ++ ;
			input = br.readLine() ;
		}
		br.close() ;
		return ;
	}
	
	private static void loadGameStat () throws IOException// load the game statistics
	{
		int i = 0 ;
		int dlcID ;
		String input ;
		String inputSplit[] ;
		gameList = new ArrayList<String> () ;
		br = new BufferedReader( new FileReader( gameStatFilePath ) ) ;
		
		input = br.readLine() ;
		input = br.readLine() ;
		GAME_TOTAL = Integer.valueOf( input ) ;
		gameCount = new int [ GAME_TOTAL ] ;
		dlcStatus = new int [ GAME_TOTAL ] ;
		
		input = br.readLine() ;
		input = br.readLine() ;
		while ( input != null )
		{
			inputSplit = input.split( "\"" ) ; // the reason why using '"' instead of ',' to be the split character is that, one of the game contains ',' in the middle of the name
			gameList.add( inputSplit [ 1 ] ) ;
			inputSplit = inputSplit [ 2 ].split( "," ) ;
			gameCount [ i ] = Integer.valueOf( inputSplit [ 1 ] ) ;
			dlcID = Integer.valueOf( inputSplit [ 2 ] ) ;
			if ( dlcID == -1 )
			{
				dlcStatus [ i ] = i ;
			}
			else
			{
				dlcStatus [ i ] = dlcID ;
			}
			i ++ ;
			input = br.readLine() ;
		}
		br.close() ;
		return ;
	}
	
	private static void getTotalTimePartition () throws IOException // use all play record's average time as a like-dislike separate standard
	{
		ArrayList<Double> timeList = new ArrayList<Double> () ;
		ArrayList<Double> mergedTimeList = new ArrayList<Double> () ;
		ArrayList<Integer> mergedLineCount = new ArrayList<Integer> () ;
		String input = null ;
		String inputSplit[] ;
		int currentGameID ;
		int lineCount = 0 ;	
		int dlcCount = 0 ;
		double currentTime ;
		double currentPercentage = 0.05 ;
		br = new BufferedReader( new FileReader( cleanedFilePath ) ) ;
		bw = new BufferedWriter( new FileWriter( totalTimePartitionFilePath ) ) ;
		
		input = br.readLine() ;
		while ( input != null )
		{
			lineCount ++ ;
			inputSplit = input.split( "," ) ;
			currentGameID = Integer.valueOf( inputSplit [ 1 ] ) ;
			if ( dlcStatus [ currentGameID ] == currentGameID )
			{
				timeList.add( Double.valueOf( inputSplit [ 2 ] ) ) ;
			}
			else
			{
				dlcCount ++ ;
			}
			input = br.readLine() ;
		}
		System.out.println( dlcCount + " of dlc deleted" ) ;
		Collections.sort( timeList ) ;
		currentTime = timeList.get( 0 ) ;
		lineCount = 0 ;
		for ( int i = 0 ; i < timeList.size() ; i ++ )
		{
			if ( timeList.get( i ) != currentTime )
			{
				mergedTimeList.add( currentTime );
				mergedLineCount.add( lineCount ) ;
				currentTime = timeList.get( i ) ;
				continue ;
			}
			else
			{
				lineCount ++ ;
			}
		}
		System.out.println( "total line count : " + lineCount ) ;
		bw.write( "total line count : " + lineCount );
		bw.newLine() ;
		while ( currentPercentage < 1.0 )
		{
			currentPercentage = ( (int)( currentPercentage * 100 ) / 100.0 ) ;
			for ( int i = 0 ; i < mergedLineCount.size() ; i ++ )
			{
				if ( i == 0 )
				{
					if ( mergedLineCount.get( i ) > (int)( currentPercentage * lineCount ) )
					{
						bw.write( ( 100 - (int)(currentPercentage * 100) ) + "% of players have played : " + mergedTimeList.get( i ) ) ;
						bw.newLine() ;
						break ;
					}
				}
				if ( mergedLineCount.get( i ) > (int)( currentPercentage * lineCount ) && mergedLineCount.get( i - 1 ) <  (int)( currentPercentage * lineCount ) )
				{
					bw.write( ( 100 - (int)(currentPercentage * 100) ) + "% of players have played : " + mergedTimeList.get( i ) ) ;
					bw.newLine() ;
					break ;
				}
			}
			currentPercentage += 0.05 ;
		}
		br.close() ;
		bw.flush() ;
		bw.close() ;
		return ;
	}
	
	private static void getDetailedTimePartition () throws IOException // use each game's average played time as like-dislike separate standard
	{
		int userThreshold ;
		int advancedThreshold ;
		String input ;
		String inputSplit[] ;
		ArrayList<Double> tempCounter ;
		userGameList = new double [ GAME_TOTAL ][ USER_TOTAL ] ;
		for ( int i= 0 ; i < GAME_TOTAL ; i ++ )
		{
			for ( int j = 0 ; j < USER_TOTAL ; j ++ )
			{
				userGameList [ i ] [ j ] = -1 ;
			}
		}
		br = new BufferedReader( new FileReader( cleanedFilePath ) ) ;
		input = br.readLine() ;
		while ( input != null )
		{
			inputSplit = input.split( "," ) ;
			userGameList [ Integer.valueOf( inputSplit [ 1 ] ) ] [ Integer.valueOf( inputSplit [ 0 ] ) ] = Double.valueOf( inputSplit [ 2 ] ) ; 
			input = br.readLine() ;
		}
		br.close() ;
		bw = new BufferedWriter( new FileWriter( gameThresholdFilePath ) ) ;
		bw.write( "Current Mode :" ) ;
		bw.newLine() ;
		if ( ADVANCED_ACTIVATED == true )
		{
			bw.write( "Advanced" ) ;
		}
		else
		{
			bw.write( "Single" ) ;
		}
		bw.newLine() ;
		bw.write( "Current threshold : " ) ;
		bw.newLine() ;
		bw.write( String.format( "%.1f" , PREFERENCE_PARTITION * 0.05 ) ) ;
		if ( ADVANCED_ACTIVATED == true )
		{
			bw.write( "," + String.format( "%.1f" , ADVANCED_PARTITION * 0.05 ) ) ;
		}
		bw.newLine() ;
		bw.write( "game ID , partition time" ) ;
		bw.newLine() ;
		for ( int i = 0 ; i < GAME_TOTAL ; i ++ )
		{
			if ( dlcStatus [ i ] != i )
			{
				continue ;
			}
			tempCounter = new ArrayList<Double>() ;
			for ( int j = 0 ; j < USER_TOTAL ; j ++ )
			{
				if ( userGameList [ i ] [ j ] != -1 )
				{
					tempCounter.add( userGameList [ i ] [ j ] ) ;
				}
			}
			Collections.sort( tempCounter ) ;
			userThreshold = (int)( gameCount [ i ] * PREFERENCE_PARTITION * 0.05 ) ;
			bw.write( i + "," + tempCounter.get( userThreshold ) ) ;
			if ( ADVANCED_ACTIVATED = true )
			{
				advancedThreshold = (int) ( gameCount [ i ] * ADVANCED_PARTITION * 0.05 ) ;
				bw.write( "," + tempCounter.get( advancedThreshold ) ) ;
			}
			bw.newLine() ;
		}
		bw.flush() ;
		bw.close() ;
		userGameList = null ;
		return ;
	}
	
	private static void loadTotalTimePartition () throws IOException // load separate standard for method 1
	{
		String input ;
		br = new BufferedReader( new FileReader( totalTimePartitionFilePath ) ) ;
		input = br.readLine() ;
		for ( int i = 0 ; i < TOTAL_TIME_THRESHOLD ; i ++ )
		{
			input = br.readLine() ;
		}
		TOTAL_TIME_PARTITION = Double.valueOf( input.substring( input.indexOf( ":" ) + 1 , input.length() ).trim() ) ;
		br.close() ;
		return ;
	}
	
	private static void loadDetailedTimePartition () throws IOException // load separate standard for method 2 and 3
	{
		String input = null ;
		String inputSplit[] ;
		int recordMode = 0 ;
		gamePartition = new double [ GAME_TOTAL ] ;
		br = new BufferedReader( new FileReader( gameThresholdFilePath ) ) ;
		for ( int i = 0 ; i < GAME_TOTAL ; i ++ )
		{
			gamePartition [ i ] = 0 ;
		}
		for ( int i = 0 ; i < 6 ; i ++ )
		{
			input = br.readLine() ;
			if ( i == 1 )
			{
				if ( input.indexOf( "Single" ) != -1 )
				{
					recordMode = 1 ;
				}
				else
				{
					recordMode = 2 ;
					advancedPartition = new double [ GAME_TOTAL ] ;
				}
			}
		}
		while ( input != null )
		{
			inputSplit = input.split( "," ) ;
			gamePartition [ Integer.valueOf( inputSplit [ 0 ] ) ] = Double.valueOf( inputSplit [ 1 ] ) ;
			if ( recordMode == 2 )
			{
				advancedPartition[ Integer.valueOf( inputSplit [ 0 ] ) ] = Double.valueOf( inputSplit [ 2 ] ) ;
			}
			input = br.readLine() ;
		}
		br.close() ;
		return ;
	}
	
	public static void loadData () throws IOException // MainBlock interaction access point
	{
		loadUserStat() ;
		loadGameStat() ;
		loadTotalTimePartition() ;
		loadDetailedTimePartition() ;
		return ;
	}

	public static void main ( String[] args ) throws IOException
	{
		getGameList() ;
		dataStat() ;
		cleanOriginData () ;
		countUpdate() ;
		saveUserStat() ;
		saveGameStat() ;
		getTotalTimePartition() ;
		getDetailedTimePartition() ;
		
	}
	
}
