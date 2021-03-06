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

package org.sonar.server.measure.custom.ws;

import java.net.HttpURLConnection;
import org.sonar.api.resources.Qualifiers;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.api.web.UserRole;
import org.sonar.core.component.ComponentDto;
import org.sonar.core.measure.custom.db.CustomMeasureDto;
import org.sonar.core.metric.db.MetricDto;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.core.persistence.DbSession;
import org.sonar.core.persistence.MyBatis;
import org.sonar.server.db.DbClient;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.exceptions.ServerException;
import org.sonar.server.user.UserSession;
import org.sonar.server.user.index.UserDoc;
import org.sonar.server.user.index.UserIndex;

import static com.google.common.base.Preconditions.checkArgument;
import static org.sonar.server.measure.custom.ws.CustomMeasureValueDescription.measureValueDescription;

public class CreateAction implements CustomMeasuresWsAction {
  public static final String ACTION = "create";
  public static final String PARAM_PROJECT_ID = "projectId";
  public static final String PARAM_PROJECT_KEY = "projectKey";
  public static final String PARAM_METRIC_ID = "metricId";
  public static final String PARAM_METRIC_KEY = "metricKey";
  public static final String PARAM_VALUE = "value";
  public static final String PARAM_DESCRIPTION = "description";

  private final DbClient dbClient;
  private final UserSession userSession;
  private final System2 system;
  private final CustomMeasureValidator validator;
  private final CustomMeasureJsonWriter customMeasureJsonWriter;
  private final UserIndex userIndex;

  public CreateAction(DbClient dbClient, UserSession userSession, System2 system, CustomMeasureValidator validator, CustomMeasureJsonWriter customMeasureJsonWriter,
    UserIndex userIndex) {
    this.dbClient = dbClient;
    this.userSession = userSession;
    this.system = system;
    this.validator = validator;
    this.customMeasureJsonWriter = customMeasureJsonWriter;
    this.userIndex = userIndex;
  }

  @Override
  public void define(WebService.NewController context) {
    WebService.NewAction action = context.createAction(ACTION)
      .setDescription("Create a custom measure.<br /> " +
        "The project id or the project key must be provided (only project and module custom measures can be created). The metric id or the metric key must be provided.<br/>" +
        "Requires 'Administer System' permission or 'Administer' permission on the project.")
      .setSince("5.2")
      .setPost(true)
      .setHandler(this);

    action.createParam(PARAM_PROJECT_ID)
      .setDescription("Project id")
      .setExampleValue("ce4c03d6-430f-40a9-b777-ad877c00aa4d");

    action.createParam(PARAM_PROJECT_KEY)
      .setDescription("Project key")
      .setExampleValue("org.apache.hbas:hbase");

    action.createParam(PARAM_METRIC_ID)
      .setDescription("Metric id")
      .setExampleValue("16");

    action.createParam(PARAM_METRIC_KEY)
      .setDescription("Metric key")
      .setExampleValue("ncloc");

    action.createParam(PARAM_VALUE)
      .setRequired(true)
      .setDescription(measureValueDescription())
      .setExampleValue("47");

    action.createParam(PARAM_DESCRIPTION)
      .setDescription("Description")
      .setExampleValue("Team size growing.");
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    DbSession dbSession = dbClient.openSession(false);
    String valueAsString = request.mandatoryParam(PARAM_VALUE);
    String description = request.param(PARAM_DESCRIPTION);
    long now = system.now();

    try {
      ComponentDto component = searchProject(dbSession, request);
      MetricDto metric = searchMetric(dbSession, request);
      checkPermissions(component);
      checkIsProjectOrModule(component);
      checkMeasureDoesNotExistAlready(dbSession, component, metric);
      UserDoc user = userIndex.getByLogin(userSession.getLogin());
      CustomMeasureDto measure = new CustomMeasureDto()
        .setComponentUuid(component.uuid())
        .setComponentId(component.getId())
        .setMetricId(metric.getId())
        .setDescription(description)
        .setUserLogin(user.login())
        .setCreatedAt(now)
        .setUpdatedAt(now);
      validator.setMeasureValue(measure, valueAsString, metric);
      dbClient.customMeasureDao().insert(dbSession, measure);
      dbSession.commit();

      JsonWriter json = response.newJsonWriter();
      customMeasureJsonWriter.write(json, measure, metric, component, user);
      json.close();
    } finally {
      MyBatis.closeQuietly(dbSession);
    }
  }

  private static void checkIsProjectOrModule(ComponentDto component) {
    if (!Qualifiers.PROJECT.equals(component.qualifier()) && !Qualifiers.MODULE.equals(component.qualifier())) {
      throw new ServerException(HttpURLConnection.HTTP_CONFLICT, String.format("Component '%s' (id: %s) must be a project or a module.", component.key(), component.uuid()));
    }
  }

  private void checkPermissions(ComponentDto component) {
    if (userSession.hasGlobalPermission(GlobalPermissions.SYSTEM_ADMIN)) {
      return;
    }

    userSession.checkLoggedIn().checkProjectUuidPermission(UserRole.ADMIN, component.projectUuid());
  }

  private void checkMeasureDoesNotExistAlready(DbSession dbSession, ComponentDto component, MetricDto metric) {
    int nbMeasuresOnSameMetricAndMeasure = dbClient.customMeasureDao().countByComponentIdAndMetricId(dbSession, component.uuid(), metric.getId());
    if (nbMeasuresOnSameMetricAndMeasure > 0) {
      throw new ServerException(HttpURLConnection.HTTP_CONFLICT, String.format("A measure already exists for project '%s' (id: %s) and metric '%s' (id: '%d')",
        component.key(), component.uuid(), metric.getKey(), metric.getId()));
    }
  }

  private MetricDto searchMetric(DbSession dbSession, Request request) {
    Integer metricId = request.paramAsInt(PARAM_METRIC_ID);
    String metricKey = request.param(PARAM_METRIC_KEY);
    checkArgument(metricId != null ^ metricKey != null, "The metric id or the metric key must be provided, not both.");

    if (metricId != null) {
      return dbClient.metricDao().selectById(dbSession, metricId);
    }

    return dbClient.metricDao().selectByKey(dbSession, metricKey);
  }

  private ComponentDto searchProject(DbSession dbSession, Request request) {
    String projectUuid = request.param(PARAM_PROJECT_ID);
    String projectKey = request.param(PARAM_PROJECT_KEY);
    checkArgument(projectUuid != null ^ projectKey != null, "The project key or the project id must be provided, not both.");

    if (projectUuid != null) {
      ComponentDto project = dbClient.componentDao().selectNullableByUuid(dbSession, projectUuid);
      if (project == null) {
        throw new NotFoundException(String.format("Project id '%s' not found", projectUuid));
      }

      return project;
    }

    ComponentDto project = dbClient.componentDao().selectNullableByKey(dbSession, projectKey);
    if (project == null) {
      throw new NotFoundException(String.format("Project key '%s' not found", projectKey));
    }

    return project;
  }
}
