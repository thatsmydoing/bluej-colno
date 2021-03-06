/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009  Michael Kolling and John Rosenberg 
 
 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 
 
 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of 
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 
 
 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software 
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. 
 
 This file is subject to the Classpath exception as provided in the  
 LICENSE.txt file that accompanied this code.
 */
package bluej.compiler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import bluej.Config;
import bluej.classmgr.BPClassLoader;
import bluej.utility.Debug;

/**
 * Reasonably generic interface between the BlueJ IDE and the Java compiler.
 * 
 * @author Michael Cahill
 * @version $Id: JobQueue.java 8510 2010-10-21 04:12:29Z davmac $
 */
public class JobQueue
{
    private static JobQueue queue = null;

    public static synchronized JobQueue getJobQueue()
    {
        if (queue == null)
            queue = new JobQueue();
        return queue;
    }

    // ---- instance ----

    private CompilerThread thread = null;
    private Compiler compiler = null;

    /**
     * Construct the JobQueue. This is private; use getJobQueue() to get the job queue instance.
     */
    private JobQueue()
    {
        // determine which compiler we should be using
        String compilertype = Config.getPropString("bluej.compiler.type");

        //even though it is specified to use internal, the preferred compiler for a
        //system running Java 6 or greater is the JavaCompiler API
        if (compilertype.equals("internal")) {
            if (Config.isJava16()){
                try
                {
                    Class<?> c = Class.forName("bluej.compiler.CompilerAPICompiler");
                    compiler =(Compiler)c.newInstance();
                }
                catch (Throwable e) {
                    Debug.message("Could not instantiate the compiler API compiler implementation; defaulting to old compiler");
                    compiler = new JavacCompilerInternal();
                }
            } else {
                compiler = new JavacCompilerInternal();
            }
        }
        else if (compilertype.equals("javac")) {
            compiler = new JavacCompiler(Config.getJDKExecutablePath("bluej.compiler.executable", "javac"));
        }
        else {
            Debug.message(Config.getString("compiler.invalidcompiler"));
        }

        thread = new CompilerThread();

        // Lower priority to improve GUI response time during compilation
        int priority = Thread.currentThread().getPriority() - 1;
        priority = Math.max(priority, Thread.MIN_PRIORITY);
        thread.setPriority(priority);

        thread.start();
    }

    /**
     * Adds a job to the compile queue.
     * 
     * @param sources   The files to compile
     * @param observer  Observer to be notified when compilation begins,
     *                  errors/warnings, completes
     * @param classPath The classpath to use to locate objects/source code
     * @param destDir   Destination for class files?
     * @param suppressUnchecked    Suppress "unchecked" warning in java 1.5
     */
    public void addJob(File[] sources, CompileObserver observer, BPClassLoader bpClassLoader, File destDir, boolean suppressUnchecked)
    {
        List<String> options = new ArrayList<String>();
        if (bpClassLoader.loadsForJavaMEproject()) {
            String optionString = Config.getPropString(Compiler.JAVAME_COMPILER_OPTIONS, null);
            Compiler.tokenizeOptionString(options, optionString);
        }
        String optionString = Config.getPropString(Compiler.COMPILER_OPTIONS, null);
        Compiler.tokenizeOptionString(options, optionString);
        
        thread.addJob(new Job(sources, compiler, observer, bpClassLoader,
                destDir, suppressUnchecked, options));
    }

    /**
     * Wait until the compiler job queue is empty, then return.
     */
    public void waitForEmptyQueue()
    {
        synchronized (thread) {
            while (thread.isBusy()) {
                try {
                    thread.wait();
                }
                catch (InterruptedException ex) {}
            }
        }
    }
}
