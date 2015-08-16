import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

public class RandomPlayer {
	static final double VER = 1.1;
	
	// Set this to true to get verbose debug output
	static final boolean DEBUG = false;
	
	static ArrayList<File> wavFiles;
	static Random random;
	
	public static ArrayList<File> getFilesInDirectory(String directory, boolean recursive) {
		// Initialize the list we will be returning
		ArrayList<File> filesToReturn = new ArrayList<File>();
		
		// Initialize current folder's file instance
		File folder = new File(directory);
		if(!folder.exists()) {
			System.err.println("Folder " + folder.getAbsolutePath() + " does not exist!");
			return null;
		}
		
		for(File file : folder.listFiles()) {
			// Check if the file exists, just in case...
			if(!file.exists()) {
				System.err.println("File " + file.getAbsolutePath() + " does not exist!");
				continue;
			}
			
			// If recursive add files in a subdirectory to the list
			if(file.isDirectory()) {
				if(recursive) {
					if(DEBUG) System.out.println("Recursively adding folder " + file.getAbsolutePath());
					
					if(file.canRead()) filesToReturn.addAll(getFilesInDirectory(file.getAbsolutePath(), true));
					else if(DEBUG) System.out.println("Can't read folder " + file.getAbsolutePath() + ": no permission");
				}
				
				continue;
			}
			
			// Add the file into the list
			filesToReturn.add(file);
		}
		
		return filesToReturn;
	}
	
	public static boolean loadWavs(String directory, boolean recursive) {
		if(DEBUG) System.out.println("Loading wav files in directory " + directory);
		
		// Remove existing items from the list
		wavFiles.clear();
		
		// Initialize folder
		File folder = new File(directory);
		if(!folder.exists()) {
			System.err.println("Folder " + folder.getAbsolutePath() + " does not exist!");
			return false;
		}
		
		// Get all files in the given directory (and subdirectories if recursive is set)
		ArrayList<File> files = getFilesInDirectory(directory, recursive);
		
		for(File file : files) {
			// This should never happen but let's check it anyway
			if(!file.exists()) {
				System.err.println("File " + file.getAbsolutePath() + " does not exist!");
				return false;
			}
			
			// Add wav files to the list
			if(file.getAbsolutePath().endsWith(".wav")) {
				wavFiles.add(file);
				if(DEBUG) System.out.println("Added " + file.getAbsolutePath());
			}
		}
		
		// Success!
		return true;
	}
	
	public static void printInfo() {
		// Print the usage info
		System.out.println("Usage: java RandomPlayer <directory> <seconds> <randomization> [options...]");
		System.out.println("\t<directory>: Directory of .wav files");
		System.out.println("\t<interval>: Interval in seconds between playing files");
		System.out.println("\t<randomization>: Adds random values between 0 and this value to the interval.");
		System.out.println("\t[options...]: available options are:");
		System.out.println("\t\trecursive: search through all subdirectories as well");
		System.out.println("\t\torder: play files in alphabetical order instead of random");
	}
	
	public static void main(String[] args) throws InterruptedException {
		// Preset arguments
		//args = new String[]{"siika", "0", "0"};
		
		// Initialize instances
		wavFiles = new ArrayList<File>();
		random = new Random();
		
		// Initialize variables
		String soundDir;
		int interval, randomization;
		boolean recursive = false, order = false;
		
		// Read user input
		if(args.length < 3) {
			// Not enought arguments
			printInfo();
			return;
		} else {
			// Get the directory string
			soundDir = args[0];
			
			// Get the interval & randomization values
			try {
				interval = (int) Math.ceil(Double.parseDouble(args[1]));
				randomization = (int) Math.ceil(Double.parseDouble(args[2]));
			} catch(NumberFormatException e) {
				printInfo();
				return;
			}
			
			// Make sure interval is not negative
			if(interval < 0) {
				System.err.println("Interval can't be negative!");
				return;
			}
			
			// Let's not have interval be zero
			if(interval == 0) interval = 1;
			
			// Get the remaining options
			for(int i = 3; i < args.length; i++) {
				if(args[i].equals("recursive")) recursive = true;
				else if(args[i].equals("order")) order = true;
				else {
					System.err.println("No option '" + args[i] + "'");
					printInfo();
					return;
				}
			}
		}
		
		// Let the user know the program started correctly
		System.out.println("RandomPlayer " + VER);
		
		// Load files for the first time
		System.out.println("Loading files from directory " + soundDir + (recursive? " and its subdirectories" : ""));
		if(!loadWavs(soundDir, recursive)) return;
		System.out.println(wavFiles.size() + " files loaded!");
		
		// Is the folder empty?
		if(wavFiles.isEmpty()) {
			System.err.println("Given folder " + soundDir + " is empty!");
			return;
		}
		
		// Init some local variables we will be reusing in the loop
		AudioInputStream audioInputStream;
		Clip clip;
		int index = 0;
		
		// Run forever
		for(;;) {
			// Wait for the specified amount of seconds
			Thread.sleep((interval + (randomization<1? 0 : random.nextInt(randomization))) * 1000L);
			
			if(order) {
				// Set next file index
				if(index < wavFiles.size()-1) index++;
				else index = 0;
			} else {
				// Choose random file index
				index = random.nextInt(wavFiles.size());
			}
			
			// Check if the file still exists
			if(!wavFiles.get(index).exists()) {
				if(DEBUG) System.out.println("File " + wavFiles.get(index).getAbsolutePath() + " no longer exists\nRegenerating list...");
				loadWavs(soundDir, recursive);
			}
			
			if(DEBUG) System.out.println("Next file to play: " + wavFiles.get(index).getAbsolutePath());
			
			try {
				// Load the file into a clip
				audioInputStream = AudioSystem.getAudioInputStream(wavFiles.get(index));
				clip = AudioSystem.getClip();
				clip.open(audioInputStream);
				
				// File info
				System.out.println("Playing " + wavFiles.get(index).getName());
				
				// Play the clip
				// There is a bug in clip.play() that makes the file not play fully (wtf Oracle)
				// To avoid that we loop it twice but stop playing before the second play starts
				clip.loop(2);
				
				// Wait for first play to finish
				Thread.sleep(clip.getMicrosecondLength() / 1000L);
				
				// Stop the clip
				clip.stop();
			} catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
				// Handle errors during playback
				System.err.println("A problem occured while playing the file " + wavFiles.get(index).getAbsolutePath());
				System.err.println("The problem is probably with the file (bad encoding)\n");
				wavFiles.remove(index);
			}
		}
	}
}
