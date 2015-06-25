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

package org.sonar.core.issue.db;

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.CheckForNull;
import org.sonar.api.server.ServerSide;
import org.sonar.core.issue.DefaultIssueComment;
import org.sonar.core.issue.FieldDiffs;
import org.sonar.core.persistence.DaoComponent;
import org.sonar.core.persistence.DbSession;
import org.sonar.core.persistence.MyBatis;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;

@ServerSide
public class IssueChangeDao implements DaoComponent {

  private final MyBatis mybatis;

  public IssueChangeDao(MyBatis mybatis) {
    this.mybatis = mybatis;
  }

  public List<DefaultIssueComment> selectCommentsByIssues(DbSession session, Collection<String> issueKeys) {
    List<DefaultIssueComment> comments = Lists.newArrayList();
    for (IssueChangeDto dto : selectByIssuesAndType(session, issueKeys, IssueChangeDto.TYPE_COMMENT)) {
      comments.add(dto.toComment());
    }
    return comments;
  }

  public List<FieldDiffs> selectChangelogByIssue(String issueKey) {
    DbSession session = mybatis.openSession(false);
    try {
      List<FieldDiffs> result = Lists.newArrayList();
      for (IssueChangeDto dto : selectByIssuesAndType(session, asList(issueKey), IssueChangeDto.TYPE_FIELD_CHANGE)) {
        result.add(dto.toFieldDiffs());
      }
      return result;
    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public List<IssueChangeDto> selectChangelogOfUnresolvedIssuesByComponent(String componentUuid) {
    DbSession session = mybatis.openSession(false);
    try {
      IssueChangeMapper mapper = session.getMapper(IssueChangeMapper.class);
      return mapper.selectChangelogOfUnresolvedIssuesByComponent(componentUuid, IssueChangeDto.TYPE_FIELD_CHANGE);

    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  @CheckForNull
  public DefaultIssueComment selectCommentByKey(String commentKey) {
    DbSession session = mybatis.openSession(false);
    try {
      IssueChangeMapper mapper = session.getMapper(IssueChangeMapper.class);
      IssueChangeDto dto = mapper.selectByKeyAndType(commentKey, IssueChangeDto.TYPE_COMMENT);
      return dto != null ? dto.toComment() : null;

    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  List<IssueChangeDto> selectByIssuesAndType(DbSession session, Collection<String> issueKeys, String changeType) {
    if (issueKeys.isEmpty()) {
      return Collections.emptyList();
    }
    IssueChangeMapper mapper = session.getMapper(IssueChangeMapper.class);
    List<IssueChangeDto> dtosList = newArrayList();
    List<List<String>> keysPartition = Lists.partition(newArrayList(issueKeys), 1000);
    for (List<String> partition : keysPartition) {
      List<IssueChangeDto> dtos = mapper.selectByIssuesAndType(partition, changeType);
      dtosList.addAll(dtos);
    }
    return dtosList;
  }

  public void insert(DbSession session, IssueChangeDto change) {
    session.getMapper(IssueChangeMapper.class).insert(change);
  }

  public boolean delete(String key) {
    DbSession session = mybatis.openSession(false);
    try {
      IssueChangeMapper mapper = session.getMapper(IssueChangeMapper.class);
      int count = mapper.delete(key);
      session.commit();
      return count == 1;

    } finally {
      MyBatis.closeQuietly(session);
    }
  }

  public boolean update(IssueChangeDto change) {
    DbSession session = mybatis.openSession(false);
    try {
      IssueChangeMapper mapper = session.getMapper(IssueChangeMapper.class);
      int count = mapper.update(change);
      session.commit();
      return count == 1;

    } finally {
      MyBatis.closeQuietly(session);
    }
  }
}
