/*
 * SonarQube PHP Plugin
 * Copyright (C) 2010-2022 SonarSource SA
 * mailto:info AT sonarsource DOT com
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.php.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.sonar.check.Rule;
import org.sonar.php.checks.utils.CheckUtils;
import org.sonar.plugins.php.api.tree.CompilationUnitTree;
import org.sonar.plugins.php.api.tree.Tree;
import org.sonar.plugins.php.api.tree.declaration.CallArgumentTree;
import org.sonar.plugins.php.api.tree.expression.ArrayAccessTree;
import org.sonar.plugins.php.api.tree.expression.ArrayInitializerBracketTree;
import org.sonar.plugins.php.api.tree.expression.ArrayPairTree;
import org.sonar.plugins.php.api.tree.expression.ExpressionTree;
import org.sonar.plugins.php.api.tree.expression.FunctionCallTree;
import org.sonar.plugins.php.api.tree.expression.LiteralTree;
import org.sonar.plugins.php.api.tree.expression.VariableIdentifierTree;
import org.sonar.plugins.php.api.visitors.PHPVisitorCheck;

import static org.sonar.php.checks.utils.CheckUtils.SUPERGLOBALS;
import static org.sonar.php.checks.utils.CheckUtils.trimQuotes;

@Rule(key = "S2053")
public class HashFunctionCheck extends PHPVisitorCheck {

  private static final String MESSAGE = "Use an unpredictable salt value.";
  private static final String MESSAGE_MISSING_SALT = "Provide a cryptographically strong salt parameter.";
  private static final String USE_DEFAULT_SALT_MESSAGE = "Use the salt that is generated by default." ;
  private boolean containsPasswordHashFunction;
  private List<Tree> passwordHashSaltTrees = new ArrayList<>();

  @Override
  public void visitFunctionCall(FunctionCallTree tree) {
    String functionName = CheckUtils.getLowerCaseFunctionName(tree);
    if ("hash_pbkdf2".equals(functionName)) {
      Optional<CallArgumentTree> saltArgument = CheckUtils.argument(tree, "salt", 2);
      if (saltArgument.isPresent()) {
        ExpressionTree saltArgumentValue = saltArgument.get().value();
        if (isPredictable(saltArgumentValue)) {
          context().newIssue(this, saltArgumentValue, MESSAGE);
        }
      }
    } else if ("crypt".equals(functionName)) {
      Optional<CallArgumentTree> saltArgument = CheckUtils.argument(tree, "salt", 1);
      if (!saltArgument.isPresent()) {
        context().newIssue(this, tree, MESSAGE_MISSING_SALT);
      } else {
        ExpressionTree saltArgumentValue = saltArgument.get().value();
        if (isPredictable(saltArgumentValue)) {
          context().newIssue(this, saltArgumentValue, MESSAGE);
        }
      }
    } else if ("password_hash".equals(functionName)) {
      containsPasswordHashFunction = true;
    }
    super.visitFunctionCall(tree);
  }

  private boolean isPredictable(ExpressionTree tree) {
    if (tree.is(Tree.Kind.REGULAR_STRING_LITERAL)) {
      return true;
    }
    if (tree.is(Tree.Kind.ARRAY_ACCESS)) {
      ExpressionTree array = ((ArrayAccessTree) tree).object();
      return array.is(Tree.Kind.VARIABLE_IDENTIFIER)
        && SUPERGLOBALS.contains(((VariableIdentifierTree) array).text());
    }
    if (tree.is(Tree.Kind.VARIABLE_IDENTIFIER)) {
      Optional<ExpressionTree> uniqueAssignedValue = CheckUtils.uniqueAssignedValue((VariableIdentifierTree) tree);
      if (uniqueAssignedValue.isPresent()) {
        return isPredictable(uniqueAssignedValue.get());
      }
    }
    return false;
  }

  @Override
  public void visitCompilationUnit(CompilationUnitTree tree) {
    passwordHashSaltTrees.clear();
    super.visitCompilationUnit(tree);
    if (containsPasswordHashFunction && !passwordHashSaltTrees.isEmpty()) {
      passwordHashSaltTrees.forEach(salt -> context().newIssue(this, salt, USE_DEFAULT_SALT_MESSAGE));
    }
  }

  @Override
  public void visitArrayInitializerBracket(ArrayInitializerBracketTree tree) {
    tree.arrayPairs().stream()
      .map(ArrayPairTree::key)
      .filter(Objects::nonNull)
      .filter(key -> key.is(Tree.Kind.REGULAR_STRING_LITERAL) && "salt".equals(trimQuotes(((LiteralTree) key))))
      .forEach(passwordHashSaltTrees::add);
    super.visitArrayInitializerBracket(tree);
  }
}
