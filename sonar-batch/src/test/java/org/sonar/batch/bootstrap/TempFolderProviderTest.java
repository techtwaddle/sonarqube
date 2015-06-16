package org.sonar.batch.bootstrap;

import org.apache.commons.io.FileUtils;
import org.sonar.api.utils.TempFolder;
import com.google.common.collect.ImmutableMap;
import org.sonar.api.CoreProperties;

import java.io.File;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TempFolderProviderTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private TempFolderProvider tempFolderProvider = new TempFolderProvider();

  @Test
  public void createTempFolderProps() throws Exception {
    File workingDir = temp.newFolder();

    TempFolder tempFolder = tempFolderProvider.provide(new BootstrapProperties(ImmutableMap.of(CoreProperties.GLOBAL_WORKING_DIRECTORY, workingDir.getAbsolutePath())));
    tempFolder.newDir();
    tempFolder.newFile();
    assertThat(new File(workingDir, TempFolderProvider.TMP_NAME)).exists();
    assertThat(new File(workingDir, ".sonartmp").list()).hasSize(2);
  }

  @Test
  public void createTempFolderSonarHome() throws Exception {
    //with sonar home, it will be in {sonar.home}/.sonartmp
    File sonarHome = temp.newFolder();
    File tmpDir = new File(new File(sonarHome, CoreProperties.GLOBAL_WORKING_DIRECTORY_DEFAULT_VALUE), TempFolderProvider.TMP_NAME);

    TempFolder tempFolder = tempFolderProvider.provide(new BootstrapProperties(ImmutableMap.of("sonar.userHome", sonarHome.getAbsolutePath())));
    tempFolder.newDir();
    tempFolder.newFile();
    assertThat(tmpDir).exists();
    assertThat(tmpDir.list()).hasSize(2);
  }

  @Test
  public void createTempFolderDefault() throws Exception {
    // if nothing is defined, it will be in {user.home}/.sonar/.sonartmp
    File defaultSonarHome = new File(System.getProperty("user.home"), ".sonar");
    File tmpDir = new File(new File(defaultSonarHome, CoreProperties.GLOBAL_WORKING_DIRECTORY_DEFAULT_VALUE), TempFolderProvider.TMP_NAME);
    
    try {
      TempFolder tempFolder = tempFolderProvider.provide(new BootstrapProperties(Collections.<String, String>emptyMap()));
      tempFolder.newDir();
      tempFolder.newFile();
      assertThat(tmpDir).exists();
      assertThat(tmpDir.list()).hasSize(2);
    } finally {
      FileUtils.deleteDirectory(tmpDir);
    }
  }
}
