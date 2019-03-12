package bonusAssignment;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

import javax.swing.JFrame;

public class MainBlock 
{
	private static double TRAIN_TEST_RATIO = 0.7 ; // defining the partition of training and testing set
	private static int MEASURE_STANDARD = 15 ; // defining the quantity of predict result when drawing ROC curve
	private static int LINE_GRANULARITY = 1 ; // defining the plot granularity when using point and line to draw the curve , smaller means the curve are more likely be smoothly
	private static int EXTRA_WEIGHT = 5 ; // defining the extra weight in method 3
	
	private static String cleanedFilePath = "data" + File.separator + "steam-200k-cleaned.csv" ;
	private static String rawTrainingFilePath = "data" + File.separator + "raw-training.csv" ;
	private static String testingFilePath = "data" + File.separator + "testing.csv" ;
	private static String userVectorFilePath = "data" + File.separator + "user-vector.csv" ;
	private static String vectorRankFilePath = "data" + File.separator + "vector-rank.csv" ;
	
	private static int GAME_TOTAL = 0 ; // quantity of game
	private static int USER_TOTAL = 0 ; // number of user
	private static double TOTAL_TIME_PARTITION = 0 ; // the like-unlike division time used in method 1 , defined by pre-process
	private static int userCount[] ; // recording the number of games which an user has purchased ( the degree of the user )
	private static int gameCount[] ; // recording the number of users which a game has been purchased ( the degree of the game )
	private static int dlcStatus[] ; // recording the DLC status of the game
	private static int trainingSet[][] ; // recording the training partition of data set
	private static int testingSet[][] ; // recording the testing partition of data set
	private static int vectorRank[][] ; // recording the games' recommendation rank per user after process
	private static double rawTrainingSet[][] ; // recording the raw partition after separate train and test data , detailed training set may have slightly differ from each method
	private static double gamePartition[] ; // recording the like-unlike division time for each game used in method 2 , defined by pre-process
	private static double advancedPartition[] ; // recording the like-very like division time for each game used in method 3 , defined by pre-process ( very like means higher weight )
	private static double resDist[][] ; // resource distribution array , main part of the algorithm
	private static double userVector[][] ; // user resource vector , main part of the algorithm
	
	private static ArrayList<String> gameList = null ; // recording game's name
	private static ArrayList<String> userList = null ; // recording user's id
	private static BufferedReader br = null ;
	private static BufferedReader br2 = null ;
	private static BufferedWriter bw = null ;
	private static BufferedWriter bw2 = null ;
	
	// JFrame configuration ( JFrame being used to draw curve )
	private static int coodinateZeroPointX = 90 ;
	private static int coodinateZeroPointY = 650 ;
	private static int coodinateXLength = 600 ;
	private static int coodinateYLength = 600 ;
	
	private static int frameStartPointX = 150 ;
	private static int frameStartPointY = 150 ;
	private static int frameWidth = 780 ;
	private static int frameHeight = 700 ;
	
	// ROC curve data storage
	private static int currentPattern = 0 ; // indicate the method used in the calculation. the results from different method will be store differently
	private static int lastPattern = 0 ; // use in the mainBlockSupUnit , indicate last used method
	private static double pattern1Width[] = null ;
	private static double pattern1Height[] = null ;
	private static double pattern2Width[] = null ;
	private static double pattern2Height[] = null ;
	private static double pattern3Width[] = null ;
	private static double pattern3Height[] = null ;
	
	private static void loadPreProcessData () throws IOException // load pre-process data from PreProcess block
	{
		System.out.println( "Loading Pre-Process Data..." ) ;
		PreProcess.loadData() ;
		GAME_TOTAL = PreProcess.GAME_TOTAL ;
		USER_TOTAL = PreProcess.USER_TOTAL ;
		TOTAL_TIME_PARTITION = PreProcess.TOTAL_TIME_PARTITION ;
		userCount = PreProcess.userCount ;
		gameCount = PreProcess.gameCount ;
		dlcStatus = PreProcess.dlcStatus ;
		gamePartition = PreProcess.gamePartition ;
		advancedPartition = PreProcess.advancedPartition ;
		gameList = PreProcess.gameList ;
		userList = PreProcess.userList ;
		return ;
	}
	
	private static void trainTestPartition () throws IOException // separate raw training set and testing set based on TRAIN_TEST_RATIO
	{
		System.out.println( "Partitioning training and testing set..." ) ;
		String input ;
		String inputSplit[] ;
		rawTrainingSet = new double [ GAME_TOTAL ] [ USER_TOTAL ] ;
		testingSet = new int [ GAME_TOTAL ] [ USER_TOTAL ] ;
		
		br = new BufferedReader( new FileReader( cleanedFilePath ) ) ;
		input = br.readLine() ; // input will be like ('user serial number','game serial number','played time')
		while ( input != null )
		{
			inputSplit = input.split( "," ) ;
			if ( Math.random() < TRAIN_TEST_RATIO )
			{
				if ( isDlc( inputSplit [ 1 ] ) == false )
				{
					rawTrainingSet [ Integer.valueOf( inputSplit [ 1 ] ) ] [ Integer.valueOf( inputSplit [ 0 ] ) ] = Double.valueOf( inputSplit [ 2 ] ) ;
				}
				else
				{
					rawTrainingSet [ Integer.valueOf( inputSplit [ 1 ] ) ] [ Integer.valueOf( inputSplit [ 0 ] ) ] = -1 ;
				}
			}
			else
			{
				testingSet [ Integer.valueOf( inputSplit [ 1 ] ) ] [ Integer.valueOf( inputSplit [ 0 ] ) ] = 1 ;
			}
			input = br.readLine() ;
		}
		br.close() ;
		
		bw = new BufferedWriter( new FileWriter( rawTrainingFilePath ) ) ;
		bw2 = new BufferedWriter( new FileWriter( testingFilePath ) ) ;
		for ( int i = 0 ; i < GAME_TOTAL ; i ++ )
		{
			for ( int j = 0 ; j < USER_TOTAL ; j ++ )
			{
				if ( j != 0 )
				{
					bw.write( "," ) ;
					bw2.write( "," ) ;
				}
				bw.write( String.valueOf( rawTrainingSet [ i ] [ j ] ) ) ;
				bw2.write( String.valueOf( testingSet [ i ] [ j ] ) ) ;
			}
			if ( i != GAME_TOTAL - 1 )
			{
				bw.newLine() ;
				bw2.newLine() ;
			}
		}
		bw.flush() ;
		bw2.flush() ;
		bw.close() ;
		bw2.close() ;
		return ;
	}
	
	private static boolean isDlc ( String gameIdString ) // check if current game id is a DLC
	{
		int gameID = Integer.valueOf( gameIdString ) ;
		if ( dlcStatus [ gameID ] != gameID )
		{
			return true ;
		}
		return false ;
	}
	
	private static void resetParameters () // reseting large array to free up some space
	{
		trainingSet = null ;
		rawTrainingSet = null ;
		testingSet = null ;
		vectorRank = null ;
		userVector = null ;
		currentPattern = 0 ;
		System.gc() ; // force JVM to collect garbage, decrease the heap use
	}
	
	private static void loadExistData () throws IOException // load raw training set and testing set from the file
	{
		System.out.println( "Loading exist training and testing set..." ) ;
		int i ;
		String input ;
		String inputSplit[] ;
		rawTrainingSet = new double [ GAME_TOTAL ] [ USER_TOTAL ] ;
		testingSet = new int [ GAME_TOTAL ] [ USER_TOTAL ] ;
		
		br = new BufferedReader( new FileReader( rawTrainingFilePath ) ) ;
		i = 0 ;
		input = br.readLine() ;
		while ( input != null )
		{
			inputSplit = input.split( "," ) ;
			for ( int j = 0 ; j < inputSplit.length ; j ++ )
			{
				rawTrainingSet [ i ] [ j ] = Double.valueOf( inputSplit [ j ] ) ;
			}
			i ++ ;
			input = br.readLine() ;
		}
		br.close() ;
		
		br = new BufferedReader( new FileReader( testingFilePath ) ) ;
		i = 0 ;
		input = br.readLine() ;
		while ( input != null )
		{
			inputSplit = input.split( "," ) ;
			for ( int j = 0 ; j < inputSplit.length ; j ++ )
			{
				testingSet [ i ] [ j ] = Integer.valueOf( inputSplit [ j ] ) ;
			}
			i ++ ;
			input = br.readLine() ;
		}
		br.close() ;
		return ;
	}
	
	private static void totalTimeMethodInit () throws IOException // method 1 , divide like and unlike data by a time standard based on an average time counting from every play record
	{
		System.out.println() ;
		System.out.println( "Current Method : Total time partition" ) ;
		System.out.println( "Re-constructing training set from raw data" ) ;
		currentPattern = 1 ;
		
		trainingSet = new int [ GAME_TOTAL ] [ USER_TOTAL ] ;
		for ( int i = 0 ; i < GAME_TOTAL ; i ++ )
		{
			for ( int j = 0 ; j < USER_TOTAL ; j ++ )
			{
				trainingSet [ i ] [ j ] = 0 ;
			}
		}
		
		for ( int i = 0 ; i < GAME_TOTAL ; i ++ )
		{
			for ( int j = 0 ; j < USER_TOTAL ; j ++ )
			{
				if ( rawTrainingSet [ i ] [ j ] >= TOTAL_TIME_PARTITION || rawTrainingSet [ i ] [ j ] < 0 ) // rawTrainingSet == 1 means it's an DLC
				{
					trainingSet [ i ] [ j ] = 1 ;
				}
			}
		}
		
		rawTrainingSet = null ;
		System.gc() ;
		return ;
	}
	
	private static void detailTimeMethodInit () throws IOException // method 2 , divide like and unlike data by time standards based on an average time differ from each game
	{
		System.out.println() ;
		System.out.println( "Current Method : Detail time partition" ) ;
		System.out.println( "Re-constructing training set from raw data" ) ;
		currentPattern = 2 ;
		
		trainingSet = new int [ GAME_TOTAL ] [ USER_TOTAL ] ;
		for ( int i = 0 ; i < GAME_TOTAL ; i ++ )
		{
			for ( int j = 0 ; j < USER_TOTAL ; j ++ )
			{
				trainingSet [ i ] [ j ] = 0 ;
			}
		}
		
		for ( int i = 0 ; i < GAME_TOTAL ; i ++ )
		{
			for ( int j = 0 ; j < USER_TOTAL ; j ++ )
			{
				if ( rawTrainingSet [ i ] [ j ] != 0 && ( rawTrainingSet [ i ] [ j ] >= gamePartition [ i ] || rawTrainingSet [ i ] [ j ] < 0 ) ) // some game may have a time standard of "0.0",
																																				// so need to filter the Non-Play before separating the like and dislike
				{
					trainingSet [ i ] [ j ] = 1 ;
				}
			}
		}
		
		rawTrainingSet = null ;
		System.gc() ;
		return ;
	}
	
	private static void advancedTimeMethodInit () throws IOException // method 3 , same as method 2 , but doing further separation on like and very like by time standards based 
																	//on an average time of "like part". very like will have a higher weight in the calculation
	{
		System.out.println() ;
		System.out.println( "Current Method : Advanced detail time partition" ) ;
		System.out.println( "Re-constructing training set from raw data" ) ;
		currentPattern = 3 ;
		
		trainingSet = new int [ GAME_TOTAL ] [ USER_TOTAL ] ;
		for ( int i = 0 ; i < GAME_TOTAL ; i ++ )
		{
			for ( int j = 0 ; j < USER_TOTAL ; j ++ )
			{
				trainingSet [ i ] [ j ] = 0 ;
			}
		}
		
		for ( int i = 0 ; i < GAME_TOTAL ; i ++ )
		{
			for ( int j = 0 ; j < USER_TOTAL ; j ++ )
			{
				if ( rawTrainingSet [ i ] [ j ] != 0 && ( rawTrainingSet [ i ] [ j ] >= gamePartition [ i ] || rawTrainingSet [ i ] [ j ] < 0 ) )
				{
					if ( rawTrainingSet [ i ] [ j ] >= advancedPartition [ i ] ) // very like group will have a higher weight in the calculation
					{
						trainingSet [ i ] [ j ] = EXTRA_WEIGHT ;
					}
					else
					{
						trainingSet [ i ] [ j ] = 1 ;
					}
				}
			}
		}
		
		rawTrainingSet = null ;
		System.gc() ;
		return ;
	}
	
	private static void countUpdate () // update the game and user's count after training set is finished
	{
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
		
		for ( int i = 0 ; i < GAME_TOTAL ; i ++ )
		{
			for ( int j = 0 ; j < USER_TOTAL ; j ++ )
			{
				if ( trainingSet [ i ] [ j ] != 0 )
				{
					gameCount [ i ] ++ ;
					userCount [ j ] ++ ;
				}
			}
		}
		return ;
	}
	
	private static void countResDistArray () // counting resource distribution array
	{
		System.out.println( "Calculating resource distibution array" ) ;
		resDist = new double [ GAME_TOTAL ] [ GAME_TOTAL ];
		for ( int i = 0 ; i < GAME_TOTAL ; i ++ )
		{
			for ( int j = 0 ; j < GAME_TOTAL ; j ++ )
			{
				resDist [ i ] [ j ] = 0 ;
			}
		}
		
		for ( int l = 0 ; l < USER_TOTAL ; l ++ )
		{
			if ( l % 1000 == 0 )
			{
				System.out.println( l + " of " + USER_TOTAL + " data processed" ) ;
			}
			for ( int i = 0 ; i < GAME_TOTAL ; i ++ )
			{
				for ( int j = i ; j < GAME_TOTAL ; j ++ )
				{
					if ( trainingSet [ i ] [ l ] != 0 && trainingSet [ j ] [ l ] != 0 )
					{
						if ( userCount [ l ] != 0 )
						{
							resDist [ i ] [ j ] += ( 1.0 / (double)userCount [ l ] ) * trainingSet [ i ] [ l ] ; // the " * trainingSet " part is mainly use in method 3, but since all vector 
																												// will be 0 and 1 in the first 2 method, it won't have a side effect
							if ( i != j )
							{
								resDist [ j ] [ i ] += ( 1.0 / (double)userCount [ l ] ) * trainingSet [ j ] [ l ] ;
							}
						}
					}
				}
			}
		}
		
		for ( int j = 0 ; j < GAME_TOTAL ; j ++ )
		{
			for ( int i = 0 ; i < GAME_TOTAL ; i ++ )
			{
				if ( gameCount [ j ] != 0 )
				{
					resDist [ i ] [ j ] /= (double) gameCount [ j ] ;
				}
				else
				{
					resDist [ i ] [ j ] = 0 ;
				}
			}
		}
		return ;
	}
	
	private static void countAllUserVector () // counting all user's resource vector
	{
		System.out.println( "Calculating all user's resource distribution vector" ) ;
		userVector = new double [ GAME_TOTAL ] [ USER_TOTAL ] ;
		vectorRank = new int [ GAME_TOTAL ] [ USER_TOTAL ] ;
		for ( int i = 0 ; i < USER_TOTAL ; i ++ )
		{
			if ( i % 1000 == 0 )
			{
				System.out.println( i + " of " + USER_TOTAL + " data processed" ) ;
			}
			getUserVector ( i ) ;
		}
		return ;
	}
	
	private static void getUserVector ( int userID ) // counting designated user's resource vector
	{
		double vector[] = new double [ GAME_TOTAL ] ;
		double vectorAux[] = new double [ GAME_TOTAL ] ;
		int gameID[] = new int [ GAME_TOTAL ] ;
		int gameIDAux[] = new int [ GAME_TOTAL ] ;
		
		for ( int j = 0 ; j < GAME_TOTAL ; j ++ ) // getting data from the resource distribution array
		{
			for ( int i = 0 ; i < GAME_TOTAL ; i ++ )
			{
				if ( trainingSet [ j ] [ userID ] != 0 )
				{
					userVector [ i ] [ userID ] += resDist [ i ] [ j ] ;
				}
			}
		}
		
		for ( int i = 0 ; i < GAME_TOTAL ; i ++ ) // use gameID to mark all game's id, and the result after the sort will be like: gameID's position = recommend position
												// gameID's storage = game's id
		{
			vector [ i ] = userVector [ i ] [ userID ] ;
			gameID [ i ] = i ;
		}
		bottomUpMergeSort( vector , vectorAux , gameID , gameIDAux , GAME_TOTAL ) ;
		
		for ( int i = 0 ; i < GAME_TOTAL ; i ++ )
		{
			vectorRank [ i ] [ userID ] = gameID [ i ] ;
		}
		return ;
	}
	
	private static void saveAllUserVector () throws IOException // saving all user's resource vector to files ( for debug purpose, file will be too large, don't recommended to use )
	{
		System.out.println( "Saving all user's vector , please wait" ) ;
		bw = new BufferedWriter( new FileWriter( userVectorFilePath ) ) ;
		bw2 = new BufferedWriter( new FileWriter( vectorRankFilePath ) ) ;
		for ( int i = 0 ; i < GAME_TOTAL ; i ++ )
		{
			if ( i % 1000 == 0 )
			{
				System.out.println( i + " of " + USER_TOTAL + " data saved" ) ;
			}
			for ( int j = 0 ; j < USER_TOTAL ; j ++ )
			{
				if ( j != 0 )
				{
					bw.write( "," ) ;
					bw2.write( "," ) ;
				}
				bw.write( String.valueOf( userVector [ i ] [ j ] ) ) ;
				bw2.write( String.valueOf( vectorRank [ i ] [ j ] ) ) ;
			}
			if ( i != GAME_TOTAL - 1 )
			{
				bw.newLine() ;
				bw2.newLine() ;
			}
		}
		bw.flush() ;
		bw2.flush() ;
		bw.close() ;
		bw2.close() ;
		return ;
	}
	
	private static void loadAllUserVector () throws IOException // load all user's resource vector, only work when file has been saved
	{
		System.out.println( "Loading all user's vector infomation , please wait..." ) ;
		String input ;
		String input2 ;
		String inputSplit[] ;
		String inputSplit2[] ;
		userVector = new double [ GAME_TOTAL ] [ USER_TOTAL ] ;
		vectorRank = new int [ GAME_TOTAL ] [ USER_TOTAL ] ;
		br = new BufferedReader( new FileReader( userVectorFilePath ) ) ;
		br2 = new BufferedReader( new FileReader( vectorRankFilePath ) ) ;
		input = br.readLine() ;
		input2 = br2.readLine() ;
		int i = 0 ;
		while ( input != null )
		{
			if ( i % 1000 == 0 )
			{
				System.out.println( i + " of " + GAME_TOTAL + " data loaded" ) ;
			}
			inputSplit = input.split( "," ) ;
			inputSplit2 = input2.split( "," ) ;
			for ( int j = 0 ; j < USER_TOTAL ; j ++ )
			{
				userVector [ i ] [ j ] = Double.valueOf( inputSplit [ j ] ) ;
				vectorRank [ i ] [ j ] = Integer.valueOf( inputSplit2 [ j ] ) ;
			}
			input = br.readLine() ;
			input2 = br2.readLine() ;
			i ++ ;
		}
		br.close() ;
		br2.close() ;
		return ;
	}
	
	// merge sort operation , vector is the main part of the sort , and id will move accordingly when vector changes
	//  --------------------
	private static void mergeOperate ( double vector[] , double vectorAux[] , int id[] , int idAux[] , int low , int mid , int high ) 
	{
		int i ;
		int j ;
		int k ;
		i = low ;
		j = mid + 1 ;
		for ( k = low ; k <= high ; k ++ )
		{
			vectorAux [ k ] = vector [ k ] ;
			idAux [ k ] = id [ k ] ;
		}
		for ( k = low ; k <= high ; k ++ )
		{
			if ( i > mid )
			{
				vector [ k ] = vectorAux [ j ] ;
				id [ k ] = idAux [ j ] ;
				j ++ ;
			}
			else if ( j > high )
			{
				vector [ k ] = vectorAux [ i ] ;
				id [ k ] = idAux [ i ] ;
				i ++ ;
			}
			else if ( vectorAux [ j ] > vectorAux [ i ] )
			{
				vector [ k ] = vectorAux [ j ] ;
				id [ k ] = idAux [ j ] ;
				j ++ ;
			}
			else
			{
				vector [ k ] = vectorAux [ i ] ;
				id [ k ] = idAux [ i ] ;
				i ++ ;
			}
		}
		return ;
	}
	
	private static void bottomUpMergeSort ( double vector[] , double vectorAux[] , int id[] , int idAux[] , int length )
	{
		for ( int i = 1 ; i < length ; i *= 2 )
		{
			for ( int low = 0 ; low < length ; low += 2 * i )
			{
				mergeOperate( vector , vectorAux , id , idAux , low , low + i - 1 , ( ( low + 2 * i - 1 ) < ( length - 1 ) ? ( low + 2 * i - 1 ) : ( length - 1 ) ) ) ;
			}
		}
		return ;
	}
	// --------------------
	
	// result query part
	// --------------------
	private static void showPurchaseHistory ( int userSerialNumber )
	{
		System.out.println( "For user " + userList.get( userSerialNumber ) ) ;
		System.out.println( "the purchase history is :" ) ;
		for ( int i = 0 ; i < GAME_TOTAL ; i ++ )
		{
			if ( trainingSet [ i ] [ userSerialNumber ] != 0 )
			{
				System.out.println( gameList.get( i ) ) ;
			}
		}
		return ;
	}
	private static void showRecommendation ( int userSerialNumber )
	{
		int offset = 0 ;
		System.out.println( "the top 10 recommendation games are :" ) ;
		for ( int i = 0 ; i < 10 + offset ; i ++ )
		{
			if ( trainingSet [ vectorRank [ i ] [ userSerialNumber ] ] [ userSerialNumber ] != 0 )
			{
				offset ++ ;
				continue ;
			}
			System.out.println( i + 1 - offset + " : " + gameList.get( vectorRank [ i ] [ userSerialNumber ] ) + " with recommendation rating " + userVector [ vectorRank [ i ] [ userSerialNumber ] ] [ userSerialNumber ] ) ;
		}
		return ;
	}
	
	private static void resultQueryByUserSerialNumber ( int userSerialNumber )
	{
		showPurchaseHistory( userSerialNumber ) ;
		showRecommendation( userSerialNumber ) ;
		return ;
	}
	
	private static void resultQueryByUserID ( int userID )
	{
		int userSerialNumber = userList.indexOf( String.valueOf( userID ) ) ;
		resultQueryByUserSerialNumber( userSerialNumber ) ;
		return ;
	}
	
	private static void queryControl ( int input )
	{
		if ( input < USER_TOTAL )
		{
			resultQueryByUserSerialNumber( input ) ;
		}
		else
		{
			if ( userList.indexOf( String.valueOf( input ) ) != -1 )
			{
				resultQueryByUserID( input ) ;
			}
			else
			{
				System.out.println( "Input Invalid : Input is not an user id or an user serial number " ) ;
			}
		}
		return ;
	}
	// --------------------
	
	// ROC curve preparing and drawing part
	//  --------------------
	private static void rocPreparation () // prepare the point in the curve
	{
		System.out.println( "Calculating ROC curve..." ) ;
		int count = 0 ;
		int tpCount = 0 ;
		int fpCount = 0 ;
		int tpTotal = 0 ;
		int fpTotal = 0 ;
		double TPRatio[] = new double [ GAME_TOTAL / LINE_GRANULARITY  + 1 ] ;
		double FPRatio[] = new double [ GAME_TOTAL / LINE_GRANULARITY  + 1 ] ;
		
		for ( int i = 0 ; i < MEASURE_STANDARD ; i ++ ) // calculating the whole true-positive and false-positive times
		{
			for ( int j = 0 ; j < USER_TOTAL ; j ++ )
			{
				if ( testingSet [ vectorRank [ i ] [ j ] ] [ j ] == 1 || trainingSet [ vectorRank [ i ] [ j ] ] [ j ] != 0 )
				{
					tpTotal ++ ;
				}
				else
				{
					fpTotal ++ ;
				}
			}
		}
		
		for ( int i = 0 ; i < MEASURE_STANDARD ; i ++ )
		{
			if ( i % LINE_GRANULARITY  == 0 )
			{
				TPRatio [ count ] = (double)tpCount / (double)tpTotal ;
				FPRatio [ count ] = (double)fpCount / (double)fpTotal ;
				count ++ ;
			}
			for ( int j = 0 ; j < USER_TOTAL ; j ++ )
			{
				if ( testingSet [ vectorRank [ i ] [ j ] ] [ j ] == 1 || trainingSet [ vectorRank [ i ] [ j ] ] [ j ] != 0 )
				{
					tpCount ++ ;
				}
				else
				{
					fpCount ++ ;
				}
			}
		}
		TPRatio [ count ] = (double)tpCount / (double)tpTotal ;
		FPRatio [ count ] = (double)fpCount / (double)fpTotal ;
		switch ( currentPattern )
		{
			case 1 :
			{
				pattern1Height = new double [ TPRatio.length ] ;
				pattern1Width = new double [ FPRatio.length] ;
				for ( int i = 0 ; i < TPRatio.length ; i ++ )
				{
					pattern1Height [ i ] = TPRatio [ i ] ;
					pattern1Width [ i ] = FPRatio [ i ] ;
				}
				break;
			}
			case 2 :
			{
				pattern2Height = new double [ TPRatio.length ] ;
				pattern2Width = new double [ FPRatio.length ] ;
				for ( int i = 0 ; i < TPRatio.length ; i ++ )
				{
					pattern2Height [ i ] = TPRatio [ i ] ;
					pattern2Width [ i ] = FPRatio [ i ] ;
				}
				break;
			}
			case 3 :
			{
				pattern3Height = new double [ TPRatio.length ] ;
				pattern3Width = new double [ FPRatio.length ] ;
				for ( int i = 0 ; i < TPRatio.length ; i ++ )
				{
					pattern3Height [ i ] = TPRatio [ i ] ;
					pattern3Width [ i ] = FPRatio [ i ] ;
				}
				break;
			}
			default :
			{
				break ;
			}
		}
	}
	
	static class DrawRoc extends JFrame // class that can make java show's graph and curve
	{
		private static final long serialVersionUID = 1L ;
		
		@Override
		public void paint ( Graphics g )
		{
			super.paint( g ) ;
			Graphics2D g2d = (Graphics2D)g ;
			g.setColor( Color.BLACK ) ;
			g.setFont( new Font( "Times New Roman" , Font.PLAIN , 22 ) ) ;
			g.drawString( "TP-Ratio" , 5 , 70 ) ;
			g.drawString( "FP-Ratio" , frameWidth - 160 , frameHeight - 25 ) ;
			g.setFont( new Font( "Times New Roman" , Font.PLAIN , 20 ) ) ;
			g.drawString( "Legend :" , frameWidth - 180 , frameHeight - 200 ) ;
			g.drawString( "Method 1:" , frameWidth - 180 , frameHeight - 170 ) ;
			g.drawString( "Method 2:" , frameWidth - 180 , frameHeight - 140 ) ;
			g.drawString( "Method 3:" , frameWidth - 180 , frameHeight - 110 ) ;
			g.drawString( "TPR=FPR:" , frameWidth - 180 , frameHeight - 80 ) ;
			g2d.setStroke( new BasicStroke ( 2.5f ) ) ;
			g.setColor( Color.BLACK ) ;
			g.drawLine( coodinateZeroPointX , coodinateZeroPointY , coodinateZeroPointX , coodinateZeroPointY - coodinateYLength ) ;
			g.drawLine( coodinateZeroPointX , coodinateZeroPointY  , coodinateZeroPointX + coodinateXLength , coodinateZeroPointY  ) ;
			g2d.setPaint( Color.BLUE ) ;
			g.drawLine( frameWidth - 90, frameHeight - 177 , frameWidth - 20, frameHeight - 177 );
			g2d.setPaint( Color.MAGENTA ) ;
			g.drawLine( frameWidth - 90, frameHeight - 147 , frameWidth - 20, frameHeight - 147 );
			g2d.setPaint( Color.ORANGE ) ;
			g.drawLine( frameWidth - 90, frameHeight - 117 , frameWidth - 20, frameHeight - 117 );
			g2d.setPaint( Color.RED ) ;
			g.drawLine( frameWidth - 90, frameHeight - 87 , frameWidth - 20, frameHeight - 87 );
			if ( pattern1Width != null )
			{
				
				int drawHigh[] = new int [ pattern1Width.length ] ;
				int drawWidth[] = new int [ pattern1Width.length ] ;
				for ( int i = 0 ; i < pattern1Width.length ; i++ ) 
				{
				    drawHigh [ i ] = coodinateZeroPointY - (int)Math.ceil( coodinateYLength * pattern1Height [ i ] ) ;
				    drawWidth [ i ] = coodinateZeroPointX + (int)Math.ceil( coodinateXLength * pattern1Width [ i ] ) ;
				}
				g2d.setStroke( new BasicStroke ( 2.5f ) ) ;
				g2d.setPaint( Color.BLUE ) ;
				g2d.drawPolyline( drawWidth , drawHigh , pattern1Width.length ) ;
				
			}
			
			if ( pattern2Width != null )
			{
				int drawHigh[] = new int [ pattern2Width.length ] ;
				int drawWidth[] = new int [ pattern2Width.length ] ;
				for ( int i = 0 ; i < pattern2Width.length ; i++ ) 
				{
					drawHigh [ i ] = coodinateZeroPointY - (int)Math.ceil( coodinateYLength * pattern2Height [ i ] ) ;
				    drawWidth [ i ] = coodinateZeroPointX + (int)Math.ceil( coodinateXLength * pattern2Width [ i ] ) ;
				}
				g2d.setStroke( new BasicStroke ( 2.5f ) ) ;
				g2d.setPaint( Color.MAGENTA ) ;
				g2d.drawPolyline( drawWidth , drawHigh , pattern2Width.length ) ;
				
			}
			
			if ( pattern3Width != null )
			{
				int drawHigh[] = new int [ pattern3Width.length ] ;
				int drawWidth[] = new int [ pattern3Width.length ] ;
				for ( int i = 0 ; i < pattern3Width.length ; i++ ) 
				{
					drawHigh [ i ] = coodinateZeroPointY - (int)Math.ceil( coodinateYLength * pattern3Height [ i ] ) ;
				    drawWidth [ i ] = coodinateZeroPointX + (int)Math.ceil( coodinateXLength * pattern3Width [ i ] ) ;
				}
				g2d.setStroke( new BasicStroke ( 2.5f ) ) ;
				g2d.setPaint( Color.ORANGE ) ;
				g2d.drawPolyline( drawWidth , drawHigh , pattern3Width.length ) ;
				
			}
			g2d.setPaint( Color.RED ) ;
			g2d.drawLine( coodinateZeroPointX , coodinateZeroPointY , coodinateZeroPointX + coodinateXLength , coodinateZeroPointY - coodinateYLength ) ;
			g.dispose() ;
		} 
	}
	//  --------------------
	
	// main block support unit
	//  --------------------
	private static void methodRunControl ( int methodNumber ) throws IOException
	{
		if ( lastPattern == methodNumber )
		{
			return ;
		}
		resetParameters() ;
		loadExistData() ;
		switch ( methodNumber )
		{
			case 1 :
			{
				totalTimeMethodInit() ;
				break ;
			}
			case 2 :
			{
				detailTimeMethodInit() ;
				break ;
			}
			case 3 :
			{

				advancedTimeMethodInit() ;
				break ;
			}
		}
		countUpdate() ;
		countResDistArray() ;
		countAllUserVector() ;
	}
	private static void operationControl ( int cmdNumber ) throws IOException
	{
		JFrame jf = new DrawRoc () ;
		switch ( cmdNumber )
		{
			case 1 :
			{
				methodRunControl( cmdNumber );
				if ( pattern1Width == null )
				{
					rocPreparation() ;
				}
				break ;
			}
			case 2 :
			{
				methodRunControl( cmdNumber );
				if ( pattern2Width == null )
				{
					rocPreparation() ;
				}
				break ;
			}
			case 3 :
			{
				methodRunControl( cmdNumber );
				if ( pattern3Width == null )
				{
					rocPreparation() ;
				}
				break ;
			}
			case 4 :
			{
				if ( pattern1Width == null )
				{
					methodRunControl( 1 ) ;
					rocPreparation() ;
				}
				if ( pattern2Width == null )
				{
					methodRunControl( 2 ) ;
					rocPreparation() ;
				}
				if ( pattern3Width == null )
				{
					methodRunControl( 3 ) ;
					rocPreparation() ;
				}
				jf.setBounds( frameStartPointX , frameStartPointY , frameWidth , frameHeight ) ;
				jf.setTitle( "ROC Curve" ) ;
				jf.setBackground( Color.WHITE ) ;
				jf.setResizable( false ) ;
				jf.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE ) ;
				jf.setVisible( true ) ;
				break ;
			}
		}
	}
	//  --------------------
	
	public static void main ( String args[] ) throws IOException
	{
		int controlCmd = 0 ;
		int inputCmd = 0 ;
		boolean exitSignal = false ;
		Scanner in = new Scanner ( System.in ) ;
		
		System.out.println( "Please make sure that PreProcess.java has been run at least once" ) ;
		loadPreProcessData() ;
		trainTestPartition() ;
		
		while ( exitSignal == false )
		{
			System.out.println() ;
			System.out.println( "Enter corresponding number to continue :" ) ;
			System.out.println( "1.show all 3 method and draw ROC curve (may take some time, 5~6 minutes in average)" ) ;
			System.out.println( "2.designate method and get recommendation list" ) ;
			System.out.println( "0.exit " ) ;
			System.out.print( ">" ) ;
			controlCmd = in.nextInt() ;
			switch ( controlCmd )
			{
				case 0 :
				{
					exitSignal = true ;
					continue ;
				}
				case 1 :
				{
					System.out.println() ;
					System.out.println( "Drawing ROC curve") ;
					operationControl( 4 ) ;
					break ;
				}
				case 2 :
				{
					System.out.println() ;
					System.out.println( "Designate method" ) ;
					System.out.println( "Enter corresponding method number to continue :" ) ;
					System.out.println( "1.Total time partition method" ) ;
					System.out.println( "2.Detail time partition method" ) ;
					System.out.println( "3.Advanced detail time partiton method" ) ;
					System.out.println( "0.return to main menu" ) ;
					System.out.print( ">" ) ;
					controlCmd = in.nextInt() ;
					switch ( controlCmd )
					{
						case 0 :
						{
							controlCmd = -1 ;
							continue ;
						}
						case 1 :
						case 2 :
						case 3 :
						{
							operationControl ( controlCmd ) ;
							inputCmd = 0 ;
							while ( true )
							{
								System.out.println() ;
								System.out.println( "Recommendation list is ready" ) ;
								System.out.println( "Enter user ID or user Serial Number to check recommendation list, or enter -1 to return main menu." ) ;
								System.out.print( ">" ) ;
								inputCmd = in.nextInt() ;
								if ( inputCmd == -1 )
								{
									break ;
								}
								queryControl ( inputCmd ) ;
							}
							break ;
						}
						default :
						{
							System.out.println( "Invalid input : Command not found" ) ;
							break ;
						}
					}
				}
				default :
				{
					System.out.println( "Invalid input : Command not found" ) ;
					break ;
				}
			}
		}
		in.close() ;
	}
}