/*
// $Id: ProductionNode.java 3 2009-05-11 08:11:57Z jhyde $
// Clapham generates railroad diagrams to represent computer language grammars.
// Copyright (C) 2008-2009 Julian Hyde
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
package net.hydromatic.clapham.parser;

/**
 * TODO:
*
* @author jhyde
* @version $Id: ProductionNode.java 3 2009-05-11 08:11:57Z jhyde $
* @since Jul 30, 2008
*/
public class ProductionNode implements EbnfNode {
    public final IdentifierNode id;
    public final EbnfNode expression;

    public ProductionNode(
        IdentifierNode id,
        EbnfNode expression)
    {
        this.id = id;
        this.expression = expression;
    }

    public void toString(StringBuilder buf) {
        id.toString(buf);
        buf.append(" = ");
        expression.toString(buf);
    }
}

// End ProductionNode.java
