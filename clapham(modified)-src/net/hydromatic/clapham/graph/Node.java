/*
// $Id: Node.java 3 2009-05-11 08:11:57Z jhyde $
// Clapham generates railroad diagrams to represent computer language grammars.
// Copyright (C) 2008-2009 Julian Hyde
// Copyright (c) 2005 Stefan Schoergenhumer, Markus Dopler
//
// This program is free software; you can redistribute it and/or modify it
// under the terms of the GNU General Public License as published by the Free
// Software Foundation; either version 2 of the License, or (at your option)
// any later version approved by The Eigenbase Project.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
package net.hydromatic.clapham.graph;

import java.awt.*;
import java.awt.geom.Point2D.Float;
import java.util.List;
import java.util.ArrayList;

/**
 * TODO:
 *
 * @author jhyde
 * @version $Id: Node.java 3 2009-05-11 08:11:57Z jhyde $
 * @since Jul 30, 2008
 */
public class Node {

    public final int      n;			// node number
	public NodeType typ;		// t, nt, eps, alt, iter, opt, rerun
	public Node     next;		// to successor node
	public Node     down;		// alt: to next alternative
	public Node     sub;		// alt, iter, opt: to first node of substructure
	public boolean  up;			// true: "next" leads to successor in enclosing structure
	public Symbol   sym;		// nt, t: symbol represented by this node
	public Node		itergraph;	// rerun: points to the b in "a {b a}", null if "a {a}"
	private boolean  firstLevel; // true if the Node is in the first Level
    private TextBox textBox;

    public Node(Grammar grammar, Symbol sym, Color lineToColor, Color nodeColor, Font nodeFont) {
		this.typ = sym.typ;
        this.sym = sym;
		this.lineToColor = lineToColor;
		this.nodeColor = nodeColor;
		this.nodeFont = nodeFont;
		n = grammar.nodes.size();
		grammar.nodes.add(this);
	}
	
	public Node(Grammar grammar, NodeType typ, Node sub, Color lineToColor, Color nodeColor, Font nodeFont) {
		this.typ = typ;
		this.lineToColor = lineToColor;
		this.nodeColor = nodeColor;
		this.nodeFont = nodeFont;
		n = grammar.nodes.size();
		grammar.nodes.add(this);
		this.sub = sub;
	}

	public Node(Grammar grammar, NodeType typ, Node sub) {
		this.typ = typ;
		//This line color is used when drawing anything != sequence nodes
		this.lineToColor = Color.BLACK;
		//Debug, to see if this problem ever arises
		this.nodeColor = Color.YELLOW;
		n = grammar.nodes.size();
		grammar.nodes.add(this);
		this.sub = sub;
	}

    //----------------- for printing ----------------------

    //----------------- for drawing ----------------------

    /*****************other variables needed for the drawing********/
    Size 	size 		= new Size(0,0);			// the required size to draw the node
	private Size 	altSize 	= new Size(0,0);			// the required size to draw a construct of alts or the size of the firstcomponent in the special rerun-node (itergraph!=null)
	private Size 	iterSize 	= new Size(0,0);			// the size of the second component in the special rerun Node (itergraph!=null)
	final Float posBegin 	= new Float(0,0);			// the point in the left above corner of the component
	public final Float posLine 	= new Float(0,0);			// the point of the line of the component
	final Float posEnd 		= new Float(0,0);			// the point in the left down corner of the component
	Color lineToColor, nodeColor;
	Font nodeFont;

    public void unparse(StringBuffer buf) {
        switch (typ) {
        case TERM:
            buf.append('\'').append(sym.name).append('\'');
            break;
        case NONTERM:
            buf.append('<').append(sym.name).append('>');
            break;
        case ALT:
            final List<Node> alts = new ArrayList<Node>();
            for (Node n = this; n != null; n = n.down) {
                alts.add(n.sub);
            }
            int count = 0;
            buf.append("(");
            for (Node alt : alts) {
                if (count++ > 0) {
                    buf.append(" | ");
                }
                unparseList(buf, nextChildren(alt), "", " ", "");
            }
            buf.append(")");
            break;
        case ITER:
            unparseList(buf, nextChildren(sub), "iter(", " ", ")");
            break;
        default:
            buf.append("unknown <").append(typ).append(">");
        }
    }

    private static List<Node> nextChildren(Node next) {
        final List<Node> list = new ArrayList<Node>();
        for (Node n = next; n != null; n = n.up ? null : n.next) {
            list.add(n);
        }
        return list;
    }

    private void unparseList(
        StringBuffer buf,
        List<Node> list,
        String before,
        String mid, 
        String after)
    {
        int count = 0;
        buf.append(before);
        for (Node n : list) {
            if (count++ > 0) {
                buf.append(mid);
            }
            n.unparse(buf);
        }
        buf.append(after);
    }

    // calculates the size if there are wraps in the rule
	public void setWrapSize(Chart chart) {
        Node n = this;
        float maxH = 0;
        while (n != null) {
            n.firstLevel = true;
            switch (n.typ) {
            case WRAP:
                n.size.setHeight(maxH);
                maxH = 0;
                break;
            case ITER:
                if (maxH
                    < n.size.getHeight()
                    + (chart.getFontHeight() + chart.componentGapHeight)
                    / 2) {
                    maxH =
                        n.size.getHeight()
                            + (chart.getFontHeight()
                            + chart.componentGapHeight) / 2;
                }
                break;
            default:
                if (maxH < n.size.getHeight()
                    || maxH < n.altSize.getHeight()) {
                    if (n.altSize.getHeight() != 0) {
                        maxH = n.altSize.getHeight();
                    } else {
                        maxH = n.size.getHeight();
                    }
                }
                break;
            }
            n = n.next;
        }
    }

    /**
     * Calculates the size of each symbol.
     */
    public Size calcSize(Chart chart) {
        Node n = this;                             //current node in the level
        Size s = new Size(); // alt,iter,opt: size of current construct
        int iterCompensation = 0;
        boolean samelevel = true;                    //next node in same level?
        int realHeight = n.calcHeight(chart);
        Size maxTotalSize = new Size(0, 0);
        while (n != null && samelevel) {
            switch (n.typ) {
            case TERM:
            case NONTERM:
				if( n.nodeFont == null )
				{
					n.nodeFont = chart.charFont;
				}
                n.textBox =
                    new TextBox(
                        chart, n.sym.name, n.nodeFont, chart.charColor);
                n.size.setHeight(
                    n.textBox.height
                        + chart.symbolGapHeight * 2
                        + chart.componentGapHeight);
                n.size.setWidth(
                    n.textBox.width
                        + chart.symbolGapWidth * 2);
                if (n.typ == NodeType.TERM) {
                    n.size.maxWidth(
                        chart.componentArcSize);
                }
                if (!n.up
                    && n.next != null
                    && n.next.typ == NodeType.WRAP
                    && n.next.size.getHeight() == 0) {
                    if (!n.next.up
                        && n.next.next != null
                        && (n.next.next.typ == NodeType.TERM
                        || n.next.next.typ == NodeType.NONTERM)) {
                        s.incWidth(chart.componentGapWidth / 2);
                    }
                }
                if (!n.up && n.next != null
                    && (n.next.typ == NodeType.TERM
                    || n.next.typ == NodeType.NONTERM)) {
                    s.incWidth(chart.componentGapWidth / 2);
                }
                break;
            case EPS:
                n.size.setHeight(
                    chart.getFontHeight() + chart.componentGapHeight);
                n.size.setWidth(chart.componentGapWidth);
                break;
            case OPT:
                n.size = n.sub.calcSize(chart);
                n.size.incWidth(chart.componentGapWidth * 2);
                n.size.incHeight(chart.componentGapHeight / 2);
                break;
            case ITER:
                n.size = n.sub.calcSize(chart);
                n.size.incWidth(chart.componentGapWidth * 2);
                break;
            case WRAP:
                maxTotalSize.incHeight(
                    s.getHeight()
                        - chart.componentGapHeight / 2);
                maxTotalSize.maxWidth(s.getWidth());
                s.setHeight(0);
                s.setWidth(0);
                break;
            case RERUN:
                n.size = n.sub.calcSize(chart);
                if (n.itergraph != null) {
                    n.altSize = n.size;
                    n.size.maxWidth(n.itergraph.calcSize(chart).getWidth());
                    n.size.incHeight(n.itergraph.calcSize(chart).getHeight());
                    n.iterSize = n.itergraph.calcSize(chart);
                } else {
                    n.size.incHeight(chart.componentGapHeight / 2);
                }
                n.size.incWidth(chart.componentGapWidth * 2);
                break;
            case ALT: {
                Node a = n;
                int maxH = -chart.componentGapHeight;
                float maxW = 0;
                while (a != null) {
                    a.size = a.sub.calcSize(chart);
                    maxH += a.size.getHeight();
                    if (a.size.getWidth() > maxW) {
                        maxW = a.size.getWidth();
                    }
                    a = a.down;
                }
                if (n.sub.typ == NodeType.ITER && realHeight != 0) {
                    maxH +=
                        (chart.getFontHeight() + chart.componentGapHeight) / 2;
                }
                maxW += 2 * chart.componentGapWidth;
                maxH += chart.componentGapHeight;

                n.altSize.setHeight(maxH);
                n.altSize.setWidth(maxW);
            }
            break;
            }
            if (n.typ == NodeType.ITER && realHeight != 0) {
                iterCompensation =
                    (chart.getFontHeight() + chart.componentGapHeight) / 2;
            }
            if (n.typ == NodeType.ALT) {
                s.maxHeight(n.altSize.getHeight());
                s.incWidth(n.altSize.getWidth());
            } else {
                s.maxHeight(n.size.getHeight());
                s.incWidth(n.size.getWidth());
            }
            if (n.typ == NodeType.ITER) {
                s.maxHeight(n.size.getHeight() + iterCompensation);
            }
            if (n.up) {
                samelevel = false;
            }
            n = n.next;
        }
        if (maxTotalSize.getWidth() != 0) {
            maxTotalSize.incHeight(
                s.getHeight()
                    - chart.componentGapHeight / 2);
            maxTotalSize.maxWidth(s.getWidth());
            return maxTotalSize;
        } else {
            return s;
        }
    }

    /**
     * Calculates the total height of all symbols wich are in the same
     * horizontal level.
     */
    int calcHeight(Chart chart) {
        Node n = this;                             //current node in the level
        float realHeight = 0;
        boolean samelevel = true;                    //next node in same level?
        while (n != null && samelevel) {
            if (n.typ == NodeType.NONTERM || n.typ == NodeType.TERM) {
                if (realHeight < n.size.getHeight()) {
                    realHeight = n.size.getHeight();
                }
            } else if (n.typ == NodeType.ITER) {
                int tmpHeight = 0;
                if (realHeight < tmpHeight) {
                    realHeight = tmpHeight;
                }
            } else if (n.typ == NodeType.OPT
                || n.typ == NodeType.RERUN) {
                int tmpHeight = n.sub.calcHeight(chart);
                if (realHeight < tmpHeight) {
                    // REVIEW:
                }
                realHeight = tmpHeight;
            } else if (n.typ == NodeType.ALT) {
                int tmpHeight = n.sub.calcHeight(chart);
                if (realHeight < tmpHeight) {
                    // REVIEW:
                }
                realHeight = tmpHeight;
            } else if (n.typ == NodeType.EPS) {
                if (realHeight < chart.getFontHeight() * 3 / 2) {
                    realHeight =
                        chart.getFontHeight() + chart.componentGapHeight;
                }
            }
            if (n.up) {
                samelevel = false;
            }
            n = n.next;
        }
        return (int) realHeight;
    }

    /**
     * Calculates the horizontal position of the symbols.
     */
    public void calcPos(Chart chart, float posBegin) {
        Node n = this;                             //current node in the level
        int realHeight = calcHeight(chart);
        boolean samelevel = true;                    //next node in same level?
        while (n != null && samelevel) {
            if (n.typ == NodeType.NONTERM || n.typ == NodeType.TERM) {
                n.posLine.y = posBegin + realHeight / 2;
                n.posBegin.y =
                    n.posLine.y
                        - (n.size.getHeight() - chart.componentGapHeight) / 2;
                n.posEnd.y =
                    n.posLine.y
                        + (n.size.getHeight() - chart.componentGapHeight) / 2;
            } else if (n.typ == NodeType.EPS) {
                n.posLine.y = posBegin + n.size.getHeight() / 2;
                n.posBegin.y = posBegin;
                n.posEnd.y = posBegin + n.size.getHeight();
            } else if (n.typ == NodeType.OPT) {
                n.posLine.y = posBegin + realHeight / 2;
                n.posBegin.y = posBegin;
                n.posEnd.y = posBegin + n.size.getHeight();
                n.sub.calcPos(chart, n.posBegin.y);
            } else if (n.typ == NodeType.RERUN) {
                n.posLine.y = posBegin + realHeight / 2;
                n.posBegin.y = posBegin;
                n.posEnd.y = posBegin + n.size.getHeight();
                if (n.itergraph != null) {
                    n.itergraph.calcPos(
                        chart,
                        posBegin + n.altSize.getHeight());
                }
                n.sub.calcPos(chart, n.posBegin.y);
            } else if (n.typ == NodeType.ITER) {
                if (realHeight == 0) {
                    n.posLine.y = posBegin + realHeight / 2;
                    n.posBegin.y = posBegin;
                    n.posEnd.y = posBegin + n.size.getHeight();
                } else {
                    n.posLine.y = posBegin + realHeight / 2;
                    n.posBegin.y =
                        posBegin
                            + (chart.getFontHeight() + chart.componentGapHeight)
                            / 2;
                    n.posEnd.y = n.posBegin.y + n.size.getHeight();
                }
                n.sub.calcPos(chart, n.posLine.y);
            } else if (n.typ == NodeType.WRAP && firstLevel) {
                n.posLine.y = posBegin + realHeight / 2;
                n.posEnd.y = posBegin + n.size.getHeight();
                posBegin = posBegin + n.size.getHeight();
            } else if (n.typ == NodeType.ALT) {
                n.posLine.y = posBegin + realHeight / 2;
                n.posBegin.y = posBegin;
                n.posEnd.y = posBegin + n.altSize.getHeight();
                if (n.sub.typ == NodeType.ITER
                    && n.calcHeight(chart) != 0
                    && n.altSize.getHeight() != 0) {
                    posBegin +=
                        (chart.getFontHeight() + chart.componentGapHeight) / 2;
                }
                n.sub.calcPos(chart, posBegin);
                if (n.down != null) {
                    n.down.calcPos(chart, posBegin + n.size.getHeight());
                }
                if (n.sub.typ == NodeType.ITER
                    && n.calcHeight(chart) != 0
                    && n.altSize.getHeight() != 0) {
                    posBegin -=
                        (chart.getFontHeight() + chart.componentGapHeight) / 2;
                }
            }
            if (n.up) {
                samelevel = false;
            }
            n = n.next;
        }
    }

	/**
	 * Draws the components from left to right.
     *
     * <p>Each component paints itself and then give its coordinates to its
     * sub-components for a recursive call, or if applicable, a call to
     * the {@link #drawComponentsInverse} method.
	*/
    public void drawComponents(Chart chart, Float p, Size s) {
        Node n = this;                             // current node in the level
        boolean samelevel = true; // next node in same level?

        while (n != null && samelevel) {
			chart.charColor = n.nodeColor;
			chart.lineColor = n.lineToColor;
			chart.charFont = n.nodeFont;
            switch (n.typ) {
            case TERM:
            case NONTERM:
                if (chart.showBorders) {
                    chart.drawRectangle(
                        Chart.RERUN_COLOR,
                        Chart.STROKE1,
                        p.x,
                        n.posBegin.y - chart.componentGapHeight / 2,
                        n.size.getWidth(),
                        n.size.getHeight());
                }
                if (n.typ == NodeType.TERM) {
                    // the quarter Arcs
                    final float foo =
                        (n.size.getHeight() - chart.componentGapHeight) / 2;
                    chart.drawArcCorner(
                        p.x,
                        n.posBegin.y,
                        foo,
                        180);
                    chart.drawArcCorner(
                        p.x,
                        n.posLine.y,
                        foo,
                        90);
                    chart.drawArcCorner(
                        p.x + n.size.getWidth() - foo,
                        n.posBegin.y,
                        foo,
                        270);
                    chart.drawArcCorner(
                        p.x + n.size.getWidth() - foo,
                        n.posLine.y,
                        foo,
                        0);

					if (false)
                    n.textBox.drawAtCenter(
                        p.x,
                        n.posBegin.y,
                        n.size.getWidth() - foo,
                        n.posLine.y - n.posBegin.y);

                    // the short vertical and horizontal lines between the quarter Arcs
                    final float quarterHeight =
                        (n.size.getHeight() - chart.componentGapHeight)
                            / 4;
                    chart.drawLine(
                        p.x + quarterHeight - 1,
                        n.posBegin.y,
                        p.x + n.size.getWidth() - quarterHeight + 1,
                        n.posBegin.y);
                    chart.drawLine(
                        p.x + quarterHeight - 1,
                        n.posEnd.y,
                        p.x + n.size.getWidth() - quarterHeight + 1,
                        n.posEnd.y);
                    chart.drawLine(
                        p.x,
                        n.posLine.y + quarterHeight + 1,
                        p.x,
                        n.posLine.y - quarterHeight - 1);
                    chart.drawLine(
                        p.x + n.size.getWidth(),
                        n.posLine.y + quarterHeight + 1,
                        p.x + n.size.getWidth(),
                        n.posLine.y - quarterHeight - 1);
                } else {
					// A: FRAME AROUND Component
                    n.posBegin.x = p.x;
                    n.posEnd.x = p.x + n.size.getWidth();
                    chart.drawRectangle(
                        chart.lineColor,
                        chart.lineStroke,
                        n.posBegin.x,
                        n.posBegin.y,
                        n.size.getWidth(),
                        n.size.getHeight() - chart.componentGapHeight);
					if( false )
                    n.textBox.drawAtCenter(
                        n.posBegin.x,
                        n.posBegin.y,
                        n.size.getWidth(),
                        n.size.getHeight() - chart.componentGapHeight);
                }
                if (Grammar.TRACE) {
                    System.out.println("text=" + n.sym.name);
                    System.out.println("n.posBegin.y=" + n.posBegin.y);
                    System.out.println(
                        "chart.getFontHeight()=" + chart.getFontHeight());
                    System.out.println("n.size=" + n.size.getHeight());
                    System.out.println(
                        "2=" + +(n.size.getHeight()
                                 - chart.componentGapHeight));
                    System.out.println(
                        "3="
                        + (n.size.getHeight() - chart.componentGapHeight - chart
                            .getFontHeight()) / 2);
                }
                //if (false)
                chart.drawString(
                    n.sym.name,
                    chart.charFont,
                    chart.charColor,
                    p.x + (n.size.getWidth() - n.textBox.width) / 2,
                    n.posBegin.y
                        + (n.size.getHeight() - chart.componentGapHeight)
                        - chart.symbolGapHeight - 2);
				// A: first arrow (head) pointing to the very first component (only the first component)
                chart.drawArrow(
                    p.x,
                    n.posLine.y,
                    p.x,
                    n.posLine.y,
                    Grammar.Direction.RIGHT);
                p.x += n.size.getWidth();
                // draw lines between t and nt nodes
				// A: Lines between each sequence! component
                if (!n.up && n.next != null
                    && (n.next.typ == NodeType.TERM
                    || n.next.typ == NodeType.NONTERM)) {
                    chart.drawArrow(
                        p.x,
                        n.posLine.y,
                        p.x
                            + chart.componentGapWidth / 2,
                        n.posLine.y,
                        Grammar.Direction.RIGHT);
                    p.x += chart.componentGapWidth / 2;
                }
				// A: Don't know
                if (!n.up
                    && n.next != null
                    && n.next.typ == NodeType.WRAP
                    && n.next.size.getHeight() == 0) {
                    chart.drawArrow(
                        p.x,
                        n.posLine.y,
                        p.x
                            + chart.componentGapWidth / 2,
                        n.posLine.y,
                        Grammar.Direction.RIGHT);
                    p.x += chart.componentGapWidth / 2;
                }
                break;
            case EPS:
			// A: Don't know
                if (chart.showBorders) {
                    chart.drawRectangle(
                        Chart.EPS_COLOR,
                        chart.lineStroke,
                        p.x,
                        n.posBegin.y,
                        n.size.getWidth(),
                        n.size.getHeight());
                }

                chart.drawLine(
                    p.x,
                    n.posLine.y,
                    p.x + n.size.getWidth(),
                    n.posLine.y);
                break;
            case OPT:
			// A: Don't know about this entire section :|
                if (chart.showBorders) {
                    chart.drawRectangle(
                        Chart.EPS_COLOR,
                        Chart.STROKE1,
                        p.x,
                        n.posBegin.y,
                        n.size.getWidth(),
                        n.size.getHeight());
                }

                // the two short lines at the beginning and the end
                chart.drawLine(
                    p.x,
                    n.posLine.y,
                    p.x + chart.componentGapWidth,
                    n.posLine.y);
                chart.drawLine(
                    p.x + n.size.getWidth(),
                    n.posLine.y,
                    p.x
                        + n.size.getWidth()
                        - chart.componentGapWidth,
                    n.posLine.y);
                // the quarter Arcs
                chart.drawArcCorner(
                    p.x
                        + chart.componentGapWidth / 4
                        - chart.componentArcSize / 2,
                    n.posLine.y,
                    270);
                chart.drawArcCorner(
                    p.x + chart.componentGapWidth / 4
                        + chart.componentArcSize / 2,
                    n.posEnd.y
                        - chart.componentArcSize
                        - chart.componentGapHeight / 2,
                    90);
                chart.drawArcCorner(
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize / 2
                        + n.size.getWidth(),
                    n.posLine.y,
                    180);
                chart.drawArcCorner(
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize * 3 / 2
                        + n.size.getWidth(),
                    n.posEnd.y
                        - chart.componentArcSize
                        - chart.componentGapHeight / 2,
                    0);
                // the short vertical lines between the quarter Arcs
                chart.drawLine(
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize / 2,
                    n.posLine.y
                        + chart.componentArcSize / 2,
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize / 2,
                    n.posEnd.y
                        - chart.componentArcSize / 2
                        - chart.componentGapHeight / 2 + 1);
                chart.drawLine(
                    p.x - chart.componentGapWidth / 4
                        - chart.componentArcSize / 2
                        + n.size.getWidth(),
                    n.posLine.y
                        + chart.componentArcSize / 2,
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize / 2
                        + n.size.getWidth(),
                    n.posEnd.y
                        - chart.componentArcSize / 2
                        - chart.componentGapHeight / 2 + 1);
                // the the long horizontal line between the quarter Arcs
                chart.drawLine(
                    p.x + chart.componentGapWidth / 4
                        + chart.componentArcSize,
                    n.posEnd.y
                        - chart.componentGapHeight / 2,
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize
                        + n.size.getWidth() + 1,
                    n.posEnd.y
                        - chart.componentGapHeight / 2);

                n.sub.drawComponents(
                    chart,
                    new Float(
                        p.x + chart.componentGapWidth,
                        0),
                    n.size);
                p.x += n.size.getWidth();
                break;
            case RERUN:
			// A: Don't know about this entire section :|
                if (n.itergraph == null) {
                    if (chart.showBorders) {
                        chart.drawRectangle(
                            Chart.RERUN_COLOR,
                            Chart.STROKE1,
                            p.x,
                            n.posBegin.y,
                            n.size.getWidth(),
                            n.size.getHeight());
                    }

                    // the two short lines at the beginning and the end
                    chart.drawLine(
                        p.x,
                        n.posLine.y,
                        p.x + chart.componentGapWidth,
                        n.posLine.y);
                    chart.drawLine(
                        p.x + n.size.getWidth(),
                        n.posLine.y,
                        p.x
                            + n.size.getWidth()
                            - chart.componentGapWidth,
                        n.posLine.y);
                    // the quarter Arcs
                    chart.drawArcCorner(
                        p.x
                            + chart.componentGapWidth / 4
                            + chart.componentArcSize / 2,
                        n.posEnd.y
                            - chart.componentGapHeight / 2
                            - chart.componentArcSize,
                        90);
                    chart.drawArcCorner(
                        p.x
                            + chart.componentGapWidth / 4
                            + chart.componentArcSize / 2,
                        n.posLine.y,
                        180);
                    chart.drawArcCorner(
                        p.x
                            - chart.componentGapWidth / 4
                            - chart.componentArcSize * 3 / 2
                            + n.size.getWidth(),
                        n.posEnd.y
                            - chart.componentGapHeight / 2
                            - chart.componentArcSize,
                        0);
                    chart.drawArcCorner(
                        p.x
                            - chart.componentGapWidth / 4
                            - chart.componentArcSize * 3 / 2
                            + n.size.getWidth(),
                        n.posLine.y,
                        270);
                    // the short vertical lines between the quarter Arcs
                    chart.drawLine(
                        p.x
                            + chart.componentGapWidth / 4
                            + chart.componentArcSize / 2,
                        n.posLine.y
                            + chart.componentArcSize / 2,
                        p.x
                            + chart.componentGapWidth / 4
                            + chart.componentArcSize / 2,
                        n.posEnd.y
                            - chart.componentGapHeight / 2
                            - chart.componentArcSize / 2 + 1);
                    chart.drawLine(
                        p.x
                            - chart.componentGapWidth / 4
                            - chart.componentArcSize / 2
                            + n.size.getWidth(),
                        n.posLine.y
                            + chart.componentArcSize / 2,
                        p.x
                            - chart.componentGapWidth / 4
                            - chart.componentArcSize / 2
                            + n.size.getWidth(),
                        n.posEnd.y
                            - chart.componentGapHeight / 2
                            - chart.componentArcSize / 2 + 1);
                    // the the long horizontal line between the quarter Arcs
                    chart.drawLine(
                        p.x
                            + chart.componentGapWidth / 4
                            + chart.componentArcSize - 1,
                        n.posEnd.y
                            - chart.componentGapHeight / 2,
                        p.x
                            - chart.componentGapWidth / 4
                            - chart.componentArcSize
                            + n.size.getWidth() + 1,
                        n.posEnd.y
                            - chart.componentGapHeight / 2);

                    n.sub.drawComponents(
                        chart,
                        new Float(
                            p.x + chart.componentGapWidth,
                            0),
                        n.size);
                    p.x += n.size.getWidth();
                } else {
                    if (chart.showBorders) {
                        chart.drawRectangle(
                            Chart.RERUN1_COLOR,
                            Chart.STROKE1,
                            p.x,
                            n.posBegin.y,
                            n.size.getWidth(),
                            n.size.getHeight());
                    }

                    // the two short lines at the beginning and the end of the first component
                    chart.drawLine(
                        p.x,
                        n.posLine.y,
                        p.x
                            + n.size.getWidth() / 2
                            - n.altSize.getWidth() / 2
                            - 1,
                        n.posLine.y);
                    chart.drawLine(
                        p.x
                            + n.size.getWidth() / 2
                            + n.altSize.getWidth() / 2
                            + 1,
                        n.posLine.y,
                        p.x
                            + n.size.getWidth(),
                        n.posLine.y);
                    // the quarter Arcs
                    chart.drawArcCorner(
                        p.x
                            + chart.componentGapWidth / 4
                            + chart.componentArcSize / 2,
                        n.itergraph.posLine.y
                            - chart.componentArcSize,
                        90);
                    chart.drawArcCorner(
                        p.x
                            + chart.componentGapWidth / 4
                            + chart.componentArcSize / 2,
                        n.posLine.y,
                        180);
                    chart.drawArcCorner(
                        p.x
                            - chart.componentGapWidth / 4
                            - chart.componentArcSize * 3 / 2
                            + n.size.getWidth(),
                        n.itergraph.posLine.y
                            - chart.componentArcSize,
                        0);
                    chart.drawArcCorner(
                        p.x
                            - chart.componentGapWidth / 4
                            - chart.componentArcSize * 3 / 2
                            + n.size.getWidth(),
                        n.posLine.y,
                        270);
                    // the short vertical lines between the quarter Arcs
                    chart.drawLine(
                        p.x
                            + chart.componentGapWidth / 4
                            + chart.componentArcSize / 2,
                        n.posLine.y
                            + chart.componentArcSize / 2,
                        p.x
                            + chart.componentGapWidth / 4
                            + chart.componentArcSize / 2,
                        n.itergraph.posLine.y
                            - chart.componentArcSize / 2 + 1);
                    chart.drawLine(
                        p.x
                            - chart.componentGapWidth / 4
                            - chart.componentArcSize / 2
                            + n.size.getWidth(),
                        n.posLine.y
                            + chart.componentArcSize / 2,
                        p.x
                            - chart.componentGapWidth / 4
                            - chart.componentArcSize / 2
                            + n.size.getWidth(),
                        n.itergraph.posLine.y
                            - chart.componentArcSize / 2 + 1);
                    // the two short lines at the beginning and the end of the second component
                    chart.drawLine(
                        p.x
                            + chart.componentGapWidth / 4
                            + chart.componentArcSize,
                        n.itergraph.posLine.y,
                        p.x
                            + n.size.getWidth() / 2
                            - n.iterSize.getWidth() / 2
                            - 1,
                        n.itergraph.posLine.y);
                    chart.drawLine(
                        p.x
                            + n.size.getWidth() / 2
                            + n.iterSize.getWidth() / 2
                            + 1,
                        n.itergraph.posLine.y,
                        p.x
                            - chart.componentGapWidth / 4
                            - chart.componentArcSize
                            + n.size.getWidth()
                            + 1,
                        n.itergraph.posLine.y);

                    n.itergraph.drawComponentsInverse(
                        chart,
                        new Float(
                            p.x
                                + n.size.getWidth() / 2
                                + n.iterSize.getWidth() / 2,
                            n.posEnd.y),
                        n.size);
                    n.sub.drawComponents(
                        chart,
                        new Float(
                            p.x
                                + n.size.getWidth() / 2
                                - n.altSize.getWidth() / 2,
                            n.posEnd.y), n.size);
                    p.x += n.size.getWidth();
                }
                break;
            case ITER:
                if (chart.showBorders) {
                    chart.drawRectangle(
                        Chart.ITER_COLOR,
                        Chart.STROKE1,
                        p.x,
                        n.posBegin.y,
                        n.size.getWidth(),
                        n.size.getHeight());
                }

                // the quarter Arcs
                chart.drawArcCorner(
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize / 2,
                    n.sub.posLine.y
                        - chart.componentArcSize,
                    90);
                chart.drawArcCorner(
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize / 2,
                    n.posLine.y,
                    180);
                chart.drawArcCorner(
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize * 3 / 2
                        + n.size.getWidth(),
                    n.sub.posLine.y
                        - chart.componentArcSize,
                    0);
                chart.drawArcCorner(
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize * 3 / 2
                        + n.size.getWidth(),
                    n.posLine.y,
                    270);
                // the short vertical lines between the quarter Arcs
                chart.drawLine(
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize / 2,
                    n.posLine.y
                        + chart.componentArcSize / 2,
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize / 2,
                    n.sub.posLine.y
                        - chart.componentArcSize / 2 + 1);
                chart.drawLine(
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize / 2
                        + n.size.getWidth(),
                    n.posLine.y
                        + chart.componentArcSize / 2,
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize / 2
                        + n.size.getWidth(),
                    n.sub.posLine.y
                        - chart.componentArcSize / 2 + 1);
                // the two short horizontal lines between the quater Arcs and the components
                chart.drawLine(
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize - 1,
                    n.sub.posLine.y,
                    p.x
                        + chart.componentGapWidth,
                    n.sub.posLine.y);
                chart.drawLine(
                    p.x
                        - chart.componentGapWidth
                        + n.size.getWidth(),
                    n.sub.posLine.y,
                    p.x
                        + n.size.getWidth()
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize + 1,
                    n.sub.posLine.y);
                // the long horizontal line in the middle
                chart.drawLine(
                    p.x,
                    n.posLine.y,
                    p.x + n.size.getWidth(),
                    n.posLine.y);

                n.sub.drawComponentsInverse(
                    chart,
                    new Float(
                        p.x - chart.componentGapWidth + n.size.getWidth(),
                        0),
                    n.size);
                p.x += n.size.getWidth();
                break;
            case WRAP:
                if (n.size.getHeight() != 0
                    && n.next != null) {

                    // the short horizontal line after the first component
                    chart.drawLine(
                        p.x, n.posLine.y, p.x
                        + chart.componentGapWidth / 4 + 1, n.posLine.y);
                    // the short horizontal line at the beginning of the second component
                    chart.drawLine(
                        chart.beginningXCoordinate,
                        n.next.posLine.y,
                        chart.beginningXCoordinate
                            - chart.componentGapWidth / 4,
                        n.next.posLine.y);
                    // the quarter Arcs
                    chart.drawArcCorner(
                        p.x + chart.componentGapWidth / 4 -
                            chart.componentArcSize / 2,
                        n.posLine.y,
                        270);
                    chart.drawArcCorner(
                        p.x + chart.componentGapWidth / 4
                            - chart.componentArcSize / 2,
                        n.posEnd.y
                            - chart.componentArcSize,
                        0);
                    chart.drawArcCorner(
                        (float) (chart.beginningXCoordinate
                            - chart.componentGapWidth / 4
                            - chart.componentArcSize / 2),
                        n.posEnd.y,
                        180);
                    chart.drawArcCorner(
                        (float) (chart.beginningXCoordinate
                            - chart.componentGapWidth / 4
                            - chart.componentArcSize / 2),
                        n.next.posLine.y
                            - chart.componentArcSize,
                        90);
                    // the short vertical lines between the quarter Arcs
                    chart.drawLine(
                        p.x
                            + chart.componentGapWidth / 4
                            + chart.componentArcSize / 2,
                        n.posLine.y
                            + chart.componentArcSize / 2,
                        p.x
                            + chart.componentGapWidth / 4
                            + chart.componentArcSize / 2,
                        n.posEnd.y
                            - chart.componentArcSize / 2 + 1);
                    chart.drawLine(
                        chart.beginningXCoordinate
                            - chart.componentGapWidth / 4
                            - chart.componentArcSize / 2,
                        n.posEnd.y + chart.componentArcSize / 2,
                        chart.beginningXCoordinate
                            - chart.componentGapWidth / 4
                            - chart.componentArcSize / 2,
                        n.next.posLine.y
                            - chart.componentArcSize / 2 + 1);
                    // the long horizontal line in the middle oft the two components
                    chart.drawLine(
                        p.x + chart.componentGapWidth / 4 + 1,
                        n.posEnd.y,
                        chart.beginningXCoordinate
                            - chart.componentGapWidth / 4,
                        n.posEnd.y);

                    p.x = chart.beginningXCoordinate;
                }
                break;
            case ALT: {
			// A: For multiple possibilities
                if (chart.showBorders) {
                    chart.drawRectangle(
                        Color.RED,
                        Chart.STROKE1,
                        p.x,
                        n.posBegin.y,
                        n.altSize.getWidth(),
                        n.altSize.getHeight());
                }

                // the two short lines at the beginning and the end of the alt
                // component
                chart.drawLine(
                    p.x,
                    n.posLine.y,
                    p.x
                        + chart.componentArcSize * 3 / 2,
                    n.posLine.y);
                chart.drawLine(
                    p.x + n.altSize.getWidth(),
                    n.posLine.y,
                    p.x
                        + n.altSize.getWidth()
                        - chart.componentArcSize * 3 / 2,
                    n.posLine.y);
                Node a = n;
                boolean first = true;
                while (a != null) {
					chart.charColor = a.nodeColor;
					chart.lineColor = a.lineToColor;
					chart.charFont = a.nodeFont;
                    // the horizontal lines at the beginning and the end
                    chart.drawLine(
                        p.x
                            + chart.componentArcSize * 3 / 2,
                        a.sub.posLine.y,
                        p.x
                            + (n.altSize.getWidth() - a.size.getWidth()) / 2,
                        a.sub.posLine.y);
                    chart.drawLine(
                        p.x
                            - chart.componentArcSize * 3 / 2
                            + n.altSize.getWidth()
                            + 1,
                        a.sub.posLine.y,
                        p.x
                            + (n.altSize.getWidth() - a.size.getWidth()) / 2
                            + a.size.getWidth(),
                        a.sub.posLine.y);
                    // the first alternative draws different arcs
                    if (first) {
                        chart.drawArcCorner(
                            p.x,
                            n.posLine.y,
                            270);
                        chart.drawArcCorner(
                            p.x
                                + n.altSize.getWidth()
                                - chart.componentArcSize,
                            n.posLine.y,
                            180);
                        first = false;
                    } else {
                        // else draw other arcs and vertical lines
                        chart.drawArcCorner(
                            p.x + chart.componentArcSize,
                            a.sub.posLine.y
                                - chart.componentArcSize,
                            90);
                        chart.drawLine(
                            p.x + chart.componentArcSize,
                            n.posLine.y
                                + chart.componentArcSize / 2,
                            p.x
                                + chart.componentArcSize,
                            a.posLine.y
                                - chart.componentArcSize / 2 + 1);
                        chart.drawArcCorner(
                            p.x
                                - chart.componentArcSize * 2
                                + n.altSize.getWidth(),
                            a.sub.posLine.y
                                - chart.componentArcSize,
                            0);
                        chart.drawLine(
                            p.x
                                - chart.componentArcSize
                                + n.altSize.getWidth(),
                            n.posLine.y
                                + chart.componentArcSize / 2,
                            p.x
                                - chart.componentArcSize
                                + n.altSize.getWidth(),
                            a.posLine.y
                                - chart.componentArcSize / 2 + 1);
                    }
                    a.sub.drawComponents(
                        chart,
                        new Float(
                            p.x
                                + (n.altSize.getWidth() - a.size.getWidth())
                                / 2, a.posEnd.y), a.size);
                    a = a.down;
                }
                p.x += n.altSize.getWidth();
            }
            break;
            }

            if (n.up) {
                samelevel = false;
            }
            if (n.next == null && firstLevel) {
				
				// Set back to default for the very last arrow pointing away (since the error is inside somewhere..)
				chart.charColor = Chart.defaultCharColor;
				chart.lineColor = Chart.defaultLineColor;
				
                chart.drawLine(
                    p.x,
                    n.posLine.y,
                    p.x
                        + chart.componentGapWidth / 4,
                    n.posLine.y);
                chart.drawArrow(
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.arrowSize,
                    n.posLine.y,
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.arrowSize,
                    n.posLine.y,
                    Grammar.Direction.RIGHT);
            }
            n = n.next;
        }
    }

    /*
      * Draw the components from right to left.
      * Needed if for example in an iter-node.
      */
    void drawComponentsInverse(
        Chart chart, Float p, Size s)
    {
        Node n = this;                             //current node in the level
        boolean samelevel = true;                    //next node in same level?
        Float p1 = new Float(0, 0);

        while (n != null && samelevel) {
			chart.charColor = n.nodeColor;
			chart.lineColor = n.lineToColor;
			chart.charFont = n.nodeFont;
            p.x -= n.size.getWidth();
            if (n.typ == NodeType.TERM || n.typ == NodeType.NONTERM) {
                if (chart.showBorders) {
                    chart.drawRectangle(
                        Chart.N_NT_COLOR,
                        Chart.STROKE1,
                        p.x,
                        n.posBegin.y - chart.componentGapHeight / 2,
                        n.size.getWidth(),
                        n.size.getHeight());
                }
                if (n.typ == NodeType.TERM) {
                    // the quarter Arcs
                    final float foo =
                        (n.size.getHeight() - chart.componentGapHeight) / 2;
                    chart.drawArc(
                        chart.lineStroke,
                        chart.lineColor,
                        p.x,
                        n.posBegin.y,
                        foo,
                        foo,
                        180,
                        90);
                    chart.drawArc(
                        chart.lineStroke,
                        chart.lineColor,
                        p.x,
                        n.posLine.y,
                        foo,
                        foo,
                        90,
                        90);
                    chart.drawArc(
                        chart.lineStroke,
                        chart.lineColor,
                        p.x + n.size.getWidth() - foo,
                        n.posBegin.y,
                        foo,
                        foo,
                        270,
                        90);
                    chart.drawArc(
                        chart.lineStroke,
                        chart.lineColor,
                        p.x + n.size.getWidth() - foo,
                        n.posLine.y,
                        foo,
                        foo,
                        0,
                        90);
                    // the short vertical and horizontal lines between the quarter Arcs
                    chart.drawLine(
                        p.x
                        + (n.size.getHeight() - chart.componentGapHeight) / 4
                        - 1, n.posBegin.y, p.x
                        + n.size.getWidth()
                        - (n.size.getHeight() - chart.componentGapHeight) / 4
                        + 1, n.posBegin.y);
                    chart.drawLine(
                        p.x
                        + (n.size.getHeight() - chart.componentGapHeight) / 4
                        - 1, n.posEnd.y, p.x
                        + n.size.getWidth()
                        - (n.size.getHeight() - chart.componentGapHeight) / 4
                        + 1, n.posEnd.y);
                    chart.drawLine(
                        p.x,
                        n.posLine.y
                            + (n.size.getHeight() - chart.componentGapHeight) / 4
                            + 1, p.x,
                        n.posLine.y
                            - (n.size.getHeight() - chart.componentGapHeight) / 4
                            - 1);
                    chart.drawLine(
                        p.x + n.size.getWidth(),
                        n.posLine.y
                            + (n.size.getHeight() - chart.componentGapHeight)
                            / 4 + 1,
                        p.x + n.size.getWidth(),
                        n.posLine.y
                            - (n.size.getHeight() - chart.componentGapHeight)
                            / 4 - 1);
                } else {
                    n.posBegin.x = p.x;
                    n.posEnd.x = p.x + n.size.getWidth();
                    chart.drawRectangle(
                        chart.lineColor,
                        chart.lineStroke,
                        n.posBegin.x,
                        n.posBegin.y,
                        n.size.getWidth(),
                        (n.size.getHeight() - chart.componentGapHeight));
                }
//				StringFormat drawFormat  = new StringFormat();
//				drawFormat.setAlignment(StringAlignment.Center);
//				drawFormat.setLineAlignment(StringAlignment.Center);
//				DrawString(n.sym.name , charFont , charColor , new Rectangle((int)p.x,(int)n.posBegin.y,n.size.getWidth(),n.size.getHeight()-componentGapHeight-2),drawFormat);
                chart.drawString(
                    n.sym.name,
                    chart.charFont,
                    chart.charColor,
                    p.x
                        + chart.symbolGapWidth,
                    n.posBegin.y
                        + (n.size.getHeight() - chart.componentGapHeight)
                        - chart.symbolGapHeight);
                chart.drawArrow(
                    p.x + n.size.getWidth(),
                    n.posLine.y,
                    p.x + n.size.getWidth(),
                    n.posLine.y,
                    Grammar.Direction.LEFT);

                if (!n.up
                    && n.next != null
                    && (n.next.typ == NodeType.TERM
                    || n.next.typ == NodeType.NONTERM))
                {
                    chart.drawArrow(
                        p.x,
                        n.posLine.y,
                        p.x - chart.componentGapWidth / 2,
                        n.posLine.y,
                        Grammar.Direction.LEFT);
                    p.x -= chart.componentGapWidth / 2;
                }
                if (!n.up
                    && n.next != null
                    && n.next.typ == NodeType.WRAP
                    && n.next.size.getHeight() == 0)
                {
                    if (!n.next.up
                        && n.next.next != null
                        && (n.next.next.typ == NodeType.TERM
                        || n.next.next.typ == NodeType.NONTERM))
                    {
                        chart.drawArrow(
                            p.x,
                            n.posLine.y,
                            p.x
                                - chart.componentGapWidth / 2,
                            n.posLine.y,
                            Grammar.Direction.LEFT);
                        p.x -= chart.componentGapWidth / 2;
                    }
                }
            } else if (n.typ == NodeType.EPS) {
                if (chart.showBorders) {
                    chart.drawRectangle(
                        Chart.EPS_COLOR,
                        Chart.STROKE1,
                        p.x,
                        n.posBegin.y,
                        n.size.getWidth(),
                        n.size.getHeight());
                }

                chart.drawLine(
                    p.x,
                    n.posLine.y,
                    p.x + n.size.getWidth(),
                    n.posLine.y);
            } else if (n.typ == NodeType.OPT) {
                if (chart.showBorders) {
                    chart.drawRectangle(
                        Chart.OPT_COLOR,
                        Chart.STROKE1,
                        p.x,
                        n.posBegin.y,
                        n.size.getWidth(),
                        n.size.getHeight());
                }

                // the two short lines at the beginning and the end
                chart.drawLine(
                    p.x,
                    n.posLine.y,
                    p.x + chart.componentGapWidth,
                    n.posLine.y);
                chart.drawLine(
                    p.x + n.size.getWidth(),
                    n.posLine.y,
                    p.x
                        + n.size.getWidth() - chart.componentGapWidth,
                    n.posLine.y);
                // the quarter Arcs
                chart.drawArcCorner(
                    p.x
                        + chart.componentGapWidth / 4
                        - chart.componentArcSize / 2,
                    n.posLine.y,
                    270);
                chart.drawArcCorner(
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize / 2,
                    n.posEnd.y
                        - chart.componentArcSize
                        - chart.componentGapHeight / 2,
                    90);
                chart.drawArcCorner(
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize / 2
                        + n.size.getWidth(),
                    n.posLine.y,
                    180);
                chart.drawArcCorner(
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize * 3 / 2
                        + n.size.getWidth(),
                    n.posEnd.y
                        - chart.componentArcSize
                        - chart.componentGapHeight / 2,
                    0);
                // the short vertical lines between the quarter Arcs
                chart.drawLine(
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize / 2,
                    n.posLine.y
                        + chart.componentArcSize / 2,
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize / 2,
                    n.posEnd.y
                        - chart.componentArcSize / 2
                        - chart.componentGapHeight / 2 + 1);
                chart.drawLine(
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize / 2
                        + n.size.getWidth(),
                    n.posLine.y
                        + chart.componentArcSize / 2,
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize / 2
                        + n.size.getWidth(),
                    n.posEnd.y
                        - chart.componentArcSize / 2
                        - chart.componentGapHeight / 2 + 1);
                // the the long horizontal line between the quarter Arcs
                chart.drawLine(
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize,
                    n.posEnd.y
                        - chart.componentGapHeight / 2,
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize
                        + n.size.getWidth() + 1,
                    n.posEnd.y
                        - chart.componentGapHeight / 2);

                p1.x = p.x + n.size.getWidth() - chart.componentGapWidth;
                n.sub.drawComponentsInverse(chart, p1, n.size);
            } else if (n.typ == NodeType.RERUN && n.itergraph == null) {
                if (chart.showBorders) {
                    chart.drawRectangle(
                        Chart.RERUN_COLOR,
                        Chart.STROKE1,
                        p.x,
                        n.posBegin.y,
                        n.size.getWidth(),
                        n.size.getHeight());
                }

                // the two short lines at the beginning and the end
                chart.drawLine(
                    p.x,
                    n.posLine.y,
                    p.x + chart.componentGapWidth,
                    n.posLine.y);
                chart.drawLine(
                    p.x + n.size.getWidth(),
                    n.posLine.y,
                    p.x
                        + n.size.getWidth() - chart.componentGapWidth,
                    n.posLine.y);
                // the quarter Arcs
                chart.drawArcCorner(
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize / 2,
                    n.posEnd.y
                        - chart.componentGapHeight / 2
                        - chart.componentArcSize,
                    90);
                chart.drawArcCorner(
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize / 2,
                    n.posLine.y,
                    180);
                chart.drawArcCorner(
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize * 3 / 2
                        + n.size.getWidth(),
                    n.posEnd.y
                        - chart.componentGapHeight / 2
                        - chart.componentArcSize,
                    0);
                chart.drawArcCorner(
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize * 3 / 2
                        + n.size.getWidth(),
                    n.posLine.y,
                    270);
                // the short vertical lines between the quarter Arcs
                chart.drawLine(
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize / 2,
                    n.posLine.y
                        + chart.componentArcSize / 2,
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize / 2,
                    n.posEnd.y
                        - chart.componentGapHeight / 2
                        - chart.componentArcSize / 2 + 1);
                chart.drawLine(
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize / 2
                        + n.size.getWidth(),
                    n.posLine.y
                        + chart.componentArcSize / 2,
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize / 2
                        + n.size.getWidth(),
                    n.posEnd.y
                        - chart.componentGapHeight / 2
                        - chart.componentArcSize / 2 + 1);
                // the the long horizontal line between the quarter Arcs
                chart.drawLine(
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize - 1,
                    n.posEnd.y
                        - chart.componentGapHeight / 2,
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize
                        + n.size.getWidth() + 1,
                    n.posEnd.y
                        - chart.componentGapHeight / 2);

                p1.x = p.x + n.size.getWidth() - chart.componentGapWidth;
                n.sub.drawComponentsInverse(chart, p1, n.size);
            } else if (n.typ == NodeType.RERUN && n.itergraph != null) {
                if (chart.showBorders) {
                    chart.drawRectangle(
                        Chart.RERUN1_COLOR,
                        Chart.STROKE1,
                        p.x,
                        n.posBegin.y,
                        n.size.getWidth(),
                        n.size.getHeight());
                }

                // the two short lines at the beginning and the end of the first component
                chart.drawLine(
                    p.x,
                    n.posLine.y,
                    p.x
                        + n.size.getWidth() / 2
                        - n.altSize.getWidth() / 2 - 1,
                    n.posLine.y);
                chart.drawLine(
                    p.x
                        + n.size.getWidth() / 2
                        + n.altSize.getWidth() / 2 + 1,
                    n.posLine.y,
                    p.x
                        + n.size.getWidth(),
                    n.posLine.y);
                // the quarter Arcs
                chart.drawArcCorner(
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize / 2,
                    n.itergraph.posLine.y
                        - chart.componentArcSize,
                    90);
                chart.drawArcCorner(
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize / 2,
                    n.posLine.y,
                    180);
                chart.drawArcCorner(
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize * 3 / 2
                        + n.size.getWidth(),
                    n.itergraph.posLine.y
                        - chart.componentArcSize,
                    0);
                chart.drawArcCorner(
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize * 3 / 2
                        + n.size.getWidth(),
                    n.posLine.y,
                    270);
                // the short vertical lines between the quarter Arcs
                chart.drawLine(
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize / 2,
                    n.posLine.y
                        + chart.componentArcSize / 2,
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize / 2,
                    n.itergraph.posLine.y
                        - chart.componentArcSize / 2 + 1);
                chart.drawLine(
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize / 2
                        + n.size.getWidth(),
                    n.posLine.y
                        + chart.componentArcSize / 2,
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize / 2
                        + n.size.getWidth(),
                    n.itergraph.posLine.y
                        - chart.componentArcSize / 2 + 1);
                // the two short lines at the beginning and the end of the second component
                chart.drawLine(
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize,
                    n.itergraph.posLine.y,
                    p.x
                        + n.size.getWidth() / 2
                        - n.iterSize.getWidth() / 2
                        - 1,
                    n.itergraph.posLine.y);
                chart.drawLine(
                    p.x
                        + n.size.getWidth() / 2
                        + n.iterSize.getWidth() / 2
                        + 1,
                    n.itergraph.posLine.y,
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize
                        + n.size.getWidth() + 1,
                    n.itergraph.posLine.y);

                n.sub.drawComponentsInverse(
                    chart,
                    new Float(
                        p.x
                            + n.size.getWidth() / 2 + n.altSize.getWidth() / 2,
                        n.posEnd.y),
                    n.size);
                n.itergraph.drawComponents(
                    chart,
                    new Float(
                        p.x + n.size.getWidth() / 2
                            - n.iterSize.getWidth() / 2,
                        n.posEnd.y),
                    n.size);
            } else if (n.typ == NodeType.ITER) {
                if (chart.showBorders) {
                    chart.drawRectangle(
                        Chart.ITER_COLOR,
                        Chart.STROKE1,
                        p.x,
                        n.posBegin.y,
                        n.size.getWidth(),
                        n.size.getHeight());
                }

                // the quarter Arcs
                chart.drawArcCorner(
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize / 2,
                    n.sub.posLine.y
                        - chart.componentArcSize,
                    90);
                chart.drawArcCorner(
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize / 2,
                    n.posLine.y,
                    180);
                chart.drawArcCorner(
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize * 3 / 2
                        + n.size.getWidth(),
                    n.sub.posLine.y
                        - chart.componentArcSize,
                    0);
                chart.drawArcCorner(
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize * 3 / 2
                        + n.size.getWidth(),
                    n.posLine.y,
                    270);
                // the short vertical lines between the quarter Arcs
                chart.drawLine(
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize / 2,
                    n.posLine.y
                        + chart.componentArcSize / 2,
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize / 2,
                    n.sub.posLine.y
                        - chart.componentArcSize / 2 + 1);
                chart.drawLine(
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize / 2
                        + n.size.getWidth(),
                    n.posLine.y
                        + chart.componentArcSize / 2,
                    p.x
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize / 2
                        + n.size.getWidth(),
                    n.sub.posLine.y
                        - chart.componentArcSize / 2 + 1);
                // the two short horizontal lines between the quater Arcs and the components
                chart.drawLine(
                    p.x
                        + chart.componentGapWidth / 4
                        + chart.componentArcSize - 1,
                    n.sub.posLine.y,
                    p.x
                        + chart.componentGapWidth,
                    n.sub.posLine.y);
                chart.drawLine(
                    p.x
                        - chart.componentGapWidth
                        + n.size.getWidth(),
                    n.sub.posLine.y,
                    p.x
                        + n.size.getWidth()
                        - chart.componentGapWidth / 4
                        - chart.componentArcSize + 1,
                    n.sub.posLine.y);
                // the long horizontal line in the middle
                chart.drawLine(
                    p.x,
                    n.posLine.y,
                    p.x + n.size.getWidth(),
                    n.posLine.y);

                p1.x = p.x + chart.componentGapWidth;
                n.sub.drawComponents(chart, p1, n.size);
            } else if (n.typ == NodeType.ALT) {
                p.x -= n.altSize.getWidth() - n.size.getWidth();
                if (chart.showBorders) {
                    chart.drawRectangle(
                        Color.RED,
                        Chart.STROKE1,
                        p.x,
                        n.posBegin.y,
                        n.altSize.getWidth(),
                        n.altSize.getHeight());
                }

                // the two short lines at the beginning and the end of the altcomponent
                chart.drawLine(
                    p.x,
                    n.posLine.y,
                    p.x
                        + chart.componentArcSize * 3 / 2,
                    n.posLine.y);
                chart.drawLine(
                    p.x
                        + n.altSize.getWidth(),
                    n.posLine.y,
                    p.x
                        + n.altSize.getWidth()
                        - chart.componentArcSize * 3 / 2,
                    n.posLine.y);
                p1.x = p.x + 2 * chart.componentGapWidth;
                p1.y = p1.y + chart.componentGapHeight;
                Node a = n;
                boolean first = true;
                while (a != null) {
					chart.charColor = a.nodeColor;
					chart.lineColor = a.lineToColor;
					chart.charFont = a.nodeFont;
                    // the horizontal lines at the beginning and the end
                    chart.drawLine(
                        p.x + chart.componentArcSize * 3 / 2,
                        a.sub.posLine.y,
                        p.x
                            + (n.altSize.getWidth() - a.size.getWidth()) / 2,
                        a.sub.posLine.y);
                    chart.drawLine(
                        p.x
                            - chart.componentArcSize * 3 / 2
                            + n.altSize.getWidth()
                            + 1,
                        a.sub.posLine.y,
                        p.x
                            + (n.altSize.getWidth() - a.size.getWidth()) / 2
                            + a.size.getWidth(),
                        a.sub.posLine.y);
                    // if the first Alternative draw differnt Arcs
                    if (first) {
                        chart.drawArcCorner(
                            p.x,
                            n.posLine.y,
                            270);
                        chart.drawArcCorner(
                            p.x
                                + n.altSize.getWidth()
                                - chart.componentArcSize,
                            n.posLine.y,
                            180);
                        first = false;
                    } else {
                        // else draw other Arcs and vertical lines
                        chart.drawArcCorner(
                            p.x + chart.componentArcSize,
                            a.sub.posLine.y
                                - chart.componentArcSize,
                            90);
                        chart.drawLine(
                            p.x + chart.componentArcSize,
                            n.posLine.y
                                + chart.componentArcSize / 2,
                            p.x
                                + chart.componentArcSize,
                            a.posLine.y
                                - chart.componentArcSize / 2 + 1);
                        chart.drawArcCorner(
                            p.x
                                - chart.componentArcSize * 2
                                + n.altSize.getWidth(),
                            a.sub.posLine.y
                            - chart.componentArcSize,
                            0);
                        chart.drawLine(
                            p.x
                                - chart.componentArcSize
                                + n.altSize.getWidth(),
                            n.posLine.y
                                + chart.componentArcSize / 2,
                            p.x
                                - chart.componentArcSize
                                + n.altSize.getWidth(),
                            a.posLine.y
                                - chart.componentArcSize / 2 + 1);
                    }
                    Float pf = new Float(
                        p.x
                            + (n.altSize.getWidth() + a.size.getWidth()) / 2,
                        p1.y);
                    a.sub.drawComponentsInverse(chart, pf, a.size);
                    a = a.down;
                }
            }
            if (n.up) {
                samelevel = false;
            }
            n = n.next;
        }
    }

    public void accept(Chart.NodeVisitor nodeVisitor) {
        nodeVisitor.visit(this);
    }

    public void visitChildren(Chart.NodeVisitor visitor) {
        switch (typ) {
        case TERM:
        case NONTERM:
            break;
        case ALT:
            final List<Node> alts = new ArrayList<Node>();
            for (Node n = this; n != null; n = n.down) {
                n.sub.accept(visitor);
            }
            break;
        case ITER:
            for (Node node : nextChildren(sub)) {
                node.accept(visitor);
            }
            break;
        default:
            throw new RuntimeException("unknown <" + typ + ">");
        }
    }
}

// End Node.java
