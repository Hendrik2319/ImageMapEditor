package net.schwarzbaer.java.tools.imagemapeditor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.EnumMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

abstract class AreaDialog<FieldIndex extends Enum<FieldIndex>> extends JDialog {
	private static final long serialVersionUID = 1664814457425220393L;
	
	private final String itemLabel;
	private final JPanel buttonPanel;
	private final JPanel valuePanel;
	private final JTextField titleField;
	private final JTextField onclickField;
	private final EnumMap<FieldIndex, JTextField> valueFields;
	private boolean ok;
	protected Area area;
	
	AreaDialog(Window owner, String itemLabel, Class<FieldIndex> enumClass) {
		super(owner,ModalityType.APPLICATION_MODAL);
		this.itemLabel = itemLabel;
		area = null; 
		ok = false;
		
		GridBagConstraints c;
		buttonPanel = new JPanel(new GridBagLayout());
		c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		
		c.weightx = 1;
		buttonPanel.add(new JLabel(),c);
		c.weightx = 0;
		buttonPanel.add(ImageMapEditor.createButton("Ok", true, e->{
			ok = true;
			this.setVisible(false);
		}),c);
		buttonPanel.add(ImageMapEditor.createButton("Cancel", true, e->{
			this.setVisible(false);
		}),c);
		
		valuePanel = new JPanel(new GridBagLayout());
		valueFields = new EnumMap<FieldIndex,JTextField>(enumClass);
		titleField   = addStringField("Title"  , null, str->{ area.title   = str; });
		onclickField = addStringField("OnClick", null, str->{ area.onclick = str; });
		
		JPanel contentPane = new JPanel(new BorderLayout());
		contentPane.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
		contentPane.add(valuePanel,BorderLayout.CENTER);
		contentPane.add(buttonPanel,BorderLayout.SOUTH);
		setContentPane(contentPane);
	}
	
	protected void setField(FieldIndex index, int value) {
		valueFields.get(index).setText(Integer.toString(value));
	}

	protected void addField(FieldIndex index, String label, Predicate<Integer> checkValue, Consumer<Integer> setValue) {
		JTextField valueField = addField(label, str->{
			try { return Integer.parseInt(str); } catch (NumberFormatException e) { return null; }
		}, checkValue, setValue);
		valueFields.put(index, valueField);
	}

	private JTextField addStringField(String label, Predicate<String> checkValue, Consumer<String> setValue) {
		return addField(label, str->str, checkValue, setValue);
	}

	private <ValueType> JTextField addField(String label, Function<String,ValueType> parseValue, Predicate<ValueType> checkValue, Consumer<ValueType> setValue) {
		if (setValue==null) throw new IllegalArgumentException();
		
		JTextField valueField = new JTextField(10);
		Color defaultBG = valueField.getBackground();
		
		Runnable inputAction = ()->{
			String valueStr = valueField.getText();
			ValueType value = parseValue.apply(valueStr);
			if (value==null || (checkValue!=null && !checkValue.test(value))) {
				valueField.setBackground(Color.RED);
			} else {
				valueField.setBackground(defaultBG);
				setValue.accept(value);
			}
		};
		
		valueField.addActionListener(e->inputAction.run());
		valueField.addFocusListener(new FocusListener() {
			@Override public void focusLost  (FocusEvent e) { inputAction.run(); }
			@Override public void focusGained(FocusEvent e) {}
		});
		
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		
		c.gridwidth = 1;
		c.weightx = 0;
		valuePanel.add(new JLabel(label+": "),c);
		
		c.gridwidth = GridBagConstraints.REMAINDER;
		c.weightx = 1;
		valuePanel.add(valueField,c);
		
		return valueField;
	}

	protected abstract void checkArea();
	protected abstract void setGuiFields();

	protected Area showAddDialog(Area.Shape shape) {
		this.area = new Area(shape,"no title","");
		showDialog("Add "+itemLabel);
		return !ok ? null : new Area(this.area);
	}
	
	boolean showEditDialog(Area area) {
		this.area = new Area(area);
		showDialog("Edit "+itemLabel);
		if (ok) {
			area.copyFrom(this.area);
			return true;
		}
		return false;
	}
	
	private void showDialog(String title) {
		setTitle(title);
		checkArea();
		titleField  .setText(area.title  );
		onclickField.setText(area.onclick);
		setGuiFields();
		pack();
		Window owner = this.getOwner();
		if (owner!=null) {
			Dimension size = getSize();
			Point op = owner.getLocation();
			Dimension os = owner.getSize();
			setLocation(
				op.x + (os.width -size.width )/2,
				op.y + (os.height-size.height)/2
			);
		}
		setVisible(true);
	}
	
	static class CircleDialog extends AreaDialog<CircleDialog.Fields> {
		private static final long serialVersionUID = 2351843352449940364L;
		
		private enum Fields { CenterX, CenterY, Radius }
		
		CircleDialog(Window owner) {
			super(owner,"Circle",Fields.class);
			addField(Fields.CenterX, "Center.X",null,n->area.shape.center.x = n);
			addField(Fields.CenterY, "Center.Y",null,n->area.shape.center.y = n);
			addField(Fields.Radius , "Radius"  ,n->n>0,n->area.shape.radius = n);
		}
		
		@Override protected void checkArea() {
			if (area.shape.type!=Area.Shape.Type.Circle)
				throw new IllegalArgumentException();
		}

		@Override
		protected void setGuiFields() {
			setField(Fields.CenterX,area.shape.center.x);
			setField(Fields.CenterY,area.shape.center.y);
			setField(Fields.Radius ,area.shape.radius  );
		}

		static Area showAddDialog(Window owner) {
			return showAddDialog( owner, new Point(0,0), 10 );
		}
		static Area showAddDialog(Window owner, Point center, int radius) {
			CircleDialog dlg = new CircleDialog(owner);
			Area.Shape shape = new Area.Shape(new Point(center), radius);
			return dlg.showAddDialog( shape );
		}
		
		static boolean showEditDialog(Window owner, Area area) {
			CircleDialog dlg = new CircleDialog(owner);
			return dlg.showEditDialog(area);
		}
	}
	
	static class RectDialog extends AreaDialog<RectDialog.Fields> {
		private static final long serialVersionUID = 1741387755002254978L;

		private enum Fields { Corner1X, Corner1Y, Corner2X, Corner2Y }
		
		RectDialog(Window owner) {
			super(owner,"Rectangle",Fields.class);
			addField(Fields.Corner1X, "UpperLeft.X" , n->n<area.shape.corner2.x, n->area.shape.corner1.x=n  );
			addField(Fields.Corner1Y, "UpperLeft.Y" , n->n<area.shape.corner2.y, n->area.shape.corner1.y=n  );
			addField(Fields.Corner2X, "LowerRight.X", n->n>area.shape.corner1.x, n->area.shape.corner2.x=n-1);
			addField(Fields.Corner2Y, "LowerRight.Y", n->n>area.shape.corner1.y, n->area.shape.corner2.y=n-1);
		}
		
		@Override protected void checkArea() {
			if (area.shape.type!=Area.Shape.Type.Rect)
				throw new IllegalArgumentException();
		}

		@Override
		protected void setGuiFields() {
			setField(Fields.Corner1X, area.shape.corner1.x  );
			setField(Fields.Corner1Y, area.shape.corner1.y  );
			setField(Fields.Corner2X, area.shape.corner2.x+1);
			setField(Fields.Corner2Y, area.shape.corner2.y+1);
		}

		static Area showAddDialog(Window owner) {
			return showAddDialog( owner, new Point(0,0), 15, 10 );
		}
		static Area showAddDialog(Window owner, Point corner1, Point corner2) {
			return showAddDialog(owner, corner1.x, corner1.y, corner2.x, corner2.y );
		}
		static Area showAddDialog(Window owner, Point center, int width, int height) {
			return showAddDialog(owner, 
					center.x - Math.round( (float)Math.floor( width/2.0) ), 
					center.y - Math.round( (float)Math.floor(height/2.0) ), 
					center.x + Math.round( (float)Math.ceil ( width/2.0) ), 
					center.y + Math.round( (float)Math.ceil (height/2.0) )
			);
		}
		private static Area showAddDialog(Window owner, int c1X, int c1Y, int c2X, int c2Y) {
			Area.Shape shape = new Area.Shape( new Point(c1X,c1Y), new Point(c2X,c2Y) );
			RectDialog dlg = new RectDialog(owner);
			return dlg.showAddDialog(shape);
		}

		static boolean showEditDialog(Window owner, Area area) {
			RectDialog dlg = new RectDialog(owner);
			return dlg.showEditDialog(area);
		}
	}
}
