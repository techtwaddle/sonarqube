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

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.sonar.api.config.Settings;
import org.sonar.api.measures.Metric.ValueType;
import org.sonar.api.utils.System2;
import org.sonar.core.component.ComponentDto;
import org.sonar.core.measure.custom.db.CustomMeasureDto;
import org.sonar.core.metric.db.MetricDto;
import org.sonar.core.permission.GlobalPermissions;
import org.sonar.core.persistence.DbSession;
import org.sonar.core.persistence.DbTester;
import org.sonar.server.component.ComponentTesting;
import org.sonar.server.component.db.ComponentDao;
import org.sonar.server.db.DbClient;
import org.sonar.server.es.EsTester;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.exceptions.NotFoundException;
import org.sonar.server.exceptions.ServerException;
import org.sonar.server.exceptions.UnauthorizedException;
import org.sonar.server.measure.custom.persistence.CustomMeasureDao;
import org.sonar.server.metric.persistence.MetricDao;
import org.sonar.server.metric.ws.MetricTesting;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.user.index.UserDoc;
import org.sonar.server.user.index.UserIndex;
import org.sonar.server.user.index.UserIndexDefinition;
import org.sonar.server.user.ws.UserJsonWriter;
import org.sonar.server.ws.WsTester;
import org.sonar.test.DbTests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.server.measure.custom.persistence.CustomMeasureTesting.newCustomMeasureDto;
import static org.sonar.server.measure.custom.ws.UpdateAction.PARAM_DESCRIPTION;
import static org.sonar.server.measure.custom.ws.UpdateAction.PARAM_ID;
import static org.sonar.server.measure.custom.ws.UpdateAction.PARAM_VALUE;
import static org.sonar.server.util.TypeValidationsTesting.newFullTypeValidations;

@Category(DbTests.class)
public class UpdateActionTest {
  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  @Rule
  public UserSessionRule userSessionRule = UserSessionRule.standalone();
  @ClassRule
  public static DbTester db = new DbTester();
  @ClassRule
  public static EsTester es = new EsTester().addDefinitions(new UserIndexDefinition(new Settings()));
  DbClient dbClient;
  DbSession dbSession;
  System2 system;
  WsTester ws;

  @BeforeClass
  public static void setUpClass() throws Exception {
    es.putDocuments(UserIndexDefinition.INDEX, UserIndexDefinition.TYPE_USER, new UserDoc()
      .setLogin("login")
      .setName("Login")
      .setEmail("login@login.com")
      .setActive(true));
  }

  @Before
  public void setUp() throws Exception {
    dbClient = new DbClient(db.database(), db.myBatis(), new CustomMeasureDao(), new ComponentDao(), new MetricDao());
    dbSession = dbClient.openSession(false);
    db.truncateTables();
    system = mock(System2.class);
    CustomMeasureValidator validator = new CustomMeasureValidator(newFullTypeValidations());
    ws = new WsTester(new CustomMeasuresWs(new UpdateAction(dbClient, userSessionRule, system, validator, new CustomMeasureJsonWriter(new UserJsonWriter(userSessionRule)),
      new UserIndex(es.client()))));
    userSessionRule.login("login").setGlobalPermissions(GlobalPermissions.SYSTEM_ADMIN);
  }

  @After
  public void tearDown() {
    dbSession.close();
  }

  @Test
  public void update_text_value_and_description_in_db() throws Exception {
    MetricDto metric = insertNewMetric(ValueType.STRING);
    ComponentDto component = insertNewProject("project-uuid");
    CustomMeasureDto customMeasure = newCustomMeasure(component, metric)
      .setDescription("custom-measure-description")
      .setTextValue("text-measure-value");
    dbClient.customMeasureDao().insert(dbSession, customMeasure);
    dbSession.commit();
    when(system.now()).thenReturn(123_456_789L);

    ws.newPostRequest(CustomMeasuresWs.ENDPOINT, UpdateAction.ACTION)
      .setParam(PARAM_ID, String.valueOf(customMeasure.getId()))
      .setParam(PARAM_DESCRIPTION, "new-custom-measure-description")
      .setParam(PARAM_VALUE, "new-text-measure-value")
      .execute();

    CustomMeasureDto updatedCustomMeasure = dbClient.customMeasureDao().selectById(dbSession, customMeasure.getId());
    assertThat(updatedCustomMeasure.getTextValue()).isEqualTo("new-text-measure-value");
    assertThat(updatedCustomMeasure.getDescription()).isEqualTo("new-custom-measure-description");
    assertThat(updatedCustomMeasure.getUpdatedAt()).isEqualTo(123_456_789L);
    assertThat(customMeasure.getCreatedAt()).isEqualTo(updatedCustomMeasure.getCreatedAt());
  }

  @Test
  public void update_double_value_and_description_in_db() throws Exception {
    MetricDto metric = insertNewMetric(ValueType.INT);
    ComponentDto component = insertNewProject("project-uuid");
    CustomMeasureDto customMeasure = newCustomMeasure(component, metric)
      .setDescription("custom-measure-description")
      .setValue(42d);
    dbClient.customMeasureDao().insert(dbSession, customMeasure);
    dbSession.commit();

    ws.newPostRequest(CustomMeasuresWs.ENDPOINT, UpdateAction.ACTION)
      .setParam(PARAM_ID, String.valueOf(customMeasure.getId()))
      .setParam(PARAM_DESCRIPTION, "new-custom-measure-description")
      .setParam(PARAM_VALUE, "1984")
      .execute();

    CustomMeasureDto updatedCustomMeasure = dbClient.customMeasureDao().selectById(dbSession, customMeasure.getId());
    assertThat(updatedCustomMeasure.getValue()).isCloseTo(1984d, offset(0.01d));
    assertThat(updatedCustomMeasure.getDescription()).isEqualTo("new-custom-measure-description");
    assertThat(customMeasure.getCreatedAt()).isEqualTo(updatedCustomMeasure.getCreatedAt());
  }

  @Test
  public void returns_full_object_in_response() throws Exception {
    MetricDto metric = MetricTesting.newMetricDto().setEnabled(true)
      .setValueType(ValueType.STRING.name())
      .setKey("metric-key");
    dbClient.metricDao().insert(dbSession, metric);
    ComponentDto component = ComponentTesting.newProjectDto("project-uuid").setKey("project-key");
    dbClient.componentDao().insert(dbSession, component);
    CustomMeasureDto customMeasure = newCustomMeasure(component, metric)
      .setCreatedAt(100_000_000L)
      .setDescription("custom-measure-description")
      .setTextValue("text-measure-value");
    dbClient.customMeasureDao().insert(dbSession, customMeasure);
    dbSession.commit();
    when(system.now()).thenReturn(123_456_789L);

    WsTester.Result response = ws.newPostRequest(CustomMeasuresWs.ENDPOINT, UpdateAction.ACTION)
      .setParam(PARAM_ID, String.valueOf(customMeasure.getId()))
      .setParam(PARAM_DESCRIPTION, "new-custom-measure-description")
      .setParam(PARAM_VALUE, "new-text-measure-value")
      .execute();

    response.assertJson(getClass(), "custom-measure.json");
    String responseAsString = response.outputAsString();
    assertThat(responseAsString).matches(String.format(".*\"id\"\\s*:\\s*\"%s\".*", customMeasure.getId()));
    assertThat(responseAsString).matches(String.format(".*\"id\"\\s*:\\s*\"%s\".*", metric.getId()));
    assertThat(responseAsString).matches(".*createdAt.*updatedAt.*");
  }

  @Test
  public void update_value_only() throws Exception {
    MetricDto metric = insertNewMetric(ValueType.STRING);
    ComponentDto component = insertNewProject("project-uuid");
    CustomMeasureDto customMeasure = newCustomMeasure(component, metric)
      .setDescription("custom-measure-description")
      .setTextValue("text-measure-value");
    dbClient.customMeasureDao().insert(dbSession, customMeasure);
    dbSession.commit();
    when(system.now()).thenReturn(123_456_789L);

    ws.newPostRequest(CustomMeasuresWs.ENDPOINT, UpdateAction.ACTION)
      .setParam(PARAM_ID, String.valueOf(customMeasure.getId()))
      .setParam(PARAM_DESCRIPTION, "new-custom-measure-description")
      .execute();

    CustomMeasureDto updatedCustomMeasure = dbClient.customMeasureDao().selectById(dbSession, customMeasure.getId());
    assertThat(updatedCustomMeasure.getTextValue()).isEqualTo("text-measure-value");
    assertThat(updatedCustomMeasure.getDescription()).isEqualTo("new-custom-measure-description");
    assertThat(updatedCustomMeasure.getUpdatedAt()).isEqualTo(123_456_789L);
    assertThat(customMeasure.getCreatedAt()).isEqualTo(updatedCustomMeasure.getCreatedAt());
  }

  @Test
  public void update_description_only() throws Exception {
    MetricDto metric = insertNewMetric(ValueType.STRING);
    ComponentDto component = insertNewProject("project-uuid");
    CustomMeasureDto customMeasure = newCustomMeasure(component, metric)
      .setMetricId(metric.getId())
      .setComponentId(component.getId())
      .setComponentUuid(component.uuid())
      .setCreatedAt(system.now())
      .setDescription("custom-measure-description")
      .setTextValue("text-measure-value");
    dbClient.customMeasureDao().insert(dbSession, customMeasure);
    dbSession.commit();
    when(system.now()).thenReturn(123_456_789L);

    ws.newPostRequest(CustomMeasuresWs.ENDPOINT, UpdateAction.ACTION)
      .setParam(PARAM_ID, String.valueOf(customMeasure.getId()))
      .setParam(PARAM_VALUE, "new-text-measure-value")
      .execute();

    CustomMeasureDto updatedCustomMeasure = dbClient.customMeasureDao().selectById(dbSession, customMeasure.getId());
    assertThat(updatedCustomMeasure.getTextValue()).isEqualTo("new-text-measure-value");
    assertThat(updatedCustomMeasure.getDescription()).isEqualTo("custom-measure-description");
    assertThat(updatedCustomMeasure.getUpdatedAt()).isEqualTo(123_456_789L);
    assertThat(customMeasure.getCreatedAt()).isEqualTo(updatedCustomMeasure.getCreatedAt());
  }

  @Test
  public void fail_if_get_request() throws Exception {
    expectedException.expect(ServerException.class);

    ws.newGetRequest(CustomMeasuresWs.ENDPOINT, UpdateAction.ACTION)
      .setParam(PARAM_ID, "42")
      .setParam(PARAM_DESCRIPTION, "new-custom-measure-description")
      .setParam(PARAM_VALUE, "1984")
      .execute();
  }

  @Test
  public void fail_if_not_in_db() throws Exception {
    expectedException.expect(NotFoundException.class);
    expectedException.expectMessage("Custom measure '42' not found.");

    ws.newPostRequest(CustomMeasuresWs.ENDPOINT, UpdateAction.ACTION)
      .setParam(PARAM_ID, "42")
      .setParam(PARAM_DESCRIPTION, "new-custom-measure-description")
      .setParam(PARAM_VALUE, "1984")
      .execute();
  }

  @Test
  public void fail_if_insufficient_privileges() throws Exception {
    userSessionRule.login("login").setGlobalPermissions(GlobalPermissions.SCAN_EXECUTION);
    expectedException.expect(ForbiddenException.class);
    MetricDto metric = MetricTesting.newMetricDto().setEnabled(true).setValueType(ValueType.STRING.name());
    dbClient.metricDao().insert(dbSession, metric);
    ComponentDto component = ComponentTesting.newProjectDto("project-uuid");
    dbClient.componentDao().insert(dbSession, component);
    CustomMeasureDto customMeasure = newCustomMeasureDto()
      .setMetricId(metric.getId())
      .setComponentId(component.getId())
      .setComponentUuid(component.uuid())
      .setCreatedAt(system.now())
      .setDescription("custom-measure-description")
      .setTextValue("text-measure-value");
    dbClient.customMeasureDao().insert(dbSession, customMeasure);
    dbSession.commit();

    ws.newPostRequest(CustomMeasuresWs.ENDPOINT, UpdateAction.ACTION)
      .setParam(PARAM_ID, String.valueOf(customMeasure.getId()))
      .setParam(PARAM_DESCRIPTION, "new-custom-measure-description")
      .setParam(PARAM_VALUE, "1984")
      .execute();
  }

  @Test
  public void fail_if_not_logged_in() throws Exception {
    userSessionRule.anonymous();
    expectedException.expect(UnauthorizedException.class);
    MetricDto metric = MetricTesting.newMetricDto().setEnabled(true).setValueType(ValueType.STRING.name());
    dbClient.metricDao().insert(dbSession, metric);
    ComponentDto component = ComponentTesting.newProjectDto("project-uuid");
    dbClient.componentDao().insert(dbSession, component);
    CustomMeasureDto customMeasure = newCustomMeasureDto()
      .setMetricId(metric.getId())
      .setComponentId(component.getId())
      .setComponentUuid(component.uuid())
      .setCreatedAt(system.now())
      .setDescription("custom-measure-description")
      .setTextValue("text-measure-value");
    dbClient.customMeasureDao().insert(dbSession, customMeasure);
    dbSession.commit();

    ws.newPostRequest(CustomMeasuresWs.ENDPOINT, UpdateAction.ACTION)
      .setParam(PARAM_ID, String.valueOf(customMeasure.getId()))
      .setParam(PARAM_DESCRIPTION, "new-custom-measure-description")
      .setParam(PARAM_VALUE, "1984")
      .execute();
  }

  @Test
  public void fail_if_custom_measure_id_is_missing_in_request() throws Exception {
    expectedException.expect(IllegalArgumentException.class);

    ws.newPostRequest(CustomMeasuresWs.ENDPOINT, UpdateAction.ACTION)
      .setParam(PARAM_DESCRIPTION, "new-custom-measure-description")
      .setParam(PARAM_VALUE, "1984")
      .execute();
  }

  @Test
  public void fail_if_custom_measure_value_and_description_are_missing_in_request() throws Exception {
    expectedException.expect(IllegalArgumentException.class);

    ws.newPostRequest(CustomMeasuresWs.ENDPOINT, UpdateAction.ACTION)
      .setParam(PARAM_ID, "42")
      .execute();
  }

  private MetricDto insertNewMetric(ValueType metricType) {
    MetricDto metric = MetricTesting.newMetricDto().setEnabled(true).setValueType(metricType.name());
    dbClient.metricDao().insert(dbSession, metric);

    return metric;
  }

  private ComponentDto insertNewProject(String uuid) {
    ComponentDto component = ComponentTesting.newProjectDto(uuid);
    dbClient.componentDao().insert(dbSession, component);
    return component;
  }

  private CustomMeasureDto newCustomMeasure(ComponentDto project, MetricDto metric) {
    return newCustomMeasureDto()
      .setMetricId(metric.getId())
      .setComponentId(project.getId())
      .setComponentUuid(project.uuid())
      .setCreatedAt(system.now());
  }
}
