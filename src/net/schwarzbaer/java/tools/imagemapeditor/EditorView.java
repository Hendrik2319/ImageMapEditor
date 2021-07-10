package net.schwarzbaer.java.tools.imagemapeditor;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.Vector;

import javax.swing.JList;
import javax.swing.JPopupMenu;

import net.schwarzbaer.gui.ZoomableCanvas;
import net.schwarzbaer.java.tools.imagemapeditor.ImageMapEditor.AreaListModel;

class EditorView extends ZoomableCanvas<EditorView.ViewState> {
	private static final long serialVersionUID = 6673711826179057594L;
	private static final Color COLOR_AXIS = new Color(0x70000000,true);
	private static final Color COLOR_AREA = new Color(0xA0808080,true);
	private static final Color COLOR_HIGHLIGHTED_AREA = Color.WHITE;
	
	private final JList<Area> areaList;
	private final AreaListModel areaListModel;
	private BufferedImage image;
	private AreaEditing areaEditing;
	private ContextMenu contextMenu;

	EditorView(int width, int height, JList<Area> areaList, AreaListModel areaListModel) { this(null, width, height, areaList, areaListModel); }
	EditorView(BufferedImage image, int width, int height, JList<Area> areaList, AreaListModel areaListModel) {
		this.image = image;
		this.areaList = areaList;
		this.areaListModel = areaListModel;
		areaEditing = null;
		contextMenu = null;
		
		setPreferredSize(width, height);
		activateMapScale(COLOR_AXIS, "px", true);
		activateAxes(COLOR_AXIS, true,true,true,true);
		activateEditorMode();
	}
	
	boolean isEditingArea(Area area) {
		if (areaEditing==null) return false;
		return areaEditing.area==area;
	}
	
	boolean isViewStateOK() {
		return viewState.isOk();
	}
	
	static class ContextMenu extends JPopupMenu {
		private static final long serialVersionUID = 3266076470264574143L;
		
		Point         clickedScreenPos = null;
		Point2D.Float clickedPos = null;
		Area          clickedArea = null;
		private final Vector<InvokeListener> invokeListeners = new Vector<>();
		
		ContextMenu() {}

		public void prepareItems() {
			for (InvokeListener l:invokeListeners)
				l.prepareMenuItems();
		}
		
		void    addInvokeListener(InvokeListener l) { invokeListeners.   add(l); }
		void removeInvokeListener(InvokeListener l) { invokeListeners.remove(l); }
		
		interface InvokeListener {
			void prepareMenuItems();
		}
	}
	
	ContextMenu createContextMenu() {
		return contextMenu = new ContextMenu();
	}
	
	void setImage(BufferedImage image) {
		this.image = image;
		reset();
	}
	
	private void setHighlightedArea(Point p) {
		Area nearestArea = null;
		
		float pX = Float.NaN;
		float pY = Float.NaN;
		if (p != null) {
			pX = viewState.convertPos_ScreenToAngle_LongX(p.x);
			pY = viewState.convertPos_ScreenToAngle_LatY(p.y);
			
			AreaEditing.DistanceResult min = null;
			for (Area area : areaListModel) {
				AreaEditing.DistanceResult res = AreaEditing.computeDistance(area,pX,pY);
				if (min==null || min.distance>res.distance) {
					min = res;
					nearestArea = area;
				}
			}
			
			if (min!=null) {
				float distance_scr = viewState.convertLength_LengthToScreenF(min.distance);
				if (distance_scr > AreaEditing.MIN_HIGHLIGHT_AREA_DISTANCE_SCR && !min.isInside) nearestArea = null;
			}
		}
		
		boolean mustRepaint;
		if (areaEditing!=null && areaEditing.area==nearestArea) {
			mustRepaint = true;
		} else {
			mustRepaint = areaEditing!=null || nearestArea!=null;
			areaEditing = AreaEditing.createFor(areaListModel, nearestArea);
		}
		
		if (areaEditing!=null) {
			float minDist = viewState.convertLength_ScreenToLength(AreaEditing.MIN_HIGHLIGHT_HPOINT_DISTANCE_SCR);
			areaEditing.setMousePoint(pX,pY,minDist);
		}
		
		if (mustRepaint) {
			repaint();
			areaList.repaint();
		}
	}
	
	private static abstract class AreaEditing {
		public static final float MIN_HIGHLIGHT_AREA_DISTANCE_SCR = 30.0f;
		public static final float MIN_HIGHLIGHT_HPOINT_DISTANCE_SCR = 5.0f;
		
		static class DistanceResult {
			final float distance;
			final boolean isInside;
			private DistanceResult(float distance, boolean isInside) {
				this.distance = distance;
				this.isInside = isInside;
			}
		}
		
		static DistanceResult computeDistance(Area area, float pX, float pY) {
			switch (area.shape.type) {
			case Circle: return CircleEditing.computeDistanceToCircle(area, pX, pY);
			case   Rect: return   RectEditing.computeDistanceToRect  (area, pX, pY);
			}
			throw new IllegalStateException();
		}

		static AreaEditing createFor(AreaListModel areaListModel, Area area) {
			if (area==null) return null;
			switch (area.shape.type) {
			case Circle: return new CircleEditing(areaListModel, area);
			case   Rect: return new   RectEditing(areaListModel, area);
			}
			throw new IllegalStateException();
		}
		
		private final AreaListModel areaListModel;
		protected final Area area;
		protected final HandlePoint[] handlePoints;
		protected int highlightedHPindex;
		
		protected AreaEditing(AreaListModel areaListModel, Area area, HandlePoint... handlePoints) {
			this.areaListModel = areaListModel;
			this.area = area;
			this.handlePoints = handlePoints;
			highlightedHPindex = -1;
		}

		abstract void setMousePoint(float pX, float pY, float minDist);
//		abstract boolean onEntered (MouseEvent e);
//		abstract boolean onMoved   (MouseEvent e);
//		abstract boolean onExited  (MouseEvent e);
		protected abstract void startDragging(float pX, float pY);
		protected abstract void stopDragging(float pX, float pY);
		protected abstract void dragging(float pX, float pY);

		void onPressed (MouseEvent e, ViewState viewState) {
			Point p = e.getPoint();
			float pX = viewState.convertPos_ScreenToAngle_LongX(p.x);
			float pY = viewState.convertPos_ScreenToAngle_LatY (p.y);
			float minDist = viewState.convertLength_ScreenToLength(MIN_HIGHLIGHT_HPOINT_DISTANCE_SCR);
			setMousePoint(pX, pY, minDist);
			if (highlightedHPindex<0) return;
			startDragging(pX,pY);
		}

		void onReleased(MouseEvent e, ViewState viewState) {
			Point p = e.getPoint();
			float pX = viewState.convertPos_ScreenToAngle_LongX(p.x);
			float pY = viewState.convertPos_ScreenToAngle_LatY (p.y);
			stopDragging(pX,pY);
			areaListModel.notifyAreaChanged(area);
		}
		void onDragged (MouseEvent e, ViewState viewState) {
			Point p = e.getPoint();
			float pX = viewState.convertPos_ScreenToAngle_LongX(p.x);
			float pY = viewState.convertPos_ScreenToAngle_LatY (p.y);
			dragging(pX,pY);
			areaListModel.notifyAreaChanged(area);
		}
		
		interface HandlePointAction {
			void applyTo(HandlePoint hp, boolean isHighlighted);
		}
		
		public void forEachPoint(HandlePointAction action) {
			for (int i=0; i<handlePoints.length; i++) {
				HandlePoint hp = handlePoints[i];
				action.applyTo(hp, highlightedHPindex==i);
			}
		}

		static class HandlePoint {
			
			float x,y;
			boolean isVisible;
			
			HandlePoint(Point p) { this(p.x,p.y); }
			HandlePoint(Point p, boolean isVisible) { this(p.x,p.y,isVisible); }
			HandlePoint() { this(0,0,true); }
			HandlePoint(float x, float y) { this(x,y,true); }
			HandlePoint(float x, float y, boolean isVisible) {
				this.x = x;
				this.y = y;
				this.isVisible = isVisible;
			}
			
			void set(float x, float y) {
				this.x = x;
				this.y = y;
			}
		}

		static class CircleEditing extends AreaEditing {
			private static final int HPINDEX_CENTER = 0;
			private static final int HPINDEX_RADIUS = 1;
			private float dragOffsetX;
			private float dragOffsetY;
			
			CircleEditing(AreaListModel areaListModel, Area area) {
				super(areaListModel, area, new HandlePoint(area.shape.center), new HandlePoint(area.shape.center, false));
			}

			public static DistanceResult computeDistanceToCircle(Area area, float pX, float pY) {
				int cX = area.shape.center.x;
				int cY = area.shape.center.y;
				double d1 = Math.sqrt((cX-pX)*(cX-pX)+(cY-pY)*(cY-pY));
				double d2 = Math.abs(d1-area.shape.radius);
				return new DistanceResult((float) Math.min(d1,d2), d1<=area.shape.radius);
			}
			
			@Override void setMousePoint(float pX, float pY, float minDist) {
				int cX = area.shape.center.x;
				int cY = area.shape.center.y;
				double d1 = Math.sqrt((cX-pX)*(cX-pX)+(cY-pY)*(cY-pY));
				double d2 = Math.abs(d1-area.shape.radius);
				
				if (d1 < d2) {
					highlightedHPindex = minDist<d1 ? -1 : HPINDEX_CENTER;
					handlePoints[HPINDEX_RADIUS].isVisible = false;
				} else {
					highlightedHPindex = minDist<d2 ? -1 : HPINDEX_RADIUS;
					handlePoints[HPINDEX_RADIUS].isVisible = true;
					handlePoints[HPINDEX_RADIUS].x = (float) ((pX-cX)/d1*area.shape.radius)+cX;
					handlePoints[HPINDEX_RADIUS].y = (float) ((pY-cY)/d1*area.shape.radius)+cY;
				}
			}

			@Override protected void startDragging(float pX, float pY) {
				if (highlightedHPindex==HPINDEX_CENTER) {
					dragOffsetX = pX-area.shape.center.x;
					dragOffsetY = pY-area.shape.center.y;
				}
			}

			@Override protected void stopDragging(float pX, float pY) {
				dragging(pX, pY);
			}

			@Override protected void dragging(float pX, float pY) {
				switch (highlightedHPindex) {
				case HPINDEX_CENTER:
					area.shape.center.x = Math.round( pX-dragOffsetX );
					area.shape.center.y = Math.round( pY-dragOffsetY );
					handlePoints[HPINDEX_CENTER].x = area.shape.center.x;
					handlePoints[HPINDEX_CENTER].y = area.shape.center.y;
					handlePoints[HPINDEX_RADIUS].isVisible = false;
					break;
					
				case HPINDEX_RADIUS:
					int cX = area.shape.center.x;
					int cY = area.shape.center.y;
					double r = Math.sqrt((cX-pX)*(cX-pX)+(cY-pY)*(cY-pY));
					area.shape.radius = (int) Math.round(r);
					handlePoints[HPINDEX_RADIUS].isVisible = true;
					handlePoints[HPINDEX_RADIUS].x = (float) ((pX-cX)/r*area.shape.radius)+cX;
					handlePoints[HPINDEX_RADIUS].y = (float) ((pY-cY)/r*area.shape.radius)+cY;
					break;
				}
			}
		}
		
		static class RectEditing extends AreaEditing {
			private static final int HPINDEX_CENTER = 0;
			private static final int HPINDEX_C11 = 1;
			private static final int HPINDEX_C12 = 2;
			private static final int HPINDEX_C22 = 3;
			private static final int HPINDEX_C21 = 4;
			private float dragOffsetX;
			private float dragOffsetY;
			
			RectEditing(AreaListModel areaListModel, Area area) {
				super(areaListModel, area, createHPs(area));
			}

			private static HandlePoint[] createHPs(Area area) {
				return updateHandlePoints( area, new HandlePoint[] {
					new HandlePoint(), new HandlePoint(), new HandlePoint(), new HandlePoint(), new HandlePoint()
				});
			}

			private static HandlePoint[] updateHandlePoints(Area area, HandlePoint[] handlePoints) {
				int c1X = area.shape.corner1.x;
				int c1Y = area.shape.corner1.y;
				int c2X = area.shape.corner2.x+1;
				int c2Y = area.shape.corner2.y+1;
				float mX = (c1X+c2X)/2.0f;
				float mY = (c1Y+c2Y)/2.0f;
				handlePoints[HPINDEX_CENTER].set(mX,mY);
				handlePoints[HPINDEX_C11].set(c1X,c1Y);
				handlePoints[HPINDEX_C12].set(c1X,c2Y);
				handlePoints[HPINDEX_C22].set(c2X,c2Y);
				handlePoints[HPINDEX_C21].set(c2X,c1Y);
				return handlePoints;
			}

			public static DistanceResult computeDistanceToRect(Area area, float pX, float pY) {
				int c1X = area.shape.corner1.x;
				int c1Y = area.shape.corner1.y;
				int c2X = area.shape.corner2.x+1;
				int c2Y = area.shape.corner2.y+1;
				double mX = (c1X+c2X)/2.0;
				double mY = (c1Y+c2Y)/2.0;
				double d = Math.sqrt((mX-pX)*(mX-pX)+(mY-pY)*(mY-pY));
				d = Math.min(d, Math.sqrt((c1X-pX)*(c1X-pX)+(c1Y-pY)*(c1Y-pY)));
				d = Math.min(d, Math.sqrt((c1X-pX)*(c1X-pX)+(c2Y-pY)*(c2Y-pY)));
				d = Math.min(d, Math.sqrt((c2X-pX)*(c2X-pX)+(c2Y-pY)*(c2Y-pY)));
				d = Math.min(d, Math.sqrt((c2X-pX)*(c2X-pX)+(c1Y-pY)*(c1Y-pY)));
				return new DistanceResult( (float) d, c1X<=pX && pX<=c2X && c1Y<=pY && pY<=c2Y);
			}
			
			@Override void setMousePoint(float pX, float pY, float minDist) {
				int c1X = area.shape.corner1.x;
				int c1Y = area.shape.corner1.y;
				int c2X = area.shape.corner2.x+1;
				int c2Y = area.shape.corner2.y+1;
				double mX = (c1X+c2X)/2.0;
				double mY = (c1Y+c2Y)/2.0;
				double d1, d = Math.sqrt((mX-pX)*(mX-pX)+(mY-pY)*(mY-pY)); highlightedHPindex = minDist<d ? -1 : HPINDEX_CENTER;
				d1 = Math.sqrt((c1X-pX)*(c1X-pX)+(c1Y-pY)*(c1Y-pY)); if (d1<d) { d=d1; highlightedHPindex = minDist<d ? -1 : HPINDEX_C11; }
				d1 = Math.sqrt((c1X-pX)*(c1X-pX)+(c2Y-pY)*(c2Y-pY)); if (d1<d) { d=d1; highlightedHPindex = minDist<d ? -1 : HPINDEX_C12; }
				d1 = Math.sqrt((c2X-pX)*(c2X-pX)+(c2Y-pY)*(c2Y-pY)); if (d1<d) { d=d1; highlightedHPindex = minDist<d ? -1 : HPINDEX_C22; }
				d1 = Math.sqrt((c2X-pX)*(c2X-pX)+(c1Y-pY)*(c1Y-pY)); if (d1<d) { d=d1; highlightedHPindex = minDist<d ? -1 : HPINDEX_C21; }
			}

			@Override protected void startDragging(float pX, float pY) {
				int c1X = area.shape.corner1.x;
				int c1Y = area.shape.corner1.y;
				int c2X = area.shape.corner2.x+1;
				int c2Y = area.shape.corner2.y+1;
				switch (highlightedHPindex) {
				case HPINDEX_CENTER:
				case HPINDEX_C11: dragOffsetX = pX-c1X; dragOffsetY = pY-c1Y; break;
				case HPINDEX_C12: dragOffsetX = pX-c1X; dragOffsetY = pY-c2Y; break;
				case HPINDEX_C22: dragOffsetX = pX-c2X; dragOffsetY = pY-c2Y; break;
				case HPINDEX_C21: dragOffsetX = pX-c2X; dragOffsetY = pY-c1Y; break;
				}
			}

			@Override protected void stopDragging(float pX, float pY) {
				dragging(pX, pY);
			}

			@Override protected void dragging(float pX, float pY) {
				switch (highlightedHPindex) {
				case HPINDEX_CENTER: 
					int w = area.shape.corner2.x-area.shape.corner1.x;
					int h = area.shape.corner2.y-area.shape.corner1.y;
					area.shape.corner1.x = Math.round(pX-dragOffsetX);
					area.shape.corner1.y = Math.round(pY-dragOffsetY);
					area.shape.corner2.x = area.shape.corner1.x + w;
					area.shape.corner2.y = area.shape.corner1.y + h;
					break;
				case HPINDEX_C11:
					area.shape.corner1.x = Math.min( Math.round(pX-dragOffsetX  ), area.shape.corner2.x );
					area.shape.corner1.y = Math.min( Math.round(pY-dragOffsetY  ), area.shape.corner2.y );
					break;
				case HPINDEX_C12:
					area.shape.corner1.x = Math.min( Math.round(pX-dragOffsetX  ), area.shape.corner2.x );
					area.shape.corner2.y = Math.max( Math.round(pY-dragOffsetY-1), area.shape.corner1.y );
					break;
				case HPINDEX_C22:
					area.shape.corner2.x = Math.max( Math.round(pX-dragOffsetX-1), area.shape.corner1.x );
					area.shape.corner2.y = Math.max( Math.round(pY-dragOffsetY-1), area.shape.corner1.y );
					break;
				case HPINDEX_C21:
					area.shape.corner2.x = Math.max( Math.round(pX-dragOffsetX-1), area.shape.corner1.x );
					area.shape.corner1.y = Math.min( Math.round(pY-dragOffsetY  ), area.shape.corner2.y );
					break;
				}
				updateHandlePoints(area, handlePoints);
			}
		}
	}
	
	
	@Override public void mouseEntered (MouseEvent e) { setHighlightedArea(e.getPoint()); /* if (areaEditing!=null) areaEditing.onEntered (e); */ }
	@Override public void mouseMoved   (MouseEvent e) { setHighlightedArea(e.getPoint()); /* if (areaEditing!=null) areaEditing.onMoved   (e); */ }
	@Override public void mouseExited  (MouseEvent e) { setHighlightedArea((Point)null ); /* if (areaEditing!=null) areaEditing.onExited  (e); */ }
	
	@Override public void mousePressed (MouseEvent e) { if (areaEditing!=null) { areaEditing.onPressed (e, viewState); repaint(); } }
	@Override public void mouseReleased(MouseEvent e) { if (areaEditing!=null) { areaEditing.onReleased(e, viewState); repaint(); } }
	@Override public void mouseDragged (MouseEvent e) { if (areaEditing!=null) { areaEditing.onDragged (e, viewState); repaint(); } }
	
	@Override public void mouseClicked (MouseEvent e) {
		if (contextMenu!=null && e.getButton()==MouseEvent.BUTTON3) {
			Point p = new Point(e.getPoint());
			contextMenu.clickedArea = areaEditing!=null ? areaEditing.area : null;
			contextMenu.clickedScreenPos = p;
			if (viewState.isOk())
				contextMenu.clickedPos = new Point2D.Float(
					viewState.convertPos_ScreenToAngle_LongX(p.x),
					viewState.convertPos_ScreenToAngle_LatY (p.y)
				);
			else
				contextMenu.clickedPos = null;
			contextMenu.prepareItems();
			contextMenu.show(this, p.x, p.y);
		}
		super.mouseClicked(e);
	}
	@Override public void mouseWheelMoved(MouseWheelEvent e) { super.mouseWheelMoved(e); }
	
	@Override
	protected void paintCanvas(Graphics g, int x, int y, int width, int height) {
		
		if (g instanceof Graphics2D && viewState.isOk()) {
			Graphics2D g2 = (Graphics2D) g;
			g2.setClip(x, y, width, height);
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			
			if (image!=null) {
				int imageX      = viewState.convertPos_AngleToScreen_LongX(0);
				int imageY      = viewState.convertPos_AngleToScreen_LatY (0);
				int imageWidth  = viewState.convertPos_AngleToScreen_LongX(image.getWidth ()) - imageX;
				int imageHeight = viewState.convertPos_AngleToScreen_LatY (image.getHeight()) - imageY;
				
				g2.setColor(COLOR_AXIS);
				g2.drawLine(x+imageX, y, x+imageX, y+height);
				g2.drawLine(x, y+imageY, x+width, y+imageY);
				g2.drawLine(x+imageX+imageWidth, y, x+imageX+imageWidth, y+height);
				g2.drawLine(x, y+imageY+imageHeight, x+width, y+imageY+imageHeight);
				
				g2.drawImage(image, x+imageX, y+imageY, imageWidth+1, imageHeight+1, null);
			}
			
			paintAreas(g2, x, y);
			drawMapDecoration(g2, x, y, width, height);
		}
	}

	private void paintAreas(Graphics2D g2, int x0, int y0) {
		g2.setColor(COLOR_AREA);
		areaListModel.forEach((area,isSelected)->{
			if (!isSelected && (areaEditing==null || areaEditing.area!=area))
				paintArea(g2, x0, y0, area);
		});
		g2.setColor(COLOR_HIGHLIGHTED_AREA);
		areaListModel.forEach((area,isSelected)->{
			if (isSelected && (areaEditing==null || areaEditing.area!=area))
				paintArea(g2, x0, y0, area);
		});
		if (areaEditing!=null) {
			g2.setColor(COLOR_HIGHLIGHTED_AREA);
			paintArea(g2, x0, y0, areaEditing.area);
			paintHandlePoints(g2, x0, y0);
		}
	}
	private void paintArea(Graphics2D g2, int x0, int y0, Area area) {
		if (area.shape==null) return;
		
		switch (area.shape.type) {
		
		case Circle:
			int cX = viewState.convertPos_AngleToScreen_LongX(area.shape.center.x);
			int cY = viewState.convertPos_AngleToScreen_LatY (area.shape.center.y);
			int r  = viewState.convertLength_LengthToScreen((float) area.shape.radius);
			g2.drawOval(x0+cX-r, y0+cY-r, 2*r, 2*r);
			break;
			
		case Rect:
			int c1X = viewState.convertPos_AngleToScreen_LongX(area.shape.corner1.x);
			int c1Y = viewState.convertPos_AngleToScreen_LatY (area.shape.corner1.y);
			int w  = viewState.convertLength_LengthToScreen((float) (area.shape.corner2.x-area.shape.corner1.x+1));
			int h  = viewState.convertLength_LengthToScreen((float) (area.shape.corner2.y-area.shape.corner1.y+1));
			g2.drawRect(x0+c1X, y0+c1Y, w, h);
			break;
		}
	}
	
	private void paintHandlePoints(Graphics2D g2, int x0, int y0) {
		areaEditing.forEachPoint((hp,isHighlighted)->{
			if (!hp.isVisible) return;
			int hpX = viewState.convertPos_AngleToScreen_LongX(hp.x);
			int hpY = viewState.convertPos_AngleToScreen_LatY (hp.y);
			
			int r = 3;
			g2.setColor(isHighlighted ? Color.YELLOW : Color.GREEN);
			g2.fillOval(x0+hpX-r, y0+hpY-r, 2*r+1, 2*r+1);
			g2.setColor(COLOR_AXIS);
			g2.drawOval(x0+hpX-r, y0+hpY-r, 2*r, 2*r);
		});
	}
	@Override
	protected ViewState createViewState() {
		return new ViewState();
	}
	
	class ViewState extends ZoomableCanvas.ViewState {
		
		ViewState() {
			super(EditorView.this,0.1f);
			setPlainMapSurface();
			setVertAxisDownPositive(true);
			//debug_showChanges_scalePixelPerLength = true;
		}

		@Override
		protected void determineMinMax(MapLatLong min, MapLatLong max) {
			min.longitude_x = (float) 0;
			min.latitude_y  = (float) 0;
			max.longitude_x = (float) (image==null ? 100 : image.getWidth ());
			max.latitude_y  = (float) (image==null ? 100 : image.getHeight());
		}
	}
}