/*
 * Program: NanoOK
 * Author:  Richard M. Leggett
 * 
 * Copyright 2015 The Genome Analysis Centre (TGAC)
 */

package nanook;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Representation of a FAST5 file
 * @author leggettr
 */
public class Fast5File {
    private NanoOKOptions options;
    private String filename = null;
    private HashSet<String> groups = new HashSet();
    private HashSet<String> datasets = new HashSet();
    private NanoOKLog log;
    private boolean oldFormat = false;
    private boolean isCorrupt = false;
    private int highestBasecall1D = -1;
    private int highestBasecall2D = -1;
    private double meanQScore = 0;
    
    /**
     * Constructor
     * @param f 
     */
    public Fast5File(NanoOKOptions o, String f) {
        options = o;
        filename = f;
        log = options.getLog();
        indexFile();
    }
    
    /**
     * Index groups and datasets
     */
    public void indexFile() {
        boolean[] typesAvailable = new boolean[3];
        ProcessLogger pl = new ProcessLogger();
        ArrayList<String> response;
       
        log.println("Indexing file "+filename);

        response = pl.getCommandOutput("h5dump -n "+filename, true, true);
        for (int i=0; i<response.size(); i++) {
            String s = response.get(i).trim();
            String[] cols = s.split("(\\s+)");
            if (cols[0].equals("dataset")) {
                datasets.add(cols[1]);
            } else if (cols[0].equals("group")) {
                groups.add(cols[1]);
                if (cols[1].startsWith("/Analyses/Basecall_2D_")) {
                    Pattern outPattern = Pattern.compile("^/Analyses/Basecall_2D_(\\d+)$");
                    Matcher outMatcher = outPattern.matcher(cols[1]);
                    if (outMatcher.find()) {
                        int index = Integer.parseInt(outMatcher.group(1));
                        if (index > highestBasecall2D) {
                            highestBasecall2D = index;
                        }
                    }                    
                } else if (cols[1].startsWith("/Analyses/Basecall_1D_")) {
                    Pattern outPattern = Pattern.compile("^/Analyses/Basecall_1D_(\\d+)$");
                    Matcher outMatcher = outPattern.matcher(cols[1]);
                    if (outMatcher.find()) {
                        int index = Integer.parseInt(outMatcher.group(1));
                        if (index > highestBasecall1D) {
                            highestBasecall1D = index;
                        }
                    }                    
                }
            }            
        }
        
        // Old format files did not have separate Basecall_1D section
        if ((highestBasecall1D == -1) && (highestBasecall2D == -1)) {
            isCorrupt = true;
            log.println("Error: couldn't find Basecall_1D or Basecall_2D in "+filename);
        } else if ((highestBasecall1D == -1) && (highestBasecall2D >= 0)) {
            oldFormat = true;
            highestBasecall1D = highestBasecall2D;
        } else {
            if ((highestBasecall1D >=0) && (highestBasecall2D >=0)) {
                if (highestBasecall1D != highestBasecall2D) {
                    //isCorrupt = true;
                    log.println("Warning: Basecall_1D and Basecall_2D highest indicies not the same in "+filename);
                }
            }
        }

        log.println("    Highest1D: "+highestBasecall1D+" Highest2D: "+highestBasecall2D);
    }
    
    public double getMeanQAttribute(String attribute) {
        ProcessLogger pl = new ProcessLogger();
        ArrayList<String> response = pl.getCommandOutput("h5dump -a "+attribute+" "+filename, true, true);
        double meanq = 0;

        // Look for value beginning (0): 
        int l;
        for (l=0; l<response.size(); l++) {
            String line = response.get(l);
            if (line.contains("(0):")) {
                meanq = Double.parseDouble(line.substring(line.indexOf("(0):") + 5));
            }
        }

        return meanq;
    }
    
    
    /**
     * Get the FASTQ data out of the dataset
     * 
     * @param inputFilename
     * @param dataset
     * @return 
     */
    public FastAQFile getFastqFromDataset(String dataset) {
        ProcessLogger pl = new ProcessLogger();
        ArrayList<String> response = pl.getCommandOutput("h5dump -d "+dataset+" "+filename, true, true);
        FastAQFile ff = null;
                
        // Look for start of FASTQ section
        int l;
        for (l=0; l<response.size(); l++) {
            if (response.get(l).contains("\"@")) {
                break;
            }
        }
        
        // Parse FASTQ portion with regex
        if (l < response.size()) {
            String id = null;
            String seq = null;
            String qual = null;
            
            // Header row
            Pattern outPattern = Pattern.compile("@(.+)");
            Matcher outMatcher = outPattern.matcher(response.get(l));
            if (outMatcher.find()) {
                id = outMatcher.group(1);
            }
            
            // Sequence
            outPattern = Pattern.compile("(\\s*)(\\S+)");
            outMatcher = outPattern.matcher(response.get(l+1));
            if (outMatcher.find()) {
                seq = outMatcher.group(2);
            }
            
            // Qualities
            outPattern = Pattern.compile("(\\s*)(\\S+)");
            outMatcher = outPattern.matcher(response.get(l+3));
            if (outMatcher.find()) {
                qual = outMatcher.group(2);
            }
            
            // Fix IDs
            if (id != null) {
                outPattern = Pattern.compile("00000000-0000-0000-0000-000000000000(.+)");
                outMatcher = outPattern.matcher(id);
                if (outMatcher.find()) {
                    if (options.fixIDs()) {
                        id = id.replaceAll("^00000000-0000-0000-0000-000000000000_", "");
                        id = id.replaceAll(" ", "");
                    } else {
                        System.out.println("Warning: " + id + " is non-unqiue. Recommend re-running with -fixids option.");
                        System.out.println("");
                    }
                }
            }
            
            if ((id != null) && (seq != null) && (qual != null)) {
                ff = new FastAQFile(id, seq, qual);
            }
        }
                
        return ff;
    }
    
    public double getMeanQ(int index, int type) {
        String meanQAttributePath = null;
        String indexString;
        double meanQ = 0;
        
        log.println("    Trying to get mean Q type "+type+" from "+filename+" with index "+index);
        
        if (!isCorrupt) {
            if (index == -1) {
                if (type == NanoOKOptions.TYPE_2D) {
                    index = highestBasecall2D;
                } else {
                    index = highestBasecall1D;
                }
            } else {
                int highestIndex = highestBasecall2D;
                
                if (type != NanoOKOptions.TYPE_2D) {
                    highestIndex = highestBasecall1D;
                } 
                
                if (index > highestIndex) {
                    log.println("Error: index higher than highest Basecall available");
                    isCorrupt = true;
                }            
            }
        }
        
        if (!isCorrupt) {
            // Make string for group
            indexString = String.format("%03d", index);              

            // Build path to dataset
            if (type == NanoOKOptions.TYPE_2D) {
                meanQAttributePath = "/Analyses/Basecall_2D_"+indexString+"/Summary/basecall_2d/mean_qscore";
            } else { 
                // Now look if we are new format (with Basecall_1D_XXX)
                if (oldFormat) {
                    // Old format
                    if (type == NanoOKOptions.TYPE_TEMPLATE) {
                        meanQAttributePath = "/Analyses/Basecall_2D_"+indexString+"/Summary/basecall_1d_template/mean_qscore";
                    } else if (type == NanoOKOptions.TYPE_COMPLEMENT) {
                        meanQAttributePath = "/Analyses/Basecall_2D_"+indexString+"/Summary/basecall_1d_complement/mean_qscore";
                    } else {
                        System.out.println("Error: bad type in getFastq");
                        System.exit(1);
                    }
                } else {
                    // New format
                    if (type == NanoOKOptions.TYPE_TEMPLATE) {
                        meanQAttributePath = "/Analyses/Basecall_1D_"+indexString+"/Summary/basecall_1d_template/mean_qscore";
                    } else if (type == NanoOKOptions.TYPE_COMPLEMENT) {
                        meanQAttributePath = "/Analyses/Basecall_1D_"+indexString+"/Summary/basecall_1d_complement/mean_qscore";
                    } else {
                        System.out.println("Error: bad type in getFastq");
                        System.exit(1);
                    }
                }
            }
        }
        
        if (meanQAttributePath != null) {
            log.println("    Path: "+meanQAttributePath);
            meanQ = getMeanQAttribute(meanQAttributePath);
            log.println("    MeanQ: "+meanQ);
        }
        
        return meanQ;        
    }
    
    /**
     * Get a FastQ/A file for given (Basecall_) index and type (2D/Template/Complement)
     * @param index
     * @param type
     * @return 
     */
    public FastAQFile getFastq(int index, int type) {
        String fastqDatasetPath = null;
        String indexString;
        FastAQFile ff = null;
        
        log.println("    Trying to get FASTQ type "+type+" from "+filename+" with index "+index);
        
        if (!isCorrupt) {
            if (index == -1) {
                if (type == NanoOKOptions.TYPE_2D) {
                    index = highestBasecall2D;
                } else {
                    index = highestBasecall1D;
                }
            } else {
                int highestIndex = highestBasecall2D;
                
                if (type != NanoOKOptions.TYPE_2D) {
                    highestIndex = highestBasecall1D;
                } 
                
                if (index > highestIndex) {
                    log.println("Error: index higher than highest Basecall available");
                    isCorrupt = true;
                }            
            }
        }
        
        if (!isCorrupt) {
            // Make string for group
            indexString = String.format("%03d", index);              

            // Build path to dataset
            if (type == NanoOKOptions.TYPE_2D) {
                fastqDatasetPath = "/Analyses/Basecall_2D_"+indexString+"/BaseCalled_2D/Fastq";
            } else { 
                // Now look if we are new format (with Basecall_1D_XXX)
                if (oldFormat) {
                    // Old format
                    if (type == NanoOKOptions.TYPE_TEMPLATE) {
                        fastqDatasetPath = "/Analyses/Basecall_2D_"+indexString+"/BaseCalled_template/Fastq";
                    } else if (type == NanoOKOptions.TYPE_COMPLEMENT) {
                        fastqDatasetPath = "/Analyses/Basecall_2D_"+indexString+"/BaseCalled_complement/Fastq";
                    } else {
                        System.out.println("Error: bad type in getFastq");
                        System.exit(1);
                    }
                } else {
                    // New format
                    if (type == NanoOKOptions.TYPE_TEMPLATE) {
                        fastqDatasetPath = "/Analyses/Basecall_1D_"+indexString+"/BaseCalled_template/Fastq";
                    } else if (type == NanoOKOptions.TYPE_COMPLEMENT) {
                        fastqDatasetPath = "/Analyses/Basecall_1D_"+indexString+"/BaseCalled_complement/Fastq";
                    } else {
                        System.out.println("Error: bad type in getFastq");
                        System.exit(1);
                    }
                }
            }
        }
        
        if (fastqDatasetPath != null) {
            log.println("    Path: "+fastqDatasetPath);
            if (datasets.contains(fastqDatasetPath)) {
                log.println("    Found data: "+fastqDatasetPath);
                ff = getFastqFromDataset(fastqDatasetPath);
            } else {
                log.println("    Not there: "+fastqDatasetPath);
            }
        }
        
        return ff;
    }
    
    /**
     * Print list of groups
     */
    public void printGroups() {
        for (String s : groups) {
           System.out.println(s);
        }
    }
    
// JNI Library version
//    /**
//     * Get FASTQ section out of FAST5 file
//     * @param pathname path to FAST5 file
//     * @param type type of read
//     * @return multi-line String
//     */
//    public String getFastq(String pathname, int type) {
//        H5File file = null;
//        String[] fastq = null;
//        
//		// Open a file using default properties.
//		try {
//            file = new H5File(pathname, FileFormat.READ);
//            
//            // Find basecall group
//            H5Group grp;
//            String groupPath = new String();
//            String datasetPath = null;
//            String indexString;
//            int index = -1;
//            int i = 0;
//
//            // Default behaviour is to find latest
//            if (options.getBasecallIndex() == -1) {
//                do {
//                    indexString = String.format("%03d", i);                
//                    grp = (H5Group)file.get("/Analyses/Basecall_2D_" + indexString);
//                    if (grp != null) {
//                        index=i;
//                        i++;
//                    }
//                } while (grp != null);
//            } else {
//                // User has specified index - check it exists
//                indexString = String.format("%03d", options.getBasecallIndex());    
//                grp = (H5Group)file.get("/Analyses/Basecall_2D_" + indexString);
//                if (grp != null) {
//                    index=i;
//                }                
//            }
//            
//            // index will = -1 if we didn't find any group
//            if (index >=0) {
//                // Make string for group
//                indexString = String.format("%03d", index);                
//
//                // Build path to dataset
//                if (type == NanoOKOptions.TYPE_2D) {
//                    datasetPath = "/Analyses/Basecall_2D_"+indexString+"/BaseCalled_2D/Fastq";
//                } else { 
//                    // Now look if we are new format (with Basecall_1D_XXX)
//                    grp = (H5Group)file.get("/Analyses/Basecall_1D_"+indexString);
//                    if (grp == null) {
//                        // Old format
//                        if (type == NanoOKOptions.TYPE_TEMPLATE) {
//                            datasetPath = "/Analyses/Basecall_2D_"+indexString+"/BaseCalled_template/Fastq";
//                        } else if (type == NanoOKOptions.TYPE_COMPLEMENT) {
//                            datasetPath = "/Analyses/Basecall_2D_"+indexString+"/BaseCalled_complement/Fastq";
//                        } else {
//                            System.out.println("Error: bad type in getFastq");
//                            System.exit(1);
//                        }
//                    } else {
//                        // New format
//                        if (type == NanoOKOptions.TYPE_TEMPLATE) {
//                            datasetPath = "/Analyses/Basecall_1D_"+indexString+"/BaseCalled_template/Fastq";
//                        } else if (type == NanoOKOptions.TYPE_COMPLEMENT) {
//                            datasetPath = "/Analyses/Basecall_1D_"+indexString+"/BaseCalled_complement/Fastq";
//                        } else {
//                            System.out.println("Error: bad type in getFastq");
//                            System.exit(1);
//                        }
//                    }
//                }
//
//                //System.out.println("Path: "+datasetPath);            
//                Dataset ds = (Dataset)file.get(datasetPath);
//                if (ds == null) {
//                    System.out.println("No dataset at "+datasetPath);
//                } else {
//                    fastq = (String[])ds.getData();
//                }
//            }
//
//            file.close();            
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//        
//        if (fastq == null) {
//            return null;
//        } else {
//            return fastq[0];
//        }
//    }        
//    /**
//     * Dump an individual read
//     * @param inputFilename filename of FAST5 file
//     * @param type type of read
//     */
//    private void dumpRead(String inputFilename, int type, String outputDir) {
//        String outName = new File(inputFilename).getName();
//
//        String fastqDatafield = null; //getFastq(inputFilename, type);
//        if (fastqDatafield != null) {
//            String [] lines = fastqDatafield.split("\n");
//        
//            String id = null;
//            String seq = lines[1];
//            String qual = lines[3];
//            
//            if (lines[0].startsWith("@")) {
//                id = lines[0].substring(1);
//
//                // Fix IDs
//                Pattern outPattern = Pattern.compile("00000000-0000-0000-0000-000000000000(.+)");
//                Matcher outMatcher = outPattern.matcher(id);
//                if (outMatcher.find()) {
//                    if (options.fixIDs()) {
//                        id = id.replaceAll("^00000000-0000-0000-0000-000000000000_", "");
//                        id = id.replaceAll(" ", "");
//                    } else {
//                        System.out.println("Warning: " + id + " is non-unqiue. Recommend re-running with -fixids option.");
//                        System.out.println("");
//                    }
//                }
//            } else {
//                System.out.println("Couldn't parse "+inputFilename);
//            }                      
//            
//            if (id != null) {
//                if (options.getReadFormat() == NanoOKOptions.FASTA) {
//                    writeFastaFile(id, seq, outputDir + File.separator + NanoOKOptions.getTypeFromInt(type) + File.separator + outName + "_BaseCalled_" + NanoOKOptions.getTypeFromInt(type) + ".fasta");
//                } else if (options.getReadFormat() == NanoOKOptions.FASTQ) {
//                    writeFastqFile(id, seq, qual, outputDir + File.separator + NanoOKOptions.getTypeFromInt(type) + File.separator + outName + "_BaseCalled_" + NanoOKOptions.getTypeFromInt(type) + ".fastq");
//                }
//            }            
//        } else {
//            System.out.println("Error: couldn't find payload in " + inputFilename);
//        }
//    }  
}
