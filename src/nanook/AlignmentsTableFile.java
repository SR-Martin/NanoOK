package nanook;

import java.io.*;

/**
 * Represents alignment summary file written by tool and used for graph plotting.
 * 
 * @author Richard Leggett
 */
public class AlignmentsTableFile {
    private String filename;
    private PrintWriter pw;
    private int count = 0;

    /**
     * Constructor.
     * @param f filename of output file
     */
    public AlignmentsTableFile(String f) {
        filename = f;
        
        try {
            pw = new PrintWriter(new FileWriter(filename));
            writeHeader();
        } catch (IOException e) {
            System.out.println(e);
        }
    }
    
    /**
     * Write header row to file.
     */
    private void writeHeader() {
        pw.print("Filename\t");
        pw.print("QueryName\t");
        pw.print("QueryStart\t");
        pw.print("QueryBasesCovered\t");
        pw.print("QueryStrand\t");
        pw.print("QueryLength\t");
        pw.print("HitName\t");
        pw.print("HitStart\t");
        pw.print("HitBasesCovered\t");
        pw.print("HitStrand\t");
        pw.print("HitLength\t");
        pw.print("AlignmentSize\t");
        pw.print("IdenticalBases\t");
        pw.print("AlignmentPercentIdentity\t");
        pw.print("QueryPercentIdentity\t");
        pw.print("LongestPerfectKmer\t");
        pw.print("MeanPerfectKmer\t");
        pw.print("PercentQueryAligned");
        pw.println("");
    }
    
    /**
     * Write an alignment line.
     * @param alignmentFilename filename of alignment
     * @param hitLine hit object
     * @param queryLine query object
     * @param stat AlignmentInfo statistics
     */
    public void writeAlignment(String alignmentFilename, LastAlignmentLine hitLine, LastAlignmentLine queryLine, AlignmentInfo stat) {
        String outputLine = String.format("%s\t%s\t%d\t%d\t%s\t%d\t%s\t%d\t%d\t%s\t%d\t%d\t%d\t%.2f\t%.2f\t%d\t%.2f\t%.2f",
                alignmentFilename,
                queryLine.getName(),
                queryLine.getStart(),
                queryLine.getAlnSize(),
                queryLine.getStrand(),
                queryLine.getSeqSize(),
                hitLine.getName(),
                hitLine.getStart(),
                hitLine.getAlnSize(),
                hitLine.getStrand(),
                hitLine.getSeqSize(),
                stat.getAlignmentSize(),
                stat.getIdenticalBases(),
                stat.getAlignmentId(),
                stat.getQueryId(),
                stat.getLongest(),
                stat.getMeanPerfectKmer(),
                stat.getPercentQueryAligned());
        
        pw.println(outputLine);
        pw.flush();

        //if (filename.equals("/Users/leggettr/Documents/Projects/Nanopore/N79681_EvenMC_R7_06082014/analysis/Rhodobacter_sphaeroides_2D_alignments.txt")) {
        //if ((count == 19) || (count == 20) || (count == 21)) {
        //    System.out.println("DEBUG");
        //    System.out.println("["+outputLine+"]");
        //    System.out.println("["+filename+"]");
        //    System.out.println("["+alignmentFilename+"]");
        //    System.out.println("["+hitLine.getName()+"]");
        //}
        //}
        
        count++;
    }
    
    /**
     * Used when no alignment found for this query.
     * @param alignmentFilename - alignment filename
     */
    public void writeNoAlignmentMessage(String alignmentFilename) {
        pw.println(alignmentFilename+"\tNO ALIGNMENTS");
    }

    /**
     * Close file.
     */
    public void closeFile() {
        pw.flush();
        pw.close();
        //System.out.println("File closed");
    }
}