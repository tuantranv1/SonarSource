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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.sonar.check.Rule;
import org.sonar.php.checks.phpini.PhpIniBoolean;
import org.sonar.php.checks.utils.CheckUtils;
import org.sonar.php.ini.PhpIniCheck;
import org.sonar.php.ini.PhpIniIssue;
import org.sonar.php.ini.tree.PhpIniFile;
import org.sonar.plugins.php.api.tree.Tree.Kind;
import org.sonar.plugins.php.api.tree.declaration.CallArgumentTree;
import org.sonar.plugins.php.api.tree.expression.ExpressionTree;
import org.sonar.plugins.php.api.tree.expression.FunctionCallTree;
import org.sonar.plugins.php.api.visitors.PHPVisitorCheck;

import static org.sonar.php.checks.phpini.PhpIniFiles.checkRequiredBoolean;
import static org.sonar.php.checks.utils.CheckUtils.getLowerCaseFunctionName;
import static org.sonar.php.checks.utils.CheckUtils.isFalseValue;

@Rule(key = "S3330")
public class HttpOnlyCheck extends PHPVisitorCheck implements PhpIniCheck {

  private static final String MESSAGE_PHP_INI = "Set the \"session.cookie_httponly\" property to \"true\" if needed.";
  private static final String MESSAGE = "Make sure creating this cookie without the \"httpOnly\" flag is safe here.";

  private static final List<String> SET_COOKIE_FUNCTIONS = Arrays.asList("setcookie", "setrawcookie");

  @Override
  public List<PhpIniIssue> analyze(PhpIniFile phpIniFile) {
    return checkRequiredBoolean(
      phpIniFile,
      "session.cookie_httponly",
      PhpIniBoolean.ON,
      MESSAGE_PHP_INI, MESSAGE_PHP_INI);
  }

  @Override
  public void visitFunctionCall(FunctionCallTree tree) {

    if (isSetCookie(tree)) {
      Optional<CallArgumentTree> argument = CheckUtils.argument(tree, "httponly", 6);

      if (argument.isPresent()) {
        ExpressionTree httpOnlyArgument = argument.get().value();
        if (httpOnlyArgument.is(Kind.BOOLEAN_LITERAL) && isFalseValue(httpOnlyArgument)) {
          context().newIssue(this, tree.callee(), MESSAGE).secondary(httpOnlyArgument, null);
        }
      } else if (tree.callArguments().size() != 3) {
        // if only 3 argument are defined there is an ambiguity so we don't raise issue
        context().newIssue(this, tree.callee(), MESSAGE);
      }
    }

    super.visitFunctionCall(tree);
  }

  private static boolean isSetCookie(FunctionCallTree tree) {
    String functionName = getLowerCaseFunctionName(tree);
    return functionName != null && SET_COOKIE_FUNCTIONS.contains(functionName);
  }
}
