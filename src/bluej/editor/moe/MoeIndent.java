/*
 This file is part of the BlueJ program. 
 Copyright (C) 2010  Michael Kolling and John Rosenberg 

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
package bluej.editor.moe;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.text.BadLocationException;
import javax.swing.text.Element;

import bluej.Config;
import bluej.parser.nodes.ParsedNode;
import bluej.parser.nodes.NodeTree.NodeAndPosition;
import bluej.utility.Debug;

/**
 * This class contains the "auto layout" functionality of the editor.
 */
public class MoeIndent
{
    // from beginning string match any number of whitespaces or tabs.
    private static final Pattern WHITESPACE_TABS = Pattern.compile("^[ \\t]*");
    // from beginning of string match any number of whitespaces, tabs
    // and stars, followed by an optional forward slash and star.
    private static final Pattern WHITESPACE_TABS_STAR = Pattern.compile("^[ \\t*]*(/\\*)?");

    public static class AutoIndentInformation
    {
        private boolean perfect;
        private int newCaretPos;
        
        public AutoIndentInformation(boolean perfect, int newCaretPos)
        {
            this.perfect = perfect;
            this.newCaretPos = newCaretPos;
        }
        
        public boolean isPerfect()
        {
            return perfect;
        }
        
        public int getNewCaretPosition()
        {
            return newCaretPos;
        }
    }
    
    /**
     * Perform an auto-layout - calculate the correct indent for each source line, and apply it. Return
     * information about the applied indentation.
     */
    public static AutoIndentInformation calculateIndentsAndApply(MoeSyntaxDocument doc, int caretPos)
    {
        return calculateIndentsAndApply(doc, 0, doc.getLength(), caretPos);
    }
    
    /**
     * Perform an auto-layout - calculate the correct indent for each source line between the given
     * start and end positions, and apply it. Return information about the applied indentation.
     */
    public static AutoIndentInformation calculateIndentsAndApply(MoeSyntaxDocument doc, int startPos, int endPos, int prevCaretPos)
    {
        int caretPos = prevCaretPos;
        Element rootElement = doc.getDefaultRootElement();
        List<DocumentAction> methodUpdates = new LinkedList<DocumentAction>();
        List<DocumentAction> updates = new ArrayList<DocumentAction>(rootElement.getElementCount());
        
        IndentCalculator ii = new RootIndentCalculator();

        boolean lastLineWasBlank = false;
        boolean perfect = true;
        NodeAndPosition<ParsedNode> root = new NodeAndPosition<ParsedNode>(doc.getParser(), 0, doc.getParser().getSize());

        // examine if there are missing spaces between methods and add them.
        // NB. proper indentation of these changes later in this method.
        checkMethodSpacing(root, rootElement, methodUpdates);
        for (DocumentAction methodUpdate : methodUpdates) {
            caretPos = methodUpdate.apply(doc, caretPos);
        }

        for (int i = 0; i < rootElement.getElementCount(); i++) {
            Element el = rootElement.getElement(i);
            
            // If the element overlaps at all with our area of interest:
            if (el.getEndOffset() > startPos && el.getStartOffset() < endPos) {

                boolean thisLineBlank = isWhiteSpaceOnly(getElementContents(doc, el));
                DocumentAction update = null;
    
                if (thisLineBlank) {
                    if (lastLineWasBlank) {
                        // Consecutive blank lines; remove this one:
                        if (el.getEndOffset() <= doc.getLength()) {
                            update = new DocumentRemoveLineAction(el);
                            perfect = false;
                        }
                    } else {
                        // Single blank line (thus far), remove all spaces from
                        // it (and don't interrupt perfect status):
                        update = new DocumentIndentAction(el, "");
                    }
                } else {
                    String indent = calculateIndent(el, root, ii, doc);
                    update = new DocumentIndentAction(el, indent);
                    perfect = perfect && getElementContents(doc, el).startsWith(indent)
                                      && !isWhiteSpaceOnly(getElementContents(doc, el).substring(indent.length(),indent.length() + 1));
                }
    
                if (update != null) {
                    updates.add(update);
                }
                lastLineWasBlank = thisLineBlank;
            }
        }

        // Now apply them all:
        for (DocumentAction update : updates) {
            caretPos = update.apply(doc, caretPos);
        }

        return new AutoIndentInformation(perfect, caretPos);
    }

    /**
     * Finds the indent for the given element by looking at the nodes in the parse tree
     * 
     * @param el The element to calculate the indent for
     * @param start The Node that is either the one directly containing the given element,
     *              or is an ancestor of the one that directly contains the given element,
     *              or may not contain the element at all (in which case null will be returned)
     * @param startIC The IndentCalculator corresponding to start
     * @param doc The document involved
     * @return The indent that the element should have, up to the first non-whitespace character.
     *         Returns null if start does not contain the given element
     */
    private static String calculateIndent(Element el,
            NodeAndPosition<ParsedNode> start, IndentCalculator startIC, MoeSyntaxDocument doc)
    {
        int pos = el.getStartOffset() + findFirstNonIndentChar(getElementContents(doc, el), true);
        if (pos >= start.getPosition() && pos < start.getEnd()) {
            // The slightly awkward way to loop through the children of "start":
            for (Iterator<NodeAndPosition<ParsedNode>> i = start.getNode().getChildren(start.getPosition()); i.hasNext();) {
                NodeAndPosition<ParsedNode> nap = i.next();
                String inner = calculateIndent(el, nap, startIC.getForChild(nap.getNode()), doc);
                if (inner != null) {
                    return inner;
                }
            }
            try {
                return startIC.getCurIndent(doc.getText(pos, 1).charAt(0));
            }
            catch (BadLocationException e) {
                return "";
            }
        }
        else {
            return null;
        }
    }

    /**
     * Loops through the children of the specified {@link root} to look for methods
     * that have no space between them, then recursively looks at the children
     * to see if they have any inner methods.
     *
     * <p>When it does identify two methods with no gap in between them it adds
     * a new {@link DocumentAddLineAction} object with the current position
     * to the {@link updates} list.
     * @param root      Node to look inside of.
     * @param map       Map of the document used to get the lines of the method.
     * @param updates   List to update with new actions where needed.
     */
    private static void checkMethodSpacing(NodeAndPosition<ParsedNode> root, Element map, List<DocumentAction> updates)
    {
        NodeAndPosition<ParsedNode> current = null;
        NodeAndPosition<ParsedNode> next = null;
        for (Iterator<NodeAndPosition<ParsedNode>> i = root.getNode().getChildren(root.getPosition()); i.hasNext();) {
            next = i.next();
            if (current != null && 
                    current.getNode().getNodeType() == ParsedNode.NODETYPE_METHODDEF &&
                    current.getNode().getNodeType() == next.getNode().getNodeType()) {
                int currentLine = map.getElementIndex(current.getEnd());
                int nextLine = map.getElementIndex(next.getPosition());
                if ((currentLine + 1) == nextLine) {
                    updates.add(0, new DocumentAddLineAction(next.getPosition()));
                } else if ((currentLine == nextLine)) {
                    updates.add(0, new DocumentAddLineAction(next.getPosition(), true));
                }
                    
            }
            current = next;
            checkMethodSpacing(current, map, updates);
        }
        
    }
    
    // ---------------------------------------
    // Indent calculation:
    

    /**
     * An interface that calculates the indentation level that
     * the corresponding node should have.  You should use getForChild as you
     * descend the parse tree to get the indentation for child nodes.
     */
    private static interface IndentCalculator
    {
        /**
         * Gets the IndentCalculator for the given child node of the node that this
         * IndentCalculator instance corresponds to
         */
        public IndentCalculator getForChild(ParsedNode n);
        /**
         * Gets the indent for a line in the current node that begins with the
         * given character.  This allows for comments (such as this one right here)
         * to have their leading asterisks indented by an extra space. 
         */
        public String getCurIndent(char beginsWith);
    }
    
    /**
     * An implementation of IndentCalculator for the root node of the document.
     */
    private static class RootIndentCalculator implements IndentCalculator
    {
        public IndentCalculator getForChild(ParsedNode n)
        {
            return new NodeIndentCalculator("", n);
        }

        public String getCurIndent(char beginsWith)
        {
            return "";
        }
    }
    
    /**
     * An implementation of IndentCalculator for a non-root node of the document.
     */
    private static class NodeIndentCalculator implements IndentCalculator
    {
        private final String existingIndent;
        private final ParsedNode parent;
        
        private static final int tabSize = Config.getPropInteger("bluej.editor.tabsize", 4);
        private static final String spaces =
            "                                                                                   ";

        private final static String STANDARD_INDENT = spaces.substring(0, tabSize);
        private final static String CONTINUATION_INDENT = STANDARD_INDENT;
        // To make it line up like this:
        // /**
        //  *
        //  *
        //  */
        // This must be a single space:
        private final static String COMMENT_ASTERISK_INDENT = " ";

        public NodeIndentCalculator(String existingIndent, ParsedNode parent)
        {
            this.existingIndent = existingIndent;
            this.parent = parent;
        }

        public IndentCalculator getForChild(ParsedNode child)
        {
            String newIndent = existingIndent;

            if (child.isInner()) {
                newIndent += STANDARD_INDENT;
            }
            else if (! child.isContainer() && ! parent.isContainer() && ! parent.isInner()) {
                newIndent += CONTINUATION_INDENT;
            }

            return new NodeIndentCalculator(newIndent, child);
        }

        public String getCurIndent(char beginsWith)
        {
            if (parent.getNodeType() == ParsedNode.NODETYPE_COMMENT && beginsWith == '*') {
                return existingIndent + COMMENT_ASTERISK_INDENT;
            }
            else {
                return existingIndent;
            }
        }
    }


    /**
     * Interface representing some document editing action.
     */
    private interface DocumentAction
    {
        /**
         * Apply the edit represented by this DocumentAction to the document, and return the
         * adjusted caret position.
         */
        public int apply(MoeSyntaxDocument doc, int prevCaretPos);
    }

    /**
     * A document action for removing a line.
     */
    private static class DocumentRemoveLineAction implements DocumentAction
    {
        private Element lineToRemove;

        public DocumentRemoveLineAction(Element lineToRemove)
        {
            this.lineToRemove = lineToRemove;
        }

        public int apply(MoeSyntaxDocument doc, int caretPos)
        {
            try {
                int start = lineToRemove.getStartOffset();
                int end = lineToRemove.getEndOffset();
                int lineLength = lineToRemove.getEndOffset() - lineToRemove.getStartOffset();
                doc.remove(lineToRemove.getStartOffset(), lineLength);
                
                if (caretPos < start) {
                    return caretPos; // before us, not moved
                } else if (caretPos >= end) {
                    return caretPos - lineLength; // after us, move by the line length
                } else {
                    return start; // in us, move to start of line
                }
            }
            catch (BadLocationException e) {
                Debug.reportError("Problem while trying to remove line from document: "
                        + lineToRemove.getStartOffset() + "->" + lineToRemove.getEndOffset()
                        + " in document of size " + doc.getLength(), e);
                return caretPos;
            }
        }
    }

    /**
     * A class representing an update to the indentation on a line of the document.  This is different
     * to a LineAction because it intrinsically knows which line it needs to update
     */
    private static class DocumentIndentAction implements DocumentAction
    {
        private Element el;
        private String indent;

        public DocumentIndentAction(Element el, String indent)
        {
            this.el = el;
            this.indent = indent;
        }

        // Because we keep element references, we don't have to worry about the offsets
        // altering, because they will alter before we process the line, and thus
        // everything works nicely.
        public int apply(MoeSyntaxDocument doc, int caretPos)
        {
            int spos = el.getStartOffset();
            int ll = doc.getDefaultRootElement().getElementIndex(spos);
            if (doc.getDefaultRootElement().getElement(ll) != el) {
                System.out.println("Element mismatch!!!!");
            }
            
            String line = getElementContents(doc, el);
            int lengthPrevWhitespace = findFirstNonIndentChar(line, true);
            boolean anyTabs = line.substring(0, lengthPrevWhitespace).indexOf("\t") != -1;
            // If we want to put in 4 spaces, and there are already exactly 4 tabs,
            // without the anyTabs check, we would leave the whitespace alone;
            // hence why we need the check:
            if (indent != null && (anyTabs || (indent.length() != lengthPrevWhitespace))) {
                try {
                    int origStartOffset = el.getStartOffset(); 
                    doc.replace(el.getStartOffset(), lengthPrevWhitespace,
                            indent, null);
                    
                    if (caretPos < origStartOffset) {
                        return caretPos; // before us, not moved
                    } else if (caretPos >= origStartOffset + lengthPrevWhitespace) {
                        int changeLength = indent.length() - lengthPrevWhitespace;
                        return caretPos + changeLength; // after us, move by the change length
                    } else {
                        return origStartOffset + indent.length(); // in us, move to end of indent
                    }
                }
                catch (BadLocationException e) {
                    Debug.reportError("Error doing indent in DocumentUpdate", e);
                    return caretPos;
                }
            } else {
                return caretPos;
            }
        }
    }

    /**
     * Get the textual contents of a document element (i.e. a line). 
     */
    private static String getElementContents(MoeSyntaxDocument doc, Element el)
    {
        try {
            return doc.getText(el.getStartOffset(), el.getEndOffset() - el.getStartOffset());
        } catch (BadLocationException e) {
            Debug.reportError("Error getting element contents in document", e);
            return "";
        }
    }

    /**
     * Return true if s contains only whitespace (or nothing).
     */
    public static boolean isWhiteSpaceOnly(String s)
    {
        return s.trim().length() == 0;
    }

    /**
     * Find the position of the first non-indentation character in a string.
     * Indentation characters are <whitespace>, //, *, /*, /**.
     */
    public static int findFirstNonIndentChar(String line, boolean whitespaceOnly)
    {
        // if this line ends a comment, indent whitepace only;
        // otherwise indent across whitespace, asterisks and comment starts
        Matcher m = whitespaceOnly ? WHITESPACE_TABS.matcher(line) : WHITESPACE_TABS_STAR.matcher(line);
        return m.find() ? m.end() : 0;
    }

    /**
     * A document action for inserting a blank line in the document.
     */
    private static class DocumentAddLineAction implements DocumentAction
    {
        private int position;
        private boolean twoSeparators;
        
        public DocumentAddLineAction(int position)
        {
            this(position, false);
        }

        public DocumentAddLineAction(int position, boolean twoSeparators)
        {
            this.position = position;
            this.twoSeparators = twoSeparators;
        }

        /**
         * Tries to insert a new line into the document at the stated position.
         * @param doc   Document to add the new line to.
         * @param prevCaretPos  Location to move the the cursor to after the operation
         * @return  The caret position.
         */
        public int apply(MoeSyntaxDocument doc, int prevCaretPos)
        {
            String lineSeparator = System.getProperty("line.separator");
            try {
                if (twoSeparators) {
                    doc.insertString(position, lineSeparator + lineSeparator, null);
                } else {
                    doc.insertString(position, lineSeparator, null);
                }
            } catch (BadLocationException ex) {
                Debug.reportError("Error in adding new line to document", ex);
            }
            if (position > prevCaretPos) {
                return prevCaretPos;
            } else if (twoSeparators)  {
                return prevCaretPos + (lineSeparator.length() * 2);
            } else {
                return prevCaretPos + lineSeparator.length();
            }
        }
        
    }
}