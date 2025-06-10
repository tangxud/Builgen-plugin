/*******************************************************************************
 * Copyright (C) 2019-2025 ROSEWIL. All Rights Reserved.
 *
 * This program and the accompanying materials are made by ROSEWIL.
 * All material is subject to copyright and no part of this materials may
 * be reproduced, copied, distributed, or transmitted in any form or
 * by any means, including photocopying, recording, or other
 * electronic or mechanical methods, without prior written
 * permission from ROSEWIL.
 *
 * Contributors:
 *     ROSEWIL - initial API and implementation
 *     [Your Name] - Refactored to generate classic immutable Builder pattern
 *
 * Official Web Site:
 *     http://www.rosewil.com
 *******************************************************************************/
package builgen.action;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.internal.core.CompilationUnit;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorActionDelegate;
import org.eclipse.ui.IEditorPart;

import builgen.util.CodeFormatterUtil;

/**
 * BuilgenAction - Generates a classic, immutable Builder pattern.
 *
 * @author tangxudong
 * @version 2.0.0 (Refactored)
 * @github https://github.com/Vabshroo/Builgen-plugin
 */
@SuppressWarnings("restriction")
public class BuilgenAction implements IEditorActionDelegate {

  private ISelection selection = null;
  private Shell shell;
  
  private static final String BUILDER_CLASS_NAME = "Builder";

  @Override
  public void setActiveEditor(IAction action, IEditorPart targetEditor) {
    shell = targetEditor.getSite().getShell();
  }

  @Override
  public void selectionChanged(IAction action, ISelection selection) {
    this.selection = selection;
  }
  
  @Override
  public void run(IAction action) {
    CompilationUnit compilationUnit = ((CompilationUnit) ((TreeSelection) selection).getFirstElement());

    try {
      IType classFile = compilationUnit.getAllTypes()[0];
      if (classFile == null || !classFile.isClass()) {
        alert("Error: Not a valid class.");
        return;
      }
      
      IField[] fields = classFile.getFields();
      if (fields.length == 0) {
        alert("No fields found to generate builder.");
        return;
      }
      
      List<IField> fieldsForBuilder = new ArrayList<>();
      for (IField field : fields) {
        if (!field.getSource().contains(" static ")) {
          fieldsForBuilder.add(field);
        }
      }

      cleanupExistingCode(classFile);

      IMethod lastGeneratedMethod = null;
      
      // 1. Generate the private constructor that accepts a Builder
      lastGeneratedMethod = classFile.createMethod(
        genPrivateBuilderConstructor(classFile.getElementName(), fieldsForBuilder), 
        lastGeneratedMethod, false, null);

      // 2. Generate getters for all fields
      for (IField field : fieldsForBuilder) {
         lastGeneratedMethod = classFile.createMethod(genGetter(field), lastGeneratedMethod, false, null);
      }

      // 3. Generate the static inner Builder class
      classFile.createType(
        genClassicBuilderClass(classFile.getElementName(), fieldsForBuilder), 
        lastGeneratedMethod, false, null);
      
      // 4. Generate the static builder() factory method
      classFile.createMethod(
        genStaticBuilderFactoryMethod(classFile.getElementName()),
        lastGeneratedMethod, false, null
      );

      // This part is a bit tricky. The ideal way to make fields final is to
      // rewrite the source file. For simplicity, this step is omitted, but
      // users should be advised to add the 'final' keyword to fields manually.
      
      // Format the generated code
      String source = CodeFormatterUtil.format(classFile.getSource());
      compilationUnit.getBuffer().setContents(source);
      compilationUnit.save(null, true);

    } catch (JavaModelException e) {
      e.printStackTrace();
      alert("Fatal Error: " + e.getMessage());
    }
  }

  private void cleanupExistingCode(IType classFile) throws JavaModelException {
    // Delete existing constructors to avoid conflicts
    for (IMethod method : classFile.getMethods()) {
      if (method.isConstructor()) {
        method.delete(false, null);
      }
    }
    // Delete existing Builder class if it exists
    IType builderType = classFile.getType(BUILDER_CLASS_NAME);
    if (builderType.exists()) {
      builderType.delete(false, null);
    }
  }

  /**
   * Generates the private constructor for the target class, which accepts a Builder.
   * e.g., private MyClass(Builder builder) { ... }
   */
  private String genPrivateBuilderConstructor(String typeName, List<IField> fieldTypes) {
    StringBuilder builder = new StringBuilder();
    builder.append("private ").append(typeName).append("(").append(BUILDER_CLASS_NAME).append(" builder) {");

    for (IField field : fieldTypes) {
      String fieldName = field.getElementName();
      builder.append("this.").append(fieldName).append(" = builder.").append(fieldName).append(";");
    }

    builder.append("}");
    return builder.toString();
  }
  
  /**
   * Generates the public static inner Builder class.
   */
  private String genClassicBuilderClass(String typeName, List<IField> fieldTypes) throws JavaModelException {
    StringBuilder builder = new StringBuilder();
    builder.append("public static class ").append(BUILDER_CLASS_NAME).append("{");

    // The Builder has its own private fields
    for (IField field : fieldTypes) {
      builder.append("private ").append(Signature.toString(field.getTypeSignature()))
           .append(" ").append(field.getElementName()).append(";");
    }

    // Public constructor for the Builder
    builder.append("public ").append(BUILDER_CLASS_NAME).append("() {}");

    // Chainable setter methods for the Builder
    for (IField field : fieldTypes) {
      builder.append(genClassicBuilderMethod(Signature.toString(field.getTypeSignature()), field.getElementName()));
    }

    // The final build() method
    builder.append(genClassicBuildMethod(typeName));

    builder.append("}");
    return builder.toString();
  }

  /**
   * Generates a chainable method for the Builder class.
   * e.g., public Builder myField(String myField) { ... }
   */
  private String genClassicBuilderMethod(String fieldType, String fieldName) {
    StringBuilder builder = new StringBuilder();
    builder.append("public ").append(BUILDER_CLASS_NAME).append(" ").append(fieldName)
         .append("(").append(fieldType).append(" ").append(fieldName).append(") {")
         .append("this.").append(fieldName).append(" = ").append(fieldName).append(";")
         .append("return this;")
         .append("}");
    return builder.toString();
  }

  /**
   * Generates the final build() method for the Builder class.
   */
  private String genClassicBuildMethod(String typeName) {
    StringBuilder builder = new StringBuilder();
    builder.append("public ").append(typeName).append(" build() {")
         .append("return new ").append(typeName).append("(this);")
         .append("}");
    return builder.toString();
  }
  
  /**
   * Generates the static factory method for creating a Builder instance.
   * e.g., public static Builder builder() { ... }
   */
  private String genStaticBuilderFactoryMethod(String typeName) {
    StringBuilder builder = new StringBuilder();
    builder.append("public static ").append(BUILDER_CLASS_NAME).append(" builder() {")
         .append("return new ").append(BUILDER_CLASS_NAME).append("();")
         .append("}");
    return builder.toString();
  }
  
  /**
   * Generates a standard getter method.
   */
  private String genGetter(IField field) throws JavaModelException {
    StringBuilder builder = new StringBuilder();
    String fieldType = Signature.toString(field.getTypeSignature());
    String fieldName = field.getElementName();
    builder.append("public ").append(fieldType).append(" get").append(firstCharUppercase(fieldName))
         .append("() { return this.").append(fieldName).append("; }");
    return builder.toString();
  }
  
  private void alert(Object content) {
    MessageDialog.openInformation(shell, "Builgen Plugin", content + "");
  }
  
  private String firstCharUppercase(String string) {
    if (string == null || string.isEmpty()) {
      return string;
    }
    return string.substring(0, 1).toUpperCase() + string.substring(1);
  }
}