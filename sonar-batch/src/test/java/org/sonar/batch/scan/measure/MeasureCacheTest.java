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
package org.sonar.batch.scan.measure;

import java.util.Date;
import java.util.Iterator;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.measure.MetricFinder;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.Measure;
import org.sonar.api.measures.Metric.Level;
import org.sonar.api.resources.Directory;
import org.sonar.api.resources.File;
import org.sonar.api.resources.Project;
import org.sonar.api.resources.Resource;
import org.sonar.batch.index.Cache.Entry;
import org.sonar.batch.index.Caches;
import org.sonar.batch.index.CachesTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MeasureCacheTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  Caches caches;

  private MetricFinder metricFinder;

  private MeasureCache cache;

  @Before
  public void start() {
    caches = CachesTest.createCacheOnTemp(temp);
    caches.start();
    metricFinder = mock(MetricFinder.class);
    when(metricFinder.findByKey(CoreMetrics.NCLOC_KEY)).thenReturn(CoreMetrics.NCLOC);
    cache = new MeasureCache(caches, metricFinder);
  }

  @After
  public void stop() {
    caches.stop();
  }

  @Test
  public void should_add_measure() {
    Project p = new Project("struts");

    assertThat(cache.entries()).hasSize(0);
    assertThat(cache.byResource(p)).hasSize(0);

    Measure m = new Measure(CoreMetrics.NCLOC, 1.0);
    cache.put(p, m);

    assertThat(cache.contains(p, m)).isTrue();
    assertThat(cache.entries()).hasSize(1);
    Iterator<Entry<Measure>> iterator = cache.entries().iterator();
    iterator.hasNext();
    Entry<Measure> next = iterator.next();
    assertThat(next.value()).isEqualTo(m);
    assertThat(next.key()[0]).isEqualTo("struts");

    assertThat(cache.byResource(p)).hasSize(1);
    assertThat(cache.byResource(p).iterator().next()).isEqualTo(m);
  }

  @Test
  public void should_add_measure_with_big_data() {
    Project p = new Project("struts");

    assertThat(cache.entries()).hasSize(0);

    assertThat(cache.byResource(p)).hasSize(0);

    Measure m = new Measure(CoreMetrics.NCLOC, 1.0).setDate(new Date());
    m.setAlertText("foooooooooooooooooooooooooooooooooooo");
    StringBuilder data = new StringBuilder();
    for (int i = 0; i < 1048575; i++) {
      data.append("a");
    }
    m.setData(data.toString());

    cache.put(p, m);

    assertThat(cache.contains(p, m)).isTrue();
    assertThat(cache.entries()).hasSize(1);
    Iterator<Entry<Measure>> iterator = cache.entries().iterator();
    iterator.hasNext();
    Entry<Measure> next = iterator.next();
    assertThat(next.value()).isEqualTo(m);
    assertThat(next.key()[0]).isEqualTo("struts");

    assertThat(cache.byResource(p)).hasSize(1);
    assertThat(cache.byResource(p).iterator().next()).isEqualTo(m);
  }

  /**
   * This test fails with stock PersisitIt.
   */
  @Test
  public void should_add_measure_with_too_big_data_for_persistit_pre_patch() {
    Project p = new Project("struts");

    assertThat(cache.entries()).hasSize(0);

    assertThat(cache.byResource(p)).hasSize(0);

    Measure m = new Measure(CoreMetrics.NCLOC, 1.0).setDate(new Date());
    StringBuilder data = new StringBuilder();
    for (int i = 0; i < 500000; i++) {
      data.append("some data");
    }
    m.setData(data.toString());

    cache.put(p, m);

    assertThat(cache.contains(p, m)).isTrue();
    assertThat(cache.entries()).hasSize(1);
    Iterator<Entry<Measure>> iterator = cache.entries().iterator();
    iterator.hasNext();
    Entry<Measure> next = iterator.next();
    assertThat(next.value()).isEqualTo(m);
    assertThat(next.key()[0]).isEqualTo("struts");

    assertThat(cache.byResource(p)).hasSize(1);
    assertThat(cache.byResource(p).iterator().next()).isEqualTo(m);

    assertThat(cache.entries()).hasSize(1);
  }

  @Test
  public void should_add_measure_with_too_big_data_for_persistit() {
    Project p = new Project("struts");

    assertThat(cache.entries()).hasSize(0);

    assertThat(cache.byResource(p)).hasSize(0);

    Measure m = new Measure(CoreMetrics.NCLOC, 1.0).setDate(new Date());
    StringBuilder data = new StringBuilder(64 * 1024 * 1024 + 1);
    // Limit is 64Mo
    for (int i = 0; i < (64 * 1024 * 1024 + 1); i++) {
      data.append('a');
    }
    m.setData(data.toString());

    thrown.expect(IllegalStateException.class);
    thrown.expectMessage("Fail to put element in the cache measures");

    cache.put(p, m);
  }

  @Test
  public void should_add_measure_with_same_metric() {
    Project p = new Project("struts");

    assertThat(cache.entries()).hasSize(0);
    assertThat(cache.byResource(p)).hasSize(0);

    Measure m1 = new Measure(CoreMetrics.NCLOC, 1.0);
    Measure m2 = new Measure(CoreMetrics.NCLOC, 1.0).setPersonId(2);
    cache.put(p, m1);
    cache.put(p, m2);

    assertThat(cache.entries()).hasSize(2);
    assertThat(cache.byResource(p)).hasSize(2);
  }

  @Test
  public void should_get_measures() {
    Project p = new Project("struts");
    Resource dir = Directory.create("foo/bar").setEffectiveKey("struts:foo/bar");
    Resource file1 = Directory.create("foo/bar/File1.txt").setEffectiveKey("struts:foo/bar/File1.txt");
    Resource file2 = Directory.create("foo/bar/File2.txt").setEffectiveKey("struts:foo/bar/File2.txt");

    assertThat(cache.entries()).hasSize(0);

    assertThat(cache.byResource(p)).hasSize(0);
    assertThat(cache.byResource(dir)).hasSize(0);

    Measure mFile1 = new Measure(CoreMetrics.NCLOC, 1.0);
    cache.put(file1, mFile1);
    Measure mFile2 = new Measure(CoreMetrics.NCLOC, 3.0);
    cache.put(file2, mFile2);

    assertThat(cache.entries()).hasSize(2);
    assertThat(cache.byResource(p)).hasSize(0);
    assertThat(cache.byResource(dir)).hasSize(0);

    Measure mDir = new Measure(CoreMetrics.NCLOC, 4.0);
    cache.put(dir, mDir);

    assertThat(cache.entries()).hasSize(3);
    assertThat(cache.byResource(p)).hasSize(0);
    assertThat(cache.byResource(dir)).hasSize(1);
    assertThat(cache.byResource(dir).iterator().next()).isEqualTo(mDir);

    Measure mProj = new Measure(CoreMetrics.NCLOC, 4.0);
    cache.put(p, mProj);

    assertThat(cache.entries()).hasSize(4);
    assertThat(cache.byResource(p)).hasSize(1);
    assertThat(cache.byResource(p).iterator().next()).isEqualTo(mProj);
    assertThat(cache.byResource(dir)).hasSize(1);
    assertThat(cache.byResource(dir).iterator().next()).isEqualTo(mDir);
  }

  @Test
  public void test_measure_coder() throws Exception {
    Resource file1 = File.create("foo/bar/File1.txt").setEffectiveKey("struts:foo/bar/File1.txt");

    Measure measure = new Measure(CoreMetrics.NCLOC, 3.14);
    measure.setData("data");
    measure.setAlertStatus(Level.ERROR);
    measure.setAlertText("alert");
    measure.setDate(new Date());
    measure.setDescription("description");
    measure.setPersistenceMode(null);
    measure.setPersonId(3);
    measure.setUrl("http://foo");
    measure.setVariation1(11.0);
    measure.setVariation2(12.0);
    measure.setVariation3(13.0);
    measure.setVariation4(14.0);
    measure.setVariation5(15.0);
    cache.put(file1, measure);

    Measure savedMeasure = cache.byResource(file1).iterator().next();
    assertThat(EqualsBuilder.reflectionEquals(measure, savedMeasure)).isTrue();

  }
}
