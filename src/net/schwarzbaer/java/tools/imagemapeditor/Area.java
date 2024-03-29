package net.schwarzbaer.java.tools.imagemapeditor;

import java.awt.Point;

import net.schwarzbaer.java.tools.imagemapeditor.Area.Shape.Type;

public class Area {
	String title;
	String onclick;
	Area.Shape shape;
	
	public Area(Area.Shape shape, String title, String onclick) {
		this.shape = shape;
		this.title = title;
		this.onclick = onclick;
	}

	public Area(Area area) {
		copyFrom(area);
	}

	public void copyFrom(Area area) {
		shape = new Shape(area.shape);
		title = area.title;
		onclick = area.onclick;
	}

	public boolean switchToShapeType(Type type) {
		if (shape.type==type) return false;
		
		Shape newShape = shape.derive(type);
		if (newShape==null) return false;
		
		shape = newShape;
		return true;
	}

	@Override
	public String toString() {
		return String.format("%s \"%s\" <%s>", shape, title, onclick);
	}

	public static class Shape {
		public enum Type {
			Rect, Circle;
			String toHtmlValue() { return name().toLowerCase(); }
		}
		
		final Shape.Type type;
		final Point center;
		      int   radius;
		final Point corner1;
		final Point corner2;
		
		public Shape(Point center, int radius) {
			type = Type.Circle;
			this.center = center;
			this.radius = radius;
			this.corner1 = null;
			this.corner2 = null;
		}
		public Shape(Point corner1, Point corner2) {
			type = Type.Rect;
			this.center = null;
			this.radius = 0;
			this.corner1 = new Point(Math.min(corner1.x,corner2.x), Math.min(corner1.y,corner2.y));
			this.corner2 = new Point(Math.max(corner1.x,corner2.x), Math.max(corner1.y,corner2.y));
		}
		public Shape(Shape shape) {
			this.type    = shape.type;
			this.center  = shape.center==null ? null : new Point(shape.center);
			this.radius  = shape.radius;
			this.corner1 = shape.corner1==null ? null : new Point(shape.corner1);
			this.corner2 = shape.corner2==null ? null : new Point(shape.corner2);
		}
		
		public Shape derive(Type type) {
			switch (type) {
			
			case Circle: {
				if (this.type!=Type.Rect) return null;
				float cX = (corner1.x+corner2.x+1)/2.0f;
				float cY = (corner1.y+corner2.y+1)/2.0f;
				float radius = (float) Math.sqrt((corner1.x-cX)*(corner1.x-cX)+(corner1.y-cY)*(corner1.y-cY));
				Point center = new Point();
				center.x = Math.round(cX);
				center.y = Math.round(cY);
				return new Shape(center, Math.round(radius));
			}
				
			case Rect: {
				if (this.type!=Type.Circle) return null;
				double dX = Math.cos(Math.PI/6)*radius;
				double dY = Math.sin(Math.PI/6)*radius;
				Point corner1 = new Point();
				corner1.x = (int) Math.floor(center.x-dX);
				corner1.y = (int) Math.floor(center.y-dY);
				Point corner2 = new Point();
				corner2.x = (int) Math.ceil(center.x+dX);
				corner2.y = (int) Math.ceil(center.y+dY);
				return new Shape(corner1, corner2);
			}
			}
			
			throw new IllegalStateException();
		}
		@Override
		public String toString() {
			switch (type) {
			case Circle: return String.format("Circle(%d,%d|%d)", center.x, center.y, radius);
			case Rect  : return String.format("Rect(%d,%d|%d,%d)", corner1.x, corner1.y, corner2.x, corner2.y);
			}
			throw new IllegalStateException();
		}
		public String toCoordsValue() {
			switch (type) {
			case Circle: return String.format("%d,%d,%d", center.x, center.y, radius);
			case Rect  : return String.format("%d,%d,%d,%d", corner1.x, corner1.y, corner2.x, corner2.y);
			}
			throw new IllegalStateException();
		}
	}
}