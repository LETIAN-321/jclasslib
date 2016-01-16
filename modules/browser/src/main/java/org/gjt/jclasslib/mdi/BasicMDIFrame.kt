/*
    This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public
    License as published by the Free Software Foundation; either
    version 2 of the license, or (at your option) any later version.
*/

package org.gjt.jclasslib.mdi;

import org.gjt.jclasslib.browser.BrowserDesktopManager
import org.gjt.jclasslib.browser.BrowserInternalFrame
import org.gjt.jclasslib.util.GUIHelper
import java.awt.*
import java.awt.List
import java.awt.event.*
import java.util.*
import java.util.prefs.Preferences
import javax.swing.Action
import javax.swing.JFrame
import javax.swing.KeyStroke


public abstract class BasicMDIFrame extends JFrame {

    private static final int DEFAULT_WINDOW_WIDTH = 800;
    private static final int DEFAULT_WINDOW_HEIGHT = 600;

    private static final String SETTINGS_WINDOW_WIDTH = "windowWidth";
    private static final String SETTINGS_WINDOW_HEIGHT = "windowHeight";
    private static final String SETTINGS_WINDOW_X = "windowX";
    private static final String SETTINGS_WINDOW_Y = "windowY";
    private static final String SETTINGS_WINDOW_MAXIMIZED = "windowMaximized";

    // Actions
    
    /** Action for selecting the next child window. */
    protected Action nextWindowAction;
    /** Action for selecting the previous child window. */
    protected Action previousWindowAction;
    /** Action for tiling all child windows. */
    protected Action tileWindowsAction;
    /** Action for stacking all child windows. */
    protected Action stackWindowsAction;

    // Visual components

    /** <tt>JDesktop</tt> pane which contains all child windows. */
    protected JScrollPane scpDesktop;
    /** The desktop pane. */
    protected JDesktopPane desktopPane;
    /** <tt>DesktopManager</tt> for this MDI parent frame. */
    protected BrowserDesktopManager desktopManager;
    /** <tt>JMenu</tt> for window actions. */
    protected JMenu menuWindow;

    private Rectangle lastNormalFrameBounds;

    /**
        Constructor.
     */
    public BasicMDIFrame() {

        setupActions();
        setupMenu();
        setupFrame();
        setupEventHandlers();
        loadWindowSettings();
    }

    public JDesktopPane getDesktopPane() {
        return desktopPane;
    }

    public JMenu getMenuWindow() {
        return menuWindow;
    }

    public void setWindowActionsEnabled(boolean enabled) {
        nextWindowAction.setEnabled(enabled);
        previousWindowAction.setEnabled(enabled);
        tileWindowsAction.setEnabled(enabled);
        stackWindowsAction.setEnabled(enabled);
    }

    /**
        Create a <tt>BasicDesktopManager</tt> for this MDI parent window.
        @return the <tt>BasicDesktopManager</tt>
     */
    protected abstract BrowserDesktopManager createDesktopManager();
    
    /**
        Exit the application.
     */
    protected void quit() {

        saveWindowSettings();
        dispose();
        System.exit(0);
    }

    /**
        Close all internal frames.
     */
    protected void closeAllFrames() {

        List<BrowserInternalFrame> frames = desktopManager.getOpenFrames();
        while (frames.size() > 0) {
            BrowserInternalFrame frame = frames.get(0);
            frame.doDefaultCloseAction();
        }
    }

    /**
         Create an <tt>MDIConfig</tt> object that describes the current configuration of
         all internal frames. This object can be serialized and reactivated with
         <tt>readMDIConfig</tt>.
         @return the <tt>MDIConfig</tt> object
     */
    protected MDIConfig createMDIConfig() {

        MDIConfig config = new MDIConfig();
        List<BrowserInternalFrame> openFrames = desktopManager.getOpenFrames();
        List<MDIConfig.InternalFrameDesc> internalFrameDescs = new ArrayList<MDIConfig.InternalFrameDesc>(openFrames.size());

        for (BrowserInternalFrame openFrame : openFrames) {

            Rectangle bounds = openFrame.getNormalBounds();
            MDIConfig.InternalFrameDesc internalFrameDesc = new MDIConfig.InternalFrameDesc();
            internalFrameDesc.setInitParam(openFrame.getInitParam());
            internalFrameDesc.setX(bounds.x);
            internalFrameDesc.setY(bounds.y);
            internalFrameDesc.setWidth(bounds.width);
            internalFrameDesc.setHeight(bounds.height);
            internalFrameDesc.setMaximized(openFrame.isMaximum());
            internalFrameDesc.setIconified(openFrame.isIcon());

            if (openFrame == desktopPane.getSelectedFrame()) {
                config.setActiveFrameDesc(internalFrameDesc);
            }
            internalFrameDescs.add(internalFrameDesc);

        }
        config.setInternalFrameDescs(internalFrameDescs);

        return config;
    }

    /**
         Takes an <tt>MDIConfig</tt> object that describes a configuration of
         internal frames and populates the MDI frame with this configuration.
         @param config the <tt>MDIConfig</tt> object to be read
     */
    protected void readMDIConfig(MDIConfig config) {

        boolean anyFrameMaximized = false;
        for (MDIConfig.InternalFrameDesc internalFrameDesc : config.getInternalFrameDescs()) {
            BrowserInternalFrame frame;
            try {
                frame = new BrowserInternalFrame(desktopManager, internalFrameDesc.getInitParam());
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }
            desktopManager.resizeFrame(
                frame,
                internalFrameDesc.getX(),
                internalFrameDesc.getY(),
                internalFrameDesc.getWidth(),
                internalFrameDesc.getHeight()
            );

            boolean frameMaximized = internalFrameDesc.isMaximized();
            anyFrameMaximized = anyFrameMaximized || frameMaximized;

            try {
                if (frameMaximized || anyFrameMaximized) {
                    frame.setMaximum(true);
                } else if (internalFrameDesc.isIconified()) {
                    frame.setIcon(true);
                }
            } catch (PropertyVetoException ex) {
            }

            if (internalFrameDesc == config.getActiveFrameDesc()) {
                desktopManager.setActiveFrame(frame);
            }
        }

        desktopManager.showAll();
    }

    private void setupActions() {

        nextWindowAction = new WindowAction("Next window");
        nextWindowAction.putValue(Action.SHORT_DESCRIPTION,
                                  "Cycle to the next opened window");
        nextWindowAction.setEnabled(false);
        
        previousWindowAction = new WindowAction("Previous window");
        previousWindowAction.putValue(Action.SHORT_DESCRIPTION,
                                     "Cycle to the previous opened window");
        previousWindowAction.setEnabled(false);
        
        tileWindowsAction = new WindowAction("Tile windows");
        tileWindowsAction.putValue(Action.SHORT_DESCRIPTION,
                                   "Tile all windows in the main frame");
        tileWindowsAction.setEnabled(false);

        stackWindowsAction = new WindowAction("Stack windows");
        stackWindowsAction.putValue(Action.SHORT_DESCRIPTION,
                                    "Stack all windows in the main frame");
        stackWindowsAction.setEnabled(false);
    }

    private void setupMenu() {

        menuWindow = new JMenu("Window");
            menuWindow.add(previousWindowAction).setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_F2, InputEvent.CTRL_MASK));
            menuWindow.add(nextWindowAction).setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_F3, InputEvent.CTRL_MASK));
            menuWindow.add(tileWindowsAction);
            menuWindow.add(stackWindowsAction);
    }

    private void setupFrame() {

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout(5, 5));
        contentPane.add(buildDesktop(), BorderLayout.CENTER);
        
    }

    private void setupEventHandlers() {

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent event) {
                quit();
            }
        });

        addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent event) {
                desktopManager.checkResizeInMaximizedState();
                recordLastNormalFrameBounds();
            }

            public void componentMoved(ComponentEvent event) {
                recordLastNormalFrameBounds();
            }
        });

    }

    private void saveWindowSettings() {

        Preferences preferences = Preferences.userNodeForPackage(getClass());

        boolean maximized = (getExtendedState() & MAXIMIZED_BOTH) != 0;
        preferences.putBoolean(SETTINGS_WINDOW_MAXIMIZED, maximized);
        Rectangle frameBounds = maximized ? lastNormalFrameBounds : getBounds();

        if (frameBounds != null) {
            preferences.putInt(SETTINGS_WINDOW_WIDTH, frameBounds.width);
            preferences.putInt(SETTINGS_WINDOW_HEIGHT, frameBounds.height);
            preferences.putInt(SETTINGS_WINDOW_X, frameBounds.x);
            preferences.putInt(SETTINGS_WINDOW_Y, frameBounds.y);
        }
    }

    private void loadWindowSettings() {

        Preferences preferences = Preferences.userNodeForPackage(getClass());
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Rectangle screenBounds = new Rectangle(screenSize);

        int windowX = preferences.getInt(SETTINGS_WINDOW_X, (int)(screenSize.getWidth() - DEFAULT_WINDOW_WIDTH)/2);
        int windowY = preferences.getInt(SETTINGS_WINDOW_Y, (int)(screenSize.getHeight() - DEFAULT_WINDOW_HEIGHT)/2);
        int windowWidth = preferences.getInt(SETTINGS_WINDOW_WIDTH, DEFAULT_WINDOW_WIDTH);
        int windowHeight = preferences.getInt(SETTINGS_WINDOW_HEIGHT, DEFAULT_WINDOW_HEIGHT);

        Rectangle frameBounds = new Rectangle(windowX, windowY, windowWidth, windowHeight);

        // sanitize frame bounds
        frameBounds.translate(-Math.min(0, frameBounds.x), -Math.min(0, frameBounds.y));
        frameBounds.translate(-Math.max(0, frameBounds.x + frameBounds.width - screenSize.width), -Math.max(0, frameBounds.y + frameBounds.height- screenSize.height));
        frameBounds = screenBounds.intersection(frameBounds);

        setBounds(frameBounds);

        if (preferences.getBoolean(SETTINGS_WINDOW_MAXIMIZED, false)) {
            setExtendedState(MAXIMIZED_BOTH);
        }

    }

    private void recordLastNormalFrameBounds() {
        if ((getExtendedState() & MAXIMIZED_BOTH) == 0) {
            Rectangle frameBounds = getBounds();
            if (frameBounds.getX() >= 0 && frameBounds.getY() >= 0) {
                lastNormalFrameBounds = frameBounds;
            }
        }

    }

    private JComponent buildDesktop() {

        desktopPane = new JDesktopPane();
        desktopPane.setBackground(Color.LIGHT_GRAY);
        desktopManager = createDesktopManager();
        desktopPane.setDesktopManager(desktopManager);
        scpDesktop = new JScrollPane(desktopPane);
        GUIHelper.setDefaultScrollbarUnits(scpDesktop);

        return scpDesktop;
    }

    public void invalidateDektopPane() {
        desktopPane.setPreferredSize(null);
        desktopPane.invalidate();
        desktopPane.getParent().validate();
        scpDesktop.invalidate();
        scpDesktop.validate();

    }

    private class WindowAction extends AbstractAction {

        private WindowAction(String name) {
            super(name);
        }

        public void actionPerformed(ActionEvent ev) {

            if (this == previousWindowAction) {
                desktopManager.cycleToPreviousWindow();
            } else if (this == nextWindowAction) {
                desktopManager.cycleToNextWindow();
            } else if (this == tileWindowsAction) {
                desktopManager.tileWindows();
            } else if (this == stackWindowsAction) {
                desktopManager.stackWindows();
            }

        }
    }

}