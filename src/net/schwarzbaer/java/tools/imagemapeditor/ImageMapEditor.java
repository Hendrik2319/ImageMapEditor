package net.schwarzbaer.java.tools.imagemapeditor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Point;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Vector;
import java.util.function.BiConsumer;
import java.util.function.Function;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import net.schwarzbaer.java.lib.gui.ContextMenu;
import net.schwarzbaer.java.lib.gui.FileChooser;
import net.schwarzbaer.java.lib.gui.StandardMainWindow;
import net.schwarzbaer.java.lib.gui.Tables;
import net.schwarzbaer.java.lib.gui.StandardMainWindow.DefaultCloseOperation;

public class ImageMapEditor {

	private final StandardMainWindow mainWindow;
	private final EditorView editorView;
	private final AreaListModel areaListModel;
	private final JList<Area> areaList;
	private MapImage mapImage;
	private String suggestedHtmlOutFileName;
	private Area clickedArea;
	private int clickedAreaListIndex;

	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		new ImageMapEditor("Image Map Editor",null,null,null,true).initialize();
	}
	
	public static void show(String title, MapImage mapImage, Vector<Area> areas, String suggestedHtmlOutFileName) {
		new ImageMapEditor(title,mapImage,areas,suggestedHtmlOutFileName,false).initialize();
	}
	
	//  OK : switch shape type of area
	//  OK : add / remove area
	//  OK : edit title & onclick
	//  OK : selecting in AreaList  -> highlighting in EditorView
	//  OK : coloring in AreaList  <-  highlighting in EditorView
	// NOPE: selecting in AreaList <-  highlighting in EditorView
	// TODO: coloring in AreaList
	
	ImageMapEditor(String title, MapImage mapImage, Vector<Area> areas, String suggestedHtmlOutFileName, boolean asStandAloneApp) {
		this.mapImage = mapImage;
		this.suggestedHtmlOutFileName = suggestedHtmlOutFileName;
		
		DefaultCloseOperation closeOp = asStandAloneApp ? DefaultCloseOperation.EXIT_ON_CLOSE : DefaultCloseOperation.DISPOSE_ON_CLOSE;
		mainWindow = new StandardMainWindow(title, closeOp);
		
		areaListModel = new AreaListModel(areas);
		areaList = new JList<>(areaListModel);
		JScrollPane areaListScrollPane = new JScrollPane(areaList);
		areaListScrollPane.setBorder(BorderFactory.createTitledBorder("List of Areas"));
		
		editorView = new EditorView(800,600,areaList,areaListModel);
		
		areaList.setCellRenderer(new ImageMapEditor.AreaListRenderer());

		JMenuItem miALCMEdit;
		JMenuItem miALCMRemove;
		JMenuItem miALCMRemoveSelected;
		JMenuItem miALCMSwitch;
		ContextMenu areaListContextMenu = new ContextMenu();
		areaListContextMenu.addTo(areaList);
		areaListContextMenu.add(createMenuItem("Add Circle", true, e->{
			Area area = AreaDialog.CircleDialog.showAddDialog(mainWindow);
			if (area==null) return;
			areaListModel.add(area);
			editorView.repaint();
		}));
		areaListContextMenu.add(createMenuItem("Add Rectangle", true, e->{
			Area area = AreaDialog.RectDialog.showAddDialog(mainWindow);
			if (area==null) return;
			areaListModel.add(area);
			editorView.repaint();
		}));
		areaListContextMenu.add(miALCMSwitch = createMenuItem("Switch Shape Type", true, e->{
			if (clickedAreaListIndex == -1) return;
			switchShapeType(clickedArea,clickedAreaListIndex);
		}));
		areaListContextMenu.add(miALCMEdit = createMenuItem("Edit Area", true, e->{
			if (clickedAreaListIndex == -1) return;
			editArea(clickedArea,clickedAreaListIndex);
		}));
		areaListContextMenu.add(miALCMRemove = createMenuItem("Remove Area", true, e->{
			remove(clickedArea, clickedAreaListIndex);
		}));
		areaListContextMenu.add(miALCMRemoveSelected = createMenuItem("Remove Selected Areas", true, e->{
			int[] indices = areaList.getSelectedIndices();
			if (indices.length==0) return;
			
			String message = String.format("Do you really want to delete following %d areas?%n", indices.length);
			for (int index:indices)
				message += String.format("   [%d] %s%n", index+1, areaListModel.getElementAt(index));
			
			int result = JOptionPane.showConfirmDialog(mainWindow, message, "Are You Sure?", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
			if (result!=JOptionPane.YES_OPTION) return;
			
			areaListModel.remove(indices);
			editorView.repaint();
		}));
		
		clickedArea = null;
		clickedAreaListIndex = -1;
		areaListContextMenu.addContextMenuInvokeListener((comp,x,y)->{
			clickedAreaListIndex = areaList.locationToIndex(new Point(x,y));
			clickedArea = clickedAreaListIndex>=0 && clickedAreaListIndex<areaListModel.getSize() ? areaListModel.getElementAt(clickedAreaListIndex) : null;
			
			String targetTypeStr = getSwitchTargetType(clickedArea);
			miALCMSwitch.setText   (clickedArea==null ? "Switch Shape Type" : String.format("Switch Shape Type of [%d] %s to %s", clickedAreaListIndex+1, clickedArea.toString(), targetTypeStr));
			miALCMSwitch.setEnabled(clickedArea!=null);
			
			miALCMEdit  .setText   (clickedArea==null ?   "Edit Area" : String.format(  "Edit [%d] %s", clickedAreaListIndex+1, clickedArea.toString()));
			miALCMEdit  .setEnabled(clickedArea!=null);
			
			miALCMRemove.setText   (clickedArea==null ? "Remove Area" : String.format("Remove [%d] %s", clickedAreaListIndex+1, clickedArea.toString()));
			miALCMRemove.setEnabled(clickedArea!=null);
			
			int[] indices = areaList.getSelectedIndices();
			miALCMRemoveSelected.setText   (indices.length==0 ? "Remove Selected Areas" : String.format("Remove %d Selected Areas", indices.length));
			miALCMRemoveSelected.setEnabled(indices.length>0);
		});
		
		
		JMenuItem miEVCMAddCircle, miEVCMAddRectangle, miEVCMEdit, miEVCMRemove, miEVCMSwitch;
		EditorView.ContextMenu editorViewContextMenu = editorView.createContextMenu();
		editorViewContextMenu.add(miEVCMAddCircle = createMenuItem("Add Circle", true, e->{
			
			Point center = new Point();
			center.x = Math.round( editorViewContextMenu.clickedPos.x );
			center.y = Math.round( editorViewContextMenu.clickedPos.y );
			
			Area area = AreaDialog.CircleDialog.showAddDialog(mainWindow, center, 10);
			if (area==null) return;
			
			areaListModel.add(area);
			editorView.repaint();
		}));
		editorViewContextMenu.add(miEVCMAddRectangle = createMenuItem("Add Rectangle", true, e->{
			
			Point center = new Point();
			center.x = Math.round( editorViewContextMenu.clickedPos.x );
			center.y = Math.round( editorViewContextMenu.clickedPos.y );
			
			Area area = AreaDialog.RectDialog.showAddDialog(mainWindow, center, 15, 10);
			if (area==null) return;
			
			areaListModel.add(area);
			editorView.repaint();
		}));
		editorViewContextMenu.add(miEVCMSwitch = createMenuItem("Switch Shape Type", true, e->{
			switchShapeType(editorViewContextMenu.clickedArea,-1);
		}));
		editorViewContextMenu.add(miEVCMEdit = createMenuItem("Edit Area", true, e->{
			editArea(editorViewContextMenu.clickedArea,-1);
		}));
		editorViewContextMenu.add(miEVCMRemove = createMenuItem("Remove Area", true, e->{
			remove(editorViewContextMenu.clickedArea, -1);
		}));
		
		editorViewContextMenu.addInvokeListener(()->{
			boolean viewStateOK = editorView.isViewStateOK();
			miEVCMAddCircle   .setEnabled(viewStateOK);
			miEVCMAddRectangle.setEnabled(viewStateOK);
			Area area = editorViewContextMenu.clickedArea;
			String targetTypeStr = getSwitchTargetType(area);
			miEVCMSwitch.setText   (area==null ? "Switch Shape Type" : String.format("Switch Shape Type of %s to %s", area.toString(), targetTypeStr));
			miEVCMSwitch.setEnabled(area!=null);
			miEVCMEdit  .setText   (area==null ?   "Edit Area" : String.format(  "Edit %s", area.toString()));
			miEVCMEdit  .setEnabled(area!=null);
			miEVCMRemove.setText   (area==null ? "Remove Area" : String.format("Remove %s", area.toString()));
			miEVCMRemove.setEnabled(area!=null);
		});
		
		areaList.addListSelectionListener(e -> {
			int[] arr = areaList.getSelectedIndices();
			HashSet<Integer> indices = new HashSet<>();
			for (int index:arr)
				indices.add(index);
			areaListModel.setSelectedIndices(indices);
			editorView.repaint();
		});
		
		JPanel leftPanel = new JPanel(new BorderLayout(3,3));
		leftPanel.add(areaListScrollPane,BorderLayout.CENTER);
		
		JPanel contentPane = new JPanel(new BorderLayout(3,3));
		contentPane.add(leftPanel,BorderLayout.WEST);
		contentPane.add(editorView,BorderLayout.CENTER);
		
		JMenuBar menuBar = new JMenuBar();
		
		FileChooser htmlFileChooser = new FileChooser("HTML-File", "html");
		
		JMenu fileMenu = menuBar.add(new JMenu("File"));
		fileMenu.add(createMenuItem("Write HTML ...", true, e->{
			if (this.suggestedHtmlOutFileName!=null) htmlFileChooser.suggestFileName(this.suggestedHtmlOutFileName);
			if (htmlFileChooser.showSaveDialog(mainWindow)!=JFileChooser.APPROVE_OPTION) return;
			writeToHTML(htmlFileChooser.getSelectedFile(),true);
			this.suggestedHtmlOutFileName = null;
		}));
		fileMenu.add(createMenuItem("Write HTML (reduced) ...", true, e->{
			if (this.suggestedHtmlOutFileName!=null) htmlFileChooser.suggestFileName(this.suggestedHtmlOutFileName);
			if (htmlFileChooser.showSaveDialog(mainWindow)!=JFileChooser.APPROVE_OPTION) return;
			writeToHTML(htmlFileChooser.getSelectedFile(),false);
			this.suggestedHtmlOutFileName = null;
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

	private void remove(Area area, int index) {
		if (area==null)
			return;
		
		String message;
		if (index>=0)
			message = String.format("Do you really want to delete area %s at position %d?", area.toString(), index);
		else
			message = String.format("Do you really want to delete area %s?", area.toString());
		
		int result = JOptionPane.showConfirmDialog(mainWindow, message, "Are You Sure?", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (result!=JOptionPane.YES_OPTION) return;
		
		areaListModel.remove(area);
		editorView.repaint();
	}

	private String getSwitchTargetType(Area area) {
		String targetTypeStr = null;
		if (area!=null)
			switch (area.shape.type) {
			case Circle: targetTypeStr = "Rectangle"; break;
			case Rect  : targetTypeStr = "Circle"   ; break;
			}
		return targetTypeStr;
	}

	private void switchShapeType(Area area, int index) {
		changeArea(area, index, this::switchShapeType);
	}

	private boolean switchShapeType(Area area) {
		boolean changed = false;
		switch (area.shape.type) {
		case Circle: changed = area.switchToShapeType(Area.Shape.Type.Rect  ); break;
		case Rect  : changed = area.switchToShapeType(Area.Shape.Type.Circle); break;
		}
		return changed;
	}

	private void editArea(Area area, int index) {
		changeArea(area, index, this::editArea);
	}

	private boolean editArea(Area area) {
		boolean changed = false;
		switch (area.shape.type) {
		case Circle: changed = AreaDialog.CircleDialog.showEditDialog(mainWindow, area); break;
		case Rect  : changed = AreaDialog.  RectDialog.showEditDialog(mainWindow, area); break;
		}
		return changed;
	}

	private void changeArea(Area area, int index, Function<Area,Boolean> action) {
		if (area==null)
			return;
		
		boolean changed = action.apply(area);
		if (!changed) return;
		
		if (index<0) areaListModel.notifyAreaChanged(area);
		else         areaListModel.fireContentsChangedEvent(index, index);
		editorView.repaint();
	}

	private void writeToHTML(File file, boolean completeHTML) {
		try (PrintWriter htmlOut = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
			
			if (completeHTML) {
				htmlOut.println("<!DOCTYPE html>");
				htmlOut.println("<html>");
				htmlOut.println("<head>");
				htmlOut.printf ("    <title>%s</title>%n", file.getName());
				htmlOut.println("    <meta http-equiv=\"Content-Type\" content=\"text/html;charset=UTF-8\">");
				htmlOut.println("</head>");
				htmlOut.println("<body>");
			}
			
			if (mapImage!=null) {
				String url = null;
				switch (mapImage.type) {
				case File:
					System.out.printf("Convert File: \"%s\"%n", mapImage.file.getAbsolutePath());
					URI uri = mapImage.file.toURI();
					System.out.printf("      to URI: \"%s\"%n", uri.toString());
					URL url_;
					try {
						url_ = uri.toURL();
						System.out.printf("      to URL: \"%s\"%n", url_.toString());
					} catch (MalformedURLException e) {
						System.err.printf("      to URL: --> MalformedURLException: %s%n", e.getMessage());
						url_ = null;
					}
					if (url_!=null)
						url = url_.toString();
					break;
				case URL: url = mapImage.url; break;
				}
				htmlOut.printf ("<img border=\"0\" src=\"%s\" usemap=\"#map\" >%n", url);
			}
			
			htmlOut.println("<map name=\"map\">");
			areaListModel.forEach(area->{
				String type = area.shape.type.toHtmlValue();
				String coords = area.shape.toCoordsValue();
				String title   = area.title  .replace("\\", "\\\\").replace("\"", "\\\"");
				String onclick = area.onclick.replace("\\", "\\\\").replace("\"", "\\\"");
				htmlOut.printf("	<area shape=\"%s\" coords=\"%s\" title=\"%s\" onclick=\"%s\">%n", type, coords, title, onclick);
			});
			htmlOut.println("</map>");
			
			if (completeHTML) {
				htmlOut.println("</body>");
				htmlOut.println("</html>");
			}
			
		}
		catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
	}

	static JMenuItem createMenuItem(String title, boolean isEnabled, ActionListener al) {
		JMenuItem comp = new JMenuItem(title);
		comp.setEnabled(isEnabled);
		if (al!=null) comp.addActionListener(al);
		return comp;
	}

	static JButton createButton(String title, boolean isEnabled, ActionListener al) {
		JButton comp = new JButton(title);
		comp.setEnabled(isEnabled);
		if (al!=null) comp.addActionListener(al);
		return comp;
	}

	private void initialize() {
		if (mapImage!=null)
			editorView.setImage(mapImage.image);
		else
			editorView.reset();
	}
	
	private class AreaListRenderer implements ListCellRenderer<Area> {
		Tables.LabelRendererComponent rendComp = new Tables.LabelRendererComponent();

		@Override
		public Component getListCellRendererComponent(JList<? extends Area> list, Area value, int index, boolean isSelected, boolean hasFocus) {
			String valueStr = value==null ? "<null>" : value.toString();
			rendComp.configureAsListCellRendererComponent(list, null, valueStr , index, isSelected, hasFocus, null, ()->{
				if (editorView.isEditingArea(value)) return Color.RED;
				return list.getForeground();
			});
			
			return rendComp;
		}
	
	}

	static class AreaListModel implements ListModel<Area>, Iterable<Area> {
		
		private final Vector<ListDataListener> listDataListeners;
		private final Vector<Area> data;
		private HashSet<Integer> selectedIndices;
		
		AreaListModel() { this(null); }
		AreaListModel(Vector<Area> data) {
			listDataListeners = new Vector<>();
			selectedIndices = null;
			this.data = data==null ? new Vector<>() : new Vector<>(data);
		}
				
		@Override public void    addListDataListener(ListDataListener l) { listDataListeners.   add(l); }
		@Override public void removeListDataListener(ListDataListener l) { listDataListeners.remove(l); }
		
		public void setSelectedIndices(HashSet<Integer> selectedIndices) {
			this.selectedIndices = selectedIndices;
		}
		
		public interface AreaAction {
			void apply(Area area, boolean isSelected);
		}
		
		public void forEach(AreaAction action) {
			for (int i=0; i<data.size(); i++) {
				Area area = data.get(i);
				boolean isSelected = selectedIndices!=null && selectedIndices.contains(i);
				action.apply(area,isSelected);
			}
		}

		@Override public Iterator<Area> iterator() { return data.iterator(); }
		@Override public int getSize() { return data.size(); }
		@Override public Area getElementAt(int index) { return data.get(index); }
		
		public void add(Area area) {
			data.add(area);
			fireIntervalAddedEvent(data.size()-1, data.size()-1);
		}
		
		public void remove(Area area) {
			remove(data.indexOf(area));
		}
		
		public void remove(int index) {
			if (index<0 || index>=data.size()) return;
			data.remove(index);
			fireIntervalRemovedEvent(index, index);
		}
		
		public void remove(int[] indices) {
			Arrays.sort(indices);
			for (int i=indices.length-1; i>=0; i--)
				remove(indices[i]);
		}
		
		public void notifyAreaChanged(Area area) {
			int index = data.indexOf(area);
			if (index<0 || index>=data.size()) return;
			fireContentsChangedEvent(index, index);
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
