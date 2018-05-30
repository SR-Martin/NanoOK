/*
 * Program: NanoOK
 * Author:  Richard M. Leggett
 * 
 * Copyright 2015 The Genome Analysis Centre (TGAC)
 */

package nanook;

import java.io.Serializable;
import java.util.Hashtable;
import java.util.Set;

/**
 * KmerTable used for 5-mer comparison
 * 
 * @author Richard Leggett
 */
public class KmerTable implements Serializable {
    private static final long serialVersionUID = NanoOK.SERIAL_VERSION;
    private int kmerSize = 5;
    private Hashtable<String, Integer> counts = new Hashtable();
   
    public KmerTable(int k) {
        kmerSize = k;
    }
    
    public synchronized void countKmer(String kmer) {
        int count = 0;
        
        if (counts.containsKey(kmer)) {
            count = counts.get(kmer);
        }
        
        count++;
        
        counts.put(kmer, count);
    }
    
    public void writeKmerTable() {
        Set<String> keys = counts.keySet();
        
        System.out.println("");
        System.out.println("Writing kmer table...");
        
        for(String kmer : keys) {
            int count = counts.get(kmer);
            System.out.println(kmer + "\t" + count);
        }

        System.out.println("");    
    }
    
    public int getKmerSize() {
        return kmerSize;
    }
    
    public Set<String> getKeys() {
        return counts.keySet();
    }
    
    public int get(String kmer) {
        int value = 0;
        
        if (counts.containsKey(kmer)) {
            value = counts.get(kmer);
        }
        
        return value;
    }
    
    public Hashtable getTable() {
        return counts;
    }
}
