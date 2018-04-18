/*
// $Id: Clapham.java 3 2009-05-11 08:11:57Z jhyde $
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
package net.hydromatic.clapham;

import net.hydromatic.clapham.parser.bnf.BnfParser;
import net.hydromatic.clapham.parser.bnf.ParseException;
import net.hydromatic.clapham.parser.*;
import net.hydromatic.clapham.parser.wirth.WirthParser;
import net.hydromatic.clapham.graph.*;

import javax.xml.parsers.*;
import java.io.*;
import java.util.*;

import org.apache.batik.transcoder.*;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.w3c.dom.Document;

/**
 * Command line utility Clapham, the railroad diagram generator.
 *
 * @author jhyde
 * @version $Id: Clapham.java 3 2009-05-11 08:11:57Z jhyde $
 * @since Sep 11, 2008
 */
public class Clapham {
    

    /**
     * Makes a name distinct from other names which have already been used
     * and shorter than a length limit, adds it to the list, and returns it.
     *
     * @param name Suggested name, may not be unique
     * @param maxLength Maximum length of generated name
     * @param nameList Collection of names already used
     *
     * @return Unique name
     */
    private static String uniquify(
        String name,
        int maxLength,
        Collection<String> nameList)
    {
        assert name != null;
        if (name.length() > maxLength) {
            name = name.substring(0, maxLength);
        }
        if (nameList.contains(name)) {
            String aliasBase = name;
            int j = 0;
            while (true) {
                name = aliasBase + j;
                if (name.length() > maxLength) {
                    aliasBase = aliasBase.substring(0, aliasBase.length() - 1);
                    continue;
                }
                if (!nameList.contains(name)) {
                    break;
                }
                j++;
            }
        }
        nameList.add(name);
        return name;
    }
	
    public static Grammar buildGrammar(
        List<ProductionNode> productionNodes)
    {
        Grammar grammar = new Grammar();
        for (ProductionNode productionNode : productionNodes) {
            Symbol symbol = new Symbol(NodeType.NONTERM, productionNode.id.s);
            grammar.nonterminals.add(symbol);
            grammar.symbolMap.put(symbol.name, symbol);
            Graph g = toGraph(grammar, productionNode.expression);
            symbol.graph = g;
            grammar.ruleMap.put(symbol, g);
        }
        return grammar;
    }

    public static Graph toGraph(
        Grammar grammar,
        EbnfNode expression)
    {
        if (expression instanceof OptionNode) {
            OptionNode optionNode = (OptionNode) expression;
            final Graph g = toGraph(grammar, optionNode.n);
            grammar.makeOption(g);
            return g;
        } else if (expression instanceof RepeatNode) {
            RepeatNode repeatNode = (RepeatNode) expression;
            final Graph g = toGraph(grammar, repeatNode.node);
            grammar.makeIteration(g);
            return g;
        } else if (expression instanceof MandatoryRepeatNode) {
            MandatoryRepeatNode repeatNode = (MandatoryRepeatNode) expression;
            final Graph g = toGraph(grammar, repeatNode.node);
            grammar.makeIteration(g); // TODO: make mandatory
            return g;
        } else if (expression instanceof AlternateNode) {
            AlternateNode alternateNode = (AlternateNode) expression;
            Graph g = null;
            for (EbnfNode node : alternateNode.list) {
                if (g == null) {
                    g = toGraph(grammar, node);
                    grammar.makeFirstAlt(g);
                } else {
                    Graph g2 = toGraph(grammar, node);
                    grammar.makeAlternative(g, g2);
                }
            }
            return g;
        } else if (expression instanceof SequenceNode) {
            SequenceNode sequenceNode = (SequenceNode) expression;
            Graph g = null;
            for (EbnfNode node : sequenceNode.list) {
                if (g == null) {
                    g = toGraph(grammar, node);
                } else {
                    Graph g2 = toGraph(grammar, node);
                    grammar.makeSequence(g, g2);
                }
            }
            return g;
        } else if (expression instanceof EmptyNode) {
            Graph g = new Graph();
            grammar.makeEpsilon(g);
            return g;
        } else if (expression instanceof IdentifierNode) {
            IdentifierNode identifierNode = (IdentifierNode) expression;
            Symbol symbol = new Symbol(NodeType.NONTERM, identifierNode.s);
//            grammar.symbolMap.put(symbol.name, symbol);
            return new Graph(new Node(grammar, symbol, identifierNode.lineToColor, identifierNode.nodeColor, identifierNode.nodeFont));
        } else if (expression instanceof LiteralNode) {
            LiteralNode literalNode = (LiteralNode) expression;
            Symbol symbol = new Symbol(NodeType.TERM, literalNode.s);
            grammar.terminals.add(symbol);
//            grammar.symbolMap.put(symbol.name, symbol);
            return new Graph(new Node(grammar, symbol, literalNode.lineToColor, literalNode.nodeColor, literalNode.nodeFont));
        } else {
            throw new UnsupportedOperationException(
                "unknown node type " + expression);
        }
    }

    public static void toPng(File inFile, File file)
        throws IOException, TranscoderException
    {
        // Create a PNG transcoder
        PNGTranscoder t = new PNGTranscoder();

        // Create the transcoder input.
        TranscoderInput input = new TranscoderInput("file:" + inFile.getPath());

        // Create the transcoder output.
        OutputStream ostream = new FileOutputStream(file);
        TranscoderOutput output = new TranscoderOutput(ostream);

        // Save the image.
        t.transcode(input, output);

        // Flush and close the stream.
        ostream.flush();
        ostream.close();
    }

    private enum Dialect {
        WIRTH,
        BNF
    }

    /**
     * Output format for graphics.
     */
    public static enum ImageFormat {
        SVG,
        PNG,
    }

    private static class Pair<L, R> {
        L left;
        R right;

        Pair(L left, R right) {
            this.left = left;
            this.right = right;
        }

        public int hashCode() {
            return (left == null ? 0 : left.hashCode()) << 4
                ^ (right == null ? 1 : right.hashCode());
        }

        public boolean equals(Object obj) {
            return obj instanceof Pair
                && eq(left, ((Pair) obj).left)
                && eq(right, ((Pair) obj).right);
        }

        private static boolean eq(Object o, Object o2) {
            return o == null ? o2 == null : o.equals(o2);
        }
    }
}

// End Clapham.java
