/**
 * 
 */
package bloomfilter;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

/**
 * @author Lukas Keller
 *
 */
public class BloomFilter 
{
	/**
	 * size of the filter
	 */
	private final int m;
	
	/**
	 * number of elements in filter
	 */
	private final int n;
	
	/**
	 * probability of false positive results
	 */
	private final double p;
	
	/**
	 * number of hash functions
	 */
	private final int k;
	
	private BitSet filter;
	private HashFunction[] hashFunctions;
	
	//Validation parameters
	
	/**
	 * Percentage of all words in test set as "unknown words"
	 */
	private static final int P_TEST_SET_UNKNOWN = 50;
	
	/**
	 * Percentage of all words in test set as "known words"
	 */
	private static final int P_TEST_SET_KNOWN = 50;
	
	private final static Random shuffleRandom = new Random(1);
	
	/**
	 * Initialize the filter with an optimal filtersize {@code m} and an optimal number of hashfunctions {@code k} and generates {@code k} hashfunctions.
	 * @param n number of expected elements in the filter
	 * @param p probability of false positive results. {@code 0<=p<=1}
	 */
	public BloomFilter(int n, double p) 
	{
		assert(p>=0 && p<=1);
		
		this.n = n;
		this.p = p;
		
		this.m = this.optimalFiltersize(n,p);
		this.k = this.optimalNumberOfHashfunctions(this.m,n);
		
		this.filter = new BitSet(this.m); 
		
		this.hashFunctions = new HashFunction[this.k];
		for(int seed=0;seed<this.k;seed++)
		{
			HashFunction function = Hashing.murmur3_128(seed);
			this.hashFunctions[seed] = function;
		}
	}
	
	public static void main(String[] args)
	{
		String filename = "words.txt";
		double p = 0.1;
		boolean crossValidation = true;
		
		
		String[][] dataSet;
		int n;
		
		if(crossValidation)
		{
			dataSet = BloomFilter.readWithValidation(filename);
			n = dataSet[1].length;
		}
		else
		{
			dataSet = BloomFilter.read(filename);
			n = dataSet[0].length;
		}
		
		BloomFilter bloomFilter = new BloomFilter(n, p);
		
		bloomFilter.run(dataSet);
		System.out.println("Bloom filter: " + bloomFilter.toString());

	}
	
	/**
	 * Adds a single word to the bloom filter by calculating {@code k} indices with {@code k} hashfunctions
	 * @param word to add to the bloom filter
	 */
	public void addWord(CharSequence word)
	{
		for(HashFunction function : this.hashFunctions)
		{
			HashCode hashCode = function.hashUnencodedChars(word);
			this.applyHashCodeOnFilter(hashCode,this.filter );
		}
	}

	/**
	 * Checks if a given word exists in the filter or not. 
	 * @param word word to check
	 * @return true if <i>word</i> may exist in filter with a given probability of {@code 1-p}<br> false if<i>word</i> does not exist in filter
	 */
	public boolean contains(CharSequence word)
	{
		BitSet set = new BitSet(this.m);
		
		for(HashFunction function : this.hashFunctions)
		{
			HashCode hashCode = function.hashUnencodedChars(word);
			this.applyHashCodeOnFilter(hashCode,set);
		}
		
		int bits = set.cardinality(); 	//counting number of set bits.
		set.and(this.filter);			
		
		return bits==set.cardinality(); //returns true if and only if number of set bits hasn't changed. 
		
	}

	/**
	 * 
	 * @param dataSet could be a rather {@code 1 x N} array (without validation) or a {@code 3 x N} array (with validation)
	 */
	public void run(String[][] dataSet)
	{
		if(dataSet.length==1)
		{
			this.runWithoutValidation(dataSet);
		}
		else if(dataSet.length == 3)
		{
			this.runWithValidation(dataSet);
		}
		else
		{
			System.err.println("Invalid dataSet");
		}
	}
	
	private void runWithoutValidation(String[][] dataSet)
	{
		assert(dataSet.length==1);
		
		this.addWords(dataSet[0]);
		
		String[] w = new String[dataSet[0].length];
		
		for(int i=0;i<w.length;i++)
		{
			w[i] = dataSet[0][i]+"aajsoisjf";
		}
		
		int c = this.containsWords(w, true); //should be around p
		
		System.out.println("unknown false positive: " + c/(double) w.length);
	}
	
	private void runWithValidation(String[][] dataSet)
	{
		assert(dataSet.length==3);
		assert(dataSet[1].length==this.n);
		
		this.addWords(dataSet[1]);
		
		int unknown = this.containsWords(dataSet[0], true);
		int known = this.containsWords(dataSet[2], true);
		
		System.out.println("unknown false positive: " + unknown/(double) dataSet[0].length); //should be around p
		System.out.println("(known true positive: " + known/(double) dataSet[2].length+")"); //should always be 1

	}

	/**
	 * Reads all words of a dictionary file into a {@code 1 x N} array for running bloom filter without validation.
	 * @param dictionaryFile filename of the dictionary file
	 * @return a {@code 1 x N} array with {@code N=number of lines/words}}
	 * 
	 * @see #readWithValidation(String)
	 * @see BloomFilter#readWords(String, boolean)
	 */
	public static String[][] read(String dictionaryFile)
	{
		return BloomFilter.readWords(dictionaryFile, false);
	}
	
	/**
	 * Reads all words of a dictionary file into a {@code 3 x N} array for running bloom filter with validation.
	 * @param dictionaryFile filename of the dictionary file
	 * @return a {@code 3 x N} array. [0]: unknown test set [1]: training set [2]: known test set
	 * 
	 * @see #read(String)
	 * @see BloomFilter#readWords(String, boolean)
	 * @see BloomFilter#createCrossValidationDataSet(List)
	 */
	public static String[][] readWithValidation(String dictionaryFile)
	{
		return BloomFilter.readWords(dictionaryFile, true);
	}
	
	/**
	 * Counts the number of words which in or not in the bloom filter
	 * @param words words to check
	 * @param contains 
	 * @return contains == true: number of words which are may in the bloom filter <br> contains == false: number of words which are not in the bloom filter
	 * @see #contains(CharSequence)
	 */
	private int containsWords(String[] words, boolean contains)
	{
		int counter = 0;
		
		for(String word:words)
		{
			boolean result = this.contains(word);
			
			if(result==contains)
			{
				counter++;
			}
		}
		
		return counter;
	}
	
	/**
	 * Adds all given words to the bloom filter
	 * @param words words to add
	 */
	private void addWords(String[] words)
	{
		for(String word:words)
		{
			this.addWord(word);
		}
	}
	
	/**
	 * Reads the words of the given file and saves them in an array
	 * @param dictionaryFile
	 * @param crossValidation enable/disable validation
	 * @return 
	 */
	private static String[][] readWords(String dictionaryFile, boolean crossValidation)
	{
		Scanner scanner = null;
		List<String> words = new ArrayList<String>();
		
		try 
		{
			scanner = new Scanner(new File(dictionaryFile));
			while(scanner.hasNextLine())
			{
				String word = scanner.nextLine();
				words.add(word);
			}
		} 
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
		}
		finally
		{
			scanner.close();
		}
		
		System.out.println("Read " + words.size() +" words.");
		
		String[][] dataSet;
		
		if(crossValidation)
		{
			System.out.println("Cross validation enabled...");
			
			dataSet = BloomFilter.createCrossValidationDataSet(words);
		}
		else
		{
			System.out.println("Cross validation disabled...");
			
			dataSet = new String[1][];
			
			String[] wordArray = new String[words.size()];
			wordArray = words.toArray(wordArray);
			
			dataSet[0] = wordArray;
		}
		
		return dataSet;
	}
	
	/**
	 * Splits up a list of words into a test set of unknown words (0), a training set (1) and a set of known words (2)
	 * @param words
	 * @return [0]: test unknown words<br>[1]: known words<br>[2]: test known words
	 */
	private static String[][] createCrossValidationDataSet(List<String> words)
	{
		int onePercent = words.size()/100;
		int sizeTestSetUnknown = onePercent*BloomFilter.P_TEST_SET_UNKNOWN;
		int sizeSetKnown = words.size()-sizeTestSetUnknown;
		int sizeTestKnown = onePercent*BloomFilter.P_TEST_SET_KNOWN;
		
		System.out.println("CREATE: word size: " + words.size() +" unknown test size: " + sizeTestSetUnknown +" sizeSetKnown: " + sizeSetKnown +" sizeTestSetKnown: " + sizeTestKnown);
		
		String[][] wordLists = new String[3][];
		wordLists[0] = new String[sizeTestSetUnknown];
		wordLists[1] = new String[sizeSetKnown];
		wordLists[2] = new String[sizeTestKnown];
		
		Collections.shuffle(words, shuffleRandom);
		
		int i =0;
		for(;i<sizeTestSetUnknown;i++)
		{
			wordLists[0][i] = words.get(i);
		}
		
		for(i=0;i<sizeSetKnown;i++)
		{
			wordLists[1][i] = words.get(i+sizeTestSetUnknown);
		}
		
		for(i=0;i<sizeTestKnown;i++)
		{
			wordLists[2][i] = wordLists[1][i];
		}
		
		return wordLists;
	}

	/**
	 * Calculates the index of the given hashCode and sets the index-th bit of the filter:<br>
	 * 
	 * {@code index = |code.asLong mod m|}
	 * 
	 * @param code generated hashcode
	 * @param filter bitset of the word or the bloomfilter itself.
	 */
	private void applyHashCodeOnFilter(HashCode code, BitSet filter)
	{

		long h = code.asLong();
		int modeHash = (int) Math.abs(h%this.m);
		filter.set(modeHash);
		
		assert(filter.length()<=this.m);
	}
	
	//Source: https://en.wikipedia.org/wiki/Bloom_filter#Optimal_number_of_hash_functions
	private int optimalFiltersize(int n, double p)
	{
		return (int) (-(n*Math.log(p))/(Math.pow(Math.log(2),2)));
	}
	
	//Source: https://en.wikipedia.org/wiki/Bloom_filter#Optimal_number_of_hash_functions
	private int optimalNumberOfHashfunctions(int m, int n)
	{
		return (int) Math.ceil(((m/(double) n)*Math.log(2))); //Math.ceil is necessary, otherwise it can happen that result is 0
	}
	
	@Override
	public String toString()
	{
		return "n=" + this.n + " p=" + this.p +" k=" + this.k + " m= "+ this.m + " set size = " + this.filter.length();
	}
}
