package claw.wani.transformation.ll.first_directive;

import claw.shenron.transformation.DependentTransformationGroup;
import claw.shenron.transformation.Transformation;
import claw.shenron.translator.Translator;
import claw.tatsu.xcodeml.exception.IllegalTransformationException;
import claw.tatsu.xcodeml.xnode.common.XcodeProgram;
import claw.tatsu.xcodeml.xnode.common.Xnode;
import claw.wani.language.ClawPragma;
import claw.wani.transformation.ClawTransformation;

public class FirstDirective extends ClawTransformation {

  public FirstDirective(ClawPragma directive) {
    super(directive);
  }

  /**
   * Analyze the possibility to apply the transformation. Gather information to
   * be able to apply the transformation in when calling #transform.
   *
   * @param xcodeml    The XcodeML on which the transformations are applied.
   * @param translator The translator used to applied the transformations.
   * @return True if analysis succeeded. False otherwise.
   */
  @Override
  public boolean analyze(XcodeProgram xcodeml, Translator translator) {
    return true;
  }

  /**
   * Check whether the current transformation can be transformed together with
   * the given transformation. Useful only for dependent transformation.
   *
   * @param xcodeml The XcodeML on which the transformations are applied.
   * @param other   The other transformation part of the dependent transformation.
   * @return True if the two transformation can be transform together. False
   * otherwise.
   * @see DependentTransformationGroup
   */
  @Override
  public boolean canBeTransformedWith(XcodeProgram xcodeml, Transformation other) {
    return false;
  }

  /**
   * Apply the actual transformation.
   *
   * @param xcodeml    The XcodeML on which the transformations are applied.
   * @param translator The translator used to applied the transformations.
   * @param other      Only for dependent transformation. The other
   *                   transformation part of the transformation.
   * @throws IllegalTransformationException if the transformation cannot be
   *                                        applied.
   */
  @Override
  public void transform(XcodeProgram xcodeml, Translator translator, Transformation other) throws Exception {
    int lineNo = xcodeml.lineNo();
    Xnode mul = _claw.getPragma().nextSibling().child(1);
    mul.child(0).delete();
    mul.append(mul.child(0).cloneNode());
    removePragma();
  }
}
