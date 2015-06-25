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

package org.sonar.server.computation.step;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.core.measure.db.MeasureDto;
import org.sonar.core.persistence.DbSession;
import org.sonar.server.computation.component.Component;
import org.sonar.server.computation.component.DbIdsRepository;
import org.sonar.server.computation.component.DepthTraversalTypeAwareVisitor;
import org.sonar.server.computation.component.TreeRootHolder;
import org.sonar.server.computation.measure.BestValueOptimization;
import org.sonar.server.computation.measure.Measure;
import org.sonar.server.computation.measure.MeasureRepository;
import org.sonar.server.computation.measure.MeasureToMeasureDto;
import org.sonar.server.computation.metric.Metric;
import org.sonar.server.computation.metric.MetricRepository;
import org.sonar.server.db.DbClient;

import static com.google.common.collect.FluentIterable.from;
import static org.sonar.server.computation.component.ComponentVisitor.Order.PRE_ORDER;

public class PersistMeasuresStep implements ComputationStep {

  /**
   * List of metrics that should not be received from the report, as they should only by fed by the compute engine
   */
  private static final List<String> FORBIDDEN_METRIC_KEYS = ImmutableList.of(CoreMetrics.DUPLICATIONS_DATA_KEY);

  private final DbClient dbClient;
  private final MetricRepository metricRepository;
  private final DbIdsRepository dbIdsRepository;
  private final TreeRootHolder treeRootHolder;
  private final MeasureRepository measureRepository;

  public PersistMeasuresStep(DbClient dbClient, MetricRepository metricRepository, DbIdsRepository dbIdsRepository,
    TreeRootHolder treeRootHolder, MeasureRepository measureRepository) {
    this.dbClient = dbClient;
    this.metricRepository = metricRepository;
    this.dbIdsRepository = dbIdsRepository;
    this.treeRootHolder = treeRootHolder;
    this.measureRepository = measureRepository;
  }

  @Override
  public String getDescription() {
    return "Persist measures";
  }

  @Override
  public void execute() {
    DbSession dbSession = dbClient.openSession(true);
    try {
      new MeasureVisitor(dbSession).visit(treeRootHolder.getRoot());
      dbSession.commit();
    } finally {
      dbSession.close();
    }
  }

  private class MeasureVisitor extends DepthTraversalTypeAwareVisitor {
    private final DbSession session;

    private MeasureVisitor(DbSession session) {
      super(Component.Type.FILE, PRE_ORDER);
      this.session = session;
    }

    @Override
    public void visitAny(Component component) {
      Multimap<String, Measure> measures = measureRepository.getRawMeasures(component);
      long componentId = dbIdsRepository.getComponentId(component);
      long snapshotId = dbIdsRepository.getSnapshotId(component);

      persistMeasures(component, measures, componentId, snapshotId);
    }

    private void persistMeasures(Component component, Multimap<String, Measure> batchReportMeasures, long componentId, long snapshotId) {
      for (Map.Entry<String, Collection<Measure>> measures : batchReportMeasures.asMap().entrySet()) {
        String metricKey = measures.getKey();
        if (FORBIDDEN_METRIC_KEYS.contains(metricKey)) {
          throw new IllegalStateException(String.format("Measures on metric '%s' cannot be send in the report", metricKey));
        }

        Metric metric = metricRepository.getByKey(metricKey);
        Predicate<Measure> notBestValueOptimized = Predicates.not(BestValueOptimization.from(metric, component));
        for (Measure measure : from(measures.getValue()).filter(NonEmptyMeasure.INSTANCE).filter(notBestValueOptimized)) {
          MeasureDto measureDto = MeasureToMeasureDto.INSTANCE.toMeasureDto(measure, metric, componentId, snapshotId);
          dbClient.measureDao().insert(session, measureDto);
        }
      }
    }

  }

  private enum NonEmptyMeasure implements Predicate<Measure> {
    INSTANCE;

    @Override
    public boolean apply(@Nonnull Measure input) {
      return input.getValueType() != Measure.ValueType.NO_VALUE || input.hasVariations() || input.getData() != null;
    }
  }

}
