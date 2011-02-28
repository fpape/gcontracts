/**
 * Copyright (c) 2011, Andre Steingress
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1.) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 * 2.) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 * 3.) Neither the name of Andre Steingress nor the names of its contributors may be used to endorse or
 * promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.gcontracts.ast.visitor;

import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.io.ReaderSource;
import org.gcontracts.annotations.Ensures;
import org.gcontracts.annotations.Invariant;
import org.gcontracts.annotations.Requires;
import org.gcontracts.annotations.meta.ContractElement;
import org.gcontracts.ast.visitor.BaseVisitor;
import org.gcontracts.generation.CandidateChecks;
import org.gcontracts.util.AnnotationUtils;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Modifier;
import java.util.List;

/**
 * <p>
 * Visits interface and looks for <tt>@Requires</tt> or <tt>@Ensures</tt> and adds a dummy implementation
 * of that interface if pre- or postconditions have been found.
 * </p>
 *
 * @see org.gcontracts.annotations.Requires
 * @see org.gcontracts.annotations.Ensures
 *
 * @see org.gcontracts.ast.visitor.BaseVisitor
 *
 * @author ast
 */
public class InterfaceVisitor extends BaseVisitor {

    private boolean hasContractElement = false;

    public InterfaceVisitor(final SourceUnit sourceUnit, final ReaderSource source) {
        super(sourceUnit, source);
    }

    @Override
    public void visitClass(ClassNode node) {
        if (!CandidateChecks.isInterfaceContractsCandidate(node)) return;

        super.visitClass(node);

        if (hasContractElement)  {
            generateDummyImplementation(node);
        }
    }

    @Override
    public void visitAnnotations(AnnotatedNode node) {
        hasContractElement = hasContractElement || AnnotationUtils.hasMetaAnnotations(node, ContractElement.class.getName()).size() > 0;
    }

    private void generateDummyImplementation(ClassNode node) {

        String packageName = node.getPackageName() != null ? node.getPackageName() + "." : "";
        ClassNode dummyClass = new ClassNode(
                packageName + "$GContracts_Dummy_" + node.getNameWithoutPackage(),
                Opcodes.ACC_PRIVATE,
                node);

        node.getModule().addClass(dummyClass);

        for (MethodNode methodNode : node.getMethods())  {
            if (methodNode.isSynthetic()) continue;
            if (methodNode.isStatic()) continue;

            int modifiers = methodNode.getModifiers();

            if (methodNode.isVoidMethod())  {

                MethodNode generatedMethodNode = dummyClass.addMethod(
                        methodNode.getName(),
                        modifiers,
                        methodNode.getReturnType(),
                        methodNode.getParameters(),
                        methodNode.getExceptions(),
                        new BlockStatement());

                generatedMethodNode.setSynthetic(false);
                generatedMethodNode.addAnnotations(methodNode.getAnnotations());
            } else {

                 MethodNode generatedMethodNode = dummyClass.addMethod(
                        methodNode.getName(),
                         modifiers,
                        methodNode.getReturnType(),
                        methodNode.getParameters(),
                        methodNode.getExceptions(),
                        new ReturnStatement(ConstantExpression.NULL));

                generatedMethodNode.setSynthetic(false);
                generatedMethodNode.addAnnotations(methodNode.getAnnotations());
            }
        }
    }
}