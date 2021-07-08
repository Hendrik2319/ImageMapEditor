package net.schwarzbaer.java.tools.imagemapeditor;

import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
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

import net.schwarzbaer.gui.ImageView;
import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.gui.StandardMainWindow.DefaultCloseOperation;

public class ImageMapEditor {

	private final StandardMainWindow mainWindow;
	private final ImageView imageView;
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
		
		JFileChooser imageFileChooser = new JFileChooser("./");
		imageFileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		imageFileChooser.setMultiSelectionEnabled(false);
		imageFileChooser.addChoosableFileFilter(new FileNameExtensionFilter("JPEG Image", "jpg", "jpeg"));
		imageFileChooser.addChoosableFileFilter(new FileNameExtensionFilter("PNG Image", "png"));
		
		DefaultCloseOperation closeOp = asStandAloneApp ? DefaultCloseOperation.EXIT_ON_CLOSE : DefaultCloseOperation.DISPOSE_ON_CLOSE;
		//String title = "ImageMapEditor";
		mainWindow = new StandardMainWindow(title, closeOp);
		
		imageView = new ImageView(800,600);
		if (this.mapImage!=null)
			imageView.setImage(this.mapImage.image);
		
		areaList = new JList<>(new AreaListModel(areas));
		JScrollPane areaListScrollPane = new JScrollPane(areaList);
		areaListScrollPane.setBorder(BorderFactory.createTitledBorder("List of Areas"));
		
		JPanel leftPanel = new JPanel(new BorderLayout(3,3));
		leftPanel.add(areaListScrollPane,BorderLayout.CENTER);
		
		JPanel contentPane = new JPanel(new BorderLayout(3,3));
		contentPane.add(leftPanel,BorderLayout.WEST);
		contentPane.add(imageView,BorderLayout.CENTER);
		
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
			JMenu imageMenu = menuBar.add(new JMenu("Image"));
			imageMenu.add(createMenuItem("Load Image from File ...", true, e->{
				if (imageFileChooser.showOpenDialog(mainWindow)!=JFileChooser.APPROVE_OPTION) return;
				File file = imageFileChooser.getSelectedFile();
				MapImage newMapImage = MapImage.loadImage(file);
				if (newMapImage==null) return;
				this.mapImage = newMapImage;
				imageView.setImage(this.mapImage.image);
			}));
			imageMenu.add(createMenuItem("Load Image from URL ...", true, e->{
				String urlStr = JOptionPane.showInputDialog(mainWindow, "message", "title", JOptionPane.QUESTION_MESSAGE);
				if (urlStr==null) return;
				MapImage newMapImage = MapImage.loadImage(urlStr);
				if (newMapImage==null) return;
				this.mapImage = newMapImage;
				imageView.setImage(this.mapImage.image);
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
		// TODO Auto-generated method stub
	}

	@SuppressWarnings("unused")
	private static class AreaListModel implements ListModel<Area> {
		
		private final Vector<ListDataListener> listDataListeners;
		private final Vector<Area> data;
		
		AreaListModel() { this(null); }
		AreaListModel(Vector<Area> data) {
			listDataListeners = new Vector<>();
			this.data = data==null ? new Vector<>() : new Vector<>(data);
		}
				
		@Override public void    addListDataListener(ListDataListener l) { listDataListeners.   add(l); }
		@Override public void removeListDataListener(ListDataListener l) { listDataListeners.remove(l); }
		
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
	
	@SuppressWarnings("unused")
	private static class MapImage {
		
		enum Type { File, URL }

		final Type type;
		final BufferedImage image;
		final File file;
		final String url;
		
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

		static MapImage loadImage(File file) {
			try {
				return new MapImage(ImageIO.read(file),file);
			} catch (IOException e) {
				//e.printStackTrace();
				System.err.printf("IOException while loading image from file \"%s\": %s%n", file.getAbsolutePath(), e.getMessage());
				return null;
			}
		}

		static MapImage loadImage(String urlStr) {
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

	public static class Area {
		public String title;
		public String onclick;
		public Shape shape;
		
		public Area(Shape shape, String title, String onclick) {
			this.shape = shape;
			this.title = title;
			this.onclick = onclick;
		}

		@Override
		public String toString() {
			return String.format("%s \"%s\" <%s>", shape, title, onclick);
		}

		public static class Shape {
			public enum Type { Rect, Circle }
			
			public final Type type;
			public final Point center;
			public final int radius;
			public final Point corner1;
			public final Point corner2;
			
			public Shape(Point center, int radius) {
				type = Type.Circle;
				this.corner1 = null;
				this.corner2 = null;
				this.center = center;
				this.radius = radius;
			}
			public Shape(Point corner1, Point corner2) {
				type = Type.Rect;
				this.corner1 = new Point(Math.min(corner1.x,corner2.x), Math.min(corner1.y,corner2.y));
				this.corner2 = new Point(Math.max(corner1.x,corner2.x), Math.max(corner1.y,corner2.y));
				this.center = null;
				this.radius = 0;
			}
			
			@Override
			public String toString() {
				switch (type) {
				case Circle: return String.format("Circle(%d,%d|%d)", center.x, center.y, radius);
				case Rect  : return String.format("Rect(%d,%d|%d,%d)", corner1.x, corner1.y, corner2.x, corner2.y);
				}
				throw new IllegalStateException();
			}
		}
	}
}
