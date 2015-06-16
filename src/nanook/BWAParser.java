/*
 * Program: NanoOK
 * Author:  Richard M. Leggett
 * 
 * Copyright 2015 The Genome Analysis Centre (TGAC)
 */

package nanook;

/**
 * Parser for BWA files
 * @author Richard Leggett
 */
public class BWAParser extends SAMParser implements AlignmentFileParser {
    private String alignmentParams = "-x ont2d";
    
    public BWAParser(NanoOKOptions o, References r) {
        super(o, r);
    }
    
    public String getProgramID() {
        return "bwa";
    }

    public int getReadFormat() {
        return NanoOKOptions.FASTA;
    }
    
    public void setAlignmentParams(String p) {
        alignmentParams = p;
    }
    
    public boolean outputsToStdout() {
        return true;
    }
        
    public String getRunCommand(String query, String output, String reference) {
        //reference = reference.replaceAll("\\.fasta$", "");
        //reference = reference.replaceAll("\\.fa$", "");
        
        return "bwa mem " + alignmentParams + " " + reference + " " + query;
    }
}
