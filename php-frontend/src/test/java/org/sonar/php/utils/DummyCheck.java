/*
 * SonarQube PHP Plugin
 * Copyright (C) 2010 SonarSource and Akram Ben Aissi
 * sonarqube@googlegroups.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.php.utils;

import org.sonar.plugins.php.api.tree.Tree;
import org.sonar.plugins.php.api.tree.expression.AssignmentExpressionTree;
import org.sonar.plugins.php.api.visitors.PHPVisitorCheck;

/**
 * Dummy check for testing. Raises an issue on assignment expression.
 * <p> Example:
 * <pre>
 *   {@literal<}?php
 *
 *     $a = 1;   // issue
 *     $b += 1;  // no issue
 * </pre>
 */
public class DummyCheck extends PHPVisitorCheck {

  public static final String KEY = "test";
  public static final String MESSAGE = "message";

    @Override
    public void visitAssignmentExpression(AssignmentExpressionTree tree) {
      if (tree.is(Tree.Kind.ASSIGNMENT)) {
        context().newIssue(KEY, MESSAGE).tree(tree);
      }

      super.visitAssignmentExpression(tree);
    }

}