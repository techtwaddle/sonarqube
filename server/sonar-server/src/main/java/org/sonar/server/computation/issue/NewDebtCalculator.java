/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.computation.issue;

import java.util.Arrays;
import org.sonar.core.issue.DefaultIssue;
import org.sonar.core.issue.tracking.Tracking;
import org.sonar.server.computation.component.Component;
import org.sonar.server.computation.period.PeriodsHolder;

public class NewDebtCalculator extends IssueVisitor {

  private final PeriodsHolder periodsHolder;
  private Double[] currentVariations = new Double[PeriodsHolder.MAX_NUMBER_OF_PERIODS];

  public NewDebtCalculator(PeriodsHolder periodsHolder) {
    this.periodsHolder = periodsHolder;
  }

  @Override
  public void beforeComponent(Component component, Tracking tracking) {
    Arrays.fill(currentVariations, null);
  }

  @Override
  public void onIssue(Component component, DefaultIssue issue) {
    // TODO
  }

  @Override
  public void afterComponent(Component component) {
    super.afterComponent(component);
  }

}
