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
package bluej.debugmgr.inspector;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.border.EmptyBorder;

import bluej.BlueJTheme;
import bluej.Config;
import bluej.debugger.DebuggerObject;
import bluej.pkgmgr.Package;
import bluej.pkgmgr.PackageEditor;
import bluej.prefmgr.PrefMgr;
import bluej.testmgr.record.ArrayElementGetRecord;
import bluej.testmgr.record.ArrayElementInspectorRecord;
import bluej.testmgr.record.GetInvokerRecord;
import bluej.testmgr.record.InvokerRecord;
import bluej.testmgr.record.ObjectInspectInvokerRecord;
import bluej.utility.DialogManager;
import java.awt.Dimension;
import java.awt.Font;

/**
 * A window that displays the fields in an object or a method return value.
 * 
 * @author Michael Kolling
 * @author Poul Henriksen
 * @author Bruce Quig
 */
public class ObjectInspector extends Inspector
{
    // === static variables ===

    protected final static String inspectTitle = Config.getString("debugger.inspector.object.title");
    protected final static String noFieldsMsg = Config.getString("debugger.inspector.object.noFields");

    // === instance variables ===
    
    /** A reference to the object being inspected */
    protected DebuggerObject obj;

    /**
     * Name of the object, as it appears on the object bench, or null if the
     * object being inspected is not on the object bench
     */
    protected String objName;

    protected boolean queryArrayElementSelected = false;
    private int selectedIndex;

    /**
     * array of Integers representing the array indexes from a large array that
     * have been selected for viewing
     */
    //protected TreeSet arraySet = null;

    /**
     * list which is built when viewing an array that records the object slot
     * corresponding to each array index
     */
    protected List<Integer> indexToSlotList = null; 

    /**
     *  Note: 'pkg' may be null if 'ir' is null.
     * 
     * @param obj
     *            The object displayed by this viewer
     * @param name
     *            The name of this object or "null" if the name is unobtainable
     * @param pkg
     *            The package all this belongs to
     * @param ir
     *            the InvokerRecord explaining how we created this result/object
     *            if null, the "get" button is permanently disabled
     * @param parent
     *            The parent frame of this frame
     */
    public ObjectInspector(DebuggerObject obj, InspectorManager inspectorManager, String name, Package pkg, InvokerRecord ir, final JFrame parent)
    {
        super(inspectorManager, pkg, ir, new Color(244, 158, 158));

        this.obj = obj;
        this.objName = name;

        final ObjectInspector thisInspector = this;
        EventQueue.invokeLater(new Runnable() {
            public void run()
            {
                makeFrame();
                pack();
                if (parent instanceof Inspector) {
                    DialogManager.tileWindow(thisInspector, parent);
                }
                else {
                    DialogManager.centreWindow(thisInspector, parent);
                }

                if (Config.isMacOS() || Config.isWinOS()) {
                    // Window translucency doesn't seem to work on linux.
                    // We'll assume that it might not work on any OS other
                    // than those on which it's known to work: MacOS and Windows.
                    thisInspector.setWindowOpaque(false);
                }
                if (!Config.isMacOS()) {
                    // MacOS automatically makes tranparent windows draggable by their
                    // content - no need to do it ourselves.
                    thisInspector.installListenersForMoveDrag();
                }
            }
        });
    }

    /**
     * Build the GUI
     */
    protected void makeFrame()
    {
        setTitle(inspectTitle);
        setUndecorated(true);
        setLayout(new BorderLayout());
        setBackground(new Color(232,230,218));

        // Create the header

        JComponent header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.setDoubleBuffered(false);
        String className = obj.getStrippedGenClassName();

        String fullTitle = null;
        if(objName != null) {
            fullTitle = objName + " : " + className;   
        }
        else {
            fullTitle = " : " + className;
        }
        JLabel headerLabel = new JLabel(fullTitle, JLabel.CENTER);
        Font font = headerLabel.getFont();
        headerLabel.setFont(font.deriveFont(Font.BOLD));
        headerLabel.setOpaque(false);
        headerLabel.setAlignmentX(0.5f);
        headerLabel.setForeground(Color.white);
        header.add(headerLabel);
        header.add(Box.createVerticalStrut(BlueJTheme.generalSpacingWidth));
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(214, 92, 92));
        sep.setBackground(new Color(0, 0, 0, 0));
        header.add(sep);

        // Create the main panel (field list, Get/Inspect buttons)

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setOpaque(false);
        mainPanel.setDoubleBuffered(false);

        if (getListData().length != 0) {
            JScrollPane scrollPane = createFieldListScrollPane();
            mainPanel.add(scrollPane, BorderLayout.CENTER);
        } else {
            JLabel lab = new JLabel("  " + noFieldsMsg);
            lab.setPreferredSize(new Dimension(200, 30));
            lab.setFont(PrefMgr.getStandardFont().deriveFont(20.0f));
            lab.setForeground(new Color(250, 160, 160));
            mainPanel.add(lab);
        }

        JPanel inspectAndGetButtons = createInspectAndGetButtons();
        mainPanel.add(inspectAndGetButtons, BorderLayout.EAST);

        Insets insets = BlueJTheme.generalBorderWithStatusBar.getBorderInsets(mainPanel);
        mainPanel.setBorder(new EmptyBorder(insets));

        // create bottom button pane with "Close" button

        JPanel bottomPanel = new JPanel();
        bottomPanel.setOpaque(false);
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 5, 5, 5));

        JPanel buttonPanel;
        buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.setOpaque(false);
        JButton button = createCloseButton();
        buttonPanel.add(button, BorderLayout.EAST);
        JButton classButton = new JButton(showClassLabel);
        classButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                showClass();
            }
        });
        buttonPanel.add(classButton, BorderLayout.WEST);
        buttonPanel.setDoubleBuffered(false);

        bottomPanel.add(buttonPanel);
        bottomPanel.setDoubleBuffered(false);

        // add the components

        JPanel contentPane = new JPanel() {

            @Override
            protected void paintComponent(Graphics g)
            {               
                Graphics2D g2d = (Graphics2D)g.create();
                {
                    GraphicsConfiguration gc = g2d.getDeviceConfiguration();
                    BufferedImage img = gc.createCompatibleImage(getWidth(),
                                    getHeight(),
                                    Transparency.TRANSLUCENT);
                    Graphics2D imgG = img.createGraphics();

                    imgG.setComposite(AlphaComposite.Clear);
                    imgG.fillRect(0, 0, getWidth(), getHeight());
    
                    imgG.setComposite(AlphaComposite.Src);
                    imgG.setRenderingHint(
                            RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    imgG.setColor(Color.WHITE);
                    imgG.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30);
    
                    imgG.setComposite(AlphaComposite.SrcAtop);
                    imgG.setPaint(new GradientPaint(getWidth() / 2, getHeight() / 2, new Color(227, 71, 71)
                                                   ,getWidth() / 2, getHeight(), new Color(205, 39, 39)));
                    imgG.fillRect(0, 0, getWidth(), getHeight());
                    
                    imgG.setPaint(new GradientPaint(getWidth() / 2, 0, new Color(248, 120, 120)
                                                   ,getWidth() / 2, getHeight() / 2, new Color(231, 96, 96)));
                    imgG.fill(new Ellipse2D.Float(-2*getWidth(),-5*getHeight()/2,5*getWidth(),3*getHeight()));

                    imgG.setColor(Color.BLACK);
                    imgG.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 30, 30);                    
                    
                    imgG.dispose();
                    g2d.drawImage(img, 0, 0, this);
                }
                g2d.dispose();
            }
        };
        add(contentPane);

        contentPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        contentPane.setOpaque(false);
        contentPane.setDoubleBuffered(false);
        contentPane.setLayout(new BorderLayout());
        contentPane.add(header, BorderLayout.NORTH);
        contentPane.add(mainPanel, BorderLayout.CENTER);
        contentPane.add(bottomPanel, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(button);
    }

    /**
     * True if this inspector is used to display a method call result.
     */
    protected Object[] getListData()
    {
        // if is an array (we potentially will compress the array if it is
        // large)
        if (obj.isArray()) {
            return compressArrayList(obj).toArray(new Object[0]);
        }
        else {
            return obj.getInstanceFields(true).toArray(new Object[0]);
        }
    }

    /**
     * An element in the field list was selected.
     */
    protected void listElementSelected(int slot)
    {
        // add index to slot method for truncated arrays
        if (obj.isArray()) {
            slot = indexToSlot(slot);
            if (slot >= 0) {
                selectedIndex = slot;
            }
            // if selection is the first field containing array length
            // we treat as special case and do nothing more
            if (slot == ARRAY_LENGTH_SLOT_VALUE) {
                setCurrentObj(null, null, null);
                setButtonsEnabled(false, false);
                return;
            }
        }

        queryArrayElementSelected = (slot == (ARRAY_QUERY_SLOT_VALUE));

        // for array compression..
        if (queryArrayElementSelected) { // "..." in Array inspector
            setCurrentObj(null, null, null); //  selected
            // check to see if elements are objects,
            // using the first item in the array
            if (obj.instanceFieldIsObject(0)) {
                setButtonsEnabled(true, false);
            }
            else {
                setButtonsEnabled(false, false);
            }
        }
        else if (obj.instanceFieldIsObject(slot)) {
            setCurrentObj(obj.getInstanceFieldObject(slot), obj.getInstanceFieldName(slot), obj.getInstanceFieldType(slot));

            if (obj.instanceFieldIsPublic(slot)) {
                setButtonsEnabled(true, true);
            }
            else {
                setButtonsEnabled(true, false);
            }
        }
        else {
            setCurrentObj(null, null, null);
            setButtonsEnabled(false, false);
        }
    }

    /**
     * Show the inspector for the class of an object.
     */
    protected void showClass()
    {
        inspectorManager.getClassInspectorInstance(obj.getClassRef(), pkg, this);
    }

    @Override
    protected void doInspect()
    {
        if (queryArrayElementSelected) {
            selectArrayElement();
        }
        else if (selectedField != null) {
            boolean isPublic = getButton.isEnabled();
            
            if (! obj.isArray()) {
                InvokerRecord newIr = new ObjectInspectInvokerRecord(selectedFieldName, ir);
                inspectorManager.getInspectorInstance(selectedField, selectedFieldName, pkg, isPublic ? newIr : null, this);
            }
            else {
                InvokerRecord newIr = new ArrayElementInspectorRecord(ir, selectedIndex);
                inspectorManager.getInspectorInstance(selectedField, selectedFieldName, pkg, isPublic ? newIr : null, this);
            }
        }
    }
    
    @Override
    protected void doGet()
    {
        if (selectedField != null) {
            InvokerRecord getIr;
            if (! obj.isArray()) {
                getIr = new GetInvokerRecord(selectedFieldType, selectedFieldName, ir);
            }
            else {
                getIr = new ArrayElementGetRecord(selectedFieldType, selectedIndex, ir);
            }
                
            PackageEditor pkgEd = pkg.getEditor();
            pkgEd.recordInteraction(getIr);
            pkgEd.raisePutOnBenchEvent(this, selectedField, selectedField.getGenType(), getIr);
        }
    }
    
    /**
     * Remove this inspector.
     */
    protected void remove()
    {
        if(inspectorManager != null) {
            inspectorManager.removeInspector(obj);
        }
    }

    /**
     * Shows a dialog to select array element for inspection
     */
    private void selectArrayElement()
    {
        String response = DialogManager.askString(this, "ask-index");

        if (response != null) {
            try {
                int slot = Integer.parseInt(response);
                // check if within bounds of array
                if (slot >= 0 && slot < obj.getInstanceFieldCount()) {
                    // if its an object set as current object
                    if (obj.instanceFieldIsObject(slot)) {
                        boolean isPublic = getButton.isEnabled();
                        InvokerRecord newIr = new ArrayElementInspectorRecord(ir, slot);
                        setCurrentObj(obj.getInstanceFieldObject(slot), obj.getInstanceFieldName(slot), obj.getInstanceFieldType(slot));
                        inspectorManager.getInspectorInstance(selectedField, selectedFieldName, pkg, isPublic ? newIr : null, this);
                    }
                    else {
                        // it is not an object - a primitive, so lets
                        // just display it in the array list display
                        setButtonsEnabled(false, false);
                        //arraySet.add(new Integer(slot));
                        // TODO: this is currently broken. Primitive array elements re just
                        //       not displayed right now. Would need to be added to display list.
                        update();
                    }
                }
                else { // not within array bounds
                    DialogManager.showError(this, "out-of-bounds");
                }
            }
            catch (NumberFormatException e) {
                // input could not be parsed, eg. non integer value
                setCurrentObj(null, null, null);
                DialogManager.showError(this, "cannot-access-element");
            }
        }
        else {
            // set current object to null to avoid re-inspection of
            // previously selected wildcard
            setCurrentObj(null, null, null);
        }
    }

    private final static int VISIBLE_ARRAY_START = 40; // show at least the
                                                       // first 40 elements
    private final static int VISIBLE_ARRAY_TAIL = 5; // and the last five
                                                     // elements

    private final static int ARRAY_QUERY_SLOT_VALUE = -2; // signal marker of
                                                          // the [...] slot in
                                                          // our
    private final static int ARRAY_LENGTH_SLOT_VALUE = -1; // marker for having
                                                           // selected the slot
                                                           // containing array
                                                           // length

    /**
     * Compress a potentially large array into a more displayable shortened
     * form.
     * 
     * Compresses an array field name list to a maximum of VISIBLE_ARRAY_START
     * which are guaranteed to be displayed at the start, then some [..]
     * expansion slots, followed by VISIBLE_ARRAY_TAIL elements from the end of
     * the array. When a selected element is chosen indexToSlot allows the
     * selection to be converted to the original array element position.
     * 
     * @param fullArrayFieldList
     *            the full field list for an array
     * @return the compressed array
     */
    private List<String> compressArrayList(DebuggerObject arrayObject)
    {
        // mimic the public length field that arrays possess
        // according to the java spec...
        indexToSlotList = new LinkedList<Integer>();
        indexToSlotList.add(0, new Integer(ARRAY_LENGTH_SLOT_VALUE));

        // the +1 here is due to the fact that if we do not have at least one
        // more than
        // the sum of start elements and tail elements, then there is no point
        // in displaying
        // the ... elements because there would be no elements for them to
        // reveal
        if (arrayObject.getInstanceFieldCount() > (VISIBLE_ARRAY_START + VISIBLE_ARRAY_TAIL + 2)) {

            // the destination list
            List<String> newArray = new ArrayList<String>(2 + VISIBLE_ARRAY_START + VISIBLE_ARRAY_TAIL);
            newArray.add(0, ("int length = " + arrayObject.getInstanceFieldCount()));
            for (int i = 0; i <= VISIBLE_ARRAY_START; i++) {
                // first 40 elements are displayed as per normal
                newArray.add(arrayObject.getInstanceField(i, true));
                indexToSlotList.add(new Integer(i));
            }

            // now the first of our expansion slots
            newArray.add("[...]");
            indexToSlotList.add(new Integer(ARRAY_QUERY_SLOT_VALUE));

            for (int i = VISIBLE_ARRAY_TAIL; i > 0; i--) {
                // last 5 elements are displayed
                newArray.add(arrayObject.getInstanceField(arrayObject.getInstanceFieldCount() - i, true));
                // slot is offset by one due to length field being included in
                // fullArrayFieldList therefore we add 1 to compensate
                indexToSlotList.add(new Integer(arrayObject.getInstanceFieldCount() - (i + 1)));
            }
            return newArray;
        }
        else {
            List<String> fullArrayFieldList = arrayObject.getInstanceFields(true);
            fullArrayFieldList.add(0, ("int length = " + arrayObject.getInstanceFieldCount()));
            
            for (int i = 0; i < fullArrayFieldList.size(); i++) {
                indexToSlotList.add(new Integer(i));
            }
            return fullArrayFieldList;
        }
    }

    /**
     * Converts list index position to that of array element position in arrays.
     * Uses the List built in compressArrayList to do the mapping.
     * 
     * @param listIndexPosition
     *            the position selected in the list
     * @return the translated index of field array element
     */
    private int indexToSlot(int listIndexPosition)
    {
        Integer slot = indexToSlotList.get(listIndexPosition);

        return slot.intValue();
    }

    protected int getPreferredRows()
    {
        return 8;
    }
}
