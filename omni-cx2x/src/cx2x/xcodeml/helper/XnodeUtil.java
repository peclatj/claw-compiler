/*
 * This file is released under terms of BSD license
 * See LICENSE file for more information
 */

package cx2x.xcodeml.helper;

import cx2x.translator.common.ClawConstant;
import cx2x.xcodeml.exception.IllegalTransformationException;
import cx2x.xcodeml.xnode.*;
import exc.xcodeml.XcodeMLtools_Fmod;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The class XnodeUtil contains only static method to help manipulating the
 * raw Elements in the XcodeML representation by using the abstracted Xnode.
 *
 * @author clementval
 */

public class XnodeUtil {

  public static final String XMOD_FILE_EXTENSION = ".xmod";

  /**
   * Find all array references elements in a given body and give var name.
   *
   * @param parent    The body element to search for the array references.
   * @param arrayName Name of the array for the array reference to be found.
   * @return A list of all array references found.
   */
  public static List<Xnode> getAllArrayReferences(Xnode parent,
                                                  String arrayName)
  {
    List<Xnode> references = new ArrayList<>();
    NodeList nList = parent.element().
        getElementsByTagName(Xname.F_ARRAY_REF);
    for(int i = 0; i < nList.getLength(); i++) {
      Node n = nList.item(i);
      if(n.getNodeType() == Node.ELEMENT_NODE) {
        Xnode ref = new Xnode((Element) n);
        Xnode var = ref.matchSeq(Xcode.VARREF, Xcode.VAR);
        if(var != null && var.value().equals(arrayName.toLowerCase())) {
          references.add(ref);
        }
      }
    }
    return references;
  }

  /**
   * Find all var references elements in a given body and give var name.
   *
   * @param parent  The body element to search for the array references.
   * @param varName Name of the var for the reference to be found.
   * @return A list of all references found.
   */
  public static List<Xnode> getAllVarReferences(Xnode parent, String varName) {
    List<Xnode> references = new ArrayList<>();
    NodeList nList = parent.element().getElementsByTagName(Xname.VAR);
    for(int i = 0; i < nList.getLength(); i++) {
      Node n = nList.item(i);
      if(n.getNodeType() == Node.ELEMENT_NODE) {
        Xnode var = new Xnode((Element) n);
        if(var.value().equals(varName.toLowerCase())) {
          references.add(var);
        }
      }
    }
    return references;
  }

  /**
   * Demote an array reference to a var reference.
   *
   * @param ref The array reference to be modified.
   */
  public static void demoteToScalar(Xnode ref) {
    Xnode var = ref.matchSeq(Xcode.VARREF, Xcode.VAR).cloneNode();
    ref.insertAfter(var);
    ref.delete();
  }

  /**
   * Demote an array reference to a reference with fewer dimensions.
   *
   * @param ref            The array reference to be modified.
   * @param keptDimensions List of dimensions to be kept. Dimension index starts
   *                       at 1.
   */
  public static void demote(Xnode ref, List<Integer> keptDimensions) {
    for(int i = 1; i < ref.children().size(); ++i) {
      if(!keptDimensions.contains(i)) {
        ref.child(i).delete();
      }
    }
  }

  /**
   * Retrieve the index ranges of an array notation.
   *
   * @param arrayRef The array reference statements to extract the ranges from.
   * @return A list if indexRanges elements.
   */
  public static List<Xnode> getIdxRangesFromArrayRef(Xnode arrayRef) {
    List<Xnode> ranges = new ArrayList<>();
    if(arrayRef.opcode() != Xcode.FARRAYREF) {
      return ranges;
    }
    for(Xnode el : arrayRef.children()) {
      if(el.opcode() == Xcode.INDEXRANGE) {
        ranges.add(el);
      }
    }
    return ranges;
  }


  /**
   * Compare two list of indexRange.
   *
   * @param list1 First list of indexRange.
   * @param list2 Second list of indexRange.
   * @return True if the indexRange at the same position in the two list are all
   * identical. False otherwise.
   */
  public static boolean compareIndexRanges(List<Xnode> list1,
                                           List<Xnode> list2)
  {
    if(list1.size() != list2.size()) {
      return false;
    }

    for(int i = 0; i < list1.size(); ++i) {
      if(!isIndexRangeIdentical(list1.get(i), list2.get(i), true)) {
        return false;
      }
    }
    return true;
  }

  /**
   * <pre>
   * Intersect two sets of elements in XPath 1.0
   *
   * This method use Xpath to select the correct nodes. Xpath 1.0 does not have
   * the intersect operator but only union. By using the Kaysian Method, we can
   * it is possible to express the intersection of two node sets.
   *
   *       $set1[count(.|$set2)=count($set2)]
   *
   * </pre>
   *
   * @param s1 First set of element.
   * @param s2 Second set of element.
   * @return Xpath query that performs the intersect operator between s1 and s2.
   */
  private static String xPathIntersect(String s1, String s2) {
    return String.format("%s[count(.|%s)=count(%s)]", s1, s2, s2);
  }

  /**
   * <pre>
   * Find all assignment statement from a node until the end pragma.
   *
   * We intersect all assign statements which are next siblings of
   * the "from" element with all the assign statements which are previous
   * siblings of the ending pragma.
   * </pre>
   *
   * @param from      The element from which the search is initiated.
   * @param endPragma Value of the end pragma. Search will be performed until
   *                  there.
   * @return A list of all assign statements found. List is empty if no
   * statements are found.
   */
  public static List<Xnode> getArrayAssignInBlock(Xnode from, String endPragma)
  {
    /* Define all the assign element with array refs which are next siblings of
     * the "from" element */
    String s1 = String.format(
        "following-sibling::%s[%s]",
        Xname.F_ASSIGN_STATEMENT,
        Xname.F_ARRAY_REF
    );
    /* Define all the assign element with array refs which are previous siblings
     * of the end pragma element */
    String s2 = String.format(
        "following-sibling::%s[text()=\"%s\"]/preceding-sibling::%s[%s]",
        Xname.F_PRAGMA_STMT,
        endPragma,
        Xname.F_ASSIGN_STATEMENT,
        Xname.F_ARRAY_REF
    );
    // Use the Kaysian method to express the intersect operator
    String intersect = XnodeUtil.xPathIntersect(s1, s2);
    return getFromXpath(from, intersect);
  }

  /**
   * Get all array references in the siblings of the given element.
   *
   * @param from       Element to start from.
   * @param identifier Array name value.
   * @return List of all array references found. List is empty if nothing is
   * found.
   */
  public static List<Xnode> getAllArrayReferencesInSiblings(Xnode from,
                                                            String identifier)
  {
    String s1 = String.format("following-sibling::*//%s[%s[%s[text()=\"%s\"]]]",
        Xname.F_ARRAY_REF,
        Xname.VAR_REF,
        Xname.VAR,
        identifier
    );
    return getFromXpath(from, s1);
  }

  /**
   * Get the first assignment statement for an array reference.
   *
   * @param from      Statement to look from.
   * @param arrayName Identifier of the array.
   * @return The assignment statement if found. Null otherwise.
   */
  public static Xnode getFirstArrayAssign(Xnode from, String arrayName) {
    String s1 = String.format(
        "following::%s[%s[%s[%s[text()=\"%s\"]] and position()=1]]",
        Xname.F_ASSIGN_STATEMENT,
        Xname.F_ARRAY_REF,
        Xname.VAR_REF,
        Xname.VAR,
        arrayName
    );

    try {
      NodeList output = evaluateXpath(from.element(), s1);
      if(output.getLength() == 0) {
        return null;
      }
      Element assign = (Element) output.item(0);
      return new Xnode(assign);
    } catch(XPathExpressionException ignored) {
      return null;
    }
  }

  /**
   * Find all the nested do statement groups following the inductions iterations
   * define in inductionVars and being located between the "from" element and
   * the end pragma.
   *
   * @param from          Element from which the search is started.
   * @param endPragma     End pragma that terminates the search block.
   * @param inductionVars Induction variables that define the nested group to
   *                      locates.
   * @return List of do statements elements (outer most loops for each nested
   * group) found between the "from" element and the end pragma.
   */
  public static List<Xnode> findDoStatement(Xnode from, Xnode endPragma,
                                            List<String> inductionVars)
  {

    /* s1 is selecting all the nested do statement groups that meet the criteria
     * from the "from" element down to the end of the block. */
    String s1 = "following::";

    String dynamic_part_s1 = "";
    for(int i = inductionVars.size() - 1; i >= 0; --i) {
      /*
       * Here is example of there xpath query format for 1,2 and 3 nested loops
       *
       * FdoStatement[Var[text()="induction_var"]]
       *
       * FdoStatement[Var[text()="induction_var"] and
       * body[FdoStatement[Var[text()="induction_var"]]]
       *
       * FdoStatement[Var[text()="induction_var"] and
       * body[FdoStatement[Var[text()="induction_var"] and
       * body[FdoStatement[Var[text()="induction_var"]]]]
       */

      String tempQuery;
      if(i == inductionVars.size() - 1) { // first iteration
        tempQuery = String.format("%s[%s[text()=\"%s\"]]",
            Xname.F_DO_STATEMENT,
            Xname.VAR,
            inductionVars.get(i));
      } else {
        tempQuery = String.format("%s[%s[text()=\"%s\"] and %s[%s]]",
            Xname.F_DO_STATEMENT,
            Xname.VAR,
            inductionVars.get(i),
            Xname.BODY,
            dynamic_part_s1); // Including previously formed xpath query
      }
      dynamic_part_s1 = tempQuery;
    }
    s1 = s1 + dynamic_part_s1;
    List<Xnode> doStatements = new ArrayList<>();
    try {
      NodeList output = evaluateXpath(from.element(), s1);
      for(int i = 0; i < output.getLength(); i++) {
        Element el = (Element) output.item(i);
        Xnode doStmt = new Xnode(el);
        if(doStmt.lineNo() != 0 &&
            doStmt.lineNo() < endPragma.lineNo())
        {
          doStatements.add(doStmt);
        }
      }
    } catch(XPathExpressionException ignored) {
    }
    return doStatements;
  }

  /**
   * Evaluates an Xpath expression and return its result as a NodeList.
   *
   * @param from  Element to start the evaluation.
   * @param xpath Xpath expression.
   * @return Result of evaluation as a NodeList.
   * @throws XPathExpressionException if evaluation fails.
   */
  private static NodeList evaluateXpath(Element from, String xpath)
      throws XPathExpressionException
  {
    XPathExpression ex = XPathFactory.newInstance().newXPath().compile(xpath);
    return (NodeList) ex.evaluate(from, XPathConstants.NODESET);
  }

  /**
   * Find all array references in the next children that match the given
   * criteria.
   * <p>
   * This methods use powerful Xpath expression to locate the correct nodes in
   * the AST
   * <p>
   * Here is an example of such a query that return all node that are array
   * references for the array "array6" with an offset of 0 -1
   * <p>
   * //FarrayRef[varRef[Var[text()="array6"]] and arrayIndex and
   * arrayIndex[minusExpr[Var and FintConstant[text()="1"]]]]
   *
   * @param from       The element from which the search is initiated.
   * @param identifier Identifier of the array.
   * @param offsets    List of offsets to be search for.
   * @return A list of all array references found.
   */
  public static List<Xnode> getAllArrayReferencesByOffsets(Xnode from,
                                                           String identifier,
                                                           List<Integer> offsets)
  {
    StringBuilder offsetXpath = new StringBuilder();
    for(int i = 0; i < offsets.size(); ++i) {
      if(offsets.get(i) == 0) {
        offsetXpath.append(String.format("%s[position()=%s and %s]",
            Xname.ARRAY_INDEX,
            i + 1,
            Xname.VAR
        ));
      } else if(offsets.get(i) > 0) {
        offsetXpath.append(String.format("%s[position()=%s and %s[%s and %s[text()=\"%s\"]]]",
            Xname.ARRAY_INDEX,
            i + 1,
            Xname.MINUS_EXPR,
            Xname.VAR,
            Xname.F_INT_CONST,
            offsets.get(i)));
      } else {
        offsetXpath.append(String.format("%s[position()=%s and %s[%s and %s[text()=\"%s\"]]]",
            Xname.ARRAY_INDEX,
            i + 1,
            Xname.MINUS_EXPR,
            Xname.VAR,
            Xname.F_INT_CONST,
            Math.abs(offsets.get(i))));
      }
      if(i != offsets.size() - 1) {
        offsetXpath.append(" and ");
      }
    }

    // Start of the Xpath query
    String xpathQuery = String.format(".//%s[%s[%s[text()=\"%s\"]] and %s]",
        Xname.F_ARRAY_REF,
        Xname.VAR_REF,
        Xname.VAR,
        identifier,
        offsetXpath.toString()
    );

    return getFromXpath(from, xpathQuery);
  }

  /**
   * Find a pragma element in the previous nodes containing a given keyword.
   *
   * @param from    Element to start from.
   * @param keyword Keyword to be found in the pragma.
   * @return The pragma if found. Null otherwise.
   */
  public static Xnode findPreviousPragma(Xnode from, String keyword) {
    if(from == null || from.element() == null) {
      return null;
    }
    Node prev = from.element().getPreviousSibling();
    Node parent = from.element();
    do {
      while(prev != null) {
        if(prev.getNodeType() == Node.ELEMENT_NODE) {
          Element element = (Element) prev;
          if(element.getTagName().equals(Xcode.FPRAGMASTATEMENT.code())
              && element.getTextContent().toLowerCase().
              contains(keyword.toLowerCase()))
          {
            return new Xnode(element);
          }
        }
        prev = prev.getPreviousSibling();
      }
      parent = parent.getParentNode();
      prev = parent;
    } while(parent != null);
    return null;
  }

  /**
   * Find all the index elements (arrayIndex and indexRange) in an element.
   *
   * @param parent Root element to search from.
   * @return A list of all index ranges found.
   */
  public static List<Xnode> findIndexes(Xnode parent) {
    List<Xnode> indexRanges = new ArrayList<>();
    if(parent == null || parent.element() == null) {
      return indexRanges;
    }

    Node node = parent.element().getFirstChild();
    while(node != null) {
      if(node.getNodeType() == Node.ELEMENT_NODE) {
        Element element = (Element) node;
        switch(element.getTagName()) {
          case Xname.ARRAY_INDEX:
          case Xname.INDEX_RANGE:
            indexRanges.add(new Xnode(element));
            break;
        }
      }
      node = node.getNextSibling();
    }

    return indexRanges;
  }

  /**
   * Find all the var elements that are real references to a variable. Var
   * element nested in an arrayIndex element are excluded.
   *
   * @param parent Root element to search from.
   * @return A list of all var elements found.
   */
  public static List<Xnode> findAllReferences(Xnode parent) {
    List<Xnode> vars = parent.matchAll(Xcode.VAR);
    List<Xnode> realReferences = new ArrayList<>();
    for(Xnode var : vars) {
      if(!((Element) var.element().getParentNode()).getTagName().
          equals(Xcode.ARRAYINDEX.code()))
      {
        realReferences.add(var);
      }
    }
    return realReferences;
  }

  /**
   * Get all the variables names from a list of var elements.
   *
   * @param nodes List containing var element.
   * @return A set of all variable's name.
   */
  public static Set<String> getNamesFromReferences(List<Xnode> nodes) {
    Set<String> names = new HashSet<>();
    for(Xnode node : nodes) {
      if(node.opcode() != Xcode.VAR) {
        continue;
      }
      names.add(node.value());
    }
    return names;
  }

  /**
   * Find all the var elements that are real references to a variable. Var
   * element nested in an arrayIndex element are excluded.
   *
   * @param parent Root element to search from.
   * @param id     Identifier of the var to be found.
   * @return A list of all var elements found.
   */
  public static List<Xnode> findAllReferences(Xnode parent, String id) {
    List<Xnode> vars = parent.matchAll(Xcode.VAR);
    List<Xnode> realReferences = new ArrayList<>();
    for(Xnode var : vars) {
      if(!((Element) var.element().getParentNode()).getTagName().
          equals(Xcode.ARRAYINDEX.code())
          && var.value().equals(id.toLowerCase()))
      {
        realReferences.add(var);
      }
    }
    return realReferences;
  }

  /**
   * Extract the body of a do statement and place it after the reference node.
   *
   * @param loop The do statement containing the body to be extracted.
   * @param ref  Element after which statement are shifted.
   */
  public static void extractBody(Xnode loop, Xnode ref) {
    if(loop.opcode() != Xcode.FDOSTATEMENT) {
      return;
    }
    Xnode body = loop.body();
    if(body == null) {
      return;
    }
    Xnode refNode = ref;
    for(Xnode child : body.children()) {
      refNode.insertAfter(child);
      refNode = child;
    }
  }

  /**
   * Extract the body of a do statement and place it directly after it.
   *
   * @param loop The do statement containing the body to be extracted.
   */
  public static void extractBody(Xnode loop) {
    extractBody(loop, loop);
  }

  /**
   * Extract the body of the inner do statement and place it directly after the
   * outer do statement.
   *
   * @param nest Nest do statement group.
   */
  public static void extractBody(NestedDoStatement nest) {
    extractBody(nest.getInnerStatement(), nest.getOuterStatement());
  }

  /**
   * Check whether the index of an arrayIndex element is an induction variable
   * given in the list.
   *
   * @param arrayIndex         arrayIndex to be checked.
   * @param inductionVariables List of potential induction variables.
   * @return True if the arrayIndex uses a var from induction variables list.
   * False in any other cases.
   */
  public static boolean isInductionIndex(Xnode arrayIndex,
                                         List<String> inductionVariables)
  {
    if(arrayIndex == null || arrayIndex.opcode() != Xcode.ARRAYINDEX
        || inductionVariables == null || inductionVariables.isEmpty())
    {
      return false;
    }

    Xnode var = arrayIndex.matchDirectDescendant(Xcode.VAR);
    return var != null && inductionVariables.contains(var.value());
  }

  /**
   * Shift all statements from the first siblings of the "from" element until
   * the "until" element (not included).
   *
   * @param from       Start element for the swifting.
   * @param until      End element for the swifting.
   * @param targetBody Body element in which statements are inserted.
   */
  public static void shiftStatementsInBody(Xnode from, Xnode until,
                                           Xnode targetBody, boolean included)
  {
    Node currentSibling = from.element();
    if(!included) {
      currentSibling = from.element().getNextSibling();
    }

    Node firstStatementInBody = targetBody.element().getFirstChild();
    while(currentSibling != null && currentSibling != until.element()) {
      Node nextSibling = currentSibling.getNextSibling();
      targetBody.element().insertBefore(currentSibling,
          firstStatementInBody);
      currentSibling = nextSibling;
    }
    if(included && currentSibling == until.element()) {
      targetBody.element().insertBefore(currentSibling,
          firstStatementInBody);
    }
  }

  /**
   * Copy the whole body element into the destination one. Destination is
   * overwritten.
   *
   * @param from The body to be copied.
   * @param to   The destination of the copied body.
   */
  public static void copyBody(Xnode from, Xnode to) {
    Node copiedBody = from.cloneRawNode();
    if(to.body() != null) {
      to.body().delete();
    }
    to.element().appendChild(copiedBody);
  }

  /**
   * Get given statements in the subtree.
   *
   * @param root       Root of the subtree.
   * @param statements List of statements to look for.
   * @return List of statement found.
   */
  public static List<Xnode> getStatements(Xnode root, List<Xcode> statements) {
    List<Xnode> unsupportedStatements = new ArrayList<>();
    if(root == null) {
      return unsupportedStatements;
    }
    for(Xcode opcode : statements) {
      unsupportedStatements.addAll(root.matchAll(opcode));
    }
    return unsupportedStatements;
  }

  /**
   * Get given statements in between from and to included.
   *
   * @param from       Node from.
   * @param to         Node to.
   * @param statements List of statements to look for.
   * @return List of statement found.
   */
  public static List<Xnode> getStatements(Xnode from, Xnode to,
                                          List<Xcode> statements)
  {
    List<Xnode> unsupportedStatements = new ArrayList<>();
    Xnode crt = from;
    while(crt != null && crt.element() != to.element()) {
      if(statements.contains(crt.opcode())) {
        unsupportedStatements.add(crt);
      }
      unsupportedStatements.addAll(getStatements(crt, statements));
      crt = crt.nextSibling();
    }
    return unsupportedStatements;
  }

  /* XNODE SECTION */

  /**
   * Delete all the elements between the two given elements.
   *
   * @param start The start element. Deletion start from next element.
   * @param end   The end element. Deletion end just before this element.
   */
  public static void deleteBetween(Xnode start, Xnode end) {
    List<Xnode> toDelete = new ArrayList<>();
    Xnode node = start.nextSibling();
    while(node != null && node.element() != end.element()) {
      toDelete.add(node);
      node = node.nextSibling();
    }

    for(Xnode n : toDelete) {
      n.delete();
    }
  }

  /**
   * Insert all the statements from a given body at the end of another body
   *
   * @param originalBody The body in which the extra body will be appended
   * @param extraBody    The body that will be appended to the original body
   * @throws IllegalTransformationException if one of the body or their base
   *                                        element is null.
   */
  public static void appendBody(Xnode originalBody, Xnode extraBody)
      throws IllegalTransformationException
  {
    if(originalBody == null || originalBody.element() == null
        || extraBody == null || extraBody.element() == null
        || originalBody.opcode() != Xcode.BODY
        || extraBody.opcode() != Xcode.BODY)
    {
      throw new IllegalTransformationException("One of the body is null.");
    }

    // Append content of loop-body (loop) to this loop-body
    Node childNode = extraBody.element().getFirstChild();
    while(childNode != null) {
      Node nextChild = childNode.getNextSibling();
      // Do something with childNode, including move or delete...
      if(childNode.getNodeType() == Node.ELEMENT_NODE) {
        originalBody.element().appendChild(childNode);
      }
      childNode = nextChild;
    }
  }

  /**
   * Compare the iteration range of two do statements.
   *
   * @param e1             First do statement.
   * @param e2             Second do statement.
   * @param withLowerBound Compare lower bound or not.
   * @return True if the iteration range are identical.
   */
  private static boolean compareIndexRanges(Xnode e1, Xnode e2,
                                            boolean withLowerBound)
  {
    // The two nodes must be do statement
    if(e1.opcode() != Xcode.FDOSTATEMENT || e2.opcode() != Xcode.FDOSTATEMENT) {
      return false;
    }

    Xnode inductionVar1 = e1.matchDirectDescendant(Xcode.VAR);
    Xnode inductionVar2 = e2.matchDirectDescendant(Xcode.VAR);
    Xnode indexRange1 = e1.matchDirectDescendant(Xcode.INDEXRANGE);
    Xnode indexRange2 = e2.matchDirectDescendant(Xcode.INDEXRANGE);

    return compareValues(inductionVar1, inductionVar2) &&
        isIndexRangeIdentical(indexRange1, indexRange2, withLowerBound);
  }

  /**
   * Compare the iteration range of two do statements.
   *
   * @param e1 First do statement.
   * @param e2 Second do statement.
   * @return True if the iteration range are identical.
   */
  public static boolean hasSameIndexRange(Xnode e1, Xnode e2) {
    return compareIndexRanges(e1, e2, true);
  }

  /**
   * Compare the iteration range of two do statements.
   *
   * @param e1 First do statement.
   * @param e2 Second do statement.
   * @return True if the iteration range are identical besides the lower bound.
   */
  public static boolean hasSameIndexRangeBesidesLower(Xnode e1, Xnode e2) {
    return compareIndexRanges(e1, e2, false);
  }

  /**
   * Compare the inner values of two nodes.
   *
   * @param n1 First node.
   * @param n2 Second node.
   * @return True if the values are identical. False otherwise.
   */
  private static boolean compareValues(Xnode n1, Xnode n2) {
    return !(n1 == null || n2 == null) && n1.value().equals(n2.value());
  }

  /**
   * Compare the inner value of the first child of two nodes.
   *
   * @param n1 First node.
   * @param n2 Second node.
   * @return True if the value are identical. False otherwise.
   */
  private static boolean compareFirstChildValues(Xnode n1, Xnode n2) {
    if(n1 == null || n2 == null) {
      return false;
    }
    Xnode c1 = n1.child(0);
    Xnode c2 = n2.child(0);
    return compareValues(c1, c2);
  }

  /**
   * Compare the inner values of two optional nodes.
   *
   * @param n1 First node.
   * @param n2 Second node.
   * @return True if the values are identical or elements are null. False
   * otherwise.
   */
  private static boolean compareOptionalValues(Xnode n1, Xnode n2) {
    return n1 == null && n2 == null || (n1 != null && n2 != null &&
        n1.value().toLowerCase().equals(n2.value()));
  }

  /**
   * Compare the iteration range of two do statements
   *
   * @param idx1           First do statement.
   * @param idx2           Second do statement.
   * @param withLowerBound If true, compare lower bound. If false, lower bound
   *                       is not compared.
   * @return True if the index range are identical.
   */
  private static boolean isIndexRangeIdentical(Xnode idx1, Xnode idx2,
                                               boolean withLowerBound)
  {
    if(idx1.opcode() != Xcode.INDEXRANGE || idx2.opcode() != Xcode.INDEXRANGE) {
      return false;
    }

    if(idx1.getBooleanAttribute(Xattr.IS_ASSUMED_SHAPE) &&
        idx2.getBooleanAttribute(Xattr.IS_ASSUMED_SHAPE))
    {
      return true;
    }

    Xnode low1 = idx1.matchSeq(Xcode.LOWERBOUND);
    Xnode up1 = idx1.matchSeq(Xcode.UPPERBOUND);
    Xnode low2 = idx2.matchSeq(Xcode.LOWERBOUND);
    Xnode up2 = idx2.matchSeq(Xcode.UPPERBOUND);
    Xnode s1 = idx1.matchSeq(Xcode.STEP);
    Xnode s2 = idx2.matchSeq(Xcode.STEP);

    if(s1 != null) {
      s1 = s1.child(0);
    }
    if(s2 != null) {
      s2 = s2.child(0);
    }

    if(withLowerBound) {
      return compareFirstChildValues(low1, low2) &&
          compareFirstChildValues(up1, up2) && compareOptionalValues(s1, s2);
    } else {
      return compareFirstChildValues(up1, up2) && compareOptionalValues(s1, s2);
    }
  }

  /**
   * Swap the iteration range information of two do statement.
   *
   * @param e1 First do statement.
   * @param e2 Second do statement.
   * @throws IllegalTransformationException if necessary elements are missing
   *                                        to apply the transformation.
   */
  public static void swapIterationRange(Xnode e1, Xnode e2)
      throws IllegalTransformationException
  {
    // The two nodes must be do statement
    if(e1.opcode() != Xcode.FDOSTATEMENT || e2.opcode() != Xcode.FDOSTATEMENT) {
      return;
    }

    Xnode inductionVar1 = e1.matchDirectDescendant(Xcode.VAR);
    Xnode inductionVar2 = e2.matchDirectDescendant(Xcode.VAR);
    Xnode indexRange1 = e1.matchDirectDescendant(Xcode.INDEXRANGE);
    Xnode indexRange2 = e2.matchDirectDescendant(Xcode.INDEXRANGE);
    if(inductionVar1 == null || inductionVar2 == null ||
        indexRange1 == null || indexRange2 == null)
    {
      throw new IllegalTransformationException("Induction variable or index " +
          "range missing.");
    }

    Xnode low1 = indexRange1.matchSeq(Xcode.LOWERBOUND).child(0);
    Xnode up1 = indexRange1.matchSeq(Xcode.UPPERBOUND).child(0);
    Xnode s1 = indexRange1.matchSeq(Xcode.STEP).child(0);

    Xnode low2 = indexRange2.matchSeq(Xcode.LOWERBOUND).child(0);
    Xnode up2 = indexRange2.matchSeq(Xcode.UPPERBOUND).child(0);
    Xnode s2 = indexRange2.matchSeq(Xcode.STEP).child(0);

    // Set the range of loop2 to loop1
    inductionVar2.insertAfter(inductionVar1.cloneNode());
    low2.insertAfter(low1.cloneNode());
    up2.insertAfter(up1.cloneNode());
    s2.insertAfter(s1.cloneNode());

    inductionVar1.insertAfter(inductionVar2.cloneNode());
    low1.insertAfter(low2.cloneNode());
    up1.insertAfter(up2.cloneNode());
    s1.insertAfter(s2.cloneNode());

    inductionVar1.delete();
    inductionVar2.delete();
    low1.delete();
    up1.delete();
    s1.delete();
    low2.delete();
    up2.delete();
    s2.delete();
  }

  /**
   * Get a list of T elements from an xpath query executed from the
   * given element.
   *
   * @param from  Element to start from.
   * @param query XPath query to be executed.
   * @return List of all array references found. List is empty if nothing is
   * found.
   */
  private static List<Xnode> getFromXpath(Xnode from, String query)
  {
    List<Xnode> elements = new ArrayList<>();
    try {
      XPathExpression ex = XPathFactory.newInstance().newXPath().compile(query);
      NodeList output = (NodeList) ex.evaluate(from.element(),
          XPathConstants.NODESET);
      for(int i = 0; i < output.getLength(); i++) {
        Element element = (Element) output.item(i);
        elements.add(new Xnode(element));
      }
    } catch(XPathExpressionException ignored) {
    }
    return elements;
  }

  /**
   * Find specific argument in a function call.
   *
   * @param value   Value of the argument to be found.
   * @param fctCall Function call to search from.
   * @return The argument if found. Null otherwise.
   */
  public static Xnode findArg(String value, Xnode fctCall) {
    if(fctCall.opcode() != Xcode.FUNCTIONCALL) {
      return null;
    }
    Xnode args = fctCall.matchSeq(Xcode.ARGUMENTS);
    if(args == null) {
      return null;
    }
    for(Xnode arg : args.children()) {
      if(value.toLowerCase().equals(arg.value())) {
        return arg;
      }
    }
    return null;
  }

  /**
   * Find module by name.
   *
   * @param moduleName Name of the module.
   * @return Module object if found. Null otherwise.
   */
  public static Xmod findModule(String moduleName) {
    for(String dir : XcodeMLtools_Fmod.getSearchPath()) {
      String path = dir + "/" + moduleName + XMOD_FILE_EXTENSION;
      File f = new File(path);
      if(f.exists()) {
        Document doc = readXmlFile(path);
        return doc != null ? new Xmod(doc, moduleName, dir) : null;
      }
    }
    return null;
  }

  /**
   * Read XML file.
   *
   * @param input Xml file path.
   * @return Document if the XML file could be read. Null otherwise.
   */
  public static Document readXmlFile(String input) {
    try {
      File fXmlFile = new File(input);
      if(!fXmlFile.exists()) {
        return null;
      }
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      Document doc = dBuilder.parse(fXmlFile);
      doc.getDocumentElement().normalize();
      return doc;
    } catch(Exception ignored) {
    }
    return null;
  }

  /**
   * Duplicate a lower or an upper bound between two different XcodeML units.
   *
   * @param baseBound  Base bound to be duplicated.
   * @param xcodemlDst Destination XcodeML unit. Duplicate will be created here.
   * @param xcodemlSrc Source XcodeML unit. Contains base bound.
   * @return The newly duplicated bound element.
   * @throws IllegalTransformationException If bound cannot be duplicated.
   */
  public static Xnode duplicateBound(Xnode baseBound, XcodeML xcodemlDst,
                                     XcodeML xcodemlSrc)
      throws IllegalTransformationException
  {
    if(baseBound.opcode() != Xcode.LOWERBOUND
        && baseBound.opcode() != Xcode.UPPERBOUND)
    {
      throw new IllegalTransformationException("Cannot duplicate bound");
    }

    if(xcodemlSrc == xcodemlDst) {
      return baseBound.cloneNode();
    }

    Xnode boundChild = baseBound.child(0);
    if(boundChild == null) {
      throw new IllegalTransformationException("Cannot duplicate bound as it " +
          "has no children element");
    }

    Xnode bound = xcodemlDst.createNode(baseBound.opcode());
    if(boundChild.opcode() == Xcode.FINTCONSTANT
        || boundChild.opcode() == Xcode.VAR)
    {
      bound.append(xcodemlDst.importConstOrVar(boundChild, xcodemlSrc));
    } else if(boundChild.opcode() == Xcode.PLUSEXPR) {
      Xnode lhs = boundChild.child(0);
      Xnode rhs = boundChild.child(1);
      Xnode plusExpr = xcodemlDst.createNode(Xcode.PLUSEXPR);
      bound.append(plusExpr);
      plusExpr.append(xcodemlDst.importConstOrVar(lhs, xcodemlSrc));
      plusExpr.append(xcodemlDst.importConstOrVar(rhs, xcodemlSrc));
    } else {
      throw new IllegalTransformationException(
          String.format("Lower/upper bound type currently not supported (%s)",
              boundChild.opcode().toString())
      );
    }

    return bound;
  }


  /**
   * Delete all sibling elements from the start element included.
   *
   * @param start Element to start from.
   */
  public static void deleteFrom(Xnode start) {
    List<Xnode> toDelete = new ArrayList<>();
    toDelete.add(start);
    Xnode sibling = start.nextSibling();
    while(sibling != null) {
      toDelete.add(sibling);
      sibling = sibling.nextSibling();
    }
    for(Xnode n : toDelete) {
      n.delete();
    }
  }

  /**
   * Delete a node in the ast.
   *
   * @param node Node to be deleted.
   */
  public static void safeDelete(Xnode node) {
    if(node != null) {
      node.delete();
    }
  }

  /**
   * Clean up extra pragma that have no more sense after transformation.
   *
   * @param node     Do statement that will be removed.
   * @param previous List of pragma to be removed before the do statement.
   * @param next     List of pragmas to be removed after the do statement.
   */
  public static void cleanPragmas(Xnode node, String[] previous, String[] next)
  {
    if(node.opcode() != Xcode.FDOSTATEMENT) {
      return;
    }

    Xnode doStatement = node;

    while(node.prevSibling() != null
        && node.prevSibling().opcode() == Xcode.FPRAGMASTATEMENT) {
      String pragma = node.prevSibling().value();
      Xnode toDelete = null;

      for(String p : previous) {
        if(!pragma.startsWith(ClawConstant.CLAW) && pragma.contains(p)) {
          toDelete = node.prevSibling();
          break;
        }
      }

      node = node.prevSibling();
      safeDelete(toDelete);
    }

    node = doStatement; // Reset node to the initial position.
    while(node.nextSibling() != null
        && node.nextSibling().opcode() == Xcode.FPRAGMASTATEMENT) {
      String pragma = node.nextSibling().value();
      Xnode toDelete = null;

      for(String n : next) {
        if(!pragma.startsWith(ClawConstant.CLAW) && pragma.contains(n)) {
          toDelete = node.nextSibling();
          break;
        }
      }

      node = node.nextSibling();
      safeDelete(toDelete);
    }
  }

  /**
   * Remove the "pure" attribute from the function type. Issue a warning.
   *
   * @param fctDef  Function definition node where the pure attribute must be
   *                removed.
   * @param fctType Function type node where the pure attribute must be
   *                removed.
   * @return True if the PURE specifier had to be removed false otherwise.
   */
  public static boolean removePure(Xnode fctDef, Xnode fctType) {
    if(fctType.opcode() != Xcode.FFUNCTIONTYPE ||
        fctDef.opcode() != Xcode.FFUNCTIONDEFINITION)
    {
      return false;
    }

    if(fctType.getBooleanAttribute(Xattr.IS_PURE)) {
      fctType.element().removeAttribute(Xattr.IS_PURE.toString());
      return true;
    }
    return false;
  }

  /**
   * Check whether the end node is a direct sibling of the start node. If other
   * nodes are between the two nodes and their opcode is not listed in the
   * skippedNodes list, the nodes are not direct siblings.
   *
   * @param start        First node in the tree.
   * @param end          Node to be check to be a direct sibling.
   * @param skippedNodes List of opcode that are allowed between the two nodes.
   * @return True if the nodes are direct siblings.
   */
  public static boolean isDirectSibling(Xnode start, Xnode end,
                                        List<Xcode> skippedNodes)
  {
    if(start == null || end == null) {
      return false;
    }

    Xnode nextSibling = start.nextSibling();
    while(nextSibling != null) {
      if(nextSibling.equals(end)) {
        return true;
      }
      if(skippedNodes.contains(nextSibling.opcode())) {
        nextSibling = nextSibling.nextSibling();
      } else {
        return false;
      }
    }
    return false;
  }

  /**
   * Return the directive prefix.
   *
   * @param pragma The pragma node.
   * @return Directive prefix if any. Empty string otherwise.
   */
  public static String getPragmaPrefix(Xnode pragma) {
    if(pragma == null || pragma.opcode() != Xcode.FPRAGMASTATEMENT
        || pragma.value().isEmpty())
    {
      return "";
    }

    if(!pragma.value().contains(" ")) {
      return pragma.value();
    }

    return pragma.value().substring(0, pragma.value().indexOf(" "));
  }

  /**
   * Get the string representation of the induction variable of a do statement.
   *
   * @param doStatement Do statement to extract the induction variable.
   * @return The string value of the induction variable. Empty string if the
   * passed Xnode is not a FdoStatement element.
   */
  public static String extractInductionVariable(Xnode doStatement) {
    if(doStatement.opcode() != Xcode.FDOSTATEMENT) {
      return "";
    }
    return doStatement.matchDirectDescendant(Xcode.VAR).value();
  }

  /**
   * Split a long pragma into chunk depending on the maxColumns sepcified.
   * The split is done on spaces or commas.
   *
   * @param fullPragma   The original full pragma value.
   * @param maxColumns   Maximum number of columns. This length take into
   *                     account the added prefix and continuation symbol.
   * @param pragmaPrefix Prefix used by the pragma.
   * @return A list of chunks from the original pragma.
   */
  public static List<String> splitByLength(String fullPragma, int maxColumns,
                                           String pragmaPrefix)
  {
    List<String> splittedPragmas = new ArrayList<>();
    fullPragma = fullPragma.toLowerCase();
    if(fullPragma.length() > maxColumns) {
      fullPragma = XnodeUtil.dropEndingComment(fullPragma);
      int addLength = pragmaPrefix.length() + 5; // "!$<prefix> PRAGMA &"
      while(fullPragma.length() > (maxColumns - addLength)) {
        int splitIndex =
            fullPragma.substring(0,
                maxColumns - addLength).lastIndexOf(" ");
        // Cannot cut as it should. Take first possible cutting point.
        if(splitIndex == -1) {
          splitIndex = fullPragma.substring(0,
              maxColumns - addLength).lastIndexOf(",");
          if(splitIndex == -1) {
            splitIndex =
                (fullPragma.contains(" ")) ? fullPragma.lastIndexOf(" ") :
                    (fullPragma.contains(",")) ? fullPragma.lastIndexOf(",") :
                        fullPragma.length();
          }
        }
        String splittedPragma = fullPragma.substring(0, splitIndex);
        fullPragma =
            fullPragma.substring(splitIndex, fullPragma.length()).trim();
        splittedPragmas.add(splittedPragma);
      }
    }
    if(fullPragma.length() > 0) {
      splittedPragmas.add(fullPragma);
    }
    return splittedPragmas;
  }

  /**
   * Remove any trailing comment from a pragma string.
   *
   * @param pragma Original pragma string.
   * @return Pragma string without the trailing comment if any.
   */
  public static String dropEndingComment(String pragma) {
    if(pragma != null && pragma.indexOf("!") > 0) {
      return pragma.substring(0, pragma.indexOf("!")).trim();
    }
    return pragma;
  }

  /**
   * Gather arguments of a function call.
   *
   * @param xcodeml   Current XcodeML translation unit.
   * @param fctCall   functionCall node in which the arguments are retrieved.
   * @param intent    Intent to use for gathering.
   * @param arrayOnly If true, gather only arrays arguments.
   * @return List of arguments as their string representation.
   */
  public static List<String> gatherArguments(XcodeProgram xcodeml,
                                             Xnode fctCall, Xintent intent,
                                             boolean arrayOnly)
  {
    List<String> gatheredArguments = new ArrayList<>();
    if(fctCall == null || fctCall.opcode() != Xcode.FUNCTIONCALL) {
      return gatheredArguments;
    }
    Xnode argumentsNode = fctCall.matchDescendant(Xcode.ARGUMENTS);
    if(argumentsNode == null) {
      return gatheredArguments;
    }

    // Retrieve function type to check intents and types of parameters
    XfunctionType fctType = xcodeml.getTypeTable().getFunctionType(fctCall);
    List<Xnode> parameters = fctType.getParams().getAll();
    List<Xnode> arguments = argumentsNode.children();

    for(int i = 0; i < parameters.size(); ++i) {
      // TODO handle optional arguments, named value args
      Xnode parameter = parameters.get(i);
      Xnode arg = arguments.get(i);

      String rep = "";
      if(XcodeType.isBuiltInType(arg.getType()) && !arrayOnly
          && xcodeml.getTypeTable().isBasicType(parameter))
      {
        XbasicType btParameter = xcodeml.getTypeTable().getBasicType(parameter);
        if(!intent.isCompatible(btParameter.getIntent())) {
          continue;
        }
        rep = arg.constructRepresentation(false);
      } else if(xcodeml.getTypeTable().isBasicType(parameter)
          && xcodeml.getTypeTable().isBasicType(arg))
      {
        XbasicType btParameter = xcodeml.getTypeTable().getBasicType(parameter);
        XbasicType btArg = xcodeml.getTypeTable().getBasicType(arg);
        if((arrayOnly && !btArg.isArray())
            || !intent.isCompatible(btParameter.getIntent()))
        {
          continue;
        }
        rep = arg.constructRepresentation(false);
      }
      if(rep != null && !rep.isEmpty()) {
        gatheredArguments.add(rep);
      }
    }
    return gatheredArguments;
  }

}
