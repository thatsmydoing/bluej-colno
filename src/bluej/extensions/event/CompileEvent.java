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
package bluej.extensions.event;

import java.io.File;

/**
 * This class encapsulates compiler events.
 * It allows an extension writer to know when a compilation starts and
 * finishes, whether it succeeds or fails, and what warnings or errors are 
 * generated.
 * 
 * @version $Id: CompileEvent.java 6215 2009-03-30 13:28:25Z polle $
 */

/*
 * Author Damiano Bolla, University of Kent at Canterbury, 2003
 */
public class CompileEvent implements ExtensionEvent 
{
  /**
   * Event generated when compilation begins.
   */
  public static final int COMPILE_START_EVENT=1;

  /**
   * Event generated when a compilation warning occurs.
   * A warning event is one that will not invalidate the compilation.
   */
  public static final int COMPILE_WARNING_EVENT=2;

  /**
   * Event generated when a compilation error occurs.
   * An error event is one that will invalidate the compilation
   */
  public static final int COMPILE_ERROR_EVENT=3;

  /**
   * Event generated when a compilation finishes successfully.
   */
  public static final int COMPILE_DONE_EVENT=4;

  /**
   * Event generated when a compilation finishes unsuccessfully.
   */
  public static final int COMPILE_FAILED_EVENT=5;

  private int    eventId;
  private File[] fileNames;   // An array of names this event belong to
  private int    errorLineNumber;
  private int    errorColumnNumber;
  private String errorMessage;

  /**
   * Constructor for a CompileEvent.
   */
  public CompileEvent(int anEventId, File[] aFileNames)
    {
    eventId   = anEventId;
    fileNames = aFileNames;
    }

  /**
   * Returns the eventId, one of the values defined.
   */
  public int getEvent ()
    {
    return eventId;
    }


  /**
   * Returns an array of zero, one or more files related to this event.
   */
  public File[] getFiles ()
    {
    return fileNames;
    }

  /**
   * Sets the line number where an error or warning occurred.
   */
  public void setErrorLineNumber ( int aLineNumber )
    {
    errorLineNumber = aLineNumber;
    }

  /**
   * Returns the line number where the compilation error occurs.
   * Only valid in the case of an error or warning event.
   */
  public int getErrorLineNumber ( )
    {
    return errorLineNumber;
    }
  
  /**
   * Sets the column number where an error or warning occurred.
   */
  public void setErrorColumnNumber ( int aColumnNumber )
    {
    errorColumnNumber = aColumnNumber;
    }

  /**
   * Returns the column number where the compilation error occurs.
   * Only valid in the case of an error or warning event.
   */
  public int getErrorColumnNumber ( )
    {
    return errorColumnNumber;
    }

  /**
   * Sets the error message for an error or warning event.
   */
  public void setErrorMessage ( String anErrorMessage )
    {
    errorMessage = anErrorMessage;
    }
  
  /**
   * Returns the error message generated by the compiler.
   * Only valid in the case of an error or warning event.
   */
  public String getErrorMessage ( )
    {
    return errorMessage;
    }

  /**
   * Returns a meaningful description of this event.
   */
  public String toString()
    {
    StringBuffer aRisul = new StringBuffer (500);

    aRisul.append("CompileEvent:");

    if ( eventId == COMPILE_START_EVENT ) aRisul.append(" COMPILE_START_EVENT");
    if ( eventId == COMPILE_WARNING_EVENT ) aRisul.append(" COMPILE_WARNING_EVENT");
    if ( eventId == COMPILE_ERROR_EVENT ) aRisul.append(" COMPILE_ERROR_EVENT");
    if ( eventId == COMPILE_DONE_EVENT ) aRisul.append(" COMPILE_DONE_EVENT");
    if ( eventId == COMPILE_FAILED_EVENT ) aRisul.append(" COMPILE_FAILED_EVENT");

    aRisul.append(" getFiles().length=");
    aRisul.append(fileNames.length);
    
    for(int i = 0; i < fileNames.length; i++) {
        aRisul.append(" getFiles()[" + i + "]=");
        aRisul.append(fileNames[i]);
    }

    if ( eventId == COMPILE_WARNING_EVENT || eventId == COMPILE_ERROR_EVENT )
      {
      aRisul.append(" errorLineNumber="+errorLineNumber);
      aRisul.append(" errorColumnNumber="+errorColumnNumber);
      aRisul.append(" errorMessage="+errorMessage);
      }

    return aRisul.toString();
    }
}
