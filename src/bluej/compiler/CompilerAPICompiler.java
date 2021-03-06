/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2010  Michael Kolling and John Rosenberg 

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
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

/**
 * A compiler implementation using the Compiler API introduced in Java 6.
 * 
 * @author Marion Zalk
 */
public class CompilerAPICompiler extends Compiler
{
    public CompilerAPICompiler()
    {
        setDebug(true);
        setDeprecation(true);
    }
    
    /**
     * Compile some source files by using the JavaCompiler API. Allows for the addition of user
     * options
     * 
     * @param sources
     *            The files to compile
     * @param observer
     *            The compilation observer
     * @param internal
     *            True if compiling BlueJ-generated code (shell files) False if
     *            compiling user code
     * 
     * @return  true if successful
     */
    public boolean compile(File[] sources, CompileObserver observer,
            boolean internal, List<String> userOptions) 
    {
        boolean result = true;
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        List<String> optionsList = new ArrayList<String>();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        try
        {  
            //setup the filemanager
            StandardJavaFileManager sjfm = jc.getStandardFileManager(diagnostics, null, null);
            List<File> pathList = new ArrayList<File>();
            List<File> outputList = new ArrayList<File>();
            outputList.add(getDestDir());
            Collections.addAll(pathList, getClassPath());
            
            // In BlueJ, the destination directory and the source path are
            // always the same
            sjfm.setLocation(StandardLocation.SOURCE_PATH, outputList);
            sjfm.setLocation(StandardLocation.CLASS_PATH, pathList);
            sjfm.setLocation(StandardLocation.CLASS_OUTPUT, outputList);
            
            //get the source files for compilation  
            Iterable<? extends JavaFileObject> compilationUnits1 =
                sjfm.getJavaFileObjectsFromFiles(Arrays.asList(sources));
            //add any options
            if(isDebug()) {
                optionsList.add("-g");
            }
            if(isDeprecation()) {
                optionsList.add("-deprecation");
            }
            
            File[] bootClassPath = getBootClassPath();
            if (bootClassPath != null && bootClassPath.length != 0) {
                sjfm.setLocation(StandardLocation.PLATFORM_CLASS_PATH, Arrays.asList(bootClassPath));
            }
            
            optionsList.addAll(userOptions);
            
            //compile
            jc.getTask(null, sjfm, diagnostics, optionsList, null, compilationUnits1).call();
            sjfm.close();            
        }
        catch(IOException e)
        {
            e.printStackTrace(System.out);
            return false;
        }

        //Query diagnostics for error/warning messages
        List<Diagnostic<? extends JavaFileObject>> diagnosticList = diagnostics.getDiagnostics();        
        String src = null;
        int pos = 0;
        int col = 0;
        String msg = null;
        boolean error = false;
        boolean warning = false;
        int diagnosticErrorPosition = -1;
        int diagnosticWarningPosition = -1;
        //ensure an error is printed if there is one, else use the warning/s; note/s
        //(errors should have priority in the diagnostic list, but this is just in case not)
        for (int i = 0; i< diagnosticList.size(); i++){
            if (diagnosticList.get(i).getKind().equals(Diagnostic.Kind.ERROR))
            {
                diagnosticErrorPosition = i;
                error = true;
                warning = false;
                break;
            }
            if (diagnosticList.get(i).getKind().equals(Diagnostic.Kind.WARNING)||
                    diagnosticList.get(i).getKind().equals(Diagnostic.Kind.NOTE))
            {
                warning = true;
                //just to ensure the first instance of the warning position is recorded 
                //(not the last position)
                if (diagnosticWarningPosition == -1){
                    diagnosticWarningPosition = i;
                }
            }
        }
        //diagnosticErrorPosition can either be the warning/error
        if (diagnosticErrorPosition < 0)
        {
            diagnosticErrorPosition = diagnosticWarningPosition;
        }
        //set the necessary values
        if (warning || error) 
        {
            Diagnostic<? extends JavaFileObject> diagnostic = diagnosticList.get(diagnosticErrorPosition);
            if (diagnostic.getSource() != null)
            {
                // See bug: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6419926
                // JDK6 returns URIs without a scheme in some cases, so always resolve against a
                // known "file:/" URI:
                URI srcUri = sources[0].toURI().resolve(diagnostic.getSource().toUri());
                src = new File(srcUri).toString();
            }
            pos = (int) diagnostic.getLineNumber();
            col = (int) diagnostic.getColumnNumber();

            // Handle compiler error messages 
            if (error) 
            {
                result = false;          
                msg = processMessage(src, pos, diagnostic.getMessage(null));  
                observer.errorMessage(src, pos, col, msg);
            }
            // Handle compiler warning messages  
            // If it is a warning message, need to get all the messages
            if (warning) 
            {
                for (int i = diagnosticErrorPosition; i < diagnosticList.size(); i++)
                {
                    //'display unchecked warning messages' in the preferences dialog is unchecked
                    //therefore notes should not be displayed
                    //warnings can still be displayed
                    if (internal && diagnosticList.get(i).getKind().equals(Diagnostic.Kind.NOTE)){
                        continue;
                    }
                    else
                    {
                        msg = diagnosticList.get(i).getMessage(null);
                        observer.warningMessage(src, pos, col, msg);
                    }
                }              
            }
        }
        return result;
    }

    /**
     * Processes messages returned from the compiler. This just slightly adjusts the format of some
     * messages.
     */
    protected String processMessage(String src, int pos, String message)
    {
        // For JDK 6, the message is in this format: 
        //   path and filename:line number:message
        // i.e includes the path and line number; so we need to strip that off.
        String expected = src + ":" + pos + ": ";
        if (message.startsWith(expected)) 
        {
            message = message.substring(expected.length());
        }
        
        if (message.contains("cannot resolve symbol")
                || message.contains("cannot find symbol")
                || message.contains("incompatible types")) 
        {
            // divide the message into lines so we can retrieve necessary values
            int index1, index2;
            String line2, line3;
            index1 = message.indexOf('\n');
            if (index1 == -1) 
            {
                // We don't know how to handle this.
                return message;
            }
            index2 = message.indexOf('\n',index1+1);
            //i.e there are only 2 lines not 3
            if (index2 < index1) 
            {
                line2 = message.substring(index1).trim();
                line3 = "";
            }
            else {
                line2 = message.substring(index1, index2).trim();
                line3 = message.substring(index2).trim();
            }
            message = message.substring(0, index1);

            //e.g incompatible types
            //found   : int
            //required: java.lang.String
            if (line2.startsWith("found") && line2.indexOf(':') != -1) 
            {
                message = message +" - found " + line2.substring(line2.indexOf(':') + 2, line2.length());
            }
            if (line3.startsWith("required") && line3.indexOf(':') != -1) {
                message = message +" but expected " + line3.substring(line3.indexOf(':') + 2, line3.length());
            }
            //e.g cannot find symbol
            //symbol: class Persons
            if (line2.startsWith("symbol") && line2.indexOf(':') != -1) 
            {
                message = message + " - " + line2.substring(line2.indexOf(':') + 2, line2.length());
            }
        }
        return message;
    }
}