package net.schwarzbaer.java.tools.imagemapeditor;

import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.Vector;
import java.util.function.BiConsumer;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListModel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.gui.StandardMainWindow.DefaultCloseOperation;

public class ImageMapEditor {

	private final StandardMainWindow mainWindow;
	private final EditorView editorView;
	private final JList<Area> areaList;
	private MapImage mapImage;

	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		new ImageMapEditor("Image Map Editor",null,null,true).initialize();
	}
	
	public static void show(String title                   ) { show(title, null    , null); }
	public static void show(String title, MapImage mapImage) { show(title, mapImage, null); }
	public static void show(String title, MapImage mapImage, Vector<Area> areas) {
		new ImageMapEditor(title,mapImage,areas,false).initialize();
	}
	
	ImageMapEditor(String title, MapImage mapImage, Vector<Area> areas, boolean asStandAloneApp) {
		this.mapImage = mapImage;
		
		DefaultCloseOperation closeOp = asStandAloneApp ? DefaultCloseOperation.EXIT_ON_CLOSE : DefaultCloseOperation.DISPOSE_ON_CLOSE;
		mainWindow = new StandardMainWindow(title, closeOp);
		
		AreaListModel areaListModel = new AreaListModel(areas);
		areaList = new JList<>(areaListModel);
		JScrollPane areaListScrollPane = new JScrollPane(areaList);
		areaListScrollPane.setBorder(BorderFactory.createTitledBorder("List of Areas"));
		
		editorView = new EditorView(800,600,areaListModel);
		
		JPanel leftPanel = new JPanel(new BorderLayout(3,3));
		leftPanel.add(areaListScrollPane,BorderLayout.CENTER);
		
		JPanel contentPane = new JPanel(new BorderLayout(3,3));
		contentPane.add(leftPanel,BorderLayout.WEST);
		contentPane.add(editorView,BorderLayout.CENTER);
		
		JMenuBar menuBar = new JMenuBar();
		
		JMenu fileMenu = menuBar.add(new JMenu("File"));
		fileMenu.add(createMenuItem("Write HTML ...", true, e->{
			// TODO
		}));
		fileMenu.add(createMenuItem("Write HTML (reduced) ...", true, e->{
			// TODO
		}));
		if (asStandAloneApp) {
			fileMenu.addSeparator();
			fileMenu.add(createMenuItem("Quit",true,e->{ System.exit(0); }));
		}
		
		if (this.mapImage==null) {
			JFileChooser imageFileChooser = new JFileChooser("./");
			imageFileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
			imageFileChooser.setMultiSelectionEnabled(false);
			imageFileChooser.addChoosableFileFilter(new FileNameExtensionFilter("JPEG Image", "jpg", "jpeg"));
			imageFileChooser.addChoosableFileFilter(new FileNameExtensionFilter("PNG Image", "png"));
			
			JMenu imageMenu = menuBar.add(new JMenu("Image"));
			imageMenu.add(createMenuItem("Load Image from File ...", true, e->{
				if (imageFileChooser.showOpenDialog(mainWindow)!=JFileChooser.APPROVE_OPTION) return;
				File file = imageFileChooser.getSelectedFile();
				MapImage newMapImage = MapImage.loadImage(file);
				if (newMapImage==null) return;
				this.mapImage = newMapImage;
				editorView.setImage(this.mapImage.image);
			}));
			imageMenu.add(createMenuItem("Load Image from URL ...", true, e->{
				String urlStr = JOptionPane.showInputDialog(mainWindow, "message", "title", JOptionPane.QUESTION_MESSAGE);
				if (urlStr==null) return;
				MapImage newMapImage = MapImage.loadImage(urlStr);
				if (newMapImage==null) return;
				this.mapImage = newMapImage;
				editorView.setImage(this.mapImage.image);
			}));
		}
		
		mainWindow.startGUI(contentPane,menuBar);
	}

	private static JMenuItem createMenuItem(String title, boolean isEnabled, ActionListener al) {
		JMenuItem comp = new JMenuItem(title);
		comp.setEnabled(isEnabled);
		if (al!=null) comp.addActionListener(al);
		return comp;
	}

	private void initialize() {
		if (mapImage!=null)
			editorView.setImage(mapImage.image);
	}
	
	@SuppressWarnings("unused")
	static class AreaListModel implements ListModel<Area>, Iterable<Area> {
		
		private final Vector<ListDataListener> listDataListeners;
		private final Vector<Area> data;
		
		AreaListModel() { this(null); }
		AreaListModel(Vector<Area> data) {
			listDataListeners = new Vector<>();
			this.data = data==null ? new Vector<>() : new Vector<>(data);
		}
				
		@Override public void    addListDataListener(ListDataListener l) { listDataListeners.   add(l); }
		@Override public void removeListDataListener(ListDataListener l) { listDataListeners.remove(l); }
		
		@Override public Iterator<Area> iterator() { return data.iterator(); }
		@Override public int getSize() { return data.size(); }
		@Override public Area getElementAt(int index) { return data.get(index); }
		
		public void add(Area area) {
			data.add(area);
			fireIntervalAddedEvent(data.size()-1, data.size()-1);
		}
		
		private void fireContentsChangedEvent(int first, int last) { fireEvent(first, last, ListDataEvent.CONTENTS_CHANGED, ListDataListener::contentsChanged); }
		private void fireIntervalRemovedEvent(int first, int last) { fireEvent(first, last, ListDataEvent.INTERVAL_REMOVED, ListDataListener::intervalRemoved); }
		private void fireIntervalAddedEvent  (int first, int last) { fireEvent(first, last, ListDataEvent.INTERVAL_ADDED  , ListDataListener::intervalAdded  ); }
		private void fireEvent(int first, int last, int type, BiConsumer<ListDataListener,ListDataEvent> eventFcn) {
			ListDataEvent e = new ListDataEvent(this, type, first, last);
			for (ListDataListener ldl:listDataListeners)
				eventFcn.accept(ldl, e);
		}
	}
	
	public static class MapImage {
		
		private enum Type { File, URL }

		public final Type type;
		public final BufferedImage image;
		public final File file;
		public final String url;
		
		private MapImage(BufferedImage image, File file) {
			this.type = Type.File;
			this.image = image;
			this.file = file;
			this.url = null;
		}

		private MapImage(BufferedImage image, String url) {
			this.type = Type.URL;
			this.image = image;
			this.file = null;
			this.url = url;
		}

		public static MapImage loadImage(File file) {
			try {
				return new MapImage(ImageIO.read(file),file);
			} catch (IOException e) {
				//e.printStackTrace();
				System.err.printf("IOException while loading image from file \"%s\": %s%n", file.getAbsolutePath(), e.getMessage());
				return null;
			}
		}

		public static MapImage loadImage(String urlStr) {
			try {
				return new MapImage(ImageIO.read(new URL(urlStr)),urlStr);
			} catch (MalformedURLException e) {
				//e.printStackTrace();
				System.err.printf("Malformed URL: \"%s\" -> %s%n", urlStr, e.getMessage());
				return null;
			} catch (IOException e) {
				//e.printStackTrace();
				System.err.printf("IOException while loading image from url \"%s\": %s%n", urlStr, e.getMessage());
				return null;
			}
		}
	}
}
