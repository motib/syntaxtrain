/*
// $Id: NodeType.java 3 2009-05-11 08:11:57Z jhyde $
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
package net.hydromatic.clapham.graph;

/**
 * Constants for node kinds.
 *
 * @author jhyde
 * @version $Id: NodeType.java 3 2009-05-11 08:11:57Z jhyde $
 * @since Aug 26, 2008
*/
public enum NodeType {
    TERM,  // terminal symbol
    NONTERM,  // nonterminal symbol
    EPS,  // empty
    ALT,  // alternative: |
    ITER,  // iteration: { }
    OPT,  // option: [ ]
    RERUN,  // the optimization of: a {a} or a {b a}
    WRAP  // forces line break if found in the outer structure
}

// End NodeType.java
