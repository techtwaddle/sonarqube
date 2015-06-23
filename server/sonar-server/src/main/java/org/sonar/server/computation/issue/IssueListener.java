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

import org.sonar.core.issue.DefaultIssue;
import org.sonar.core.issue.tracking.Tracking;
import org.sonar.server.computation.component.Component;

public abstract class IssueListener {

  /**
   * This method is called for each component before processing its issues.
   * The component does not necessarily have issues.
   */
  public void beforeComponent(Component component, Tracking tracking) {

  }

  /**
   * This method is called when initializing an open issue. At that time
   * any information related to tracking step are not available (line, assignee,
   * resolution, status, creation date, uuid, ...).
   * <p/>
   * The need for this method is for example to calculate the issue debt
   * before merging with base issue
   */
  public void onOpenIssueInitialization(Component component, DefaultIssue issue) {

  }

  /**
   * This method is called when tracking is done and issue is initialized. That means that the following fields
   * are set: resolution, status, line, creation date, uuid and all the fields merged from base issues.
   */
  public void onIssue(Component component, DefaultIssue issue) {

  }

  public void afterComponent(Component component) {

  }
}
