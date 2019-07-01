/*
 * SwingWindow
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 01/07/19 16:00
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package omegadrive.ui;

import com.google.common.base.Strings;
import omegadrive.SystemLoader;
import omegadrive.system.SystemProvider;
import omegadrive.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static omegadrive.system.SystemProvider.SystemEvent.*;
import static omegadrive.util.ScreenSizeHelper.*;

public class SwingWindow implements DisplayWindow {

    private static Logger LOG = LogManager.getLogger(SwingWindow.class.getSimpleName());

    private Dimension fullScreenSize;
    private GraphicsDevice[] graphicsDevices;
    private Dimension screenSize = DEFAULT_SCALED_SCREEN_SIZE;
    private Dimension baseScreenSize = DEFAULT_BASE_SCREEN_SIZE;

    private BufferedImage src;
    private BufferedImage dest;
    private int[] pixelsSrc;
    private int[] pixelsDest;
    private double scale = DEFAULT_SCALE_FACTOR;

    private final JLabel screenLabel = new JLabel();
    private final JLabel fpsLabel = new JLabel("");

    private JFrame jFrame;
    private SystemProvider mainEmu;

    private java.util.List<JCheckBoxMenuItem> regionItems;
    private JCheckBoxMenuItem fullScreenItem;
    private JCheckBoxMenuItem muteItem;
    private boolean showDebug = false;

    //when scaling is slow set this to FALSE
    private static boolean UI_SCALE_ON_EDT;

    static {
        initLookAndFeel();
        UI_SCALE_ON_EDT = Boolean.valueOf(System.getProperty("ui.scale.on.edt", "false"));
    }

    public void setTitle(String title) {
        jFrame.setTitle(APP_NAME + mainEmu.getSystemType().getShortName() +  " " + VERSION + " - " + title);
    }

    public SwingWindow(SystemProvider mainEmu) {
        this.mainEmu = mainEmu;
    }

    public static void main(String[] args) {
        SwingWindow frame = new SwingWindow(null);
        frame.init();
    }

    public static void initLookAndFeel() {
        String lfClassName = UIManager.getCrossPlatformLookAndFeelClassName();
        try {
            //avoid GTK3 on Java11, it has issues
            //https://bugs.java.com/bugdatabase/view_bug.do?bug_id=JDK-8203627
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("GTK+".equals(info.getName())) {
//                    lfClassName = info.getClassName();
                    break;
                }
            }
            UIManager.setLookAndFeel(lfClassName);
        } catch (Exception e) {
            LOG.error(e);
        }
    }

    private Map<SystemProvider.SystemEvent, AbstractAction> actionMap = new HashMap<>();

    private java.util.List<JCheckBoxMenuItem> createRegionItems() {
        java.util.List<JCheckBoxMenuItem> l = new ArrayList<>();
        l.add(new JCheckBoxMenuItem("AutoDetect", true));
        Arrays.stream(RegionDetector.Region.values()).sorted().
                forEach(r -> l.add(new JCheckBoxMenuItem(r.name(), false)));
        return l;
    }

    public void init() {
        Util.registerJmx(this);
        graphicsDevices = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        LOG.info("Screen detected: " + graphicsDevices.length);
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (graphicsDevices.length > 1) {
            int screenNumber = Math.min(DEFAULT_SCREEN, graphicsDevices.length - 1);
            gd = graphicsDevices[screenNumber];
        }
        LOG.info("Using screen: " + gd.getIDstring());
        fullScreenSize = gd.getDefaultConfiguration().getBounds().getSize();
        LOG.info("Full screen size: " + fullScreenSize);
        LOG.info("Emulation viewport size: " + ScreenSizeHelper.DEFAULT_SCALED_SCREEN_SIZE);
        LOG.info("Application size: " + DEFAULT_FRAME_SIZE);

        src = createImage(gd, screenSize, true);
        dest = createImage(gd, screenSize, false);
        screenLabel.setIcon(new ImageIcon(dest));

        jFrame = new JFrame(FRAME_TITLE_HEAD, gd.getDefaultConfiguration());

        jFrame.getContentPane().setBackground(Color.BLACK);
        jFrame.getContentPane().setForeground(Color.BLACK);

        JMenuBar bar = new JMenuBar();

        JMenu menu = new JMenu("File");
        bar.add(menu);

        JMenu setting = new JMenu("Setting");
        bar.add(setting);

        JMenuItem pauseItem = new JMenuItem("Pause");
        addKeyAction(pauseItem, TOGGLE_PAUSE, e -> mainEmu.handleSystemEvent(TOGGLE_PAUSE, null));
        setting.add(pauseItem);

        JMenuItem resetItem = new JMenuItem("Reset");
        addKeyAction(resetItem, RESET, e -> mainEmu.reset());
        setting.add(resetItem);

        JMenu menuBios = new JMenu("Region");
        setting.add(menuBios);

        JMenu menuView = new JMenu("View");
        bar.add(menuView);

        regionItems = createRegionItems();
        regionItems.forEach(menuBios::add);

        fullScreenItem = new JCheckBoxMenuItem("Full Screen", false);
        addKeyAction(fullScreenItem, TOGGLE_FULL_SCREEN, e -> fullScreenAction(e));
        menuView.add(fullScreenItem);

        muteItem = new JCheckBoxMenuItem("Enable Sound", true);
        addKeyAction(muteItem, TOGGLE_MUTE, e -> mainEmu.handleSystemEvent(TOGGLE_MUTE, null));
        menuView.add(muteItem);

        JMenu helpMenu = new JMenu("Help");
        bar.add(helpMenu);
        bar.add(fpsLabel);

        JMenuItem loadRomItem = new JMenuItem("Load ROM");
        addKeyAction(loadRomItem, NEW_ROM, e -> handleNewRom());

        JMenuItem closeRomItem = new JMenuItem("Close ROM");
        addKeyAction(closeRomItem, CLOSE_ROM, e -> mainEmu.handleSystemEvent(CLOSE_ROM, null));

        JMenuItem loadStateItem = new JMenuItem("Load State");
        addKeyAction(loadStateItem, LOAD_STATE, e -> handleLoadState());

        JMenuItem saveStateItem = new JMenuItem("Save State");
        addKeyAction(saveStateItem, SAVE_STATE, e -> handleSaveState());

        JMenuItem quickSaveStateItem = new JMenuItem("Quick Save State");
        addKeyAction(quickSaveStateItem, QUICK_SAVE, e -> handleQuickSaveState());

        JMenuItem quickLoadStateItem = new JMenuItem("Quick Load State");
        addKeyAction(quickLoadStateItem, QUICK_LOAD, e -> handleQuickLoadState());

        JMenuItem exitItem = new JMenuItem("Exit");
        addKeyAction(exitItem, CLOSE_APP, e -> {
            SystemProvider mainEmu = getMainEmu();
            mainEmu.handleSystemEvent(CLOSE_APP, null);
            System.exit(0);
        });

        JMenuItem aboutItem = new JMenuItem("About");
        addAction(aboutItem, e -> showHelpMessage(aboutItem.getText(), getAboutString()));

        JMenuItem creditsItem = new JMenuItem("Credits");
        addAction(creditsItem, e -> showHelpMessage(creditsItem.getText(),
                FileLoader.loadFileContentAsString("CREDITS.md")));

        JMenuItem keyBindingsItem = new JMenuItem("Key Bindings");
        addAction(keyBindingsItem, e -> showHelpMessage(keyBindingsItem.getText(),
                FileLoader.loadFileContentAsString("key.config")));

        JMenuItem readmeItem = new JMenuItem("Readme");
        addAction(readmeItem, e -> showHelpMessage(readmeItem.getText(),
                FileLoader.loadFileContentAsString("README.md")));

        JMenuItem licenseItem = new JMenuItem("License");
        addAction(licenseItem, e -> showHelpMessage(licenseItem.getText(),
                FileLoader.loadFileContentAsString("LICENSE.md")));

        JMenuItem historyItem = new JMenuItem("History");
        addAction(historyItem, e -> showHelpMessage(historyItem.getText(),
                FileLoader.loadFileContentAsString("HISTORY.md")));

        menu.add(loadRomItem);
        menu.add(closeRomItem);
        menu.add(loadStateItem);
        menu.add(saveStateItem);
        menu.add(quickLoadStateItem);
        menu.add(quickSaveStateItem);
        menu.add(exitItem);
        helpMenu.add(aboutItem);
        helpMenu.add(keyBindingsItem);
        helpMenu.add(readmeItem);
        helpMenu.add(creditsItem);
        helpMenu.add(historyItem);
        helpMenu.add(licenseItem);

        screenLabel.setHorizontalAlignment(SwingConstants.CENTER);
        screenLabel.setVerticalAlignment(SwingConstants.CENTER);

        AbstractAction debugUiAction = toAbstractAction("debugUI", e -> showDebugInfo(!showDebug));
        actionMap.put(SET_DEBUG_UI, debugUiAction);

        setupFrameKeyListener();

        jFrame.setMinimumSize(DEFAULT_FRAME_SIZE);
        jFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jFrame.setResizable(true);
        jFrame.setJMenuBar(bar);
        jFrame.add(screenLabel, -1);

        jFrame.pack();

        //get the center location and then reset it
        jFrame.setLocationRelativeTo(null);
        Point centerPoint = jFrame.getLocation();
        jFrame.setLocation(gd.getDefaultConfiguration().getBounds().x + centerPoint.x,
                gd.getDefaultConfiguration().getBounds().y + centerPoint.y);

        jFrame.setVisible(true);
    }

    private void addKeyAction(JMenuItem component, SystemProvider.SystemEvent event, ActionListener l) {
        AbstractAction action = toAbstractAction(component.getText(), l);
        if (event != NONE) {
            action.putValue(Action.ACCELERATOR_KEY, KeyBindingsHandler.getKeyStrokeForEvent(event));
            actionMap.put(event, action);
        }
        component.setAction(action);
    }

    private void addAction(JMenuItem component, ActionListener act) {
        addKeyAction(component, NONE, act);
    }

    private AbstractAction toAbstractAction(String name, ActionListener listener) {
        return new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
                listener.actionPerformed(e);
            }

            @Override
            public void setEnabled(boolean newValue) {
                super.setEnabled(true);
            }
        };
    }

    private void showHelpMessage(String title, String msg) {
        JTextArea area = new JTextArea(msg);
        JScrollPane scrollPane = new JScrollPane(area);
        scrollPane.setPreferredSize(jFrame.getPreferredSize());
        JOptionPane.showMessageDialog(this.jFrame,
                scrollPane, "Help: " + title, JOptionPane.INFORMATION_MESSAGE);
    }

    private void fullScreenAction(ActionEvent doToggle) {
        if (doToggle == null) {
            fullScreenItem.setState(!fullScreenItem.getState());
        }
        LOG.info("Full screen: {}", fullScreenItem.isSelected());
    }

    private GraphicsDevice getGraphicsDevice() {
        return jFrame.getGraphicsConfiguration().getDevice();
    }

    private BufferedImage createImage(GraphicsDevice gd, Dimension d, boolean src) {

        BufferedImage bi = gd.getDefaultConfiguration().createCompatibleImage(d.width, d.height);
        if (bi.getType() != BufferedImage.TYPE_INT_RGB) {
            //mmh we need INT_RGB here
            bi = new BufferedImage(d.width, d.height, BufferedImage.TYPE_INT_RGB);
        }
        pixelsSrc = src ? getPixels(bi) : pixelsSrc;
        pixelsDest = src ? pixelsDest : getPixels(bi);
        return bi;
    }

    private void showDebugInfo(boolean showDebug) {
        if (fullScreenItem.getState()) {
            jFrame.getJMenuBar().setVisible(showDebug);
        }
        fpsLabel.setVisible(showDebug);
        this.showDebug = showDebug;
    }

    public void resetScreen() {
        Util.sleep(250);
        SwingUtilities.invokeLater(() -> {
            Arrays.fill(pixelsDest, 0);
            screenLabel.invalidate();
            screenLabel.repaint();
            fpsLabel.setText("");
            jFrame.setTitle(FRAME_TITLE_HEAD);
            LOG.info("Blanking screen");
        });
    }

    @Override
    public void setFullScreen(boolean value) {
        fullScreenItem.setState(value);
    }

    @Override
    public String getRegionOverride() {
        return regionItems.stream().filter(i -> i.isSelected()).
                map(JCheckBoxMenuItem::getText).findFirst().orElse(null);
    }

    //TODO
    @Override
    public void renderScreenLinear(int[] data, String label, VideoMode videoMode) {
        boolean changed = resizeScreen(videoMode);
        System.arraycopy(data, 0, pixelsSrc,0, data.length);
        if (scale > 1) {
            Dimension ouput = new Dimension((int) (baseScreenSize.width * scale), (int) (baseScreenSize.height * scale));
            RenderingStrategy.renderNearest(pixelsSrc, pixelsDest, baseScreenSize, ouput);
        }
        if (!Strings.isNullOrEmpty(label)) {
            getFpsLabel().setText(label);
        }
        screenLabel.repaint();
    }

    @Override
    public void renderScreen(int[][] data, String label, VideoMode videoMode) {
        if (UI_SCALE_ON_EDT) {
            SwingUtilities.invokeLater(() -> renderScreenInternal(data, label, videoMode));
        } else {
            renderScreenInternal(data, label, videoMode);
        }
    }

    public void renderScreenInternal(int[][] data, String label, VideoMode videoMode) {
        boolean changed = resizeScreen(videoMode);
        if (scale > 1) {
            Dimension ouput = new Dimension((int) (baseScreenSize.width * scale), (int) (baseScreenSize.height * scale));
            RenderingStrategy.toLinear(pixelsSrc, data, baseScreenSize);
            RenderingStrategy.renderNearest(pixelsSrc, pixelsDest, baseScreenSize, ouput);
        } else {
            RenderingStrategy.toLinear(pixelsDest, data, baseScreenSize);
        }
        if (!Strings.isNullOrEmpty(label)) {
            getFpsLabel().setText(label);
        }
        screenLabel.repaint();
    }

    private boolean resizeScreen(VideoMode videoMode) {
        boolean goFullScreen = fullScreenItem.getState();
        Dimension newBaseScreenSize = getScreenSize(videoMode, 1);
        if (!newBaseScreenSize.equals(baseScreenSize)) {
            baseScreenSize = newBaseScreenSize;
        }
        double scale = DEFAULT_SCALE_FACTOR;
        if (goFullScreen) {
            double scaleW = fullScreenSize.getWidth() / baseScreenSize.getWidth();
            double scaleH = fullScreenSize.getHeight() * FULL_SCREEN_WITH_TITLE_BAR_FACTOR / baseScreenSize.getHeight();
            scale = Math.min(scaleW, scaleH);
        }
        return resizeScreenInternal(newBaseScreenSize, scale, goFullScreen);
    }

    private boolean resizeScreenInternal(Dimension newScreenSize, double scale, boolean isFullScreen) {
        boolean scaleChanged = this.scale != scale;
        boolean baseResize = !newScreenSize.equals(screenSize);
        if (baseResize || scaleChanged) {

            screenSize = newScreenSize;
            this.scale = scale;
            try {
                Runnable resizeRunnable = getResizeRunnable(isFullScreen);
                if (SwingUtilities.isEventDispatchThread()) {
                    resizeRunnable.run();
                } else {
                    SwingUtilities.invokeAndWait(resizeRunnable);
                }
            } catch (InterruptedException | InvocationTargetException e) {
                LOG.error(e.getMessage());
            }
            return true;
        }
        return false;
    }

    private Runnable getResizeRunnable(boolean isFullScreen) {
        return () -> {
            src = createImage(getGraphicsDevice(), baseScreenSize, true);
            Dimension d = new Dimension((int) (src.getWidth() * scale), (int) (src.getHeight() * scale));
            dest = createImage(getGraphicsDevice(), d, false);

            screenLabel.setIcon(new ImageIcon(dest));
            jFrame.setPreferredSize(isFullScreen ? fullScreenSize : baseScreenSize);
            jFrame.getJMenuBar().setVisible(!isFullScreen);
            if (!isFullScreen) {
                //TODO this breaks multi-monitor
                jFrame.setLocationRelativeTo(null); //center
            }
            jFrame.pack();
            LOG.info("Emulation Viewport size: " + d);
            LOG.info("Application size: " + jFrame.getSize());
        };
    }

    private Optional<File> loadFileDialog(Component parent, FileFilter filter) {
        return fileDialog(parent, filter, true);
    }

    private Optional<File> fileDialog(Component parent, FileFilter filter, boolean load) {
        int dialogType = load ? JFileChooser.OPEN_DIALOG : JFileChooser.SAVE_DIALOG;
        Optional<File> res = Optional.empty();
        JFileChooser fileChooser = new JFileChooser(FileLoader.basePath);
        fileChooser.setFileFilter(filter);
        fileChooser.setDialogType(dialogType);
        int result = fileChooser.showDialog(parent, null);
        if (result == JFileChooser.APPROVE_OPTION) {
            res = Optional.ofNullable(fileChooser.getSelectedFile());
        }
        return res;
    }

    private Optional<File> loadRomDialog(Component parent) {
        return loadFileDialog(parent, FileLoader.ROM_FILTER); //TODO
    }

    private Optional<File> loadStateFileDialog(Component parent) {
        return loadFileDialog(parent, FileLoader.SAVE_STATE_FILTER);
    }

    private void handleLoadState() {
        Optional<File> optFile = loadStateFileDialog(jFrame);
        if (optFile.isPresent()) {
            Path file = optFile.get().toPath();
            mainEmu.handleSystemEvent(LOAD_STATE, file);
        }
    }

    private void handleQuickLoadState() {
        Path file = Paths.get(".", FileLoader.QUICK_SAVE_FILENAME);
        mainEmu.handleSystemEvent(QUICK_LOAD, file);
    }

    private void handleQuickSaveState() {
        Path p = Paths.get(".", FileLoader.QUICK_SAVE_FILENAME);
        mainEmu.handleSystemEvent(QUICK_SAVE, p);
    }

    private void handleSaveState() {
        Optional<File> optFile = fileDialog(jFrame, FileLoader.SAVE_STATE_FILTER, false);
        if (optFile.isPresent()) {
            mainEmu.handleSystemEvent(SAVE_STATE, optFile.get().toPath());
        }
    }

    private void handleNewRom() {
        mainEmu.handleSystemEvent(CLOSE_ROM, null);
        Optional<File> optFile = loadRomDialog(jFrame);
        if (optFile.isPresent()) {
            Path file = optFile.get().toPath();
            SystemLoader.getInstance().handleNewRomFile(file);
        }
    }

    private SystemProvider getMainEmu() {
        return mainEmu;
    }

    @Override
    public void reloadSystem(SystemProvider systemProvider) {
        Optional.ofNullable(mainEmu).ifPresent(sys -> sys.handleSystemEvent(CLOSE_ROM, null));
        this.mainEmu = systemProvider;

        Arrays.stream(jFrame.getKeyListeners()).forEach(jFrame::removeKeyListener);
        setupFrameKeyListener();
        setTitle("");
    }

    @Override
    public void addKeyListener(KeyAdapter keyAdapter) {
        jFrame.addKeyListener(keyAdapter);
    }

    private JLabel getFpsLabel() {
        return fpsLabel;
    }

    private int[] getPixels(BufferedImage img) {
        return ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
    }

    //TODO this is necessary in fullScreenMode
    private void setupFrameKeyListener() {
        jFrame.addKeyListener(new KeyAdapter() {

            @Override
            public void keyPressed(KeyEvent e) {
                //avoid double firing when not in fullScreen
                if (!fullScreenItem.isSelected()) {
                    return;
                }
                SystemProvider mainEmu = getMainEmu();
                KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(e);
//                LOG.info(keyStroke.toString());
                SystemProvider.SystemEvent event = KeyBindingsHandler.getSystemEventIfAny(keyStroke);
                if (event != null && event != NONE) {
                    Optional.ofNullable(actionMap.get(event)).ifPresent(act -> act.actionPerformed(null));
                }
            }
        });
    }
}
