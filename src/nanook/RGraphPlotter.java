/*
 * Program: NanoOK
 * Author:  Richard M. Leggett
 * 
 * Copyright 2015 The Genome Analysis Centre (TGAC)
 */

package nanook;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Executes command to plot graphs with R.
 * 
 * @author Richard Leggett
 */
public class RGraphPlotter {
    private ThreadPoolExecutor executor;
    private NanoOKOptions options;
    private long lastCompleted = -1;
    private String logDirectory;

    /**
     * Constructor.
     * @param o NanoOKOptions object
     */
    public RGraphPlotter(NanoOKOptions o) {
        options = o;
        executor = new ThreadPoolExecutor(options.getNumberOfThreads(), options.getNumberOfThreads(), 10, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        logDirectory = options.getLogsDir() + File.separator + "R" + options.getAnalysisSuffix();
        File f = new File(logDirectory);
        if (!f.exists()) {
            f.mkdir();
            options.getLog().println("Made directory " + logDirectory);
        }
    }
        
    /**
     * Write progress
     */
    private void writeProgress() {
        long completed = executor.getCompletedTaskCount();
        long total = executor.getTaskCount();
        long e = 0;
        long s = NanoOKOptions.PROGRESS_WIDTH;
        
        if (total > 0) {
            e = NanoOKOptions.PROGRESS_WIDTH * completed / total;
            s = NanoOKOptions.PROGRESS_WIDTH - e;
        }
        
        
        if (completed != lastCompleted) {              
            System.out.print("\r[");
            for (int i=0; i<e; i++) {
                System.out.print("=");
            }
            for (int i=0; i<s; i++) {
                System.out.print(" ");
            }
            System.out.print("] " + completed +"/" +  total);
            lastCompleted = completed;
        }
    } 
    
    public void runScript(boolean fComparison, String scriptName, String logPrefix, String refName) {
        ArrayList<String> args = new ArrayList<String>();
        String logFilename = logDirectory + File.separator + logPrefix;
        
        args.add("Rscript");
        args.add(options.getScriptsDir() + File.separator + scriptName);

        if (fComparison) {
            File f = new File(options.getAnalysisDir());
            args.add(f.getName());
        } else {
            args.add(options.getAnalysisDir());
        }
        
        args.add(options.getGraphsDir());
        
        if (fComparison) {
            args.add(options.getSampleList());
            args.add(options.getComparisonDir());
        }
        
        if (refName != null) {
            args.add(refName);
            logFilename = logFilename + "_"+refName;
        }

        args.add(options.getImageFormat());
        
        //System.out.println(args);
                
        options.getLog().println("Running Rscript "+scriptName);
        options.getLog().println("Log file is "+logFilename);
        executor.execute(new RGraphRunnable("Rscript", args, logFilename + ".txt"));
        writeProgress();
    }
    
    /**
     * Execute plot commands.
     * @param references References object containing all references
     */
    public void plot(boolean fComparison) throws InterruptedException {
        String s = null;
        
        if (fComparison) {
            runScript(fComparison, "nanook_plot_comparison.R", "plot_lengths", null);        
        } else {
            runScript(fComparison, "nanook_plot_lengths.R", "plot_lengths", null);
        }
       
        Set<String> ids = options.getReferences().getAllIds();
        for (String id : ids) {
            ReferenceSequence rs = options.getReferences().getReferenceById(id);
            String name = rs.getName();
            if (fComparison) {
                runScript(fComparison, "nanook_plot_comparison_reference.R", "plot_reference", name);            
            } else {
                if (rs.getTotalNumberOfAlignments() > NanoOKOptions.MIN_ALIGNMENTS) {
                    runScript(fComparison, "nanook_plot_reference.R", "plot_reference", name);
                }
            }
            writeProgress();
        }          
        
        // That's all - wait for all threads to finish
        executor.shutdown();
        while (!executor.isTerminated()) {
            writeProgress();
            Thread.sleep(100);
        }        

        writeProgress();
        System.out.println("");
    }    
}
