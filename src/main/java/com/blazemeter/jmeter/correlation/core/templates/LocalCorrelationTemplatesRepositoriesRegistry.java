package com.blazemeter.jmeter.correlation.core.templates;

import static com.blazemeter.jmeter.correlation.core.templates.LocalCorrelationTemplatesRegistry.PROPERTIES_FILE_SUFFIX;
import static com.blazemeter.jmeter.correlation.core.templates.LocalCorrelationTemplatesRegistry.SNAPSHOT_FILE_TYPE;
import static com.blazemeter.jmeter.correlation.core.templates.LocalCorrelationTemplatesRegistry.TEMPLATE_FILE_SUFFIX;
import static com.blazemeter.jmeter.correlation.core.templates.RepositoryGeneralConst.LOCAL_REPOSITORY_NAME;
import static com.blazemeter.jmeter.correlation.core.templates.repository.RepositoryUtils.removeRepositoryNameFromFile;
import static org.apache.commons.io.FileUtils.copyFile;

import com.blazemeter.jmeter.correlation.core.templates.repository.RepositoryUtils;
import com.blazemeter.jmeter.correlation.core.templates.repository.TemplateProperties;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalCorrelationTemplatesRepositoriesRegistry implements
    CorrelationTemplatesRepositoriesRegistry {

  public static final String SNAPSHOT_SUFFIX = "-snapshot";
  public static final String SNAPSHOT_FILE_EXTENSION = "." + SNAPSHOT_FILE_TYPE;
  public static final String SNAPSHOT_FILE_SUFFIX = SNAPSHOT_SUFFIX + SNAPSHOT_FILE_EXTENSION;

  private static final Logger LOG = LoggerFactory
      .getLogger(LocalCorrelationTemplatesRepositoriesRegistry.class);

  protected LocalConfiguration configuration;

  public LocalCorrelationTemplatesRepositoriesRegistry(LocalConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  public void save(String name, String url) throws IOException {
    String path = url.replace("file://", "");
    if (Files.exists(Paths.get(path))) {
      String repositoryFolderName = name + File.separator;

      String installationFolderPath =
          Paths.get(
              configuration.getCorrelationsTemplateInstallationFolder(), repositoryFolderName
          ).toAbsolutePath() + File.separator;

      File repositoryFolder = new File(installationFolderPath);
      if (!repositoryFolder.exists() && repositoryFolder.mkdir()) {
        LOG.info("Created the folder for the repository {}", name);
        configuration.addRepository(name, url);
      }
      String repositoryFilePath =
          installationFolderPath + RepositoryUtils.getRepositoryFileName(name);

      copyFileFromPath(path, repositoryFilePath);

      String basePath = getBasePath(path);
      Map<String, CorrelationTemplateVersions> templatesReferences = readTemplatesVersions(
          new File(installationFolderPath + RepositoryUtils.getRepositoryFileName(name)));

      for (Map.Entry<String, CorrelationTemplateVersions> entry : templatesReferences.entrySet()) {
        for (String version : entry.getValue().getVersions()) {
          String templateFileName = entry.getKey() + "-" + version;

          copyFileFromPath(basePath + templateFileName + TEMPLATE_FILE_SUFFIX,
              installationFolderPath + templateFileName + TEMPLATE_FILE_SUFFIX);

          File templateSnapshotFile = new File(basePath + templateFileName + SNAPSHOT_FILE_SUFFIX);
          if (templateSnapshotFile.exists()) {
            copyFileFromPath(basePath + templateFileName + SNAPSHOT_FILE_SUFFIX,
                templateFileName + SNAPSHOT_FILE_SUFFIX);
          }
        }
      }
    } else {
      throw new IOException(url + " file does not exists");
    }
  }

  private String getBasePath(String path) {
    return Paths.get(path).getParent().toAbsolutePath() + File.separator;
  }

  private void copyFileFromPath(String source, String templateFileName) throws IOException {
    File localTemplateFile = new File(templateFileName);
    if (!localTemplateFile.exists() && localTemplateFile.createNewFile()) {
      copyFile(new File(source), new File(templateFileName));
      LOG.info("Created the file {}", localTemplateFile);
    }
  }

  @Override
  public List<CorrelationTemplatesRepository> getRepositories() {
    List<CorrelationTemplatesRepository> correlationRepositoryList = new ArrayList<>();
    List<String> repositoriesList = configuration.getRepositoriesNames();
    repositoriesList.forEach(r -> {
      File repositoryFile = new File(
          Paths.get(
                  configuration.getCorrelationsTemplateInstallationFolder(),
                  (
                      r.equals(LOCAL_REPOSITORY_NAME)
                          ? ""
                          : r + File.separator) + RepositoryUtils.getRepositoryFileName(r))
              .toAbsolutePath().toString()
      );
      String repoName = removeRepositoryNameFromFile(repositoryFile.getName());
      if (repositoryFile.exists()) {
        try {
          CorrelationTemplatesRepository loadedRepository =
              new CorrelationTemplatesRepository(repoName,
                  readTemplatesVersions(repositoryFile));

          correlationRepositoryList.add(loadedRepository);
        } catch (IOException e) {
          LOG.error("There was an issue trying to read the file {}.", repositoryFile.getName(), e);
        }
      } else {
        LOG.warn("Repository file not found {}.", repositoryFile);
      }
    });

    return correlationRepositoryList;
  }

  public Map<String, CorrelationTemplateVersions> readTemplatesVersions(File source)
      throws IOException {
    return configuration.readTemplatesReferences(source);
  }

  @Override
  public CorrelationTemplatesRepository find(String id) {
    Optional<String> foundRepository = configuration.getRepositoriesNames().stream()
        .filter(r -> r.equals(id))
        .findAny();

    if (!foundRepository.isPresent()) {
      return null;
    }

    String repositoryFolderPath = (id.equals(LOCAL_REPOSITORY_NAME) ? "" : id) + File.separator;

    try {
      File source = new File(
          Paths.get(
              configuration.getCorrelationsTemplateInstallationFolder(), repositoryFolderPath,
              RepositoryUtils.getRepositoryFileName(id)).toAbsolutePath().toString());
      Map<String, CorrelationTemplateVersions> templatesReferences = readTemplatesVersions(
          source);

      return new CorrelationTemplatesRepository(
          removeRepositoryNameFromFile(source.getName()),
          templatesReferences);
    } catch (IOException e) {
      LOG.warn("There was and issue trying to get the templates from the repository.", e);
    }

    return null;
  }

  @Override
  public void delete(String name) throws IOException {
    configuration.removeRepository(name);

    File repositoryFolder = new File(
        configuration.getCorrelationsTemplateInstallationFolder() + name);
    if (!repositoryFolder.exists()) {
      LOG.error("The folder for the repository {} didn't exists. Only removed from configuration",
          name);
      return;
    }

    if (repositoryFolder.isDirectory()) {
      LOG.info("Removing {}'s repository folder at {}.", name, repositoryFolder.getAbsolutePath());
      FileUtils.deleteDirectory(repositoryFolder);
    } else {
      LOG.warn(
          "The repository {} doesn't seems to have a folder, {} was found instead. Only removed "
              + "from the configuration.",
          name, repositoryFolder.getName());
    }
  }

  @Override
  public Map<Template, TemplateProperties> getCorrelationTemplatesAndPropertiesByRepositoryId(
      String id) {
    List<File> templates = getTemplatesFilesByRepositoryId(id);
    Map<Template, TemplateProperties> relatedTemplates = new HashMap<>();
    templates.forEach(templateFile -> {
      try {
        Template template = loadTemplateFromFile(id, templateFile);
        relatedTemplates.put(template, loadTemplatePropertiesFromFile(templateFile));
      } catch (IOException e) {
        LOG.warn("There was an issue trying to get the Template from {}.", templateFile, e);
      }
    });

    return relatedTemplates;
  }

  @Override
  public Map<Template, TemplateProperties> getCorrelationTemplatesAndPropertiesByRepositoryId(
      String id, List<TemplateVersion> filter) {
    List<File> templatesFiles = getTemplatesFilesByRepositoryId(id);

    Map<Template, TemplateProperties> relatedTemplates = new HashMap<>();
    templatesFiles.forEach(file -> {
      try {
        Template template = loadTemplateFromFile(id, file);
        filter.forEach(f -> {
          if (template.getId().equals(f.getName())
              && template.getVersion().equals(f.getVersion())) {
            try {
              relatedTemplates.put(template, loadTemplatePropertiesFromFile(file));
            } catch (IOException e) {
              LOG.warn("There was an issue trying to get the Template from {}.", file, e);
            }
          }
        });
      } catch (IOException e) {
        LOG.warn("There was an issue trying to get the Template from {}.", file, e);
      }
    });
    return relatedTemplates;
  }

  private Template loadTemplateFromFile(String repositoryId, File templateFile) throws IOException {
    Template template = configuration.readValue(templateFile, Template.class);
    template.setRepositoryId(repositoryId);
    template.setInstalled(
        configuration.isInstalled(repositoryId, template.getId(), template.getVersion()));
    return template;
  }

  private List<File> getTemplatesFilesByRepositoryId(String id) {
    return Stream.of(Objects.requireNonNull((new File(
            configuration.getCorrelationsTemplateInstallationFolder() + (
                id.equals(LOCAL_REPOSITORY_NAME) ? "" : id)))
            .listFiles()))
        .filter(f -> f.getName().endsWith(TEMPLATE_FILE_SUFFIX))
        .collect(Collectors.toList());
  }

  private TemplateProperties loadTemplatePropertiesFromFile(File templateFile) throws IOException {
    String propertiesFilepath =
        templateFile.getAbsolutePath().replace(TEMPLATE_FILE_SUFFIX, PROPERTIES_FILE_SUFFIX);
    TemplateProperties templateProperties = new TemplateProperties();
    if (Files.notExists(Paths.get(propertiesFilepath))) {
      LOG.warn("The properties file '{}' for the template '{}' was not found.",
          propertiesFilepath, templateFile.getName());
    } else {
      templateProperties =
          configuration.readValue(new File(propertiesFilepath), TemplateProperties.class);
    }
    return templateProperties;
  }

  @Override
  public Map<String, CorrelationTemplateVersions> getCorrelationTemplateVersionsByRepositoryId(
      String name) {
    File repositoryFile = new File(
        Paths.get(
                configuration.getCorrelationsTemplateInstallationFolder(),
                (
                    name.equals(LOCAL_REPOSITORY_NAME)
                        ? ""
                        : name + File.separator) + RepositoryUtils.getRepositoryFileName(name))
            .toAbsolutePath().toString()
    );
    try {
      return readTemplatesVersions(repositoryFile);
    } catch (IOException e) {
      LOG.error("There was an issue trying to read the file {}.", repositoryFile.getName(), e);
    }
    return null;
  }

  public boolean isLocalTemplateVersionSaved(String templateId, String templateVersion) {
    return Stream.of(Objects.requireNonNull((new File(
            configuration.getCorrelationsTemplateInstallationFolder()))
            .listFiles()))
        .anyMatch(f -> f.getName().toLowerCase()
            .startsWith(templateId.toLowerCase() + "-" + templateVersion.toLowerCase())
            && f.getName().endsWith(TEMPLATE_FILE_SUFFIX));
  }

  public void installTemplate(String repositoryName, String templateId, String templateVersion)
      throws ConfigurationException {
    manageTemplate(LocalConfiguration.INSTALL, repositoryName, templateId, templateVersion);
  }

  private void manageTemplate(String action, String repositoryName, String templateId,
                              String templateVersion) throws ConfigurationException {

    String repositoryFolderPath =
        repositoryName.equals(LOCAL_REPOSITORY_NAME) ? "" : repositoryName + File.separator;

    File template = new File(
        Paths.get(
            configuration.getCorrelationsTemplateInstallationFolder(),
            repositoryFolderPath,
            templateId + "-" + templateVersion + TEMPLATE_FILE_SUFFIX).toAbsolutePath().toString()
    );

    if (!template.exists()) {
      LOG.error("The template {} doesn't exists", template.getName());
      throw new ConfigurationException(
          "The template " + template.getAbsolutePath() + " doesn't exists");
    }

    configuration.manageTemplate(action, repositoryName, templateId, templateVersion);
  }

  public void uninstallTemplate(String repositoryName, String templateId, String templateVersion)
      throws ConfigurationException {
    manageTemplate(LocalConfiguration.UNINSTALL, repositoryName, templateId, templateVersion);
  }

  public String getRepositoryURL(String name) {
    return configuration.getRepositoryURL(name);
  }

  public void updateLocalRepository(String templateId, String templateVersion) {
    File localRepositoryFile = new File(
        configuration.getCorrelationsTemplateInstallationFolder()
            + RepositoryUtils.getRepositoryFileName(LOCAL_REPOSITORY_NAME));
    try {
      CorrelationTemplatesRepository localRepository;

      if (!localRepositoryFile.exists()) {
        localRepositoryFile.createNewFile();
        localRepository = new CorrelationTemplatesRepository();
        localRepository.setTemplates(new HashMap<String, CorrelationTemplateVersions>() {
        });
        configuration.writeValue(localRepositoryFile, localRepository.getTemplates());
        LOG.info("No local repository file found. Created a new one instead");
      } else {
        localRepository = new CorrelationTemplatesRepository("local",
            readTemplatesVersions(localRepositoryFile));
      }

      localRepository.addTemplate(templateId, templateVersion);
      configuration.writeValue(localRepositoryFile, localRepository.getTemplates());
      configuration.addRepository(LOCAL_REPOSITORY_NAME, localRepositoryFile.getAbsolutePath());
    } catch (IOException e) {
      LOG.warn("There was a problem trying to update the local repository file.", e);
    }
  }
}
