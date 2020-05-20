/*
 * SonarQube PHP Plugin
 * Copyright (C) 2010-2020 SonarSource SA
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

import com.google.common.collect.ImmutableList;
import org.sonar.php.cfg.LiveVariablesAnalysis;
import org.sonar.php.tree.symbols.Scope;
import org.sonar.plugins.php.api.cfg.CfgBlock;
import org.sonar.plugins.php.api.cfg.ControlFlowGraph;
import org.sonar.plugins.php.api.symbols.Symbol;
import org.sonar.plugins.php.api.symbols.SymbolTable;
import org.sonar.plugins.php.api.tree.Tree;
import org.sonar.plugins.php.api.tree.statement.GlobalStatementTree;
import org.sonar.plugins.php.api.visitors.PHPSubscriptionCheck;
import org.sonar.plugins.php.api.visitors.PHPTreeSubscriber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UndefinedVariablesCheck extends PHPSubscriptionCheck {
  Map<CfgBlock, Symbol> cfgBlocksGlobalBinds = new HashMap<>();

  @Override
  public List<Tree.Kind> nodesToVisit() {
    return ImmutableList.of(Tree.Kind.FUNCTION_DECLARATION, Tree.Kind.METHOD_DECLARATION);
  }

  @Override
  public void visitNode(Tree tree) {
    ControlFlowGraph cfg = ControlFlowGraph.build(tree, context());
    if (cfg == null) {
      return;
    }

    Scope scope = context().symbolTable().getScopeFor(tree);
    if (scope == null) {
      return;
    }

    LiveVariablesAnalysis lva = LiveVariablesAnalysis.analyze(cfg, context().symbolTable());

    recursiveCheckBlockAndSuccessors(cfg.start(), lva);
  }

  private void checkBlock(CfgBlock block, LiveVariablesAnalysis lva) {
    Set<Symbol> blockDeclaredSymbols = new HashSet<>();

    for (Tree statement: block.elements()) {
      if (statement.is(Tree.Kind.GLOBAL_STATEMENT)) {
        ((GlobalStatementTree)statement).variables().stream()
          .filter(v -> v.is(Tree.Kind.VARIABLE_IDENTIFIER))
          .forEach(v -> cfgBlocksGlobalBinds.put(block, context().symbolTable().getSymbol(v)));
        continue;
      }

      Map<Symbol, LiveVariablesAnalysis.VariableUsage> usagesInElement = lva.getLiveVariables(block).getVariableUsages(statement);
      for (Map.Entry<Symbol, LiveVariablesAnalysis.VariableUsage> symbolWithUsage : usagesInElement.entrySet()) {
        if (symbolWithUsage.getValue().isRead() && !symbolWithUsage.getValue().isWrite() &&
          !blockDeclaredSymbols.contains(symbolWithUsage.getKey()) && !isSymbolDefinedInBlockPredecessors(symbolWithUsage.getKey(), block, lva)) {
          reportUndefinedVariable(symbolWithUsage.getKey(), statement);
        } else if (symbolWithUsage.getValue().isWrite()) {
          blockDeclaredSymbols.add(symbolWithUsage.getKey());
        }
      }
    }
  }

  private boolean isSymbolDefinedInBlockPredecessors(Symbol symbol, CfgBlock block, LiveVariablesAnalysis lva) {
    for (CfgBlock predecessorBlock: block.predecessors()) {
      if (lva.getLiveVariables(predecessorBlock).getKill().contains(symbol) ||
        cfgBlocksGlobalBinds.containsKey(predecessorBlock) && cfgBlocksGlobalBinds.get(predecessorBlock) == symbol) {
        return true;
      }


      return isSymbolDefinedInBlockPredecessors(symbol, predecessorBlock, lva);
    }

    return false;
  }

  private void recursiveCheckBlockAndSuccessors(CfgBlock block, LiveVariablesAnalysis lva) {
    checkBlock(block, lva);
    for (CfgBlock successorBlock: block.successors()) {
      recursiveCheckBlockAndSuccessors(successorBlock, lva);
    }
  }

  private void reportUndefinedVariable(Symbol symbol, Tree statementTree) {
    // find the variable trees representing that symbol in the statement
    VariablesFinder vf = new VariablesFinder(symbol, context().symbolTable());
    vf.scanTree(statementTree);

    if (vf.foundTrees.isEmpty()) {
      return;
    }

    context().newIssue(this, vf.foundTrees.get(0), "Used and not defined");
  }

  private static class VariablesFinder extends PHPTreeSubscriber {
    private Symbol symbol;
    private SymbolTable symbolTable;
    private List<Tree> foundTrees = new ArrayList<>();

    private VariablesFinder(Symbol symbol, SymbolTable symbolTable) {
      this.symbol = symbol;
      this.symbolTable = symbolTable;
    }

    @Override
    public List<Tree.Kind> nodesToVisit() {
      return ImmutableList.of(Tree.Kind.VARIABLE_IDENTIFIER);
    }

    @Override
    public void visitNode(Tree tree) {
      if (symbolTable.getSymbol(tree) == symbol) {
        foundTrees.add(tree);
      }

      super.visitNode(tree);
    }
  }
}
